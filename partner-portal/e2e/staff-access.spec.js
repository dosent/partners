import { expect, test, loginAsStaff } from './support/fixtures.js';
import { uniqueFixtureId } from './support/test-data.js';
import { E2E_BASE_URL } from './support/base-url.js';

async function csrfHeader(request) {
  const response = await request.get('/api/v1/auth/csrf');
  expect(response.status(), 'CSRF token endpoint status').toBe(200);
  const payload = await response.json();
  return { [payload.headerName ?? 'X-XSRF-TOKEN']: payload.token };
}

async function replaceRoles(request, username, roles) {
  return request.put(`/api/v1/admin/staff-access/users/${encodeURIComponent(username)}/roles`, {
    data: { roles },
    headers: await csrfHeader(request),
  });
}

test('unassigned staff sees ACCESS_PENDING and cannot open staff admin API', async ({ page }) => {
  const username = uniqueFixtureId();
  try {
    const session = await loginAsStaff(page, username);
    expect(session.accessState, 'staff session API state').toBe('ACCESS_PENDING');
    await expect(page.getByRole('heading', { name: 'Доступ пока не выдан' })).toBeVisible();

    const response = await page.context().request.get('/api/v1/admin/staff-access/users');
    expect(response.status(), 'staff access API permission status').toBe(403);
  } finally {
    await page.context().close();
  }
});

test('administrator grants a role and cannot remove bootstrap SYSTEM_ADMIN', async ({ page }) => {
  const targetUsername = uniqueFixtureId();
  let grantedContext;
  try {
    await loginAsStaff(page, 'ivan.neshin');
    await page.getByRole('button', { name: 'Управление доступом' }).click();
    await expect(page).toHaveURL(/\/staff\/access\/roles$/);
    await page.getByLabel('Добавить или открыть логин').fill(targetUsername);
    await page.getByRole('button', { name: 'Добавить логин' }).click();
    await page.getByLabel('Менеджер партнёров').check();
    page.once('dialog', dialog => dialog.accept());
    await page.getByRole('button', { name: 'Сохранить роли' }).click();
    await expect(page.getByRole('status')).toContainText(targetUsername);

    const browser = page.context().browser();
    grantedContext = await browser.newContext({ baseURL: E2E_BASE_URL });
    const grantedPage = await grantedContext.newPage();
    const grantedSession = await loginAsStaff(grantedPage, targetUsername);
    expect(grantedSession.accessState, 'granted staff session API state').toBe('ACTIVE');
    expect(grantedSession.roles, 'granted staff session roles').toContain('PARTNER_MANAGER');
    expect(grantedSession.permissions, 'granted staff session permissions').toContain('partner.application.decide');
    await expect(grantedPage.getByRole('button', { name: 'Модерация заявок' })).toBeVisible();

    await page.getByLabel('Добавить или открыть логин').fill('ivan.neshin');
    await page.getByRole('button', { name: 'Добавить логин' }).click();
    const bootstrapCheckbox = page.getByLabel('Системный администратор');
    await expect(bootstrapCheckbox).toBeChecked();
    await bootstrapCheckbox.uncheck();
    page.once('dialog', dialog => dialog.accept());
    const failedGrantResponse = page.waitForResponse(response =>
      response.url().includes('/api/v1/admin/staff-access/users/ivan.neshin/roles')
      && response.request().method() === 'PUT');
    await page.getByRole('button', { name: 'Сохранить роли' }).click();
    const response = await failedGrantResponse;
    expect(response.status(), 'bootstrap role update API status').toBe(400);
    await expect(page.getByRole('alert')).toBeVisible();
  } finally {
    if (grantedContext) await grantedContext.close();
    await page.context().close();
  }
});

import { expect, test, completePartnerOAuth, ensurePublishedTestContent, loginAsStaff } from './support/fixtures.js';
import { syntheticPartnerData, uniqueFixtureId } from './support/test-data.js';
import { E2E_BASE_URL } from './support/base-url.js';

async function requestJson(request, path) {
  const response = await request.get(path);
  expect(response.status(), `GET ${path} API status`).toBe(200);
  return response.json();
}

async function csrfHeaders(request) {
  const response = await request.get('/api/v1/auth/csrf');
  expect(response.status(), 'CSRF token API status').toBe(200);
  const payload = await response.json();
  return { [payload.headerName ?? 'X-XSRF-TOKEN']: payload.token };
}

async function assignRole(adminRequest, username, role) {
  const response = await adminRequest.put(`/api/v1/admin/staff-access/users/${encodeURIComponent(username)}/roles`, {
    data: { roles: [role] }, headers: await csrfHeaders(adminRequest),
  });
  expect(response.status(), `grant ${role} API status`).toBe(200);
}

async function submittedApplicationId(request, email) {
  let found;
  await expect.poll(async () => {
    const queue = await requestJson(request, '/api/v1/admin/partner-applications?page=0&size=100');
    for (const item of queue.items) {
      const detail = await requestJson(request, `/api/v1/admin/partner-applications/${item.id}`);
      if (detail.currentRevision?.data?.email === email) found = item.id;
    }
    return found ?? null;
  }).not.toBeNull();
  return found;
}

async function submitPartnerApplication(page, partner) {
  await page.goto('/');
  await page.getByRole('button', { name: 'Стать партнёром' }).click();
  await expect(page).toHaveURL(/\/registration$/);
  const startPath = await page.getByRole('link', { name: 'Войти через Яндекс ID' }).getAttribute('href');
  await completePartnerOAuth(page, partner.subject, startPath);
  await expect(page.getByRole('heading', { name: 'Заявка на партнёрство' })).toBeVisible();
  await page.locator('input[name="fullName"]').fill(partner.fullName);
  await page.locator('input[name="email"]').fill(partner.email);
  await page.locator('input[name="phone"]').fill(partner.phone);
  await page.locator('input[name="region"]').fill(partner.region);
  await page.locator('input[name="offerAccepted"]').check();
  await page.locator('input[name="privacyAccepted"]').check();
  await page.getByRole('button', { name: 'Отправить заявку' }).click();
  await expect(page.getByRole('heading', { name: 'Ручная проверка' })).toBeVisible();
}

test('moderator can request changes and staff without decision permission cannot decide', async ({ page }) => {
  const browser = page.context().browser();
  let setupContext;
  let moderatorContext;
  let unauthorizedContext;
  try {
    setupContext = await browser.newContext({ baseURL: E2E_BASE_URL });
    const setupPage = await setupContext.newPage();
    await loginAsStaff(setupPage, 'ivan.neshin');
    await ensurePublishedTestContent(setupContext.request);

    const moderatorUsername = uniqueFixtureId();
    const unauthorizedUsername = uniqueFixtureId();
    await assignRole(setupContext.request, moderatorUsername, 'PARTNER_MANAGER');
    await assignRole(setupContext.request, unauthorizedUsername, 'RESOURCE_MANAGER');

    const partner = syntheticPartnerData();
    await submitPartnerApplication(page, partner);
    const applicationId = await submittedApplicationId(setupContext.request, partner.email);

    moderatorContext = await browser.newContext({ baseURL: E2E_BASE_URL });
    const moderatorPage = await moderatorContext.newPage();
    const moderatorSession = await loginAsStaff(moderatorPage, moderatorUsername);
    expect(moderatorSession.accessState, 'moderator session API state').toBe('ACTIVE');
    expect(moderatorSession.permissions, 'moderator permission API claims').toContain('partner.application.decide');
    await moderatorPage.goto('/staff/moderation');
    await expect(moderatorPage.getByRole('heading', { name: 'Заявки партнёров' })).toBeVisible();
    await moderatorPage.getByRole('button', { name: new RegExp(applicationId) }).click();
    await expect(moderatorPage.getByRole('heading', { name: 'Физическое лицо' })).toBeVisible();
    const reason = `Просим уточнить данные тестовой заявки ${partner.id.slice(-8)}.`;
    await moderatorPage.getByLabel('Причина для отказа или запроса изменений').fill(reason);
    await moderatorPage.getByRole('button', { name: 'Запросить изменения' }).click();
    await expect(moderatorPage.getByRole('status')).toContainText('Решение сохранено');
    await expect(moderatorPage.locator('.staff-application-badge')).toHaveText('Запрошены изменения');

    const decided = await requestJson(moderatorContext.request, `/api/v1/admin/partner-applications/${applicationId}`);
    expect(decided.status, 'moderation decision API status').toBe('CHANGES_REQUESTED');
    expect(decided.statusHistory.at(-1).reason, 'moderation history API reason').toBe(reason);

    unauthorizedContext = await browser.newContext({ baseURL: E2E_BASE_URL });
    const unauthorizedPage = await unauthorizedContext.newPage();
    const unauthorizedSession = await loginAsStaff(unauthorizedPage, unauthorizedUsername);
    expect(unauthorizedSession.accessState, 'unauthorized staff session API state').toBe('ACTIVE');
    expect(unauthorizedSession.permissions, 'unauthorized staff permission API claims').not.toContain('partner.application.decide');
    await unauthorizedPage.goto('/staff/moderation');
    await expect(unauthorizedPage.getByRole('heading', { name: 'Недостаточно прав' })).toBeVisible();

    const denied = await unauthorizedContext.request.post(`/api/v1/admin/partner-applications/${applicationId}/request-changes`, {
      data: { reason: 'Несанкционированное решение из e2e.' },
      headers: await csrfHeaders(unauthorizedContext.request),
    });
    expect(denied.status(), 'unauthorized moderation decision API status').toBe(403);
  } finally {
    if (unauthorizedContext) await unauthorizedContext.close();
    if (moderatorContext) await moderatorContext.close();
    if (setupContext) await setupContext.close();
  }
});

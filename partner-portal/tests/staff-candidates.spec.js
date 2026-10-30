import { test, expect } from '@playwright/test';
import { candidateItem, fillCandidate, mockCandidates } from './staff-candidates-fixture.js';

test('create requires explicit zero experience and availability, preserves string rate and guards duplicate submit', async ({ page }) => {
  let posts = 0;
  await mockCandidates(page, { items: [], handler: async ({ request, json }) => {
    if (request.method() !== 'POST') return false;
    posts++;
    expect(request.headers()['idempotency-key']).toMatch(/^[0-9a-f-]{36}$/);
    expect(request.postData()).toContain('"amount":"2500.50"');
    expect(request.postData()).toContain('"experienceMonths":0');
    await new Promise(resolve => setTimeout(resolve, 100));
    await json(candidateItem, 201); return true;
  } });
  await page.getByRole('button', { name: 'Добавить кандидата', exact: true }).click();
  await expect(page.getByLabel('Опыт, лет', { exact: true })).toHaveValue('');
  await expect(page.getByLabel('Доступность', { exact: true })).toHaveValue('');
  await fillCandidate(page);
  await page.getByRole('button', { name: 'Сохранить', exact: true }).evaluate(button => { button.click(); button.click(); });
  await expect(page.getByRole('heading', { name: candidateItem.role, exact: true })).toBeVisible();
  expect(posts).toBe(1);
});
test('conflict preserves edit text and external key may be cleared', async ({ page }) => {
  let payload;
  await mockCandidates(page, { handler: async ({ request, json }) => {
    if (request.method() !== 'PUT') return false;
    payload = request.postDataJSON();
    await json({ code: 'CONFLICT', fieldErrors: [{ field: 'externalKey' }] }, 409); return true;
  } });
  await page.getByRole('button', { name: 'Открыть карточку' }).click();
  await page.getByRole('button', { name: 'Редактировать', exact: true }).click();
  await page.getByLabel('Внешний ключ (Jira)', { exact: true }).fill('');
  await page.getByLabel('Описание', { exact: true }).fill('Unsent edited description');
  await page.getByRole('button', { name: 'Сохранить', exact: true }).click();
  await expect(page.getByRole('alert').first()).toContainText('Введённые данные сохранены');
  await expect(page.getByLabel('Описание', { exact: true })).toHaveValue('Unsent edited description');
  expect(payload.externalKey).toBeNull();
  expect(payload.expectedVersion).toBe(1);
});
test('older search response cannot replace a newer result', async ({ page }) => {
  let oldSearch;
  await mockCandidates(page, { handler: async ({ url, json }) => {
    if (url.pathname !== '/api/v1/admin/candidates' || !url.searchParams.get('q')) return false;
    if (url.searchParams.get('q') === 'old') { await new Promise(resolve => { oldSearch = resolve; }); await json({ items: [{ ...candidateItem, role: 'Old stale role' }], page: 0, size: 20, total: 1 }); }
    else await json({ items: [{ ...candidateItem, role: 'New current role' }], page: 0, size: 20, total: 1 });
    return true;
  } });
  await page.getByRole('searchbox', { name: 'Поиск кандидатов' }).fill('old');
  await expect.poll(() => Boolean(oldSearch)).toBe(true);
  await page.getByRole('searchbox', { name: 'Поиск кандидатов' }).fill('new');
  await expect(page.getByRole('heading', { name: 'New current role' })).toBeVisible();
  oldSearch();
  await page.waitForTimeout(150);
  await expect(page.getByRole('heading', { name: 'New current role' })).toBeVisible();
  await expect(page.getByText('Old stale role')).toHaveCount(0);
});
test('missing published strings close catalogue without raw keys or generated translations', async ({ page }) => {
  await mockCandidates(page, { missingText: true });
  await expect(page.getByRole('alert')).toContainText('Содержимое выбранной локали недоступно');
  await expect(page.getByRole('button', { name: 'Добавить кандидата' })).toHaveCount(0);
  await expect(page.locator('body')).not.toContainText('candidates.');
});
test('staff without permission receives no candidate data', async ({ page }) => {
  let dataRequests = 0;
  await mockCandidates(page, { permission: false, handler: async ({ url }) => { if (url.pathname.includes('/admin/candidates')) dataRequests++; return false; } });
  await expect(page.getByRole('heading', { name: 'Недостаточно прав' })).toBeVisible();
  expect(dataRequests).toBe(0);
});

test('unpublished selected locale can be changed back without introducing translated catalogue copy', async ({ page }) => {
  await mockCandidates(page);
  await page.route('**/public/registration-content/KZ', route => route.fulfill({ json: { locale: 'KZ', strings: { 'portal.profile.errors.localeUnavailable': 'Содержимое выбранной локали недоступно.' } } }));
  await page.getByLabel('Язык интерфейса').selectOption('KZ');
  await expect(page.getByRole('alert')).toContainText('Содержимое выбранной локали недоступно');
  await page.getByRole('combobox').selectOption('RU');
  await expect(page.getByRole('button', { name: 'Добавить кандидата', exact: true })).toBeVisible();
});

for (const [status, copy] of [[422, 'CV отклонён проверкой. Введённые данные сохранены.'], [503, 'Сервис временно недоступен. Повторите попытку.']]) {
  test(`create failure ${status} identifies file rejection or temporary outage and retains form`, async ({ page }) => {
    await mockCandidates(page, { items: [], handler: async ({ request, json }) => {
      if (request.method() !== 'POST') return false;
      await json({ code: status === 422 ? 'FILE_REJECTED' : 'SERVICE_UNAVAILABLE' }, status); return true;
    } });
    await page.getByRole('button', { name: 'Добавить кандидата', exact: true }).click();
    await fillCandidate(page);
    await page.getByRole('button', { name: 'Сохранить', exact: true }).click();
    await expect(page.getByRole('alert')).toHaveText(copy);
    await expect(page.getByLabel('Описание', { exact: true })).toHaveValue('Synthetic description');
  });
}

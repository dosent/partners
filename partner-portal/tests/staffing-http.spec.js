import { test, expect } from '@playwright/test';
import { registrationContentFixture } from '../e2e/support/test-data.js';

test('partner opens staffing and retries creation without randomUUID', async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(crypto, 'randomUUID', { value: undefined, configurable: true });
  });
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const content = { locale: 'RU', ...registrationContentFixture('RU') };
  const commands = [];
  const lead = { id: '11111111-1111-4111-8111-111111111111', companyName: 'Synthetic company', requestSummary: 'Synthetic project' };
  await page.route('**/api/v1/**', route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const json = (body, status = 200) => route.fulfill({ status, json: body });
    if (path === '/api/v1/auth/yandex/session') return json({ provider: 'YANDEX', subject: 'synthetic-partner' });
    if (path === '/api/v1/partner-applications/me') return json({ status: 'APPROVED' });
    if (path === '/api/v1/partners/me') return json({ locale: 'RU', fullName: 'Synthetic partner', countryCode: 'RU' });
    if (path.startsWith('/api/v1/public/registration-content/')) return json(content);
    if (path === '/api/v1/auth/csrf') return json({ token: 'test-csrf', headerName: 'X-XSRF-TOKEN' });
    if (path === '/api/v1/partner/leads/board') return json({ columns: [{ lane: 'NEW', items: [lead], nextCursor: null }] });
    if (path === '/api/v1/partner/staffing-requests' && request.method() === 'POST') {
      commands.push({ key: request.headers()['idempotency-key'], body: request.postDataJSON() });
      if (commands.length === 1) return json({ code: 'TEMPORARILY_UNAVAILABLE' }, 503);
      return json({ id: '22222222-2222-4222-8222-222222222222', number: 'SR-12345678', status: 'NEW', ...request.postDataJSON() });
    }
    if (path === '/api/v1/partner/staffing-requests') return json({ items: [], page: 0, size: 20 });
    return json({ code: 'UNEXPECTED_TEST_REQUEST' }, 404);
  });
  await page.goto('/');
  await page.getByRole('navigation').getByRole('button', { name: 'Заявки на специалистов' }).click();
  await expect(page.getByRole('heading', { name: 'Ваши заявки' })).toBeVisible();
  await page.getByRole('button', { name: 'Создать заявку', exact: true }).first().click();
  await page.getByLabel(/Проект \/ лид/).selectOption(lead.id);
  await page.getByLabel(/Должность \/ роль/).fill('Backend developer');
  await page.getByLabel('Уровень').selectOption('Middle');
  await page.getByLabel(/Обязательные навыки и стек/).fill('Java');
  await page.getByLabel(/Задачи и ожидания/).fill('Develop synthetic project');
  await page.getByLabel('Город / страна').fill('Remote');
  await page.getByLabel('Дата подключения гибкая', { exact: true }).check();
  await page.getByRole('button', { name: 'Отправить заявку', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Не удалось создать заявку');
  await page.getByRole('button', { name: 'Отправить заявку', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'SR-12345678' })).toBeVisible();
  expect(commands).toHaveLength(2);
  expect(commands[0].key).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  expect(commands[1]).toEqual(commands[0]);
  expect(errors).toEqual([]);
});

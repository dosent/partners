import { test, expect } from '@playwright/test';
import { registrationContentFixture } from '../e2e/support/test-data.js';
import { expectDesktopLayout } from '../e2e/support/desktop-layout.js';

async function openPartner(page, { longText = false, outage = false, missingStaffing = false, registration = false } = {}) {
  const content = { locale: 'RU', ...registrationContentFixture('RU') };
  if (registration) {
    // The draft fixture needs the metadata supplied by the publication API.
    content.offers = Object.fromEntries(Object.entries(content.offers).map(([type, offer]) => [type, { ...offer, sha256: 'a'.repeat(64) }]));
    content.privacyNotice = { version: content.privacyNoticeVersion, title: content.privacyNoticeTitle, body: content.privacyNoticeBody, sha256: 'b'.repeat(64) };
  }
  if (missingStaffing) delete content.strings['staffing.list.create'];
  const lead = { id: '11111111-1111-4111-8111-111111111111', companyName: 'СинтетическаяКомпания'.repeat(9),
    requestSummary: 'Разработка корпоративной платформы с длинным описанием запроса',
    status: 'NEW', updatedAt: '2026-10-30T08:00:00Z' };
  if (longText) {
    content.strings['portal.section.leads'] = 'Мои лиды и долгосрочные возможности партнёрства с корпоративными заказчиками';
    content.strings['staffing.list.create'] = 'Создать новую заявку на подбор специалистов для корпоративного проекта';
  }
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname;
    const json = (body, status = 200) => route.fulfill({ status, json: body });
    if (path.endsWith('/auth/yandex/session')) return json({ provider: 'YANDEX', subject: 'synthetic-partner' });
    if (path.endsWith('/partner-applications/me')) return registration ? json({}, 404) : json({ status: 'APPROVED' });
    if (path.endsWith('/partners/me')) return json({ locale: 'RU', fullName: 'Синтетический партнёр', email: 'ui@example.test', phone: '+79000000000', countryCode: 'RU' });
    if (path.includes('/public/registration-content/')) return json(content);
    if (path.endsWith('/partner/leads/board')) return outage
      ? json({ message: 'Временная ошибка загрузки синтетических данных' }, 503)
      : json({ columns: [{ lane: 'NEW', total: 1, items: [lead], nextCursor: null }] });
    if (path.endsWith('/partner/staffing-requests')) return json({ items: [], page: 0, size: 20 });
    return json({ code: 'UNEXPECTED_TEST_REQUEST' }, 404);
  });
  await page.goto('/');
  if (registration) await expect(page.locator('.registration-card')).toBeVisible();
  else await expect(page.getByRole('navigation')).toBeVisible();
  return content;
}

for (const width of [1024, 1280, 1440, 1920]) {
  test(`desktop actions and section headings stay aligned at ${width}px`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    const content = await openPartner(page);
    const nav = page.getByRole('navigation');
    const overviewLeft = await expectDesktopLayout(page);
    await nav.getByRole('button', { name: 'Мои лиды', exact: true }).click();
    await expect(page.locator('.plb-card')).toHaveCount(1);
    const leadsLeft = await expectDesktopLayout(page, 'Добавить лид');
    expect(Math.abs(leadsLeft - overviewLeft)).toBeLessThanOrEqual(1);
    if (width === 1440) await page.screenshot({ path: testInfo.outputPath('leads-desktop.png') });
    const controls = await page.locator('.plb-toolbar > *').evaluateAll(elements => elements
      .filter(el => el.getBoundingClientRect().width > 0)
      .map(el => { const r = el.getBoundingClientRect(); return { x:r.x, y:r.y, right:r.right, bottom:r.bottom }; }));
    for (let i = 0; i < controls.length; i++) for (let j = i + 1; j < controls.length; j++) {
      const a = controls[i], b = controls[j];
      expect(a.right <= b.x + 1 || b.right <= a.x + 1 || a.bottom <= b.y + 1 || b.bottom <= a.y + 1,
        `filter controls ${i} and ${j} overlap`).toBeTruthy();
    }
    await nav.getByRole('button', { name: 'Заявки на специалистов' }).click();
    await expect(page.getByRole('heading', { name: 'Пока нет заявок' })).toBeVisible();
    const staffingLeft = await expectDesktopLayout(page, 'Создать заявку');
    expect(Math.abs(staffingLeft - overviewLeft)).toBeLessThanOrEqual(1);
    if (width === 1440) await page.screenshot({ path: testInfo.outputPath('staffing-desktop.png') });
    await page.getByRole('button', { name: 'Создать заявку', exact: true }).first().click();
    await expect(page.getByLabel(/Должность \/ роль/)).toBeVisible();
    const formLeft = await expectDesktopLayout(page, 'Отправить заявку');
    expect(Math.abs(formLeft - overviewLeft)).toBeLessThanOrEqual(1);
    await page.getByRole('button', { name: 'К списку', exact: true }).click();
    await nav.getByRole('button', { name: content.strings['portal.profile.navigation'], exact: true }).click();
    await expect(page.getByLabel(content.strings['registration.fields.email'], { exact: true })).toBeVisible();
    expect(Math.abs(await expectDesktopLayout(page) - overviewLeft)).toBeLessThanOrEqual(1);
  });
}

test('long labels wrap without hiding actions or overlapping the title', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 900 });
  const content = await openPartner(page, { longText: true });
  await page.getByRole('navigation').getByRole('button', { name: content.strings['portal.section.leads'], exact: true }).click();
  await expect(page.locator('.plb-card')).toHaveCount(1);
  await expectDesktopLayout(page, 'Добавить лид');
  await page.getByRole('navigation').getByRole('button', { name: 'Заявки на специалистов' }).click();
  const actionName = content.strings['staffing.list.create'];
  await expectDesktopLayout(page, actionName);
  const action = page.getByRole('button', { name: actionName, exact: true }).first();
  const title = await page.locator('main h1').boundingBox();
  const button = await action.boundingBox();
  expect(title.x + title.width <= button.x + 1 || title.y + title.height <= button.y + 1).toBeTruthy();
  expect(await action.evaluate(el => el.scrollWidth <= el.clientWidth + 1 && el.scrollHeight <= el.clientHeight + 1)).toBeTruthy();
});

test('lead loading error keeps retry available inside the desktop workspace', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await openPartner(page, { outage: true });
  await page.getByRole('navigation').getByRole('button', { name: 'Мои лиды', exact: true }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await expectDesktopLayout(page, 'Повторить');
  await expect(page.locator('.plb-card')).toHaveCount(0);
});

test('unavailable staffing localization retains the section heading and retry', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await openPartner(page, { missingStaffing: true });
  await page.getByRole('navigation').getByRole('button', { name: 'Заявки на специалистов' }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Заявки на специалистов', exact: true })).toBeVisible({ timeout: 1500 });
  await expectDesktopLayout(page, 'Повторить');
  await expect(page.getByRole('button', { name: 'Создать заявку', exact: true })).toHaveCount(0);
});

test('lead form keeps text, number, currency and date controls aligned', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 1100 });
  await openPartner(page);
  await page.getByRole('navigation').getByRole('button', { name: 'Мои лиды', exact: true }).click();
  await page.getByRole('button', { name: 'Добавить лид', exact: true }).first().click();
  const form = page.getByRole('dialog', { name: 'Добавить лид', exact: true });
  await form.locator('[name=companyName]').fill('Синтетическая компания для проверки формы');
  await form.locator('[name=budgetAmount]').fill('300000');
  await form.locator('[name=plannedStartAt]').fill('2026-12-01');
  const dimensions = await form.locator('input:not([type=file]),select').evaluateAll(elements => elements.map(el => {
    const r = el.getBoundingClientRect();
    return { name: el.name, height: r.height, top: r.top, bottom: r.bottom };
  }));
  for (const item of dimensions) {
    expect(item.height, `${testInfo.project.name}: ${item.name} ${JSON.stringify(dimensions)}`).toBeCloseTo(dimensions[0].height, 0);
  }
  for (const [left, right] of [['companyName','domainOrTaxId'], ['budgetAmount','budgetCurrency'], ['plannedStartAt','decisionMakerName']]) {
    const a = dimensions.find(item => item.name === left), b = dimensions.find(item => item.name === right);
    expect(a.top, `${left}/${right}: top`).toBeCloseTo(b.top, 0);
    expect(a.bottom, `${left}/${right}: bottom`).toBeCloseTo(b.bottom, 0);
  }
  await page.screenshot({ path: testInfo.outputPath('aligned-lead-form.png'), animations: 'disabled' });
});

test('staffing form keeps text, number, select and date fields at equal height', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1100 });
  await openPartner(page);
  await page.getByRole('navigation').getByRole('button', { name: 'Заявки на специалистов' }).click();
  await page.getByRole('button', { name: 'Создать заявку', exact: true }).click();
  await page.getByLabel(/Должность \/ роль/).fill('Синтетическая роль');
  await page.getByLabel('Ожидаемая дата подключения').fill('2026-12-01');
  const heights = await page.locator('.staffing-form input:not([type=checkbox],[type=file]),.staffing-form select').evaluateAll(elements => elements.map(el => ({type:el.type,height:el.getBoundingClientRect().height})));
  for (const item of heights) expect(item.height, JSON.stringify(heights)).toBeCloseTo(heights[0].height, 0);
});

test('profile form keeps locale selection aligned with contact fields', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1100 });
  const content = await openPartner(page);
  await page.getByRole('navigation').getByRole('button', { name: content.strings['portal.profile.navigation'], exact: true }).click();
  await expect(page.locator('.approved-profile-edit-grid input')).toHaveCount(3);
  const heights = await page.locator('.approved-profile-edit-grid input,.approved-profile-edit-grid select').evaluateAll(elements => elements.map(el => el.getBoundingClientRect().height));
  for (const height of heights) expect(height, JSON.stringify(heights)).toBeCloseTo(heights[0], 0);
});

test('registration form keeps country selection aligned with text fields', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1100 });
  await openPartner(page, { registration: true });
  const heights = await page.locator('.registration-card input:not([type=checkbox],[type=file]),.registration-card select').evaluateAll(elements => elements.map(el => el.getBoundingClientRect().height));
  expect(heights.length).toBeGreaterThan(3);
  for (const height of heights) expect(height, JSON.stringify(heights)).toBeCloseTo(heights[0], 0);
});

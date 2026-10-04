import { expect, test, completePartnerOAuth, ensurePublishedTestContent, loginAsStaff } from './support/fixtures.js';
import { syntheticPartnerData, uniqueFixtureId } from './support/test-data.js';
import { E2E_BASE_URL } from './support/base-url.js';

const api = {
  applications: '/api/v1/admin/partner-applications?page=0&size=100',
  leads: '/api/v1/partner/leads',
  board: '/api/v1/partner/leads/board',
};

async function csrfHeaders(request) {
  const response = await request.get('/api/v1/auth/csrf');
  expect(response.ok()).toBeTruthy();
  const csrf = await response.json();
  return {
    [csrf.headerName ?? 'X-XSRF-TOKEN']: csrf.token,
  };
}

async function applicationIds(request) {
  const response = await request.get(api.applications);
  expect(response.ok()).toBeTruthy();
  return new Set((await response.json()).items.map(item => item.id));
}

async function registerAndApprovePartner(browser, staffPage, partner) {
  const previousIds = await applicationIds(staffPage.context().request);
  const context = await browser.newContext({ baseURL: E2E_BASE_URL });
  const page = await context.newPage();
  try {
    await page.goto('/');
    await page.getByRole('button', { name: 'Стать партнёром' }).click();
    await expect(page).toHaveURL(/\/registration$/);
    const authLink = page.getByRole('link', { name: 'Войти через Яндекс ID' });
    await expect(authLink).toBeVisible();
    const startPath = await authLink.getAttribute('href');
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

    let applicationId;
    await expect.poll(async () => {
      const ids = await applicationIds(staffPage.context().request);
      const found = [...ids].filter(id => !previousIds.has(id));
      applicationId = found.length === 1 ? found[0] : undefined;
      return found.length;
    }).toBe(1);
    const queueBack = staffPage.getByRole('button', { name: 'Очередь заявок' });
    const openModeration = staffPage.getByRole('button', { name: 'Модерация заявок' });
    if (await queueBack.isVisible().catch(() => false)) await queueBack.click();
    else if (await openModeration.isVisible().catch(() => false)) await openModeration.click();
    await expect(staffPage).toHaveURL('/staff/moderation');
    await staffPage.getByRole('button', { name: 'Обновить' }).click();
    await staffPage.getByRole('button', { name: new RegExp(applicationId) }).click();
    await expect(staffPage.getByRole('heading', { name: 'Физическое лицо' })).toBeVisible();
    await staffPage.getByRole('button', { name: 'Одобрить' }).click();
    await expect(staffPage.getByRole('status')).toContainText('Решение сохранено');
    await page.goto('/');
    await completePartnerOAuth(page, partner.subject);
    await expect(page.getByRole('navigation', { name: 'Партнёрский портал' })).toBeVisible();
    return { context, page };
  } catch (error) {
    await context.close().catch(() => {});
    throw error;
  }
}

function newLead(partner, suffix = uniqueFixtureId()) {
  return {
    companyName: `Тестовая компания ${suffix}`,
    domainOrTaxId: `${suffix}.example.test`,
    requestSummary: `Проверка процесса ${suffix}`,
    budgetAmount: '1250000',
    budgetCurrency: 'RUB',
    plannedStartAt: '2026-12-01',
    decisionMakerName: partner.fullName,
    decisionMakerEmail: partner.email,
    description: `Синтетический запрос для E2E ${suffix}`,
  };
}

async function createLeadViaApi(request, partner, suffix) {
  const response = await request.post(api.leads, {
    data: newLead(partner, suffix),
    headers: await csrfHeaders(request),
  });
  if (!response.ok()) throw new Error(`Lead fixture creation failed: HTTP ${response.status()} ${await response.text()}`);
  return response.json();
}

async function createLeadWithAttachment(request, partner, suffix, file) {
  return request.post(api.leads, {
    headers: await csrfHeaders(request),
    multipart: {
      request: { name: 'request.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(newLead(partner, suffix))) },
      attachment: file,
    },
  });
}

test('approved partner creates and manages leads on the Kanban board safely', async ({ page }) => {
  test.setTimeout(300_000);
  const browser = page.context().browser();
  const staffContext = await browser.newContext({ baseURL: E2E_BASE_URL });
  let approvedPartner;
  let unrelatedPartner;
  try {
    const staffPage = await staffContext.newPage();
    await loginAsStaff(staffPage, 'ivan.neshin');
    await ensurePublishedTestContent(staffContext.request);

    const partner = syntheticPartnerData();
    approvedPartner = await registerAndApprovePartner(browser, staffPage, partner);
    const partnerPage = approvedPartner.page;
    await partnerPage.getByRole('navigation', { name: 'Партнёрский портал' })
      .getByRole('button', { name: 'Мои лиды' }).click();
    await expect(partnerPage.getByRole('heading', { name: 'Мои лиды' })).toBeVisible();

    const primaryLead = newLead(partner, `основной-${partner.id.slice(-8)}`);
    await partnerPage.getByRole('button', { name: 'Добавить лид' }).first().click();
    const createDialog = partnerPage.getByRole('dialog', { name: 'Добавить лид' });
    await expect(createDialog).toBeVisible();
    await createDialog.locator('[name="companyName"]').fill(primaryLead.companyName);
    await createDialog.locator('[name="domainOrTaxId"]').fill(primaryLead.domainOrTaxId);
    await createDialog.locator('[name="requestSummary"]').fill(primaryLead.requestSummary);
    await createDialog.locator('[name="budgetAmount"]').fill(primaryLead.budgetAmount);
    await createDialog.locator('[name="budgetCurrency"]').selectOption(primaryLead.budgetCurrency);
    await createDialog.locator('[name="plannedStartAt"]').fill(primaryLead.plannedStartAt);
    await createDialog.locator('[name="decisionMakerName"]').fill(primaryLead.decisionMakerName);
    await createDialog.locator('[name="decisionMakerEmail"]').fill(primaryLead.decisionMakerEmail);
    await createDialog.locator('[name="description"]').fill(primaryLead.description);
    await createDialog.getByRole('button', { name: 'Отправить лид' }).click();
    await expect(partnerPage.getByRole('heading', { name: 'Новый' })).toBeVisible();
    await expect(partnerPage.getByText(primaryLead.companyName)).toBeVisible();

    const createdResponse = await approvedPartner.context.request.get(api.board);
    expect(createdResponse.ok()).toBeTruthy();
    const initialBoard = await createdResponse.json();
    const newLane = initialBoard.columns.find(column => column.lane === 'NEW');
    expect(newLane.items.some(item => item.companyName === primaryLead.companyName)).toBe(true);
    const createdLead = newLane.items.find(item => item.companyName === primaryLead.companyName);
    expect(createdLead.status).toBe('NEW');

    const search = partnerPage.getByRole('searchbox', { name: 'Поиск лидов' });
    await search.fill(primaryLead.domainOrTaxId);
    await expect(partnerPage.getByText(primaryLead.companyName)).toBeVisible();
    await expect(partnerPage.getByRole('heading', { name: 'Лиды не найдены' })).toHaveCount(0);
    await search.fill('неизвестное-запрос-нет-совпадений');
    await expect(partnerPage.getByRole('heading', { name: 'Лиды не найдены' })).toBeVisible();
    await partnerPage.getByRole('button', { name: 'Сбросить', exact: true }).click();

    const statusFilter = partnerPage.getByRole('combobox', { name: 'Фильтр по этапу' });
    await statusFilter.selectOption('DUPLICATE_REVIEW');
    await expect(partnerPage.getByRole('heading', { name: 'Проверка совпадения' })).toBeVisible();
    await expect(partnerPage.getByRole('heading', { name: 'Новый' })).toHaveCount(0);
    await statusFilter.selectOption('');
    await expect(partnerPage.getByRole('heading', { name: 'Новый' })).toBeVisible();

    // Create enough synthetic records to force a second page in NEW.
    const extraLeads = [];
    for (let index = 0; index < 21; index += 1) {
      extraLeads.push(await createLeadViaApi(
        approvedPartner.context.request, partner, `страница-${index}-${partner.id.slice(-6)}`,
      ));
    }
    await partnerPage.reload();
    await partnerPage.getByRole('navigation', { name: 'Партнёрский портал' })
      .getByRole('button', { name: 'Мои лиды' }).click();
    const newLaneRegion = partnerPage.getByRole('region', { name: /^Новый,/ });
    const loadMore = newLaneRegion.getByRole('button', { name: 'Показать ещё' });
    await expect(loadMore).toBeVisible();
    const cardButtons = newLaneRegion.getByRole('button', { name: /^Открыть лид / });
    const firstPageCount = await cardButtons.count();
    const pageTotal = Number((await newLaneRegion.getAttribute('aria-label')).match(/, (\d+) лидов$/)?.[1]);
    expect(pageTotal).toBeGreaterThan(firstPageCount);
    await loadMore.click();
    await expect.poll(() => cardButtons.count()).toBeGreaterThan(firstPageCount);
    await expect(newLaneRegion).toHaveAttribute('aria-label', `Новый, ${pageTotal} лидов`);

    // The details endpoint and UI expose the partner conversation only.
    await search.fill(primaryLead.companyName);
    const detailButton = newLaneRegion.getByRole('button', { name: `Открыть лид ${primaryLead.companyName}` });
    await detailButton.click();
    await expect(partnerPage.getByRole('dialog', { name: primaryLead.companyName })).toBeVisible();
    const message = `Ответ партнёра ${partner.id.slice(-8)}`;
    await partnerPage.getByLabel('Сообщение менеджеру').fill(message);
    const messageResponsePromise = partnerPage.waitForResponse(response =>
      response.request().method() === 'POST' && response.url().endsWith(`/api/v1/partner/leads/${createdLead.id}/messages`));
    await partnerPage.getByRole('button', { name: 'Отправить сообщение' }).click();
    const messageResponse = await messageResponsePromise;
    expect(messageResponse.ok()).toBeTruthy();
    expect(await messageResponse.json()).toMatchObject({ authorType: 'PARTNER', body: message });
    await expect(partnerPage.getByText(message)).toBeVisible();
    const detailsResponse = await approvedPartner.context.request.get(`${api.leads}/${createdLead.id}`);
    expect(detailsResponse.ok()).toBeTruthy();
    const details = await detailsResponse.json();
    expect(details.id).toBe(createdLead.id);
    expect(details.companyName).toBe(primaryLead.companyName);
    expect(details.messages.some(item => item.body === message && item.authorType === 'PARTNER'),
      `Unexpected details projection: ${JSON.stringify(details)}`).toBe(true);
    expect(details).not.toHaveProperty('internalNotes');
    expect(details.messages.every(item => item.visibility !== 'INTERNAL')).toBe(true);

    const attachmentResponse = await createLeadWithAttachment(approvedPartner.context.request, partner,
      `вложение-${partner.id.slice(-8)}`, {
        name: 'brief.pdf', mimeType: 'application/pdf',
        buffer: Buffer.from('%PDF-1.7\nsynthetic E2E document'),
      });
    expect(attachmentResponse.status()).toBe(200);
    const attachedLead = await attachmentResponse.json();
    expect(attachedLead.attachments).toHaveLength(1);
    expect(attachedLead.attachments[0].scanStatus).toBe('CLEAN');
    const fileResponse = await approvedPartner.context.request.get(attachedLead.attachments[0].downloadUrl);
    expect(fileResponse.ok()).toBeTruthy();
    expect(await fileResponse.body()).toEqual(Buffer.from('%PDF-1.7\nsynthetic E2E document'));
    const infectedResponse = await createLeadWithAttachment(approvedPartner.context.request, partner,
      `eicar-${partner.id.slice(-8)}`, {
        name: 'infected.pdf', mimeType: 'application/pdf',
        buffer: Buffer.from('%PDF-1.7\nEICAR-STANDARD-ANTIVIRUS-TEST-FILE'),
      });
    expect(infectedResponse.status()).toBe(422);
    const invalidAttachment = await createLeadWithAttachment(approvedPartner.context.request, partner,
      `invalid-${partner.id.slice(-8)}`, {
        name: 'image.pdf', mimeType: 'application/pdf', buffer: Buffer.from('not a pdf'),
      });
    expect(invalidAttachment.status()).toBe(400);

    // Staff with LEAD_MANAGE moves the lead; the partner sees both the lane and event history.
    const statusResponse = await staffContext.request.post(
      `/api/v1/admin/leads/${createdLead.id}/status`,
      { data: { status: 'QUALIFICATION' }, headers: await csrfHeaders(staffContext.request) },
    );
    expect(statusResponse.ok()).toBeTruthy();
    await partnerPage.getByRole('dialog').getByRole('button', { name: 'Закрыть' }).click();
    await partnerPage.reload();
    await partnerPage.getByRole('navigation', { name: 'Партнёрский портал' })
      .getByRole('button', { name: 'Мои лиды' }).click();
    const qualificationLane = partnerPage.getByRole('region', { name: /^Квалификация,/ });
    await expect(qualificationLane.getByRole('button', { name: `Открыть лид ${primaryLead.companyName}` })).toBeVisible();
    const updatedDetailsResponse = await approvedPartner.context.request.get(`${api.leads}/${createdLead.id}`);
    expect(updatedDetailsResponse.ok()).toBeTruthy();
    const updatedDetails = await updatedDetailsResponse.json();
    expect(updatedDetails.status).toBe('QUALIFICATION');
    expect(updatedDetails.history.some(item => item.status === 'QUALIFICATION')).toBe(true);

    // A second approved partner cannot enumerate or read this lead or its conversation.
    const second = syntheticPartnerData();
    unrelatedPartner = await registerAndApprovePartner(browser, staffPage, second);
    const foreignBoard = await unrelatedPartner.context.request.get(api.board);
    expect(foreignBoard.ok()).toBeTruthy();
    expect(JSON.stringify(await foreignBoard.json())).not.toContain(primaryLead.companyName);
    const forbiddenDetail = await unrelatedPartner.context.request.get(`${api.leads}/${createdLead.id}`);
    expect([403, 404]).toContain(forbiddenDetail.status());
    expect(await forbiddenDetail.text()).not.toContain(primaryLead.companyName);
    const forbiddenMessage = await unrelatedPartner.context.request.post(`${api.leads}/${createdLead.id}/messages`, {
      data: { body: 'Несанкционированное сообщение' },
      headers: await csrfHeaders(unrelatedPartner.context.request),
    });
    expect([403, 404]).toContain(forbiddenMessage.status());
    expect(await forbiddenMessage.text()).not.toContain('Несанкционированное сообщение');
    const foreignAttachment = await unrelatedPartner.context.request.get(attachedLead.attachments[0].downloadUrl);
    expect([403, 404]).toContain(foreignAttachment.status());

    // On mobile only one lane is shown; it can be switched without horizontal overflow.
    await partnerPage.setViewportSize({ width: 390, height: 844 });
    await partnerPage.reload();
    await partnerPage.getByRole('navigation', { name: 'Партнёрский портал' })
      .getByRole('button', { name: 'Мои лиды' }).click();
    const lanePicker = partnerPage.getByRole('group', { name: 'Этапы лидов' });
    await expect(lanePicker).toBeVisible();
    await lanePicker.getByRole('button', { name: /В работе/ }).click();
    await expect(partnerPage.getByRole('heading', { name: 'В работе' })).toBeVisible();
    const dimensions = await partnerPage.evaluate(() => ({
      width: document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
      overflowingElements: [...document.querySelectorAll('body *')]
        .map(element => ({ tag: element.tagName, className: typeof element.className === 'string' ? element.className : '', right: Math.round(element.getBoundingClientRect().right), width: Math.round(element.getBoundingClientRect().width) }))
        .filter(element => element.right > document.documentElement.clientWidth + 1)
        .slice(0, 12),
    }));
    expect(dimensions.scrollWidth, JSON.stringify(dimensions)).toBeLessThanOrEqual(dimensions.width + 1);

    // An API outage is shown as an error state; it must not be replaced by demo records.
    await partnerPage.route('**/api/v1/partner/leads/board**', route =>
      route.fulfill({ status: 503, contentType: 'application/json', body: '{"message":"unavailable"}' }));
    await partnerPage.reload();
    await partnerPage.getByRole('navigation', { name: 'Партнёрский портал' })
      .getByRole('button', { name: 'Мои лиды' }).click();
    await expect(partnerPage.getByRole('alert')).toBeVisible();
    await expect(partnerPage.getByRole('button', { name: /^Открыть лид / })).toHaveCount(0);
    await expect(partnerPage.getByRole('button', { name: 'Повторить' })).toBeVisible();
  } finally {
    if (approvedPartner) await approvedPartner.context.close().catch(() => {});
    if (unrelatedPartner) await unrelatedPartner.context.close().catch(() => {});
    await staffContext.close().catch(() => {});
  }
});

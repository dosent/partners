import { expect, test, completePartnerOAuth, ensurePublishedTestContent, loginAsStaff } from './support/fixtures.js';
import { syntheticPartnerData, uniqueFixtureId } from './support/test-data.js';
import { E2E_BASE_URL } from './support/base-url.js';
import { expectDesktopLayout } from './support/desktop-layout.js';

const api = {
  applications: '/api/v1/admin/partner-applications?page=0&size=100',
  leads: '/api/v1/partner/leads',
  leadsWithAttachment: '/api/v1/partner/leads/with-attachment',
  board: '/api/v1/partner/leads/board',
};

async function csrfHeaders(request) {
  const response = await request.get('/api/v1/auth/csrf');
  expect(response.ok(), `CSRF endpoint returned HTTP ${response.status()}: ${await response.text()}`).toBeTruthy();
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
  let approvedContext;
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
    approvedContext = await browser.newContext({ baseURL: E2E_BASE_URL });
    const approvedPage = await approvedContext.newPage();
    await completePartnerOAuth(approvedPage, partner.subject);
    await expect(approvedPage.getByRole('navigation', { name: 'Партнёрский портал' })).toBeVisible();
    await context.close();
    return { context: approvedContext, page: approvedPage };
  } catch (error) {
    await approvedContext?.close().catch(() => {});
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

async function createLeadWithAttachment(request, partner, suffix, file, endpoint = api.leadsWithAttachment) {
  return request.post(endpoint, {
    headers: await csrfHeaders(request),
    multipart: {
      request: { name: 'request.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(newLead(partner, suffix))) },
      attachment: file,
    },
  });
}

async function replaceStaffRoles(request, username, roles) {
  const response = await request.put(`/api/v1/admin/staff-access/users/${encodeURIComponent(username)}/roles`, {
    data: { roles }, headers: await csrfHeaders(request),
  });
  expect(response.status(), `replace roles for ${username}`).toBe(200);
}

test('staffing request assignment persists and enforces staff roles without partner disclosure', async ({ page }) => {
  test.setTimeout(300_000);
  const browser = page.context().browser();
  const adminContext = await browser.newContext({ baseURL: E2E_BASE_URL });
  let approvedPartner;
  let managerContext;
  let unauthorizedContext;
  try {
    const adminPage = await adminContext.newPage();
    await loginAsStaff(adminPage, 'ivan.neshin');
    await ensurePublishedTestContent(adminContext.request);
    const firstManager = uniqueFixtureId();
    const secondManager = uniqueFixtureId();
    const unauthorizedUsername = uniqueFixtureId();
    await replaceStaffRoles(adminContext.request, firstManager, ['RESOURCE_MANAGER']);
    await replaceStaffRoles(adminContext.request, secondManager, ['RESOURCE_MANAGER']);
    await replaceStaffRoles(adminContext.request, unauthorizedUsername, ['PARTNER_MANAGER']);

    const partner = syntheticPartnerData();
    approvedPartner = await registerAndApprovePartner(browser, adminPage, partner);
    const lead = await createLeadViaApi(approvedPartner.context.request, partner, `assignment-${partner.id.slice(-8)}`);
    const partnerPage = approvedPartner.page;
    // HTTP staging does not expose randomUUID; keep the real getRandomValues API.
    await partnerPage.evaluate(() => {
      Object.defineProperty(crypto, 'randomUUID', { value: undefined, configurable: true });
    });
    await partnerPage.getByRole('navigation', { name: 'Партнёрский портал' })
      .getByRole('button', { name: 'Заявки на специалистов' }).click();
    await partnerPage.getByRole('button', { name: 'Создать заявку' }).click();
    await partnerPage.getByLabel(/Проект \/ лид/).selectOption(lead.id);
    const title = `Backend assignment ${partner.id.slice(-8)}`;
    await partnerPage.getByLabel(/Должность \/ роль/).fill(title);
    await partnerPage.getByLabel(/Количество специалистов/).fill('2');
    await partnerPage.getByLabel('Уровень').selectOption('Middle+');
    await partnerPage.getByLabel(/Обязательные навыки и стек/).fill('Java, Spring Boot');
    await partnerPage.getByLabel(/Задачи и ожидания/).fill('Разработка синтетического проекта для E2E.');
    await partnerPage.getByLabel(/Загрузка, часов в неделю/).fill('40');
    await partnerPage.getByLabel('Формат работы').selectOption('REMOTE');
    await partnerPage.getByLabel('Город \/ страна').fill('Удалённо');
    await partnerPage.getByLabel('Ожидаемая дата подключения').fill('2026-12-15');
    await partnerPage.getByRole('button', { name: 'Отправить заявку' }).click();
    await expect(partnerPage.getByText('Заявка сохранена')).toBeVisible();
    const createdResponse = await approvedPartner.context.request.get('/api/v1/partner/staffing-requests');
    expect(createdResponse.status()).toBe(200);
    const createdItems = (await createdResponse.json()).items;
    expect(createdItems).toHaveLength(1);
    const staffingRequest = createdItems[0];
    expect(staffingRequest.role.title).toBe(title);
    const assignmentPath = `/api/v1/admin/staffing-requests/${staffingRequest.id}/assignment`;

    managerContext = await browser.newContext({ baseURL: E2E_BASE_URL });
    const managerPage = await managerContext.newPage();
    const managerSession = await loginAsStaff(managerPage, firstManager);
    expect(managerSession.permissions).toContain('staffing.request.manage');
    await managerPage.getByRole('button', { name: 'Заявки на специалистов' }).click();
    await expect(managerPage).toHaveURL('/staff/staffing-requests');
    const row = managerPage.getByRole('article').filter({ has: managerPage.getByRole('heading', { name: title, exact: true }) });
    const selector = row.getByRole('combobox', { name: `Ответственный для заявки ${staffingRequest.number}` });
    await expect(row).toContainText('Ответственный: Не назначен');
    const initialQueueResponse = await managerContext.request.get('/api/v1/admin/staffing-requests');
    expect(initialQueueResponse.status()).toBe(200);
    const initialItem = (await initialQueueResponse.json()).items.find(item => item.id === staffingRequest.id);
    expect(initialItem).toMatchObject({ assignedResourceManagerUsername: null, version: 0 });
    const candidatesResponse = await managerContext.request.get('/api/v1/admin/staffing-requests/assignees');
    expect(candidatesResponse.status()).toBe(200);
    const candidates = await candidatesResponse.json();
    expect(candidates).toEqual(expect.arrayContaining([
      { username: firstManager, role: 'RESOURCE_MANAGER' },
      { username: secondManager, role: 'RESOURCE_MANAGER' },
    ]));
    expect(candidates.every(candidate => candidate.role === 'RESOURCE_MANAGER')).toBe(true);
    expect(candidates.map(candidate => candidate.username)).not.toContain(unauthorizedUsername);

    for (const username of [uniqueFixtureId(), unauthorizedUsername]) {
      const invalid = await managerContext.request.put(assignmentPath, {
        data: { username, expectedVersion: 0 }, headers: await csrfHeaders(managerContext.request),
      });
      expect(invalid.status(), `reject ineligible assignee ${username}`).toBe(400);
    }

    unauthorizedContext = await browser.newContext({ baseURL: E2E_BASE_URL });
    const unauthorizedPage = await unauthorizedContext.newPage();
    const unauthorizedSession = await loginAsStaff(unauthorizedPage, unauthorizedUsername);
    expect(unauthorizedSession.accessState).toBe('ACTIVE');
    expect(unauthorizedSession.permissions).not.toContain('staffing.request.manage');
    await expect(unauthorizedPage.getByRole('button', { name: 'Заявки на специалистов' })).toHaveCount(0);
    await unauthorizedPage.goto('/staff/staffing-requests');
    await expect(unauthorizedPage.getByRole('heading', { name: 'Недостаточно прав' })).toBeVisible();
    await expect(unauthorizedPage.getByRole('combobox')).toHaveCount(0);
    for (const path of ['/api/v1/admin/staffing-requests', '/api/v1/admin/staffing-requests/assignees']) {
      const denied = await unauthorizedContext.request.get(path);
      expect(denied.status()).toBe(403);
      expect(await denied.text()).not.toContain(title);
    }
    const deniedAssignment = await unauthorizedContext.request.put(assignmentPath, {
      data: { username: firstManager, expectedVersion: 0 }, headers: await csrfHeaders(unauthorizedContext.request),
    });
    expect(deniedAssignment.status()).toBe(403);

    for (const [username, version] of [[firstManager, 1], [secondManager, 2], [null, 3], [secondManager, 4]]) {
      if (username === null) await row.getByRole('button', { name: 'Снять назначение' }).click();
      else {
        await selector.selectOption(username);
        await row.getByRole('button', { name: 'Сохранить назначение' }).click();
      }
      await expect(managerPage.getByRole('status')).toContainText(username === null ? 'снято' : 'сохранён');
      await managerPage.reload();
      await expect(row).toContainText(`Ответственный: ${username ?? 'Не назначен'}`);
      await expect(selector).toHaveValue(username ?? '');
      const queueResponse = await managerContext.request.get('/api/v1/admin/staffing-requests');
      expect(queueResponse.status()).toBe(200);
      expect((await queueResponse.json()).items.find(item => item.id === staffingRequest.id))
        .toMatchObject({ assignedResourceManagerUsername: username, version });
      // The partner projection must remain private even while an assignment exists.
      for (const path of ['/api/v1/partner/staffing-requests', `/api/v1/partner/staffing-requests/${staffingRequest.id}`]) {
        const partnerResponse = await approvedPartner.context.request.get(path);
        expect(partnerResponse.status()).toBe(200);
        const projection = await partnerResponse.json();
        expect(JSON.stringify(projection)).not.toContain('assignedResourceManagerUsername');
        expect(JSON.stringify(projection)).not.toContain(firstManager);
        expect(JSON.stringify(projection)).not.toContain(secondManager);
      }
    }

    await replaceStaffRoles(adminContext.request, secondManager, []);
    await managerPage.reload();
    await expect(row).toContainText(`Ответственный: ${secondManager}`);
    await expect(selector).toHaveValue(secondManager);
    await expect(selector.locator(`option[value="${secondManager}"]`)).toBeDisabled();
    await expect(row.getByRole('button', { name: 'Сохранить назначение' })).toBeDisabled();
    const revokedCandidates = await managerContext.request.get('/api/v1/admin/staffing-requests/assignees');
    expect(revokedCandidates.status()).toBe(200);
    expect((await revokedCandidates.json()).map(candidate => candidate.username)).not.toContain(secondManager);
    const preservedQueue = await managerContext.request.get('/api/v1/admin/staffing-requests');
    expect(preservedQueue.status()).toBe(200);
    expect((await preservedQueue.json()).items.find(item => item.id === staffingRequest.id))
      .toMatchObject({ assignedResourceManagerUsername: secondManager, version: 4 });
    const revokedAssignment = await managerContext.request.put(assignmentPath, {
      data: { username: secondManager, expectedVersion: 4 }, headers: await csrfHeaders(managerContext.request),
    });
    expect(revokedAssignment.status()).toBe(400);
    await selector.selectOption(firstManager);
    await row.getByRole('button', { name: 'Сохранить назначение' }).click();
    await expect(managerPage.getByRole('status')).toContainText('сохранён');
    await managerPage.reload();
    await expect(row).toContainText(`Ответственный: ${firstManager}`);
    const replacedQueue = await managerContext.request.get('/api/v1/admin/staffing-requests');
    expect(replacedQueue.status()).toBe(200);
    expect((await replacedQueue.json()).items.find(item => item.id === staffingRequest.id))
      .toMatchObject({ assignedResourceManagerUsername: firstManager, version: 5 });
    await row.getByRole('button', { name: 'Снять назначение' }).click();
    await expect(managerPage.getByRole('status')).toContainText('снято');
    await managerPage.reload();
    await expect(row).toContainText('Ответственный: Не назначен');
    const clearedQueue = await managerContext.request.get('/api/v1/admin/staffing-requests');
    expect(clearedQueue.status()).toBe(200);
    expect((await clearedQueue.json()).items.find(item => item.id === staffingRequest.id))
      .toMatchObject({ assignedResourceManagerUsername: null, version: 6 });
  } finally {
    if (unauthorizedContext) await unauthorizedContext.close().catch(() => {});
    if (managerContext) await managerContext.close().catch(() => {});
    if (approvedPartner) await approvedPartner.context.close().catch(() => {});
    await adminContext.close().catch(() => {});
  }
});

test('approved partner creates and manages leads on the Kanban board safely', async ({ page }) => {
  test.setTimeout(300_000);
  const browser = page.context().browser();
  const staffContext = await browser.newContext({ baseURL: E2E_BASE_URL });
  let approvedPartner;
  let unrelatedPartner;
  let unassignedStaffContext;
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
    // Real backend/DB regression: desktop actions must not escape the window.
    for (const width of [1280, 1440, 1920, 1024]) {
      await partnerPage.setViewportSize({ width, height: 900 });
      await expectDesktopLayout(partnerPage, 'Добавить лид');
    }
    await partnerPage.setViewportSize({ width: 1280, height: 900 });
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

    await partnerPage.getByRole('navigation', { name: 'Партнёрский портал' })
      .getByRole('button', { name: 'Заявки на специалистов' }).click();
    await expect(partnerPage.getByRole('heading', { name: 'Ваши заявки' })).toBeVisible();
    await partnerPage.getByRole('button', { name: 'Создать заявку' }).click();
    await partnerPage.getByLabel(/Проект \/ лид/).selectOption(createdLead.id);
    await partnerPage.getByLabel(/Должность \/ роль/).fill('Backend-разработчик');
    await partnerPage.getByLabel(/Количество специалистов/).fill('2');
    await partnerPage.getByLabel('Уровень').selectOption('Middle+');
    await partnerPage.getByLabel(/Обязательные навыки и стек/).fill('Java, Spring Boot');
    await partnerPage.locator('input[type="file"]').first().setInputFiles({ name: 'brief.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7\nbrief') });
    await partnerPage.locator('input[type="file"]').first().setInputFiles({ name: 'invalid.png', mimeType: 'image/png', buffer: Buffer.from('not a supported attachment') });
    await expect(partnerPage.getByRole('alert')).toContainText('PDF или DOCX');
    await expect(partnerPage.getByLabel(/Обязательные навыки и стек/)).toHaveValue('Java, Spring Boot');
    await partnerPage.getByRole('button', { name: 'Продолжить без файла', exact: true }).click();
    await partnerPage.locator('input[type="file"]').nth(1).setInputFiles({ name: 'cv-example.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7\ncv example') });
    await partnerPage.getByLabel(/Задачи и ожидания/).fill('Разработка и развитие сервисов проекта.');
    await partnerPage.getByLabel(/Загрузка, часов в неделю/).fill('40');
    await partnerPage.getByLabel('Формат работы').selectOption('REMOTE');
    await partnerPage.getByLabel('Город \/ страна').fill('Удалённо');
    await partnerPage.getByLabel('Ожидаемая дата подключения').fill('2026-12-15');
    await partnerPage.getByLabel(/Обязательные навыки и стек/).fill(' , , ');
    await partnerPage.getByRole('button', { name: 'Отправить заявку' }).click();
    await expect(partnerPage.getByText('Проверьте это поле.')).toBeVisible();
    await partnerPage.getByLabel(/Обязательные навыки и стек/).fill('Java, Spring Boot');
    await partnerPage.getByRole('button', { name: 'Отправить заявку' }).click();
    await expect(partnerPage.getByText(/^SR-[A-F0-9]{8}$/)).toBeVisible();
    await expect(partnerPage.getByText('Заявка сохранена')).toBeVisible();
    await partnerPage.getByRole('button', { name: 'К списку заявок' }).click();
    await expect(partnerPage.getByRole('heading', { name: 'Backend-разработчик' })).toBeVisible();

    const requestsResponse = await approvedPartner.context.request.get('/api/v1/partner/staffing-requests');
    expect(requestsResponse.ok()).toBeTruthy();
    const requestItems = (await requestsResponse.json()).items;
    expect(requestItems).toHaveLength(1);
    expect(requestItems[0].role.requiredSkills).toEqual(['Java', 'Spring Boot']);
    expect(requestItems[0].status).toBe('NEW');
    const staffingRequestId = requestItems[0].id;
    const staffingDetailResponse = await approvedPartner.context.request.get(`/api/v1/partner/staffing-requests/${staffingRequestId}`);
    expect(staffingDetailResponse.ok()).toBeTruthy();
    const staffingDetail = await staffingDetailResponse.json();
    expect(staffingDetail.attachments.map(file => file.kind)).toEqual(['cv']);
    const staffingFile = await approvedPartner.context.request.get(`/api/v1/partner/staffing-requests/${staffingRequestId}/attachments/${staffingDetail.attachments[0].id}`);
    expect(staffingFile.ok()).toBeTruthy();
    expect(await staffingFile.body()).toEqual(Buffer.from('%PDF-1.7\ncv example'));

    await partnerPage.getByRole('navigation', { name: 'Партнёрский портал' })
      .getByRole('button', { name: 'Мои лиды' }).click();

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
    const legacyAttachmentResponse = await createLeadWithAttachment(approvedPartner.context.request, partner,
      `legacy-вложение-${partner.id.slice(-8)}`, {
        name: 'legacy-brief.pdf', mimeType: 'application/pdf',
        buffer: Buffer.from('%PDF-1.7\nlegacy synthetic E2E document'),
      }, api.leads);
    expect(legacyAttachmentResponse.status()).toBe(200);
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

    // Staff without LEAD_MANAGE cannot alter a lead, even with a valid LDAP-style session.
    unassignedStaffContext = await browser.newContext({ baseURL: E2E_BASE_URL });
    const unassignedStaffPage = await unassignedStaffContext.newPage();
    const unassignedCsrfHeaders = await csrfHeaders(unassignedStaffContext.request);
    const unassignedSession = await loginAsStaff(unassignedStaffPage, uniqueFixtureId());
    expect(unassignedSession.accessState).toBe('ACCESS_PENDING');
    const forbiddenStatusResponse = await unassignedStaffContext.request.post(
      `/api/v1/admin/leads/${createdLead.id}/status`,
      { data: { status: 'QUALIFICATION' }, headers: unassignedCsrfHeaders },
    );
    expect(forbiddenStatusResponse.status()).toBe(403);
    expect(await forbiddenStatusResponse.text()).not.toContain(primaryLead.companyName);
    const forbiddenQueueResponse = await unassignedStaffContext.request.get('/api/v1/admin/leads');
    expect(forbiddenQueueResponse.status()).toBe(403);
    await unassignedStaffContext.close();
    unassignedStaffContext = undefined;

    // Staff with LEAD_MANAGE processes the lead in the staff workspace.
    await staffPage.goto('/staff');
    await staffPage.getByRole('button', { name: 'Обработка лидов' }).click();
    await expect(staffPage).toHaveURL('/staff/leads');
    await expect(staffPage.getByRole('heading', { name: 'Обработка лидов' })).toBeVisible();
    await staffPage.getByRole('searchbox', { name: 'Поиск лидов' }).fill(primaryLead.companyName);
    await staffPage.getByRole('button', { name: `Открыть лид ${primaryLead.companyName}` }).click();
    await expect(staffPage.getByRole('heading', { name: primaryLead.companyName })).toBeVisible();
    await staffPage.getByLabel('Статус лида').selectOption('QUALIFICATION');
    await staffPage.getByRole('button', { name: 'Сохранить статус' }).click();
    await expect(staffPage.getByRole('status')).toContainText('Статус сохранён');
    const publicReply = `Просим уточнить срок запуска ${partner.id.slice(-8)}.`;
    await staffPage.getByLabel('Комментарий партнёру').fill(publicReply);
    await staffPage.getByRole('button', { name: 'Отправить комментарий' }).click();
    await expect(staffPage.getByRole('status')).toContainText('Комментарий отправлен');
    const internalNote = `Внутренняя проверка ${partner.id.slice(-8)}.`;
    await staffPage.getByLabel('Внутренний комментарий').check();
    await staffPage.getByLabel('Комментарий партнёру').fill(internalNote);
    await staffPage.getByRole('button', { name: 'Отправить комментарий' }).click();
    await expect(staffPage.getByRole('status')).toContainText('Внутренний комментарий сохранён');
    await expect(staffPage.getByText(internalNote)).toBeVisible();
    const staffLeadResponse = await staffContext.request.get(`/api/v1/admin/leads/${createdLead.id}`);
    expect(staffLeadResponse.ok()).toBeTruthy();
    const staffLead = await staffLeadResponse.json();
    expect(staffLead.messages, JSON.stringify(staffLead.messages)).toEqual(expect.arrayContaining([
      expect.objectContaining({ body: publicReply, authorName: 'ivan.neshin', internal: false }),
      expect.objectContaining({ body: internalNote, authorName: 'ivan.neshin', internal: true }),
    ]));
    await staffPage.setViewportSize({ width: 390, height: 844 });
    await expect(staffPage.getByRole('heading', { name: primaryLead.companyName })).toBeVisible();
    expect(await staffPage.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
    await staffPage.setViewportSize({ width: 1280, height: 800 });
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
    expect(updatedDetails.messages.some(item => item.body === publicReply)).toBe(true);
    expect(updatedDetails.messages.some(item => item.body === internalNote)).toBe(false);

    // A second approved partner cannot enumerate or read this lead or its conversation.
    const second = syntheticPartnerData();
    await staffPage.goto('/staff');
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
    const foreignStaffingRequest = await unrelatedPartner.context.request.get(`/api/v1/partner/staffing-requests/${staffingRequestId}`);
    expect([403, 404]).toContain(foreignStaffingRequest.status());
    expect(await foreignStaffingRequest.text()).not.toContain('Java, Spring Boot');
    const foreignStaffingList = await unrelatedPartner.context.request.get('/api/v1/partner/staffing-requests');
    expect(foreignStaffingList.ok()).toBeTruthy();
    expect(JSON.stringify(await foreignStaffingList.json())).not.toContain(staffingRequestId);

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
    if (unassignedStaffContext) await unassignedStaffContext.close().catch(() => {});
    if (approvedPartner) await approvedPartner.context.close().catch(() => {});
    if (unrelatedPartner) await unrelatedPartner.context.close().catch(() => {});
    await staffContext.close().catch(() => {});
  }
});

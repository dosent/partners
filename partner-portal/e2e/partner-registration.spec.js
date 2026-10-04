import { expect, test, completePartnerOAuth, ensurePublishedTestContent, loginAsPartner, loginAsStaff } from './support/fixtures.js';
import { isValidRussianInn, syntheticPartnerData } from './support/test-data.js';
import { E2E_BASE_URL } from './support/base-url.js';

async function requestJson(request, path) {
  const response = await request.get(path);
  if (!response.ok()) throw new Error(`GET ${path} failed: HTTP ${response.status()} ${await response.text()}`);
  return response.json();
}

async function manualReviewIds(request) {
  const queue = await requestJson(request, '/api/v1/admin/partner-applications?page=0&size=100');
  return new Set(queue.items.map(item => item.id));
}

async function findSubmittedApplicationId(request, previousIds) {
  await expect.poll(async () => {
    const currentIds = await manualReviewIds(request);
    return [...currentIds].filter(id => !previousIds.has(id));
  }).toHaveLength(1);
  const currentIds = await manualReviewIds(request);
  return [...currentIds].find(id => !previousIds.has(id));
}

async function beginPartnerRegistration(page, partner) {
  await page.goto('/');
  await page.getByRole('button', { name: 'Стать партнёром' }).click();
  await expect(page).toHaveURL(/\/registration$/);
  const authLink = page.getByRole('link', { name: 'Войти через Яндекс ID' });
  const startPath = await authLink.getAttribute('href');
  expect(startPath).toBe('/api/v1/auth/yandex/start');
  await completePartnerOAuth(page, partner.subject, startPath);
  await expect(page).toHaveURL('/');
  await expect(page.getByRole('heading', { name: 'Заявка на партнёрство' })).toBeVisible();
}

async function prepareIsolatedStaffAccess(page) {
  const staffContext = await page.context().browser().newContext({
    baseURL: E2E_BASE_URL,
  });
  try {
    const staffPage = await staffContext.newPage();
    await loginAsStaff(staffPage, 'ivan.neshin');
    return { staffContext, staffPage };
  } catch (error) {
    await staffContext.close();
    throw error;
  }
}

function expectConsentMatchesPublished(revision, published, partnerType) {
  const consent = revision.consent;
  expect(consent.locale).toBe(published.locale);
  expect(consent.offerVersion).toBe(published.offers[partnerType].version);
  expect(consent.privacyNoticeVersion).toBe(published.privacyNotice.version);
  expect(consent.contentVersion).toBe(published.contentVersion);
  expect(consent.contentSha256).toBe(published.contentSha256);
  expect(consent.offerSha256).toBe(published.offers[partnerType].sha256);
  expect(consent.privacyNoticeSha256).toBe(published.privacyNotice.sha256);
}

async function publishContentOutsidePartnerSession(page) {
  const adminContext = await page.context().browser().newContext({
    baseURL: E2E_BASE_URL,
  });
  try {
    return await ensurePublishedTestContent(adminContext.request);
  } finally {
    await adminContext.close();
  }
}

async function submitIndividualApplication(page, partner, name = partner.fullName, expectedAction = 'Отправить заявку') {
  await page.locator('input[name="fullName"]').fill(name);
  await page.locator('input[name="email"]').fill(partner.email);
  await page.locator('input[name="phone"]').fill(partner.phone);
  await page.locator('input[name="region"]').fill(partner.region);
  await page.locator('input[name="offerAccepted"]').check();
  await page.locator('input[name="privacyAccepted"]').check();
  await page.getByRole('button', { name: expectedAction }).click();
}

async function openApplicationForDecision(staffPage, applicationId) {
  await staffPage.getByRole('button', { name: new RegExp(applicationId) }).click();
  await expect(staffPage.getByRole('heading', { name: 'Физическое лицо' })).toBeVisible();
}

test('partner can submit application and see manual-review status', async ({ page }) => {
  const request = page.context().request;
  const published = await publishContentOutsidePartnerSession(page);
  const { staffContext } = await prepareIsolatedStaffAccess(page);
  try {
    const previousIds = await manualReviewIds(staffContext.request);
    const partner = syntheticPartnerData();

    await beginPartnerRegistration(page, partner);
    await submitIndividualApplication(page, partner);

    await expect(page.getByRole('heading', { name: 'Ручная проверка' })).toBeVisible();
    const ownerResponse = await request.get('/api/v1/partner-applications/me');
    expect(ownerResponse.ok()).toBeTruthy();
    const ownerStatus = await ownerResponse.json();
    expect(ownerStatus.status).toBe('MANUAL_REVIEW');
    expect(ownerStatus.latestDecisionReason).toBeNull();
    const applicationId = await findSubmittedApplicationId(staffContext.request, previousIds);
    expect(applicationId).toMatch(/^[0-9a-f-]{36}$/i);
    const detail = await requestJson(staffContext.request, `/api/v1/admin/partner-applications/${applicationId}`);
    expectConsentMatchesPublished(detail.currentRevision, published, 'INDIVIDUAL');
  } finally {
    await staffContext.close();
  }
});

test('staff approval makes the partner portal available without demo data', async ({ page }) => {
  const request = page.context().request;
  await publishContentOutsidePartnerSession(page);
  const { staffContext, staffPage } = await prepareIsolatedStaffAccess(page);
  let partnerContext;
  try {
    const previousIds = await manualReviewIds(staffContext.request);
    const partner = syntheticPartnerData();

    await beginPartnerRegistration(page, partner);
    await submitIndividualApplication(page, partner);
    await expect(page.getByRole('heading', { name: 'Ручная проверка' })).toBeVisible();
    const applicationId = await findSubmittedApplicationId(staffContext.request, previousIds);

    await staffPage.getByRole('button', { name: 'Модерация заявок' }).click();
    await expect(staffPage).toHaveURL('/staff/moderation');
    await openApplicationForDecision(staffPage, applicationId);
    await staffPage.getByRole('button', { name: 'Одобрить' }).click();
    await expect(staffPage.getByRole('status')).toContainText('Решение сохранено');
    await expect(staffPage.locator('.staff-application-badge')).toHaveText('Одобрена');

    partnerContext = await page.context().browser().newContext({
      baseURL: E2E_BASE_URL,
    });
    const approvedPartnerPage = await partnerContext.newPage();
    await loginAsPartner(approvedPartnerPage, partner.subject);
    await expect(approvedPartnerPage.getByRole('heading', { name: 'Обзор' })).toBeVisible();
    await expect(approvedPartnerPage.getByRole('navigation', { name: 'Партнёрский портал' })).toBeVisible();
    await expect(approvedPartnerPage.getByText('Добро пожаловать в партнёрский портал.')).toBeVisible();
    await expect(approvedPartnerPage.getByText('Демонстрационный аккаунт')).toHaveCount(0);
    await approvedPartnerPage.getByRole('button', { name: 'Мои лиды' }).click();
    await expect(approvedPartnerPage.getByText('Здесь пока нет данных.')).toBeVisible();
    await expect(approvedPartnerPage.getByText('Демонстрационный аккаунт')).toHaveCount(0);
  } finally {
    await staffContext.close();
    if (partnerContext) await partnerContext.close();
  }
});

test('partner can resubmit after requested changes', async ({ page }) => {
  const request = page.context().request;
  const published = await publishContentOutsidePartnerSession(page);
  const { staffContext, staffPage } = await prepareIsolatedStaffAccess(page);
  try {
    const previousIds = await manualReviewIds(staffContext.request);
    const partner = syntheticPartnerData();

    await beginPartnerRegistration(page, partner);
    await submitIndividualApplication(page, partner);
    await expect(page.getByRole('heading', { name: 'Ручная проверка' })).toBeVisible();
    const applicationId = await findSubmittedApplicationId(staffContext.request, previousIds);

    await staffPage.getByRole('button', { name: 'Модерация заявок' }).click();
    await openApplicationForDecision(staffPage, applicationId);
    const reason = `Просим уточнить тестовый регион ${partner.id.slice(-8)}.`;
    await staffPage.getByLabel('Причина для отказа или запроса изменений').fill(reason);
    await staffPage.getByRole('button', { name: 'Запросить изменения' }).click();
    await expect(staffPage.getByRole('status')).toContainText('Решение сохранено');
    await expect(staffPage.locator('.staff-application-badge')).toHaveText('Запрошены изменения');

    await page.goto('/registration');
    await expect(page.getByRole('status')).toContainText(reason);
    const revisedName = `${partner.fullName} — уточнение`;
    const resubmitRequestPromise = page.waitForRequest(candidate =>
      candidate.method() === 'PUT' && new URL(candidate.url()).pathname === '/api/v1/partner-applications/me');
    await submitIndividualApplication(page, partner, revisedName, 'Отправить повторно');
    const resubmitRequest = await resubmitRequestPromise;
    await expect(page.getByRole('heading', { name: 'Ручная проверка' })).toBeVisible();

    // Replay the exact payload from the just-submitted revision against the now MANUAL_REVIEW state.
    const staleResponse = await request.put('/api/v1/partner-applications/me', {
      data: resubmitRequest.postDataJSON(),
      headers: { 'X-XSRF-TOKEN': resubmitRequest.headers()['x-xsrf-token'] },
    });
    expect(staleResponse.status()).toBe(409);
    expect(await staleResponse.json()).toMatchObject({ status: 409, code: 'CONCURRENT_MODIFICATION' });

    const detail = await requestJson(staffContext.request, `/api/v1/admin/partner-applications/${applicationId}`);
    expect(detail.status).toBe('MANUAL_REVIEW');
    expect(detail.currentRevision.revisionNumber).toBe(2);
    expect(detail.currentRevision.data.fullName).toBe(revisedName);
    expectConsentMatchesPublished(detail.currentRevision, published, 'INDIVIDUAL');
    const ownerStatus = await requestJson(request, '/api/v1/partner-applications/me');
    expect(ownerStatus.status).toBe('MANUAL_REVIEW');
  } finally {
    await staffContext.close();
  }
});

test('partner registration form marks type-specific business fields as required', async ({ page }) => {
  const published = await publishContentOutsidePartnerSession(page);
  const partner = syntheticPartnerData();
  const { staffContext } = await prepareIsolatedStaffAccess(page);
  try {
    const previousIds = await manualReviewIds(staffContext.request);
    await beginPartnerRegistration(page, partner);

    const companyName = page.locator('input[name="companyName"]');
    const taxIdentifier = page.locator('input[name="taxIdentifier"]');
    await expect(companyName).toHaveCount(0);
    await expect(taxIdentifier).toHaveCount(0);

    await page.getByRole('button', { name: 'Юридическое лицо' }).click();
    await expect(companyName).toBeVisible();
    await expect(companyName).toHaveAttribute('required', '');
    await expect(taxIdentifier).toBeVisible();
    await expect(taxIdentifier).toHaveAttribute('required', '');
    expect(partner.legalEntityInn).toHaveLength(10);
    expect(isValidRussianInn(partner.legalEntityInn, 'LEGAL_ENTITY')).toBe(true);
    await taxIdentifier.fill(partner.legalEntityInn);
    await expect(taxIdentifier).toHaveValue(partner.legalEntityInn);

    await page.getByRole('button', { name: 'Индивидуальный предприниматель' }).click();
    await expect(companyName).toHaveCount(0);
    await expect(taxIdentifier).toBeVisible();
    await expect(taxIdentifier).toHaveAttribute('required', '');
    expect(partner.individualProprietorInn).toHaveLength(12);
    expect(partner.individualProprietorInn).toMatch(/^\d{12}$/);
    expect(isValidRussianInn(partner.individualProprietorInn, 'SOLE_PROPRIETOR')).toBe(true);
    await taxIdentifier.fill(partner.individualProprietorInn);
    await expect(taxIdentifier).toHaveValue(partner.individualProprietorInn);
    await submitIndividualApplication(page, partner);
    await expect(page.getByRole('heading', { name: 'Ручная проверка' })).toBeVisible();

    const applicationId = await findSubmittedApplicationId(staffContext.request, previousIds);
    const detail = await requestJson(staffContext.request, `/api/v1/admin/partner-applications/${applicationId}`);
    expect(detail.currentRevision.data.partnerType).toBe('SOLE_PROPRIETOR');
    expect(detail.currentRevision.data.taxIdentifier).toBe(partner.individualProprietorInn);
    expect(detail.currentRevision.data.taxIdentifier).toHaveLength(12);
    expectConsentMatchesPublished(detail.currentRevision, published, 'SOLE_PROPRIETOR');

  } finally {
    await staffContext.close();
  }
});

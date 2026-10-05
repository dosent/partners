import { expect, test, completePartnerOAuth, ensurePublishedTestContent, loginAsPartner, loginAsStaff } from './support/fixtures.js';
import { isValidRussianInn, registrationContentFixture, syntheticPartnerData } from './support/test-data.js';
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
  let unrelatedPartnerContext;
  try {
    const previousIds = await manualReviewIds(staffContext.request);
    const partner = syntheticPartnerData();
    await ensurePublishedTestContent(staffContext.request, 'EN');
    await ensurePublishedTestContent(staffContext.request, 'KZ');

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
    let servedLegacyRussianContent = false;
    let serveLegacyRussianContent = true;
    await approvedPartnerPage.route('**/api/v1/public/registration-content/RU', async route => {
      if (!serveLegacyRussianContent) {
        await route.continue();
        return;
      }
      const response = await route.fetch();
      const content = await response.json();
      for (const key of Object.keys(content.strings ?? {})) {
        if (key.startsWith('portal.profile.') || key.startsWith('portal.actions.') || key.startsWith('staffing.') || key === 'portal.errors.logout') delete content.strings[key];
      }
      expect(Object.keys(content.strings)).toHaveLength(50);
      await route.fulfill({ response, body: JSON.stringify(content) });
      servedLegacyRussianContent = true;
    });
    let releaseProfileRequest;
    const profileRequestGate = new Promise(resolve => { releaseProfileRequest = resolve; });
    await approvedPartnerPage.route('**/api/v1/partners/me', async route => {
      await profileRequestGate;
      await route.continue();
    });
    await loginAsPartner(approvedPartnerPage, partner.subject);
    await expect(approvedPartnerPage.getByRole('heading', { name: 'Обзор' })).toBeVisible();
    expect(servedLegacyRussianContent).toBe(true);
    await approvedPartnerPage.getByRole('navigation', { name: 'Партнёрский портал' })
      .getByRole('button', { name: 'Заявки на специалистов' }).click();
    await expect(approvedPartnerPage.getByRole('alert')).toBeVisible();
    await expect(approvedPartnerPage.getByRole('heading', { name: 'Заявка на специалиста' })).toHaveCount(0);
    await approvedPartnerPage.getByRole('navigation', { name: 'Партнёрский портал' })
      .getByRole('button', { name: 'Профиль' }).click();
    await expect(approvedPartnerPage.getByText('Загружаем профиль…')).toBeVisible();
    releaseProfileRequest();
    await expect(approvedPartnerPage.getByText(partner.fullName)).toBeVisible();
    serveLegacyRussianContent = false;
    await approvedPartnerPage.unroute('**/api/v1/partners/me');
    await approvedPartnerPage.getByRole('button', { name: 'Обзор' }).click();
    await expect(approvedPartnerPage.getByRole('heading', { name: 'Обзор' })).toBeVisible();
    await expect(approvedPartnerPage.getByRole('navigation', { name: 'Партнёрский портал' })).toBeVisible();
    await expect(approvedPartnerPage.getByText('Добро пожаловать в партнёрский портал.')).toBeVisible();
    await expect(approvedPartnerPage.getByText('Демонстрационный аккаунт')).toHaveCount(0);

    const profileResponse = await partnerContext.request.get('/api/v1/partners/me');
    expect(profileResponse.status()).toBe(200);
    const profile = await profileResponse.json();
    expect(profile).toMatchObject({
      fullName: partner.fullName,
      email: partner.email,
      phone: partner.phone,
      countryCode: 'RU',
      region: partner.region,
      partnerType: 'INDIVIDUAL',
    });
    expect(profile).not.toHaveProperty('taxIdentifier');
    expect(profile).not.toHaveProperty('inn');

    await approvedPartnerPage.getByRole('button', { name: 'Профиль' }).click();
    await expect(approvedPartnerPage.getByRole('heading', { name: 'Профиль' })).toBeVisible();
    await expect(approvedPartnerPage.getByText(partner.fullName)).toBeVisible();
    await expect(approvedPartnerPage.getByText(partner.email)).toBeVisible();

    const changedName = `Обновлённый партнёр ${partner.id.slice(-6)}`;
    const changedEmail = `updated-${partner.email}`;
    await approvedPartnerPage.getByLabel('ФИО', { exact: true }).fill(changedName);
    await approvedPartnerPage.getByLabel('Электронная почта', { exact: true }).fill(changedEmail);
    await approvedPartnerPage.getByLabel('Телефон', { exact: true }).fill('+79991112233');
    await approvedPartnerPage.getByLabel('Язык интерфейса').selectOption('EN');
    await approvedPartnerPage.getByLabel('По электронной почте').uncheck();
    await approvedPartnerPage.route('**/api/v1/partners/me/settings', async route => {
      await route.fulfill({ status: 500, contentType: 'application/json', body: '{}' });
    });
    await approvedPartnerPage.getByRole('button', { name: 'Сохранить изменения' }).click();
    await expect(approvedPartnerPage.getByRole('alert')).toContainText('Не удалось сохранить настройки. Попробуйте ещё раз.');
    await approvedPartnerPage.unroute('**/api/v1/partners/me/settings');
    await approvedPartnerPage.route('**/api/v1/partners/me/settings', async route => {
      await new Promise(resolve => setTimeout(resolve, 300));
      await route.continue();
    });
    await approvedPartnerPage.getByRole('button', { name: 'Сохранить изменения' }).click();
    await expect(approvedPartnerPage.getByRole('button', { name: 'Сохранение…' })).toBeVisible();
    await approvedPartnerPage.unroute('**/api/v1/partners/me/settings');
    await expect(approvedPartnerPage.getByRole('status')).toContainText('E2E EN status.saved');
    await expect(approvedPartnerPage.getByRole('heading', { name: 'E2E EN title' })).toBeVisible();
    await expect(approvedPartnerPage.getByRole('navigation', { name: 'E2E EN Partner Portal' }).getByRole('button', { name: 'E2E EN navigation' })).toBeVisible();
    const enLocale = approvedPartnerPage.getByLabel('E2E EN locale.label');
    await expect(enLocale).toHaveValue('EN');
    await expect(enLocale.locator('option[value="RU"]')).toHaveText('E2E EN locale.RU');
    await expect(enLocale.locator('option[value="KZ"]')).toHaveText('E2E EN locale.KZ');
    await expect(enLocale.locator('option[value="EN"]')).toHaveText('E2E EN locale.EN');
    await expect(approvedPartnerPage.getByRole('heading', { name: 'E2E EN settings.title' })).toBeVisible();
    await expect(approvedPartnerPage.getByText('E2E EN description')).toBeVisible();
    await expect(approvedPartnerPage.getByText('E2E EN notifications.title')).toBeVisible();
    await expect(approvedPartnerPage.getByLabel('E2E EN Full name')).toBeVisible();
    await expect(approvedPartnerPage.getByLabel('E2E EN notifications.email')).toBeVisible();
    const savedProfileResponse = await partnerContext.request.get('/api/v1/partners/me');
    expect(savedProfileResponse.status()).toBe(200);
    expect(await savedProfileResponse.json()).toMatchObject({
      fullName: changedName,
      email: changedEmail,
      phone: '+79991112233',
      locale: 'EN',
      emailNotificationsEnabled: false,
      inAppNotificationsEnabled: true,
    });
    await approvedPartnerPage.getByLabel('E2E EN locale.label').selectOption('KZ');
    await approvedPartnerPage.getByRole('button', { name: 'E2E EN actions.save' }).click();
    await expect(approvedPartnerPage.getByRole('heading', { name: 'E2E KZ title' })).toBeVisible();
    await expect(approvedPartnerPage.getByRole('navigation', { name: 'E2E KZ Partner Portal' }).getByRole('button', { name: 'E2E KZ navigation' })).toBeVisible();
    await expect(approvedPartnerPage.getByLabel('E2E KZ locale.label')).toHaveValue('KZ');

    let simulateMissingKzPublication = true;
    await approvedPartnerPage.route('**/api/v1/public/registration-content/KZ', async route => {
      if (simulateMissingKzPublication) {
        await route.fulfill({ status: 503, contentType: 'application/json', body: '{}' });
        return;
      }
      await route.continue();
    });
    await approvedPartnerPage.reload();
    await expect(approvedPartnerPage.getByRole('alert')).toContainText('E2E RU errors.localeUnavailable');
    await expect(approvedPartnerPage.getByRole('heading', { name: 'Обзор' })).toBeVisible();
    expect((await (await partnerContext.request.get('/api/v1/partners/me')).json()).locale).toBe('KZ');
    await approvedPartnerPage.getByRole('button', { name: 'E2E RU navigation' }).click();
    let profileSettingsRequests = 0;
    approvedPartnerPage.on('request', outbound => {
      if (outbound.method() === 'PUT' && outbound.url().endsWith('/api/v1/partners/me/settings')) profileSettingsRequests += 1;
    });
    await approvedPartnerPage.getByRole('button', { name: 'E2E RU actions.save' }).click();
    await expect(approvedPartnerPage.locator('.approved-profile-settings [role="alert"]')).toContainText('E2E RU errors.localeUnavailable');
    expect(profileSettingsRequests).toBe(0);
    expect((await (await partnerContext.request.get('/api/v1/partners/me')).json()).locale).toBe('KZ');
    simulateMissingKzPublication = false;
    await approvedPartnerPage.locator('.approved-portal > [role="alert"]').getByRole('button', { name: 'Повторить' }).click();
    await expect(approvedPartnerPage.getByRole('heading', { name: 'E2E KZ title' })).toBeVisible();
    await approvedPartnerPage.unroute('**/api/v1/public/registration-content/KZ');

    const anonymousContext = await page.context().browser().newContext({ baseURL: E2E_BASE_URL });
    try {
      const anonymousResponse = await anonymousContext.request.get('/api/v1/partners/me');
      expect(anonymousResponse.status()).toBe(401);
      const anonymousCsrfResponse = await anonymousContext.request.get('/api/v1/auth/csrf');
      const anonymousCsrf = await anonymousCsrfResponse.json();
      const anonymousUpdate = await anonymousContext.request.put('/api/v1/partners/me/settings', {
        data: { fullName: 'Неавторизован', email: 'x@example.test', phone: '+79990000000', locale: 'RU',
          emailNotificationsEnabled: true, inAppNotificationsEnabled: true },
        headers: { [anonymousCsrf.headerName ?? 'X-XSRF-TOKEN']: anonymousCsrf.token },
      });
      expect(anonymousUpdate.status()).toBe(401);
    } finally {
      await anonymousContext.close();
    }

    unrelatedPartnerContext = await page.context().browser().newContext({ baseURL: E2E_BASE_URL });
    const unrelatedPartnerPage = await unrelatedPartnerContext.newPage();
    await loginAsPartner(unrelatedPartnerPage, `unrelated-${partner.id}`);
    const unrelatedProfileResponse = await unrelatedPartnerContext.request.get('/api/v1/partners/me');
    expect(unrelatedProfileResponse.status()).toBe(404);
    expect(await unrelatedProfileResponse.text()).not.toContain(partner.email);
    const csrfResponse = await unrelatedPartnerContext.request.get('/api/v1/auth/csrf');
    const csrf = await csrfResponse.json();
    const unrelatedUpdate = await unrelatedPartnerContext.request.put('/api/v1/partners/me/settings', {
      data: { fullName: 'Чужое изменение', email: 'other@example.test', phone: '+79991112233', locale: 'RU',
        emailNotificationsEnabled: true, inAppNotificationsEnabled: true },
      headers: { [csrf.headerName ?? 'X-XSRF-TOKEN']: csrf.token },
    });
    expect(unrelatedUpdate.status()).toBe(404);
    expect(await unrelatedUpdate.text()).not.toContain(changedEmail);

    await approvedPartnerPage.getByRole('button', { name: 'E2E KZ Leads' }).click();
    await expect(approvedPartnerPage.getByRole('heading', { name: 'Здесь появятся ваши лиды' })).toBeVisible();
    await expect(approvedPartnerPage.getByText('Демонстрационный аккаунт')).toHaveCount(0);

    await approvedPartnerPage.setViewportSize({ width: 390, height: 844 });
    const logoutButton = approvedPartnerPage.getByRole('button', { name: 'E2E KZ portal.actions.logout' });
    await expect(logoutButton).toBeVisible();
    const logoutButtonBox = await logoutButton.boundingBox();
    expect(logoutButtonBox.x + logoutButtonBox.width).toBeLessThanOrEqual(390);

    await approvedPartnerPage.route('**/api/v1/auth/yandex/logout', route => route.fulfill({
      status: 503,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'Выход временно недоступен.' }),
    }));
    await logoutButton.focus();
    await approvedPartnerPage.keyboard.press('Enter');
    await expect(approvedPartnerPage.getByRole('alert')).toContainText('E2E KZ portal.errors.logout');
    await expect(logoutButton).toBeEnabled();
    expect((await partnerContext.request.get('/api/v1/auth/yandex/session')).status()).toBe(200);

    await approvedPartnerPage.unroute('**/api/v1/auth/yandex/logout');
    await logoutButton.click();
    await expect(approvedPartnerPage.getByRole('button', { name: 'Стать партнёром' })).toBeVisible();
    expect((await partnerContext.request.get('/api/v1/auth/yandex/session')).status()).toBe(401);
    expect((await partnerContext.request.get('/api/v1/partners/me')).status()).toBe(401);
  } finally {
    await staffContext.close();
    if (partnerContext) await partnerContext.close();
    if (unrelatedPartnerContext) await unrelatedPartnerContext.close();
  }
});

test('profile localization keys are mandatory before content publication', async ({ page }) => {
  const context = await page.context().browser().newContext({ baseURL: E2E_BASE_URL });
  try {
    const csrfResponse = await context.request.get('/api/v1/auth/csrf');
    const csrf = await csrfResponse.json();
    const login = await context.request.post('/api/v1/auth/staff/login', {
      data: { username: 'ivan.neshin', password: 'e2e-password' },
      headers: { [csrf.headerName ?? 'X-XSRF-TOKEN']: csrf.token },
    });
    expect(login.ok()).toBeTruthy();
    const stateResponse = await context.request.get('/api/v1/admin/registration-content/en');
    const state = await stateResponse.json();
    const fixture = registrationContentFixture('EN');
    delete fixture.strings['portal.profile.locale.KZ'];
    const draftResponse = await context.request.put('/api/v1/admin/registration-content/en/draft', {
      data: { expectedDraftVersion: state.draftVersion ?? 0, ...fixture },
      headers: { [csrf.headerName ?? 'X-XSRF-TOKEN']: csrf.token },
    });
    expect(draftResponse.ok()).toBeTruthy();
    const draft = await draftResponse.json();
    const publishResponse = await context.request.post('/api/v1/admin/registration-content/en/publish', {
      data: { expectedDraftVersion: draft.draftVersion },
      headers: { [csrf.headerName ?? 'X-XSRF-TOKEN']: csrf.token },
    });
    expect(publishResponse.status()).toBe(422);
    expect(await publishResponse.text()).toContain('portal.profile.locale.KZ');
  } finally {
    await context.close();
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

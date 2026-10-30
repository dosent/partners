import { readFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { expect, test, ensurePublishedTestContent, loginAsPartner, loginAsStaff } from './support/fixtures.js';
import { registrationContentFixture, syntheticCandidateCv, syntheticCandidateData, uniqueFixtureId } from './support/test-data.js';
import { E2E_BASE_URL } from './support/base-url.js';
import { expectDesktopLayout } from './support/desktop-layout.js';

const candidatesPath = '/api/v1/admin/candidates';
const copy = registrationContentFixture('RU').strings;
const t = key => copy[`candidates.${key}`];

async function csrfHeaders(request) {
  const response = await request.get('/api/v1/auth/csrf');
  expect(response.status()).toBe(200);
  const csrf = await response.json();
  return { [csrf.headerName ?? 'X-XSRF-TOKEN']: csrf.token };
}

async function managerContext(browser, roles = ['RESOURCE_MANAGER']) {
  const admin = await browser.newContext({ baseURL: E2E_BASE_URL });
  const context = await browser.newContext({ baseURL: E2E_BASE_URL });
  try {
    await loginAsStaff(await admin.newPage(), 'ivan.neshin');
    const username = uniqueFixtureId();
    const response = await admin.request.put(`/api/v1/admin/staff-access/users/${username}/roles`, {
      data: { roles }, headers: await csrfHeaders(admin.request),
    });
    expect(response.status()).toBe(200);
    const page = await context.newPage();
    const session = await loginAsStaff(page, username);
    expect(session.accessState).toBe('ACTIVE');
    expect(session.permissions.includes('candidate.manage')).toBe(roles.includes('RESOURCE_MANAGER'));
    return { context, page };
  } catch (error) {
    await context.close();
    throw error;
  } finally {
    await admin.close();
  }
}

async function createViaApi(request, profile, cv = syntheticCandidateCv(), idempotencyKey = randomUUID()) {
  const response = await request.post(candidatesPath, {
    headers: { ...await csrfHeaders(request), 'Idempotency-Key': idempotencyKey },
    multipart: {
      profile: { name: 'profile.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(profile)) },
      cv,
    },
  });
  expect([200, 201], `candidate creation HTTP ${response.status()}: ${await response.text()}`).toContain(response.status());
  return response.json();
}

async function getCandidate(request, id) {
  const response = await request.get(`${candidatesPath}/${id}`);
  expect(response.status()).toBe(200);
  return response.json();
}

async function search(page, query) {
  await page.getByRole('searchbox', { name: t('search') }).fill(query);
}

async function openCandidate(page, profile) {
  await page.goto('/staff/candidates');
  await search(page, profile.role);
  await page.getByRole('article').filter({ hasText: profile.code }).getByRole('button', { name: t('open'), exact: true }).click();
  await expect(page.getByRole('heading', { name: profile.role, exact: true })).toBeVisible();
}

async function fillProfile(page, profile, cv) {
  await page.getByLabel(t('role'), { exact: true }).fill(profile.role);
  await page.getByLabel(t('grade'), { exact: true }).fill(profile.grade ?? '');
  await page.getByLabel(t('skills'), { exact: true }).fill(profile.skills.join(', '));
  await page.getByLabel(t('years'), { exact: true }).fill(String(Math.floor(profile.experienceMonths / 12)));
  await page.getByLabel(t('months'), { exact: true }).fill(String(profile.experienceMonths % 12));
  await page.getByLabel(t('description'), { exact: true }).fill(profile.description);
  await page.getByLabel('Внешний ключ (Jira)', { exact: true }).fill(profile.externalKey ?? '');
  await page.getByLabel(t('amount'), { exact: true }).fill(profile.hourlyRate.amount);
  await page.getByLabel(t('currency'), { exact: true }).fill(profile.hourlyRate.currency);
  await page.getByRole('combobox', { name: t('filter'), exact: true }).selectOption(profile.availability);
  if (cv) await page.getByLabel(t('cv'), { exact: true }).setInputFiles(cv);
}

async function saveProfile(page, id) {
  const responsePromise = page.waitForResponse(response =>
    response.request().method() === (id ? 'PUT' : 'POST')
      && new URL(response.url()).pathname === `${candidatesPath}${id ? `/${id}` : ''}`);
  await page.getByRole('button', { name: t('save'), exact: true }).click();
  return responsePromise;
}

async function downloadCv(page, expectedCv) {
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: t('download'), exact: true }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe(expectedCv.name);
  expect(await readFile(await download.path())).toEqual(expectedCv.buffer);
}

test.beforeAll(async ({ browser }) => {
  const context = await browser.newContext({ baseURL: E2E_BASE_URL });
  try {
    await ensurePublishedTestContent(context.request);
  } finally {
    await context.close();
  }
});

test('Resource Manager creates, finds and edits a candidate, filters availability and replaces the checked CV', async ({ browser }) => {
  test.setTimeout(180_000);
  const { context, page } = await managerContext(browser);
  try {
    const profile = syntheticCandidateData();
    const cv = syntheticCandidateCv();
    await page.getByRole('button', { name: 'Кандидаты', exact: true }).click();
    await expect(page).toHaveURL('/staff/candidates');
    await expect(page.getByRole('combobox', { name: t('filter'), exact: true })).toHaveValue('AVAILABLE');
    await search(page, uniqueFixtureId());
    await expect(page.getByText(t('empty'), { exact: true })).toBeVisible();
    for (const width of [1280, 1440, 1920]) {
      await page.setViewportSize({ width, height: 1000 });
      await expectDesktopLayout(page, t('create'));
    }
    await page.getByRole('button', { name: t('create'), exact: true }).click();
    await fillProfile(page, profile, cv);
    const createdResponse = await saveProfile(page);
    expect(createdResponse.status()).toBe(201);
    const created = await createdResponse.json();
    expect(created).toMatchObject({ ...profile, hourlyRate: expect.objectContaining(profile.hourlyRate) });
    expect(created.id).toMatch(/^[a-f0-9-]{36}$/i);
    expect(created.code).toBeTruthy();
    expect(created.cv.fileName).toBe(cv.name);
    await page.reload();
    await page.goto('/staff/candidates');
    await search(page, profile.externalKey.toLowerCase());
    await expect(page.getByRole('article').filter({ hasText: created.code })).toBeVisible();
    await openCandidate(page, created);
    await expect(page.getByText(profile.externalKey, { exact: true })).toBeVisible();
    await expect(page.getByText(/2\s?500[.,]50.*RUB/)).toBeVisible();
    await expect(page.getByText(t('perHour'), { exact: false }).first()).toBeVisible();
    await downloadCv(page, cv);

    // Проверка сигнатуры выполняется настоящим backend, не только файловым input.
    await page.getByLabel(t('cv'), { exact: true }).setInputFiles({
      name: 'invalid-signature.pdf', mimeType: 'application/pdf', buffer: Buffer.from('This is not a PDF document.'),
    });
    const invalidCvResponsePromise = page.waitForResponse(response => response.request().method() === 'PUT'
      && new URL(response.url()).pathname === `${candidatesPath}/${created.id}/cv`);
    await page.getByRole('button', { name: t('replace'), exact: true }).click();
    expect((await invalidCvResponsePromise).status()).toBe(400);
    await expect(page.getByRole('alert').first()).toBeVisible();
    expect((await getCandidate(context.request, created.id)).cv).toEqual(created.cv);
    await downloadCv(page, cv);

    await page.getByRole('button', { name: t('edit'), exact: true }).click();
    await expect(page.getByLabel('Внешний ключ (Jira)', { exact: true })).toHaveValue(profile.externalKey);
    const updatedProfile = { ...profile, externalKey: '', availability: 'UNAVAILABLE', hourlyRate: { amount: '3100.75', currency: 'RUB' } };
    await fillProfile(page, updatedProfile);
    const updateResponse = await saveProfile(page, created.id);
    expect(updateResponse.status()).toBe(200);
    const updated = await updateResponse.json();
    expect(updated).toMatchObject({ id: created.id, code: created.code, externalKey: null, availability: 'UNAVAILABLE', hourlyRate: expect.objectContaining(updatedProfile.hourlyRate) });
    expect(updated.cv).toEqual(created.cv);
    expect(updated.version).toBeGreaterThan(created.version);

    await page.goto('/staff/candidates');
    await search(page, profile.role);
    await expect(page.getByText(t('empty'), { exact: true })).toBeVisible();
    await page.getByRole('combobox', { name: t('filter'), exact: true }).selectOption('UNAVAILABLE');
    await expect(page.getByRole('article').filter({ hasText: created.code })).toBeVisible();
    await page.getByRole('combobox', { name: t('filter'), exact: true }).selectOption('');
    await page.getByRole('article').filter({ hasText: created.code }).getByRole('button', { name: t('open'), exact: true }).click();
    await page.getByRole('button', { name: t('edit'), exact: true }).click();
    await expect(page.getByLabel('Внешний ключ (Jira)', { exact: true })).toHaveValue('');
    await page.getByRole('button', { name: t('cancel'), exact: true }).click();
    const replacement = syntheticCandidateCv('replacement-cv.pdf');
    await page.getByLabel(t('cv'), { exact: true }).setInputFiles(replacement);
    const replaceResponsePromise = page.waitForResponse(response => response.request().method() === 'PUT'
      && new URL(response.url()).pathname === `${candidatesPath}/${created.id}/cv`);
    await page.getByRole('button', { name: t('replace'), exact: true }).click();
    const replaceResponse = await replaceResponsePromise;
    expect(replaceResponse.status()).toBe(200);
    const replaced = await replaceResponse.json();
    expect(replaced.id).toBe(created.id);
    expect(replaced.cv.fileName).toBe(replacement.name);
    expect(replaced.cv.version).toBeGreaterThan(created.cv.version);
    await downloadCv(page, replacement);
    await page.reload();
    const persisted = await getCandidate(context.request, created.id);
    expect(persisted).toMatchObject({ externalKey: null, availability: 'UNAVAILABLE', hourlyRate: expect.objectContaining(updatedProfile.hourlyRate) });
    const downloaded = await context.request.get(`${candidatesPath}/${created.id}/cv`);
    expect(downloaded.status()).toBe(200);
    expect(await downloaded.body()).toEqual(replacement.buffer);
  } finally {
    await context.close();
  }
});

test('duplicate external key returns 409 and keeps the candidate form and persisted rate unchanged', async ({ browser }) => {
  const { context, page } = await managerContext(browser);
  try {
    const existing = await createViaApi(context.request, syntheticCandidateData());
    const original = await createViaApi(context.request, syntheticCandidateData());
    await openCandidate(page, original);
    await page.getByRole('button', { name: t('edit'), exact: true }).click();
    const edited = { ...original, role: `Несохранённая роль ${uniqueFixtureId()}`, externalKey: existing.externalKey, hourlyRate: { amount: '9999.99', currency: 'RUB' } };
    await fillProfile(page, edited);
    const response = await saveProfile(page, original.id);
    expect(response.status()).toBe(409);
    expect((await response.json()).fieldErrors).toEqual(expect.arrayContaining([expect.objectContaining({ field: 'externalKey' })]));
    await expect(page.getByRole('alert').first()).toBeVisible();
    await expect(page.getByLabel(t('role'), { exact: true })).toHaveValue(edited.role);
    await expect(page.getByLabel('Внешний ключ (Jira)', { exact: true })).toHaveValue(existing.externalKey);
    await expect(page.getByLabel(t('amount'), { exact: true })).toHaveValue('9999.99');
    expect(await getCandidate(context.request, original.id)).toEqual(original);
    expect((await getCandidate(context.request, existing.id)).externalKey).toBe(existing.externalKey);
  } finally {
    await context.close();
  }
});

test('a stale second tab receives 409 and retains its entered text without overwriting the first tab', async ({ browser }) => {
  const { context, page } = await managerContext(browser);
  try {
    const original = await createViaApi(context.request, syntheticCandidateData());
    const second = await context.newPage();
    await openCandidate(page, original);
    await openCandidate(second, original);
    for (const tab of [page, second]) await tab.getByRole('button', { name: t('edit'), exact: true }).click();
    const firstRole = `Сохранённая роль ${uniqueFixtureId()}`;
    const secondRole = `Несохранённая роль ${uniqueFixtureId()}`;
    await page.getByLabel(t('role'), { exact: true }).fill(firstRole);
    await second.getByLabel(t('role'), { exact: true }).fill(secondRole);
    await second.getByLabel(t('description'), { exact: true }).fill('Текст второй вкладки должен остаться после конфликта.');
    expect((await saveProfile(page, original.id)).status()).toBe(200);
    expect((await saveProfile(second, original.id)).status()).toBe(409);
    await expect(second.getByRole('alert').first()).toBeVisible();
    await expect(second.getByLabel(t('role'), { exact: true })).toHaveValue(secondRole);
    await expect(second.getByLabel(t('description'), { exact: true })).toHaveValue('Текст второй вкладки должен остаться после конфликта.');
    const persisted = await getCandidate(context.request, original.id);
    expect(persisted.role).toBe(firstRole);
    expect(persisted.description).toBe(original.description);
    expect(persisted.hourlyRate.amount).toBe(original.hourlyRate.amount);
  } finally {
    await context.close();
  }
});

test('partner and staff without candidate.manage cannot read profiles, rates or CV or write candidates', async ({ browser }) => {
  const manager = await managerContext(browser);
  const unauthorized = await managerContext(browser, ['PARTNER_MANAGER']);
  const partner = await browser.newContext({ baseURL: E2E_BASE_URL });
  try {
    const profile = syntheticCandidateData();
    const created = await createViaApi(manager.context.request, profile);
    await expect(unauthorized.page.getByRole('button', { name: 'Кандидаты', exact: true })).toHaveCount(0);
    await unauthorized.page.goto('/staff/candidates');
    await expect(unauthorized.page.getByRole('heading', { name: 'Недостаточно прав', exact: true })).toBeVisible();
    await loginAsPartner(await partner.newPage(), uniqueFixtureId());
    for (const context of [unauthorized.context, partner]) {
      for (const path of [candidatesPath, `${candidatesPath}/${created.id}`, `${candidatesPath}/${created.id}/cv`]) {
        const response = await context.request.get(path);
        expect(response.status(), path).toBe(403);
        const body = await response.text();
        for (const secret of [created.role, created.externalKey, profile.hourlyRate.amount, created.cv.fileName]) expect(body).not.toContain(secret);
      }
      const headers = await csrfHeaders(context.request);
      const update = await context.request.put(`${candidatesPath}/${created.id}`, {
        headers, data: { ...profile, role: 'Forbidden update', expectedVersion: created.version },
      });
      expect(update.status()).toBe(403);
      const create = await context.request.post(candidatesPath, {
        headers: { ...headers, 'Idempotency-Key': randomUUID() },
        multipart: { profile: { name: 'profile.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(profile)) }, cv: syntheticCandidateCv() },
      });
      expect(create.status()).toBe(403);
      const replace = await context.request.put(`${candidatesPath}/${created.id}/cv`, {
        headers, multipart: { cv: syntheticCandidateCv(), expectedVersion: String(created.version) },
      });
      expect(replace.status()).toBe(403);
    }
    expect(await getCandidate(manager.context.request, created.id)).toEqual(created);
  } finally {
    await partner.close();
    await unauthorized.context.close();
    await manager.context.close();
  }
});

test('replaying the original multipart creation after editing returns the same candidate without another CV', async ({ browser }) => {
  const { context, page } = await managerContext(browser);
  try {
    const profile = syntheticCandidateData();
    const cv = syntheticCandidateCv();
    const key = randomUUID();
    const created = await createViaApi(context.request, profile, cv, key);
    await openCandidate(page, created);
    await page.getByRole('button', { name: t('edit'), exact: true }).click();
    await page.getByLabel(t('description'), { exact: true }).fill('Изменено после первоначального создания.');
    expect((await saveProfile(page, created.id)).status()).toBe(200);
    const replayed = await createViaApi(context.request, profile, cv, key);
    expect(replayed.id).toBe(created.id);
    const list = await context.request.get(candidatesPath, { params: { q: profile.role, availability: 'AVAILABLE' } });
    expect(list.status()).toBe(200);
    expect((await list.json()).items.map(item => item.id)).toEqual([created.id]);
    const persisted = await getCandidate(context.request, created.id);
    expect(persisted.description).toBe('Изменено после первоначального создания.');
    expect(persisted.hourlyRate).toEqual(created.hourlyRate);
    expect(persisted.cv).toEqual(created.cv);
    await page.reload();
    await openCandidate(page, persisted);
    await downloadCv(page, cv);
  } finally {
    await context.close();
  }
});

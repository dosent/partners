import { test, expect } from '@playwright/test';
import { mockCandidates, fillCandidate, candidateItem } from './staff-candidates-fixture.js';

for (const width of [1024, 1280, 1440, 1920]) {
  test(`candidate form keeps long data, labels and controls within ${width}px`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    await mockCandidates(page, { items: [] });
    const listLeft = await page.locator('.candidate-header h1').evaluate(el => el.getBoundingClientRect().left);
    await page.getByRole('button', { name: 'Добавить кандидата', exact: true }).click();
    await fillCandidate(page);
    await page.getByLabel('Роль / направление', { exact: true }).fill('SyntheticLongRole'.repeat(12));
    await page.getByLabel('Описание', { exact: true }).fill('Synthetic description '.repeat(300));
    const formLeft = await page.locator('.candidate-form').evaluate(el => el.getBoundingClientRect().left);
    expect(Math.abs(listLeft - formLeft)).toBeLessThanOrEqual(1);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
    const controls = await page.locator('.candidate-form input, .candidate-form select, .candidate-form textarea').evaluateAll(elements => elements.map(el => {
      const rect = el.getBoundingClientRect(); return { left: rect.left, right: rect.right, width: rect.width };
    }));
    expect(controls.every(rect => rect.left >= 0 && rect.right <= width && rect.width > 70)).toBe(true);
    const save = page.getByRole('button', { name: 'Сохранить', exact: true });
    await save.scrollIntoViewIfNeeded();
    await expect(save).toBeInViewport();
    if (width === 1440) await page.screenshot({ path: testInfo.outputPath('candidate-form.png'), fullPage: true });
  });
}

test('invalid file keeps all profile text and never sends a create request', async ({ page }) => {
  let posts = 0;
  await mockCandidates(page, { items: [], handler: async ({ request }) => { if (request.method() === 'POST') posts++; return false; } });
  await page.getByRole('button', { name: 'Добавить кандидата', exact: true }).click();
  await fillCandidate(page);
  await page.getByLabel('CV', { exact: true }).setInputFiles({ name: 'synthetic.txt', mimeType: 'text/plain', buffer: Buffer.from('synthetic') });
  await page.getByRole('button', { name: 'Сохранить', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('PDF');
  await expect(page.getByLabel('Описание', { exact: true })).toHaveValue('Synthetic description');
  expect(posts).toBe(0);
});

test('lost create response retries immutable payload with the original idempotency key', async ({ page }) => {
  const requests = [];
  await page.addInitScript(() => {
    const original = window.fetch;
    window.candidatePosts = [];
    window.fetch = async (url, options) => {
      if (String(url).endsWith('/admin/candidates') && options?.method === 'POST')
        window.candidatePosts.push(await options.body.get('profile').text());
      return original(url, options);
    };
  });
  await mockCandidates(page, { items: [], handler: async ({ request, route, json }) => {
    if (request.method() !== 'POST') return false;
    requests.push({ key: request.headers()['idempotency-key'], body: request.postData() });
    if (requests.length === 1) await route.abort('failed'); else await json(candidateItem);
    return true;
  } });
  await page.getByRole('button', { name: 'Добавить кандидата', exact: true }).click();
  await fillCandidate(page);
  await page.getByRole('button', { name: 'Сохранить', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Повторить создание', exact: true })).toBeVisible();
  await expect(page.getByLabel('Описание', { exact: true })).toHaveValue('Synthetic description');
  await page.getByRole('button', { name: 'Повторить создание', exact: true }).click();
  await expect(page.getByRole('heading', { name: candidateItem.role, exact: true })).toBeVisible();
  expect(requests).toHaveLength(2);
  expect(requests[0].key).toBe(requests[1].key);
  const profiles = await page.evaluate(() => window.candidatePosts);
  expect(profiles).toHaveLength(2);
  expect(profiles[0]).toBe(profiles[1]);
  expect(JSON.parse(profiles[0]).hourlyRate.amount).toBe('2500.50');
});

test('unknown creation survives reload and asks for the original CV before retrying the same key', async ({ page }) => {
  const keys = [];
  await mockCandidates(page, { items: [], handler: async ({ request, route, json }) => {
    if (request.method() !== 'POST') return false;
    keys.push(request.headers()['idempotency-key']);
    if (keys.length === 1) await route.abort('failed'); else await json(candidateItem);
    return true;
  } });
  await page.getByRole('button', { name: 'Добавить кандидата', exact: true }).click();
  await fillCandidate(page);
  await page.getByRole('button', { name: 'Сохранить', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Повторить создание', exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByLabel('Описание', { exact: true })).toHaveValue('Synthetic description');
  await expect(page.getByRole('button', { name: 'Повторить создание', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Повторить создание', exact: true }).click();
  await expect(page.getByRole('alert').last()).toContainText('Выберите исходный файл заново');
  expect(keys).toHaveLength(1);
  await page.getByLabel('CV', { exact: true }).setInputFiles({ name: 'synthetic.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4 synthetic') });
  await page.getByRole('button', { name: 'Повторить создание', exact: true }).click();
  await expect(page.getByRole('heading', { name: candidateItem.role, exact: true })).toBeVisible();
  expect(keys).toHaveLength(2);
  expect(keys[1]).toBe(keys[0]);
});

test('recovered idempotency conflict retains original key until the original CV is selected', async ({ page }) => {
  const keys = [];
  await mockCandidates(page, { items: [], handler: async ({ request, route, json }) => {
    if (request.method() !== 'POST') return false;
    keys.push(request.headers()['idempotency-key']);
    if (keys.length === 1) await route.abort('failed');
    else if (keys.length === 2) await json({ code: 'CONFLICT' }, 409);
    else await json(candidateItem);
    return true;
  } });
  await page.getByRole('button', { name: 'Добавить кандидата', exact: true }).click();
  await fillCandidate(page);
  await page.getByRole('button', { name: 'Сохранить', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Повторить создание', exact: true })).toBeVisible();
  await page.reload();
  const cv = page.getByLabel('CV', { exact: true });
  await cv.setInputFiles({ name: 'synthetic.pdf', mimeType: 'application/pdf', buffer: Buffer.alloc(Buffer.byteLength('%PDF-1.4 synthetic'), 120) });
  await page.getByRole('button', { name: 'Повторить создание', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Выберите исходный файл заново');
  await expect(page.getByLabel('Описание', { exact: true })).toBeDisabled();
  await cv.setInputFiles({ name: 'synthetic.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4 synthetic') });
  await page.getByRole('button', { name: 'Повторить создание', exact: true }).click();
  await expect(page.getByRole('heading', { name: candidateItem.role, exact: true })).toBeVisible();
  expect(keys).toHaveLength(3);
  expect(new Set(keys).size).toBe(1);
});

for (const status of [400, 422]) {
  test(`definitive CV rejection ${status} after unknown result unlocks the retained form safely`, async ({ page }) => {
    const keys = [];
    await mockCandidates(page, { items: [], handler: async ({ request, route, json }) => {
      if (request.method() !== 'POST') return false;
      keys.push(request.headers()['idempotency-key']);
      if (keys.length === 1) await route.abort('failed');
      else if (keys.length === 2) await json({ code: 'CANDIDATE_CREATE_REJECTED', fieldErrors: [{ field: 'cv' }] }, status);
      else await json(candidateItem, 201);
      return true;
    } });
    await page.getByRole('button', { name: 'Добавить кандидата', exact: true }).click();
    await fillCandidate(page);
    await page.getByRole('button', { name: 'Сохранить', exact: true }).click();
    await page.getByRole('button', { name: 'Повторить создание', exact: true }).click();
    await expect(page.getByLabel('Описание', { exact: true })).toBeEnabled();
    await expect(page.getByLabel('Описание', { exact: true })).toHaveValue('Synthetic description');
    await expect(page.getByRole('button', { name: 'Отмена', exact: true })).toBeEnabled();
    await page.getByLabel('CV', { exact: true }).setInputFiles({name:'valid.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF-1.4 valid')});
    await page.getByRole('button', { name: 'Сохранить', exact: true }).click();
    await expect(page.getByRole('heading', { name: candidateItem.role, exact: true })).toBeVisible();
    expect(keys[0]).toBe(keys[1]);
    expect(keys[2]).not.toBe(keys[0]);
  });
}

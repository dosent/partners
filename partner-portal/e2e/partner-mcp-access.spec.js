import { expect, test, ensurePublishedTestContent, loginAsPartner, loginAsStaff } from './support/fixtures.js';
import { openMcpSession, postUnauthenticatedMcp } from './support/mcp-client.js';
import { syntheticPartnerData } from './support/test-data.js';
import { E2E_BASE_URL } from './support/base-url.js';

const subject = `mcp-partner-${Date.now()}`;

test.beforeAll(async ({ browser }) => {
  const context = await browser.newContext();
  try { await ensurePublishedTestContent(context.request); }
  finally { await context.close(); }
});

test('partner creates, sees once, lists, and revokes an API key accessibly', async ({ page, browser }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await loginAsPartner(page, subject);
  const partnerRequest = page.context().request;
  const publishedResponse = await partnerRequest.get('/api/v1/public/registration-content/RU');
  expect(publishedResponse.ok()).toBeTruthy();
  const published = await publishedResponse.json();
  const staffContext = await browser.newContext({ baseURL: E2E_BASE_URL });
  try {
    const staffPage = await staffContext.newPage();
    await loginAsStaff(staffPage, 'ivan.neshin');
    const queueBefore = await staffContext.request.get('/api/v1/admin/partner-applications?page=0&size=100');
    expect(queueBefore.ok()).toBeTruthy();
    const existingIds = new Set((await queueBefore.json()).items.map(item => item.id));

    const partner = syntheticPartnerData();
    const partnerCsrf = await partnerRequest.get('/api/v1/auth/csrf').then(response => response.json());
    const submission = await partnerRequest.post('/api/v1/partner-applications', {
      headers: { [partnerCsrf.headerName ?? 'X-XSRF-TOKEN']: partnerCsrf.token },
      data: {
        partnerType: 'INDIVIDUAL', fullName: partner.fullName, email: partner.email, phone: partner.phone,
        countryCode: 'RU', region: partner.region, locale: 'RU', contentVersion: published.contentVersion,
        offerVersion: published.offers.INDIVIDUAL.version, offerSha256: published.offers.INDIVIDUAL.sha256,
        offerAccepted: true, privacyNoticeVersion: published.privacyNotice.version,
        privacyNoticeSha256: published.privacyNotice.sha256, privacyAccepted: true,
      },
    });
    expect(submission.ok()).toBeTruthy();
    await expect.poll(async () => {
      const queue = await staffContext.request.get('/api/v1/admin/partner-applications?page=0&size=100');
      const items = (await queue.json()).items;
      return items.filter(item => !existingIds.has(item.id)).length;
    }).toBe(1);
    const queueAfter = await staffContext.request.get('/api/v1/admin/partner-applications?page=0&size=100');
    const applicationId = (await queueAfter.json()).items.find(item => !existingIds.has(item.id)).id;
    const staffCsrf = await staffContext.request.get('/api/v1/auth/csrf').then(response => response.json());
    const approval = await staffContext.request.post(`/api/v1/admin/partner-applications/${applicationId}/approve`, {
      headers: { [staffCsrf.headerName ?? 'X-XSRF-TOKEN']: staffCsrf.token }, data: {},
    });
    expect(approval.ok()).toBeTruthy();
  } finally {
    await staffContext.close();
  }
  await page.goto('/');
  const navigation = page.getByRole('navigation', { name: 'Партнёрский портал' });
  const apiKeysNav = navigation.getByRole('button', { name: 'E2E RU apiKeys navigation' });
  await apiKeysNav.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: 'E2E RU apiKeys title' })).toBeVisible();
  const createButton = page.getByRole('button', { name: 'E2E RU apiKeys actions.create' });
  await expect(createButton).toBeVisible();
  const bounds = await createButton.boundingBox();
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(391);

  await page.route('**/api/v1/partner/api-keys', async route => {
    if (route.request().method() === 'POST') {
      await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'synthetic unavailable' }) });
    } else await route.continue();
  });
  await page.getByLabel('E2E RU apiKeys name.label').fill('E2E MCP client');
  await createButton.click();
  await expect(page.getByRole('alert')).toContainText('E2E RU apiKeys errors.create');
  await page.unroute('**/api/v1/partner/api-keys');
  await createButton.click();
  const secret = page.getByText(/^ppk_[0-9a-f-]+_[A-Za-z0-9_-]{43}$/);
  await expect(secret).toBeVisible();
  const revealed = await secret.textContent();
  const mcp = await openMcpSession(page.context().request, revealed);
  const tools = await mcp.call('tools/list');
  expect(tools.status).toBe(200);
  expect(tools.payload.result.tools.map(tool => tool.name)).not.toContain('partner_api_key_list');
  expect((await postUnauthenticatedMcp(page.context().request)).status()).toBe(401);
  expect((await postUnauthenticatedMcp(page.context().request, 'invalid-partner-key')).status()).toBe(401);
  await page.getByRole('button', { name: 'E2E RU apiKeys actions.cancel' }).click();
  await expect(secret).toHaveCount(0);
  await expect(page.getByText(revealed, { exact: true })).toHaveCount(0);
  await expect(page.getByText('E2E MCP client')).toBeVisible();

  await page.getByRole('button', { name: 'E2E RU apiKeys actions.revoke' }).click();
  await expect(page.getByText('E2E RU apiKeys revoke.confirm')).toBeVisible();
  await page.getByRole('button', { name: 'E2E RU apiKeys actions.revoke' }).last().click();
  await expect(page.getByText('E2E RU apiKeys status.revoked')).toBeVisible();
  expect((await postUnauthenticatedMcp(page.context().request, revealed)).status()).toBe(401);
  await expect(page.getByRole('button', { name: 'E2E RU apiKeys actions.revoke' })).toHaveCount(0);
});

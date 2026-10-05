import { expect, test, ensurePublishedTestContent, loginAsPartner } from './support/fixtures.js';

const subject = `mcp-partner-${Date.now()}`;

test.beforeAll(async ({ browser }) => {
  const context = await browser.newContext();
  try { await ensurePublishedTestContent(context.request); }
  finally { await context.close(); }
});

test('partner creates, sees once, lists, and revokes an API key accessibly', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await loginAsPartner(page, subject);
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
  await page.getByRole('button', { name: 'E2E RU apiKeys actions.cancel' }).click();
  await expect(secret).toHaveCount(0);
  await expect(page.getByText(revealed, { exact: true })).toHaveCount(0);
  await expect(page.getByText('E2E MCP client')).toBeVisible();

  await page.getByRole('button', { name: 'E2E RU apiKeys actions.revoke' }).click();
  await expect(page.getByText('E2E RU apiKeys revoke.confirm')).toBeVisible();
  await page.getByRole('button', { name: 'E2E RU apiKeys actions.revoke' }).last().click();
  await expect(page.getByText('E2E RU apiKeys status.revoked')).toBeVisible();
  await expect(page.getByRole('button', { name: 'E2E RU apiKeys actions.revoke' })).toHaveCount(0);
});

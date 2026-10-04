import { expect, test } from '@playwright/test';

test('public landing shows partner registration and sign-in actions', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: /создавайте новые возможности вместе/i })).toBeVisible();
  const register = page.getByRole('button', { name: /стать партнёром/i }).first();
  const signIn = page.getByRole('button', { name: /^войти$/i });
  await expect(register).toBeVisible();
  await expect(signIn).toBeVisible();

  await register.click();
  await expect(page).toHaveURL(/\/registration$/);
  await page.goto('/');
  await signIn.click();
  await expect(page).toHaveURL(/\/registration$/);
});

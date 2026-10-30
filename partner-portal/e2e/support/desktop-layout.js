import { expect } from '@playwright/test';

// Catches a grid child expanding the document instead of scrolling its board.
export async function expectDesktopLayout(page, actionName) {
  await expect(page.locator('main h1')).toBeVisible();
  const dimensions = await page.evaluate(() => ({
    width: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
    headingLeft: document.querySelector('main h1').getBoundingClientRect().left,
  }));
  expect(dimensions.scrollWidth, JSON.stringify(dimensions)).toBeLessThanOrEqual(dimensions.width + 1);
  if (actionName) {
    const action = page.getByRole('button', { name: actionName, exact: true }).first();
    await expect(action).toBeVisible();
    const box = await action.boundingBox();
    expect(box.x).toBeGreaterThanOrEqual(0);
    expect(box.x + box.width).toBeLessThanOrEqual(dimensions.width + 1);
    expect(box.height).toBeGreaterThanOrEqual(40);
  }
  return dimensions.headingLeft;
}

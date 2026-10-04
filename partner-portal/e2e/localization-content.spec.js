import { expect, test, loginAsStaff } from './support/fixtures.js';
import { uniqueFixtureId } from './support/test-data.js';

test('admin saves a draft, publishes complete Russian content, and cannot publish incomplete legal text', async ({ page }) => {
  const marker = uniqueFixtureId();
  const bodies = ['INDIVIDUAL', 'SOLE_PROPRIETOR', 'LEGAL_ENTITY'].map(type =>
    `Искусственный тестовый текст оферты ${type} ${marker}.`);
  const privacyBody = `Искусственный тестовый текст согласия ${marker}.`;
  try {
    await loginAsStaff(page, 'ivan.neshin');
    await page.getByRole('button', { name: 'Тексты регистрации' }).click();
    await expect(page).toHaveURL(/\/staff\/registration-content$/);
    await expect(page.getByRole('heading', { name: 'Контент регистрации и кабинета' })).toBeVisible();

    const title = `Регистрация партнёра ${marker}`;
    await page.getByLabel(/Заголовок регистрации/).fill(title);
    await expect(page.getByLabel(/Ключ логотипа/)).toHaveValue('simbirsoft');
    const offerEditors = page.getByLabel('Текст утверждённой оферты');
    await expect(offerEditors).toHaveCount(3);
    for (let index = 0; index < bodies.length; index += 1) await offerEditors.nth(index).fill(bodies[index]);
    await page.getByLabel('Текст утверждённого согласия').fill(privacyBody);
    await page.getByRole('button', { name: 'Сохранить черновик' }).click();
    await expect(page.getByRole('status')).toContainText('Черновик сохранён');

    await page.reload();
    await expect(page.getByLabel(/Заголовок регистрации/)).toHaveValue(title);
    await expect(page.getByLabel(/Ключ логотипа/)).toHaveValue('simbirsoft');
    for (let index = 0; index < bodies.length; index += 1) {
      await expect(offerEditors.nth(index)).toHaveValue(bodies[index]);
    }
    await expect(page.getByLabel('Текст утверждённого согласия')).toHaveValue(privacyBody);

    await page.getByRole('button', { name: 'Опубликовать' }).click();
    await expect(page.getByRole('status')).toContainText('Новая версия опубликована');
    await expect(page.getByRole('heading', { name: 'Опубликованный снимок' })).toBeVisible();
    await expect(page.locator('.rc-legal-preview').first()).toHaveText(bodies[0]);

    const publicResponse = await page.context().request.get('/api/v1/public/registration-content/RU');
    expect(publicResponse.status(), 'public registration content API status').toBe(200);
    const published = await publicResponse.json();
    expect(published.locale, 'public registration content API locale').toBe('RU');
    expect(published.strings['registration.title'], 'public registration content API title').toBe(title);
    expect(published.offers.INDIVIDUAL.body, 'published individual offer API body').toBe(bodies[0]);
    expect(published.offers.SOLE_PROPRIETOR.body, 'published sole proprietor offer API body').toBe(bodies[1]);
    expect(published.offers.LEGAL_ENTITY.body, 'published legal entity offer API body').toBe(bodies[2]);
    expect(published.privacyNotice.body, 'published privacy notice API body').toBe(privacyBody);
    expect(published.logoAssetKey, 'published logo asset API key').toBe('simbirsoft');

    await page.getByRole('button', { name: 'Черновик' }).click();
    await page.getByLabel('Текст утверждённой оферты').nth(0).fill('');
    await page.getByRole('button', { name: 'Сохранить черновик' }).click();
    await expect(page.getByRole('status')).toContainText('Черновик сохранён');
    const failedPublication = page.waitForResponse(response =>
      response.url().includes('/api/v1/admin/registration-content/ru/publish')
      && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Опубликовать' }).click();
    const publicationResponse = await failedPublication;
    expect(publicationResponse.status(), 'incomplete publication API status').toBe(422);
    await expect(page.getByRole('alert')).toContainText('Публикация невозможна');
    await expect(page.getByRole('alert')).toContainText('offers.INDIVIDUAL.body');
    const stillPublished = await page.context().request.get('/api/v1/public/registration-content/RU');
    expect(stillPublished.status(), 'previous publication remains public API status').toBe(200);
    expect(await stillPublished.json(), 'entire previous publication remains unchanged').toEqual(published);
  } finally {
    await page.context().close();
  }
});

test('admin can edit each locale and unpublished translations block publication', async ({ page }) => {
  try {
    await loginAsStaff(page, 'ivan.neshin');
    await page.getByRole('button', { name: 'Тексты регистрации' }).click();
    await page.getByLabel('Локаль контента').selectOption('en');
    const localizedField = page.getByLabel(/portal\.profile\.locale\.KZ/);
    await expect(localizedField).toHaveValue('');
    await localizedField.fill('');
    await page.getByRole('button', { name: 'Сохранить черновик' }).click();
    await expect(page.getByRole('status')).toContainText('Черновик сохранён');
    await page.getByRole('button', { name: 'Опубликовать' }).click();
    await expect(page.getByRole('alert')).toContainText('portal.profile.locale.KZ');
  } finally {
    await page.context().close();
  }
});

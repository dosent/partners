import assert from 'node:assert/strict';
import test from 'node:test';
import { registrationContentFixture } from '../e2e/support/test-data.js';
import { getRegistrationContent } from '../src/api/partnerRegistrationApi.js';

test('legacy published Russian content receives missing partner profile defaults', async () => {
  const originalFetch = globalThis.fetch;
  const legacyContent = { locale: 'RU', ...registrationContentFixture('RU') };
  for (const key of Object.keys(legacyContent.strings)) {
    if (key.startsWith('portal.profile.') || key.startsWith('portal.actions.') || key === 'portal.errors.logout') delete legacyContent.strings[key];
  }
  assert.equal(Object.keys(legacyContent.strings).length, 50, 'fixture matches legacy RU dictionary size');
  legacyContent.strings['portal.title'] = 'Кабинет партнёра, настроенный администратором';
  globalThis.fetch = async () => new Response(JSON.stringify(legacyContent), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });

  try {
    const content = await getRegistrationContent('RU');

    assert.equal(content.strings['portal.title'], 'Кабинет партнёра, настроенный администратором');
    assert.equal(content.strings['portal.profile.navigation'], 'Профиль');
    assert.equal(content.strings['portal.profile.title'], 'Профиль');
    assert.equal(content.strings['portal.profile.settings.title'], 'Контактные данные и настройки');
    assert.equal(content.strings['portal.profile.errors.localeUnavailable'], 'Для выбранного языка пока нет опубликованного содержимого.');
    assert.equal(content.strings['portal.actions.logout'], 'Выйти');
    assert.equal(content.strings['portal.actions.loggingOut'], 'Выходим…');
    assert.equal(content.strings['portal.errors.logout'], 'Не удалось выйти из кабинета. Попробуйте ещё раз.');
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('legacy non-Russian published content is not filled with Russian profile text', async () => {
  const originalFetch = globalThis.fetch;
  const legacyContent = { locale: 'EN', ...registrationContentFixture('EN') };
  for (const key of Object.keys(legacyContent.strings)) {
    if (key.startsWith('portal.profile.') || key.startsWith('portal.actions.') || key === 'portal.errors.logout') delete legacyContent.strings[key];
  }
  globalThis.fetch = async () => new Response(JSON.stringify(legacyContent), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });

  try {
    const content = await getRegistrationContent('EN');
    assert.equal(content.strings['portal.profile.navigation'], undefined);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

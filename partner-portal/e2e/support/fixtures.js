import { expect, test as base } from '@playwright/test';
import { registrationContentFixture } from './test-data.js';

export { expect };
export const test = base;

async function csrfToken(request) {
  const response = await request.get('/api/v1/auth/csrf');
  if (!response.ok()) throw new Error(`Could not obtain CSRF token: HTTP ${response.status()}`);
  const payload = await response.json();
  return { token: payload.token, headerName: payload.headerName ?? 'X-XSRF-TOKEN' };
}

export async function loginAsStaff(page, username, password = 'e2e-password') {
  await page.goto('/');
  const request = page.context().request;
  const csrf = await csrfToken(request);
  const response = await request.post('/api/v1/auth/staff/login', {
    data: { username, password },
    headers: { [csrf.headerName]: csrf.token },
  });
  if (!response.ok()) throw new Error(`Staff login failed: HTTP ${response.status()} ${await response.text()}`);
  const session = await response.json();
  await page.goto('/staff');
  return session;
}

export async function preparePartnerOAuth(page, subject) {
  if (!/^[a-z0-9-]+$/i.test(subject)) throw new Error('Partner fixture subject must contain only letters, digits, and hyphens');
  await page.context().route('**/*', async route => {
    const authorization = new URL(route.request().url());
    if (authorization.hostname !== 'oauth.yandex.ru' || authorization.pathname !== '/authorize') {
      await route.continue();
      return;
    }
    const state = authorization.searchParams.get('state');
    if (!state) throw new Error('Yandex OAuth authorization request did not include state');
    const redirectUri = authorization.searchParams.get('redirect_uri');
    if (!redirectUri) throw new Error('Yandex OAuth authorization request did not include redirect_uri');
    const callback = new URL(redirectUri);
    if (callback.pathname !== '/api/v1/auth/yandex/callback') {
      throw new Error('Yandex OAuth authorization request included an unexpected callback path');
    }
    callback.searchParams.set('code', `e2e-partner-${subject.toLowerCase()}`);
    callback.searchParams.set('state', state);
    await route.fulfill({ status: 302, headers: { location: callback.toString() } });
  });
}

export async function completePartnerOAuth(page, subject, startPath = '/api/v1/auth/yandex/start') {
  await preparePartnerOAuth(page, subject);
  const start = await page.context().request.get(startPath, { maxRedirects: 0 });
  if (start.status() !== 302) {
    throw new Error(`Yandex OAuth start failed: expected HTTP 302, received ${start.status()}`);
  }
  const location = start.headers().location;
  if (!location) throw new Error('Yandex OAuth start did not return a redirect location');
  const authorization = new URL(location);
  if (authorization.hostname !== 'oauth.yandex.ru' || authorization.pathname !== '/authorize') {
    throw new Error(`Yandex OAuth start returned an unexpected provider URL: ${authorization.origin}${authorization.pathname}`);
  }
  if (!authorization.searchParams.get('state')) throw new Error('Yandex OAuth start did not return state');
  await page.goto(authorization.toString());
}

export async function loginAsPartner(page, subject) {
  await page.goto('/');
  await completePartnerOAuth(page, subject);
  return page;
}

export async function ensurePublishedTestContent(request) {
  const csrf = await csrfToken(request);
  const login = await request.post('/api/v1/auth/staff/login', {
    data: { username: 'ivan.neshin', password: 'e2e-password' },
    headers: { [csrf.headerName]: csrf.token },
  });
  if (!login.ok()) throw new Error(`Content publisher staff login failed: HTTP ${login.status()}`);

  const stateResponse = await request.get('/api/v1/admin/registration-content/ru');
  if (!stateResponse.ok()) throw new Error(`Could not read registration content draft: HTTP ${stateResponse.status()}`);
  const state = await stateResponse.json();
  const draft = await request.put('/api/v1/admin/registration-content/ru/draft', {
    data: { expectedDraftVersion: state.draftVersion ?? 0, ...registrationContentFixture() },
    headers: { [csrf.headerName]: csrf.token },
  });
  if (!draft.ok()) throw new Error(`Could not save registration content fixture: HTTP ${draft.status()} ${await draft.text()}`);
  const saved = await draft.json();
  const publish = await request.post('/api/v1/admin/registration-content/ru/publish', {
    data: { expectedDraftVersion: saved.draftVersion },
    headers: { [csrf.headerName]: csrf.token },
  });
  if (!publish.ok()) throw new Error(`Could not publish registration content fixture: HTTP ${publish.status()} ${await publish.text()}`);
  const publicResponse = await request.get('/api/v1/public/registration-content/RU');
  if (!publicResponse.ok()) throw new Error(`Could not read published registration content: HTTP ${publicResponse.status()} ${await publicResponse.text()}`);
  return publicResponse.json();
}

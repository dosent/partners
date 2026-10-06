import { enrichLegacyRussianRegistrationContent } from '../registration/partnerProfileContentDefaults.js';

const API = '/api/v1';

async function decode(response) {
  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    const error = new Error(typeof payload?.message === 'string'
      ? payload.message
      : 'Не удалось выполнить запрос. Попробуйте ещё раз.');
    error.status = response.status;
    error.code = payload?.code;
    error.fields = payload?.fieldErrors ?? [];
    throw error;
  }
  return payload ?? {};
}

function csrfCookie() {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

async function csrfHeaders() {
  const payload = await decode(await fetch(`${API}/auth/csrf`, { credentials: 'include' }));
  const token = payload.token ?? payload.csrfToken ?? csrfCookie();
  const name = payload.headerName ?? payload.header ?? 'X-XSRF-TOKEN';
  return token ? { [name]: token } : {};
}

export const getPartnerSession = () => fetch(`${API}/auth/yandex/session`, { credentials: 'include' }).then(decode);

export async function logoutPartner() {
  const headers = await csrfHeaders();
  return decode(await fetch(`${API}/auth/yandex/logout`, {
    method: 'POST', credentials: 'include', headers,
  }));
}

export const getRegistrationContent = locale => fetch(
  `${API}/public/registration-content/${encodeURIComponent(locale)}`, { credentials: 'include' },
).then(decode).then(content => enrichLegacyRussianRegistrationContent(content, locale));

export const getMyPartnerApplication = () => fetch(
  `${API}/partner-applications/me`, { credentials: 'include' },
).then(decode);

export const getMyPartnerProfile = () => fetch(
  `${API}/partners/me`, { credentials: 'include' },
).then(decode);

export async function updateMyPartnerProfileSettings(settings) {
  const headers = await csrfHeaders();
  return decode(await fetch(`${API}/partners/me/settings`, {
    method: 'PUT',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(settings),
  }));
}

export async function submitPartnerApplication(payload, resubmit = false) {
  const headers = await csrfHeaders();
  return decode(await fetch(`${API}/partner-applications${resubmit ? '/me' : ''}`, {
    method: resubmit ? 'PUT' : 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(payload),
  }));
}

export const listMyPartnerApiKeys = () => fetch(`${API}/partner/api-keys`, {
  credentials: 'include',
}).then(decode);

export async function createMyPartnerApiKey(name) {
  const headers = await csrfHeaders();
  return decode(await fetch(`${API}/partner/api-keys`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify({ name }),
  }));
}

export async function revokeMyPartnerApiKey(id) {
  const headers = await csrfHeaders();
  return decode(await fetch(`${API}/partner/api-keys/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    credentials: 'include',
    headers,
  }));
}

import { createRequestId } from '../utils/requestId.js';

const API = '/api/v1/admin/candidates';
async function decode(response) {
  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    const error = new Error(typeof payload?.message === 'string' ? payload.message : `HTTP ${response.status}`);
    Object.assign(error, { status: response.status, code: payload?.code, traceId: payload?.traceId,
      fieldErrors: payload?.fieldErrors ?? [], fields: payload?.fieldErrors ?? [] });
    throw error;
  }
  return payload;
}
async function csrfHeaders() {
  const csrf = await decode(await fetch('/api/v1/auth/csrf', { credentials: 'include' }));
  const cookie = typeof document === 'undefined' ? null : document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)?.[1];
  const token = csrf.token ?? csrf.csrfToken ?? (cookie ? decodeURIComponent(cookie) : null);
  return token ? { [csrf.headerName ?? csrf.header ?? 'X-XSRF-TOKEN']: token } : {};
}
const candidateUrl = id => `${API}/${encodeURIComponent(id)}`;
export async function getCandidates({ q = '', availability = 'AVAILABLE', page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ q, page: String(page), size: String(size) });
  params.set('availability', availability || 'ALL');
  return decode(await fetch(`${API}?${params}`, { credentials: 'include' }));
}
export async function getCandidate(id) {
  return decode(await fetch(candidateUrl(id), { credentials: 'include' }));
}
export async function createCandidate({ profile, cv, idempotencyKey = createRequestId() }) {
  const headers = await csrfHeaders();
  const body = new FormData();
  body.append('profile', new Blob([JSON.stringify(profile)], { type: 'application/json' }));
  body.append('cv', cv, cv.name);
  return decode(await fetch(API, { method: 'POST', credentials: 'include',
    headers: { ...headers, 'Idempotency-Key': idempotencyKey }, body }));
}
export async function updateCandidate(id, { profile, expectedVersion }) {
  const headers = await csrfHeaders();
  return decode(await fetch(candidateUrl(id), { method: 'PUT', credentials: 'include',
    headers: { ...headers, 'Content-Type': 'application/json' }, body: JSON.stringify({ ...profile, expectedVersion }) }));
}
export async function replaceCandidateCv(id, { file, expectedVersion }) {
  const headers = await csrfHeaders();
  const body = new FormData();
  body.append('cv', file, file.name);
  body.append('expectedVersion', String(expectedVersion));
  return decode(await fetch(`${candidateUrl(id)}/cv`, { method: 'PUT', credentials: 'include', headers, body }));
}
export async function downloadCandidateCv(id) {
  const response = await fetch(`${candidateUrl(id)}/cv`, { credentials: 'include' });
  if (!response.ok) await decode(response);
  return response.blob();
}

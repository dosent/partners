const API = '/api/v1/partner/staffing-requests';

async function decode(response) {
  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    const error = new Error(payload?.message || 'Не удалось выполнить запрос. Попробуйте ещё раз.');
    error.status = response.status;
    error.fields = payload?.fieldErrors ?? [];
    throw error;
  }
  return payload ?? {};
}

async function csrfHeaders() {
  const payload = await decode(await fetch('/api/v1/auth/csrf', { credentials: 'include' }));
  const cookie = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)?.[1];
  const token = payload.token ?? payload.csrfToken ?? (cookie ? decodeURIComponent(cookie) : null);
  return token ? { [payload.headerName ?? payload.header ?? 'X-XSRF-TOKEN']: token } : {};
}

export async function getPartnerStaffingRequests({ page = 0, size = 20 } = {}) {
  return decode(await fetch(`${API}?page=${page}&size=${size}`, { credentials: 'include' }));
}
export async function getPartnerStaffingRequest(id) {
  return decode(await fetch(`${API}/${encodeURIComponent(id)}`, { credentials: 'include' }));
}
export async function createPartnerStaffingRequest(payload, idempotencyKey = crypto.randomUUID()) {
  const csrf = await csrfHeaders();
  return decode(await fetch(API, { method: 'POST', credentials: 'include',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey, ...csrf },
    body: JSON.stringify(payload) }));
}
export async function uploadPartnerStaffingAttachment(id, kind, file) {
  const csrf = await csrfHeaders();
  const body = new FormData();
  body.append('kind', kind);
  body.append('file', file, file.name);
  return decode(await fetch(`${API}/${encodeURIComponent(id)}/attachments`, {
    method: 'POST', credentials: 'include', headers: csrf, body,
  }));
}

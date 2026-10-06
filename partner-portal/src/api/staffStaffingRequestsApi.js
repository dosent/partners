const API = '/api/v1/admin/staffing-requests';

async function decode(response) {
  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    const error = new Error(typeof payload?.message === 'string'
      ? payload.message : 'Не удалось выполнить запрос. Попробуйте ещё раз.');
    error.status = response.status;
    error.code = payload?.code;
    error.traceId = payload?.traceId;
    error.fields = payload?.fieldErrors ?? [];
    throw error;
  }
  return payload ?? {};
}

async function csrfHeaders() {
  const csrf = await decode(await fetch('/api/v1/auth/csrf', { credentials: 'include' }));
  const cookieToken = () => {
    const cookie = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)?.[1];
    return cookie ? decodeURIComponent(cookie) : null;
  };
  const token = csrf.token ?? csrf.csrfToken ?? cookieToken();
  return token ? { [csrf.headerName ?? csrf.header ?? 'X-XSRF-TOKEN']: token } : {};
}

export async function getStaffStaffingRequestQueue({ page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return decode(await fetch(`${API}?${params}`, { credentials: 'include' }));
}

export async function getStaffStaffingAssignees() {
  return decode(await fetch(`${API}/assignees`, { credentials: 'include' }));
}

export async function assignStaffStaffingRequest(id, username, expectedVersion) {
  const headers = await csrfHeaders();
  return decode(await fetch(`${API}/${encodeURIComponent(id)}/assignment`, {
    method: 'PUT', credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify({ username, expectedVersion }),
  }));
}

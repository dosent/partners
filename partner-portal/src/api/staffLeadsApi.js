const API = '/api/v1';

function csrfCookie() {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

async function decode(response) {
  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    const error = new Error(typeof payload?.message === 'string'
      ? payload.message : 'Не удалось выполнить запрос. Попробуйте ещё раз.');
    error.status = response.status;
    error.code = payload?.code;
    throw error;
  }
  return payload ?? {};
}

async function csrfHeaders() {
  const csrf = await decode(await fetch(`${API}/auth/csrf`, { credentials: 'include' }));
  const token = csrf.token ?? csrf.csrfToken ?? csrfCookie();
  return token ? { [csrf.headerName ?? 'X-XSRF-TOKEN']: token } : {};
}

export async function getStaffLeadQueue({ page = 0, size = 20, query = '', status = '' } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (query) params.set('query', query);
  if (status) params.set('status', status);
  return decode(await fetch(`${API}/admin/leads?${params}`, { credentials: 'include' }));
}

export async function getStaffLead(id) {
  return decode(await fetch(`${API}/admin/leads/${encodeURIComponent(id)}`, { credentials: 'include' }));
}

async function post(id, path, body) {
  const headers = await csrfHeaders();
  return decode(await fetch(`${API}/admin/leads/${encodeURIComponent(id)}/${path}`, {
    method: 'POST', credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body),
  }));
}

export const changeStaffLeadStatus = (id, status) => post(id, 'status', { status });
export const sendStaffLeadMessage = (id, body, internal) => post(id, 'messages', { body, internal });

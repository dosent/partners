const API = '/api/v1';

async function decode(response) {
  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    const message = typeof payload?.message === 'string' ? payload.message : 'Не удалось выполнить запрос. Попробуйте ещё раз.';
    const error = new Error(message);
    error.status = response.status;
    error.code = payload?.code;
    throw error;
  }
  return payload ?? {};
}

function cookie(name) {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

async function csrfHeaders() {
  const existingToken = cookie('XSRF-TOKEN');
  if (existingToken) return { 'X-XSRF-TOKEN': existingToken };
  const payload = await decode(await fetch(`${API}/auth/csrf`, { credentials: 'include' }));
  const token = payload.token ?? payload.csrfToken ?? cookie('XSRF-TOKEN');
  const headerName = payload.headerName ?? payload.header ?? 'X-XSRF-TOKEN';
  return token ? { [headerName]: token } : {};
}

export async function loginStaff(username, password) {
  const headers = await csrfHeaders();
  return decode(await fetch(`${API}/auth/staff/login`, {
    method: 'POST', credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify({ username, password }),
  }));
}

export async function getStaffSession() {
  return decode(await fetch(`${API}/auth/staff/session`, { credentials: 'include' }));
}

export async function logoutStaff() {
  const headers = await csrfHeaders();
  return decode(await fetch(`${API}/auth/staff/logout`, { method: 'POST', credentials: 'include', headers }));
}

export async function getStaffRoles(username) {
  return decode(await fetch(`${API}/admin/staff-access/users/${encodeURIComponent(username)}/roles`, { credentials: 'include' }));
}

export async function replaceStaffRoles(username, roles) {
  const headers = await csrfHeaders();
  return decode(await fetch(`${API}/admin/staff-access/users/${encodeURIComponent(username)}/roles`, {
    method: 'PUT', credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify({ roles }),
  }));
}

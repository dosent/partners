const API = '/api/v1';

async function decode(response) {
  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    const error = new Error(typeof payload?.message === 'string'
      ? payload.message
      : 'Не удалось выполнить запрос. Попробуйте ещё раз.');
    error.status = response.status;
    error.code = payload?.code;
    error.fields = payload?.fieldErrors ?? payload?.errors ?? [];
    throw error;
  }
  return payload ?? {};
}

function cookie(name) {
  const entry = document.cookie.split(';').map(value => value.trim()).find(value => value.startsWith(`${name}=`));
  return entry ? decodeURIComponent(entry.slice(name.length + 1)) : null;
}

async function csrfHeaders() {
  const existing = cookie('XSRF-TOKEN');
  if (existing) return { 'X-XSRF-TOKEN': existing };
  const payload = await decode(await fetch(`${API}/auth/csrf`, { credentials: 'include' }));
  const token = payload.token ?? payload.csrfToken ?? cookie('XSRF-TOKEN');
  const headerName = payload.headerName ?? payload.header ?? 'X-XSRF-TOKEN';
  return token ? { [headerName]: token } : {};
}

export const getRegistrationContentAdmin = locale => fetch(
  `${API}/admin/registration-content/${encodeURIComponent(locale)}`, { credentials: 'include' },
).then(decode);

export async function saveRegistrationContentDraft(locale, payload) {
  const headers = await csrfHeaders();
  return decode(await fetch(`${API}/admin/registration-content/${encodeURIComponent(locale)}/draft`, {
    method: 'PUT', credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(payload),
  }));
}

export async function publishRegistrationContent(locale, expectedDraftVersion) {
  const headers = await csrfHeaders();
  return decode(await fetch(`${API}/admin/registration-content/${encodeURIComponent(locale)}/publish`, {
    method: 'POST', credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify({ expectedDraftVersion }),
  }));
}

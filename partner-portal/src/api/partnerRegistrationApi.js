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
  const existing = csrfCookie();
  if (existing) return { 'X-XSRF-TOKEN': existing };
  const payload = await decode(await fetch(`${API}/auth/csrf`, { credentials: 'include' }));
  const token = payload.token ?? payload.csrfToken ?? csrfCookie();
  const name = payload.headerName ?? payload.header ?? 'X-XSRF-TOKEN';
  return token ? { [name]: token } : {};
}

export const getPartnerSession = () => decode(fetch(`${API}/auth/yandex/session`, { credentials: 'include' }));

export const getRegistrationContent = locale => decode(fetch(
  `${API}/public/registration-content/${encodeURIComponent(locale)}`, { credentials: 'include' },
));

export const getMyPartnerApplication = () => decode(fetch(`${API}/partner-applications/me`, { credentials: 'include' }));

export async function submitPartnerApplication(payload, resubmit = false) {
  const headers = await csrfHeaders();
  return decode(await fetch(`${API}/partner-applications${resubmit ? '/me' : ''}`, {
    method: resubmit ? 'PUT' : 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(payload),
  }));
}

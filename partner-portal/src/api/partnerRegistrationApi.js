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

export const getRegistrationContent = locale => fetch(
  `${API}/public/registration-content/${encodeURIComponent(locale)}`, { credentials: 'include' },
).then(decode);

export const getMyPartnerApplication = () => fetch(
  `${API}/partner-applications/me`, { credentials: 'include' },
).then(decode);

export async function submitPartnerApplication(payload, resubmit = false) {
  const headers = await csrfHeaders();
  return decode(await fetch(`${API}/partner-applications${resubmit ? '/me' : ''}`, {
    method: resubmit ? 'PUT' : 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(payload),
  }));
}

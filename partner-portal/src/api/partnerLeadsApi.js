const API = '/api/v1/partner/leads';

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

async function csrfHeaders() {
  const payload = await decode(await fetch('/api/v1/auth/csrf', { credentials: 'include' }));
  const cookie = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)?.[1];
  const token = payload.token ?? payload.csrfToken ?? (cookie ? decodeURIComponent(cookie) : null);
  const name = payload.headerName ?? payload.header ?? 'X-XSRF-TOKEN';
  return token ? { [name]: token } : {};
}

export function getPartnerLeadBoard({ query = '', updatedFrom = '', updatedTo = '', status = '', limitPerColumn = 20, cursors = {} } = {}) {
  const params = new URLSearchParams({ limitPerColumn: String(limitPerColumn) });
  if (query.trim()) params.set('query', query.trim());
  if (updatedFrom) params.set('updatedFrom', updatedFrom);
  if (updatedTo) params.set('updatedTo', updatedTo);
  if (status) params.set('status', status);
  for (const [lane, cursor] of Object.entries(cursors)) {
    if (cursor) params.set(`cursor.${lane}`, cursor);
  }
  return fetch(`${API}/board?${params}`, { credentials: 'include' }).then(decode);
}

export async function getAllPartnerLeads() {
  const leads = new Map();
  let cursors = {};
  const complete = new Set();
  for (let page = 0; page < 100; page += 1) {
    const board = await getPartnerLeadBoard({ limitPerColumn: 100, cursors });
    const next = {};
    for (const column of board.columns ?? []) {
      if (complete.has(column.lane)) continue;
      for (const lead of column.items ?? []) leads.set(lead.id, lead);
      if (column.nextCursor) next[column.lane] = column.nextCursor;
      else complete.add(column.lane);
    }
    if (!Object.keys(next).length) break;
    cursors = next;
  }
  return [...leads.values()];
}

export async function createPartnerLead(payload, attachment = null) {
  const headers = await csrfHeaders();
  if (attachment) {
    const body = new FormData();
    body.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }), 'request.json');
    body.append('attachment', attachment, attachment.name);
    return decode(await fetch(API, {
      method: 'POST', credentials: 'include', headers, body,
    }));
  }
  return decode(await fetch(API, {
    method: 'POST', credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(payload),
  }));
}

export const getPartnerLead = leadId => fetch(`${API}/${encodeURIComponent(leadId)}`, { credentials: 'include' }).then(decode);

export const getPartnerLeadAttachmentUrl = (leadId, attachmentId) =>
  `${API}/${encodeURIComponent(leadId)}/attachments/${encodeURIComponent(attachmentId)}`;

export async function sendPartnerLeadMessage(leadId, message) {
  const headers = await csrfHeaders();
  return decode(await fetch(`${API}/${encodeURIComponent(leadId)}/messages`, {
    method: 'POST', credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify({ body: message }),
  }));
}

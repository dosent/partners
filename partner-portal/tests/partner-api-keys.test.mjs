import assert from 'node:assert/strict';
import test from 'node:test';
import * as api from '../src/api/partnerRegistrationApi.js';

const response = (body, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json' },
});

test('listing partner API keys includes session credentials', async () => {
  const originalFetch = globalThis.fetch;
  let request;
  globalThis.fetch = async (url, options = {}) => {
    request = { url, options };
    return response([]);
  };
  try {
    assert.deepEqual(await api.listMyPartnerApiKeys(), []);
    assert.equal(request.url, '/api/v1/partner/api-keys');
    assert.equal(request.options.credentials, 'include');
    assert.equal(request.options.method, undefined);
  } finally { globalThis.fetch = originalFetch; }
});

test('creating a partner API key fetches CSRF and sends the JSON name', async () => {
  const originalFetch = globalThis.fetch;
  const requests = [];
  globalThis.fetch = async (url, options = {}) => {
    requests.push({ url, options });
    return url === '/api/v1/auth/csrf' ? response({ token: 'csrf-value', headerName: 'X-XSRF-TOKEN' }) : response({ secret: 'shown-once' });
  };
  try {
    await api.createMyPartnerApiKey('CRM');
    assert.equal(requests[1].url, '/api/v1/partner/api-keys');
    assert.equal(requests[1].options.method, 'POST');
    assert.equal(requests[1].options.credentials, 'include');
    assert.equal(requests[1].options.headers['X-XSRF-TOKEN'], 'csrf-value');
    assert.equal(requests[1].options.headers['Content-Type'], 'application/json');
    assert.deepEqual(JSON.parse(requests[1].options.body), { name: 'CRM' });
  } finally { globalThis.fetch = originalFetch; }
});

test('revoking a partner API key sends a CSRF-protected DELETE to its resource', async () => {
  const originalFetch = globalThis.fetch;
  const requests = [];
  globalThis.fetch = async (url, options = {}) => {
    requests.push({ url, options });
    return url === '/api/v1/auth/csrf' ? response({ token: 'csrf-value', headerName: 'X-XSRF-TOKEN' }) : response({});
  };
  try {
    await api.revokeMyPartnerApiKey('key-123');
    assert.equal(requests[1].url, '/api/v1/partner/api-keys/key-123');
    assert.equal(requests[1].options.method, 'DELETE');
    assert.equal(requests[1].options.credentials, 'include');
    assert.equal(requests[1].options.headers['X-XSRF-TOKEN'], 'csrf-value');
  } finally { globalThis.fetch = originalFetch; }
});

test('API key errors preserve status and machine code', async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => response({ message: 'Недостаточно прав', code: 'FORBIDDEN' }, 403);
  try {
    await assert.rejects(api.listMyPartnerApiKeys(), error => error.status === 403 && error.code === 'FORBIDDEN');
  } finally { globalThis.fetch = originalFetch; }
});

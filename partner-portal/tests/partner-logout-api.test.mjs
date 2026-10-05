import assert from 'node:assert/strict';
import test from 'node:test';
import * as partnerApi from '../src/api/partnerRegistrationApi.js';

test('partner logout sends a CSRF-protected POST with session credentials', async () => {
  const originalFetch = globalThis.fetch;
  const requests = [];
  globalThis.fetch = async (url, options = {}) => {
    requests.push({ url, options });
    if (url === '/api/v1/auth/csrf') {
      return new Response(JSON.stringify({ token: 'csrf-value', headerName: 'X-XSRF-TOKEN' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }
    return new Response(null, { status: 200 });
  };

  try {
    await partnerApi.logoutPartner();

    assert.equal(requests[1].url, '/api/v1/auth/yandex/logout');
    assert.equal(requests[1].options.method, 'POST');
    assert.equal(requests[1].options.credentials, 'include');
    assert.equal(requests[1].options.headers['X-XSRF-TOKEN'], 'csrf-value');
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('partner logout exposes a failed server response to the caller', async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async url => {
    if (url === '/api/v1/auth/csrf') {
      return new Response(JSON.stringify({ token: 'csrf-value', headerName: 'X-XSRF-TOKEN' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }
    return new Response(JSON.stringify({ message: 'Выход временно недоступен.' }), {
      status: 503,
      headers: { 'Content-Type': 'application/json' },
    });
  };

  try {
    await assert.rejects(partnerApi.logoutPartner(), /Выход временно недоступен\./);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

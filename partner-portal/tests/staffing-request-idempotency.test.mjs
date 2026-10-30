import assert from 'node:assert/strict';
import test from 'node:test';
import { createPartnerStaffingRequest } from '../src/api/staffingRequestsApi.js';

test('staffing creation generates a UUID on HTTP where only getRandomValues is available', async () => {
  const originalCrypto = Object.getOwnPropertyDescriptor(globalThis, 'crypto');
  const originalFetch = globalThis.fetch;
  const originalDocument = globalThis.document;
  const sent = [];
  globalThis.document = { cookie: '' };
  Object.defineProperty(globalThis, 'crypto', { configurable: true, value: {
    getRandomValues(bytes) {
      bytes.set(Array.from({ length: 16 }, (_, index) => index));
      return bytes;
    },
  } });
  globalThis.fetch = async (url, options) => {
    if (url.endsWith('/csrf')) return Response.json({ token: 'test-csrf', headerName: 'X-XSRF-TOKEN' });
    sent.push(options);
    return Response.json({ id: 'saved-request', status: 'NEW' });
  };
  try {
    const result = await createPartnerStaffingRequest({ leadId: 'owned-lead' });
    assert.equal(result.id, 'saved-request');
    assert.equal(sent[0].headers['Idempotency-Key'], '00010203-0405-4607-8809-0a0b0c0d0e0f');
    assert.equal(sent[0].headers['X-XSRF-TOKEN'], 'test-csrf');
    assert.deepEqual(JSON.parse(sent[0].body), { leadId: 'owned-lead' });
  } finally {
    Object.defineProperty(globalThis, 'crypto', originalCrypto);
    globalThis.fetch = originalFetch;
    globalThis.document = originalDocument;
  }
});

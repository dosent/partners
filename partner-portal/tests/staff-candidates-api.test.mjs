import assert from 'node:assert/strict';
import test from 'node:test';

const client = () => import('../src/api/staffCandidatesApi.js');
const profile = { role: 'Java', grade: '', skills: ['Java'], experienceMonths: 0, description: 'Synthetic', availability: 'AVAILABLE', externalKey: null, hourlyRate: { amount: '2500.50', currency: 'RUB' } };
async function capture(run) {
  const original = globalThis.fetch;
  const requests = [];
  globalThis.fetch = async (url, options = {}) => {
    requests.push({ url, ...options });
    return Response.json(url.endsWith('/csrf') ? { token: 'csrf', headerName: 'X-CSRF-TEST' } : { id: 'candidate', version: 2 });
  };
  try { await run(await client(), requests); } finally { globalThis.fetch = original; }
}
test('candidate list encodes query and sends explicit all-availability filter, reads details with credentials', async () => {
  await capture(async (api, requests) => {
    await api.getCandidates({ q: 'Java & SQL', availability: '', page: 2, size: 20 });
    await api.getCandidate('id/1');
    const url = new URL(requests[0].url, 'https://example.test');
    assert.equal(url.searchParams.get('q'), 'Java & SQL');
    assert.equal(url.searchParams.get('availability'), 'ALL');
    assert.equal(url.searchParams.get('page'), '2');
    assert.equal(requests[1].url, '/api/v1/admin/candidates/id%2F1');
    assert.ok(requests.every(request => request.credentials === 'include'));
  });
});
test('multipart create preserves string rate and reusable HTTP-compatible key without setting boundary', async () => {
  await capture(async (api, requests) => {
    const cv = new File(['%PDF-1.4 synthetic'], 'synthetic.pdf', { type: 'application/pdf' });
    const key = 'cb8e170a-1e7c-4010-8710-29b009adfd00';
    await api.createCandidate({ profile, cv, idempotencyKey: key });
    const request = requests[1];
    assert.equal(request.method, 'POST');
    assert.equal(request.headers['Idempotency-Key'], key);
    assert.equal(request.headers['X-CSRF-TEST'], 'csrf');
    assert.equal(request.headers['Content-Type'], undefined);
    assert.deepEqual(JSON.parse(await request.body.get('profile').text()), profile);
    assert.equal(request.body.get('cv').name, 'synthetic.pdf');
  });
});
test('update sends null external key, exact rate string and expectedVersion; CV replacement sends version', async () => {
  await capture(async (api, requests) => {
    await api.updateCandidate('id', { profile, expectedVersion: 7 });
    assert.deepEqual(JSON.parse(requests[1].body), { ...profile, expectedVersion: 7 });
    await api.replaceCandidateCv('id', { file: new File(['test'], 'new.pdf'), expectedVersion: 8 });
    assert.equal(requests[3].method, 'PUT');
    assert.equal(requests[3].body.get('expectedVersion'), '8');
    assert.equal(requests[3].body.get('cv').name, 'new.pdf');
  });
});
test('structured errors preserve status code and fieldErrors', async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async () => Response.json({ message: 'Conflict', code: 'EXTERNAL_KEY_CONFLICT', fieldErrors: [{ field: 'externalKey', message: 'Duplicate' }] }, { status: 409 });
  try {
    await assert.rejects((await client()).getCandidates(), error => error.status === 409 && error.code === 'EXTERNAL_KEY_CONFLICT' && error.fieldErrors[0].field === 'externalKey');
  } finally { globalThis.fetch = original; }
});

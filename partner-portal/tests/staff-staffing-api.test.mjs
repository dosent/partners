import assert from 'node:assert/strict';
import test from 'node:test';

const client = () => import('../src/api/staffStaffingRequestsApi.js');

test('staff queue and assignees use cookie credentials and paginated admin routes', async () => {
  const api = await client();
  const requests = [];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options });
    return Response.json(url.endsWith('/assignees') ? [{ username: 'rm', role: 'RESOURCE_MANAGER' }]
      : { items: [], page: 2, size: 20, total: 0 });
  };
  try {
    assert.equal((await api.getStaffStaffingRequestQueue({ page: 2 })).page, 2);
    assert.equal((await api.getStaffStaffingAssignees())[0].role, 'RESOURCE_MANAGER');
    assert.deepEqual(requests.map(item => item.url), [
      '/api/v1/admin/staffing-requests?page=2&size=20', '/api/v1/admin/staffing-requests/assignees',
    ]);
    assert.ok(requests.every(item => item.options.credentials === 'include'));
  } finally { globalThis.fetch = originalFetch; }
});

test('assignment PUT sends CSRF, selected username and expectedVersion; null clears assignment', async () => {
  const api = await client();
  const requests = [];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options });
    return Response.json(url === '/api/v1/auth/csrf' ? { token: 'csrf', headerName: 'X-CSRF-TEST' }
      : { id: 'request/1', version: 8, assignedResourceManagerUsername: null });
  };
  try {
    await api.assignStaffStaffingRequest('request/1', 'rm', 6);
    const updated = await api.assignStaffStaffingRequest('request/1', null, 7);
    assert.equal(updated.version, 8);
    for (const [index, username, version] of [[1, 'rm', 6], [3, null, 7]]) {
      const { url, options } = requests[index];
      assert.equal(url, '/api/v1/admin/staffing-requests/request%2F1/assignment');
      assert.equal(options.method, 'PUT');
      assert.equal(options.credentials, 'include');
      assert.equal(options.headers['X-CSRF-TEST'], 'csrf');
      assert.deepEqual(JSON.parse(options.body), { username, expectedVersion: version });
    }
  } finally { globalThis.fetch = originalFetch; }
});

test('client preserves structured API error context', async () => {
  const api = await client();
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => Response.json({ message: 'Заявка изменена.', code: 'CONFLICT',
    traceId: 'trace', fieldErrors: [{ field: 'expectedVersion' }] }, { status: 409 });
  try {
    await assert.rejects(api.getStaffStaffingRequestQueue(), error => {
      assert.equal(error.status, 409);
      assert.equal(error.message, 'Заявка изменена.');
      assert.equal(error.code, 'CONFLICT');
      assert.equal(error.traceId, 'trace');
      assert.deepEqual(error.fields, [{ field: 'expectedVersion' }]);
      return true;
    });
  } finally { globalThis.fetch = originalFetch; }
});

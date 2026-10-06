const MCP_URL = process.env.E2E_MCP_URL ?? 'http://localhost:18089/mcp';

function decodeResponse(response, body) {
  const contentType = response.headers()['content-type'] ?? '';
  if (contentType.includes('text/event-stream')) {
    const data = body.split('\n').filter(line => line.startsWith('data:')).map(line => line.slice(5).trim()).at(-1);
    if (!data) throw new Error('MCP returned an empty event stream');
    return JSON.parse(data);
  }
  return JSON.parse(body);
}

export async function openMcpSession(request, key) {
  const headers = {
    'X-Partner-API-Key': key,
    Accept: 'application/json, text/event-stream',
    'Content-Type': 'application/json',
  };
  const response = await request.post(MCP_URL, {
    headers,
    data: {
      jsonrpc: '2.0', id: 1, method: 'initialize',
      params: {
        protocolVersion: '2025-03-26',
        capabilities: {},
        clientInfo: { name: 'partner-portal-e2e', version: '1.0.0' },
      },
    },
  });
  if (!response.ok()) throw new Error(`MCP initialize failed: HTTP ${response.status()} ${await response.text()}`);
  const initialized = decodeResponse(response, await response.text());
  if (initialized.error) throw new Error(`MCP initialize failed: ${JSON.stringify(initialized.error)}`);
  const sessionId = response.headers()['mcp-session-id'];
  if (!sessionId) throw new Error('MCP initialize did not return a session ID');
  const sessionHeaders = { ...headers, 'MCP-Session-Id': sessionId, 'MCP-Protocol-Version': '2025-03-26' };
  const notification = await request.post(MCP_URL, {
    headers: sessionHeaders,
    data: { jsonrpc: '2.0', method: 'notifications/initialized' },
  });
  if (![200, 202, 204].includes(notification.status())) {
    throw new Error(`MCP initialized notification failed: HTTP ${notification.status()} ${await notification.text()}`);
  }
  return {
    async call(method, params = {}) {
      const result = await request.post(MCP_URL, {
        headers: sessionHeaders,
        data: { jsonrpc: '2.0', id: 2, method, params },
      });
      const payload = decodeResponse(result, await result.text());
      return { status: result.status(), payload };
    },
  };
}

export async function createPartnerMcpKey(request, name = 'MCP end-to-end') {
  const csrfResponse = await request.get('/api/v1/auth/csrf');
  if (!csrfResponse.ok()) throw new Error(`CSRF retrieval failed: HTTP ${csrfResponse.status()}`);
  const csrf = await csrfResponse.json();
  const created = await request.post('/api/v1/partner/api-keys', {
    headers: { [csrf.headerName ?? 'X-XSRF-TOKEN']: csrf.token },
    data: { name },
  });
  if (!created.ok()) throw new Error(`MCP key creation failed: HTTP ${created.status()} ${await created.text()}`);
  return created.json();
}

export async function revokePartnerMcpKey(request, keyId) {
  const csrfResponse = await request.get('/api/v1/auth/csrf');
  const csrf = await csrfResponse.json();
  const response = await request.delete(`/api/v1/partner/api-keys/${keyId}`, {
    headers: { [csrf.headerName ?? 'X-XSRF-TOKEN']: csrf.token },
  });
  if (!response.ok()) throw new Error(`MCP key revocation failed: HTTP ${response.status()} ${await response.text()}`);
}

export async function postUnauthenticatedMcp(request, key) {
  return request.post(MCP_URL, {
    headers: {
      Accept: 'application/json, text/event-stream',
      'Content-Type': 'application/json',
      ...(key ? { 'X-Partner-API-Key': key } : {}),
    },
    data: { jsonrpc: '2.0', id: 1, method: 'tools/list', params: {} },
  });
}

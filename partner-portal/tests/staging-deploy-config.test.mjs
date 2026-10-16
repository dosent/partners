import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { parseEnv } from 'node:util';
import { parse } from 'yaml';

const compose = parse(readFileSync(new URL('../compose.yaml', import.meta.url), 'utf8'));
const stagingDefaults = () => parseEnv(readFileSync(new URL('../.env.staging.example', import.meta.url), 'utf8'));

// Resolve the ${NAME:-default} expressions used in port/auth configuration.
function resolve(value, env = {}) {
  return value.replace(/\$\{(\w+):-([^}]*)\}/g, (_, name, fallback) => env[name] || fallback);
}

function bindings(service, env = {}) {
  return (service.ports ?? []).map((port) => {
    const parts = resolve(port, env).split(':');
    return parts.length === 3
      ? { host: parts[0], published: parts[1], target: parts[2] }
      : { host: '0.0.0.0', published: parts[0], target: parts[1] };
  });
}

test('frontend_is_the_only_public_service', () => {
  const publicServices = Object.entries(compose.services)
    .filter(([, service]) => bindings(service).some(({ host }) => host !== '127.0.0.1'))
    .map(([name]) => name);
  assert.deepEqual(publicServices, ['frontend']);
  assert.deepEqual(bindings(compose.services.frontend, {
    PORTAL_BIND_ADDRESS: '192.0.2.10', PORTAL_PORT: '18088',
  }), [{ host: '192.0.2.10', published: '18088', target: '80' }]);
  for (const name of ['backend', 'postgres', 'clamav']) {
    assert.deepEqual(bindings(compose.services[name]), [], `${name} must remain internal`);
  }
});

test('mcp_and_minio_bind_to_loopback', () => {
  assert.deepEqual(bindings(compose.services.mcp), [
    { host: '127.0.0.1', published: '8081', target: '8081' },
  ]);
  assert.deepEqual(bindings(compose.services['object-storage']), [
    { host: '127.0.0.1', published: '9000', target: '9000' },
    { host: '127.0.0.1', published: '9001', target: '9001' },
  ]);
  assert.deepEqual(bindings(compose.services.frontend, stagingDefaults()), [
    { host: '0.0.0.0', published: '8088', target: '80' },
  ]);
});

test('stateful_volumes_are_named_and_stable', () => {
  assert.deepEqual(Object.keys(compose.volumes).sort(), ['object-storage-data', 'postgres-data']);
  assert.deepEqual(compose.services.postgres.volumes, ['postgres-data:/var/lib/postgresql/data']);
  assert.deepEqual(compose.services['object-storage'].volumes, ['object-storage-data:/data']);
  for (const volume of Object.values(compose.volumes)) {
    assert.equal(volume?.name, undefined, 'volume names must use the fixed deployment project');
    assert.equal(volume?.driver_opts, undefined, 'data volumes must not depend on a release host path');
  }
});

test('auth_providers_are_disabled_by_staging_defaults', () => {
  const env = stagingDefaults();
  for (const name of ['LDAP_ENABLED', 'YANDEX_ID_ENABLED']) {
    assert.equal(env[name], 'false');
    assert.equal(resolve(compose.services.backend.environment[name], env), 'false');
    assert.equal(resolve(compose.services.backend.environment[name]), 'false');
  }
  assert.equal(env.YANDEX_CLIENT_ID, '');
  assert.equal(env.YANDEX_CLIENT_SECRET, '');
  assert.equal(env.SESSION_COOKIE_SECURE, 'false');
});

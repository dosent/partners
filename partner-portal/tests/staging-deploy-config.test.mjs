import assert from 'node:assert/strict';
import { existsSync, mkdtempSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
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

const workflowPath = new URL('../../.github/workflows/deploy-staging.yml', import.meta.url);
function workflow() {
  assert.ok(existsSync(workflowPath), 'staging deployment workflow must exist');
  return parse(readFileSync(workflowPath, 'utf8'));
}
function step(id) {
  const result = workflow().jobs.deploy.steps.find((entry) => entry.id === id);
  assert.ok(result?.run, `workflow must provide executable ${id} step`);
  return result;
}
function shell(script, cwd, env = {}) {
  return spawnSync('bash', ['-euo', 'pipefail', '-c', script], {
    cwd, encoding: 'utf8', env: { ...process.env, ...env },
  });
}
function git(cwd, ...args) {
  const result = spawnSync('git', args, { cwd, encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
  return result.stdout.trim();
}
function repository(t) {
  const root = mkdtempSync(join(tmpdir(), 'staging-workflow-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  git(root, 'init', '--initial-branch=main');
  git(root, 'config', 'user.email', 'fixture@example.invalid');
  git(root, 'config', 'user.name', 'Workflow fixture');
  const files = {
    'outside.txt': 'not portal',
    'partner-portal/compose.yaml': 'services: {}',
    'partner-portal/deploy/staging/deploy.sh': '#!/bin/bash\nexit 0\n',
    'partner-portal/src/app.js': 'portal',
    'partner-portal/.env.example': 'SAFE=example',
    'partner-portal/.env.staging.example': 'SAFE=example',
    'partner-portal/.env': 'PRIVATE=fixture',
    'partner-portal/backend/.env.secret': 'PRIVATE=fixture',
    'partner-portal/node_modules/package/index.js': 'dependency',
    'partner-portal/backend/dist/app.js': 'build output',
    'partner-portal/test-results/failure.txt': 'test artifact',
    'partner-portal/playwright-report/index.html': 'test artifact',
    'partner-portal/coverage/index.html': 'test artifact',
    'partner-portal/backend/build/backend.jar': 'build output',
    'partner-portal/.git/config': 'metadata',
  };
  for (const [name, content] of Object.entries(files)) {
    mkdirSync(join(root, name, '..'), { recursive: true });
    writeFileSync(join(root, name), content);
  }
  // Local Git metadata is never part of a committed source tree.
  git(root, 'add', '--force', '.');
  git(root, 'commit', '-m', 'fixture main');
  const sha = git(root, 'rev-parse', 'HEAD');
  git(root, 'update-ref', 'refs/remotes/origin/main', sha);
  return { root, sha };
}

test('workflow_accepts_only_main_push_and_manual_sha_on_staging_runner', () => {
  const config = workflow();
  assert.deepEqual(Object.keys(config.on).sort(), ['push', 'workflow_dispatch']);
  assert.deepEqual(config.on.push.branches, ['main']);
  assert.equal(config.on.workflow_dispatch.inputs.sha.required, true);
  assert.equal(config.on.workflow_dispatch.inputs.sha.type, 'string');
  assert.deepEqual(config.jobs.deploy['runs-on'], ['self-hosted', 'linux', 'staging']);
  assert.equal(config.jobs.deploy.environment, 'staging');
  assert.match(config.jobs.deploy.if, /github\.ref == 'refs\/heads\/main'/);
  assert.match(config.jobs.deploy.if, /github\.event_name == 'push'/);
  assert.match(config.jobs.deploy.if, /github\.event_name == 'workflow_dispatch'/);
  assert.equal(config.concurrency.group, 'partner-portal-staging');
  assert.equal(config.concurrency['cancel-in-progress'], false);
  assert.deepEqual(config.permissions, { contents: 'read' });
  const checkout = config.jobs.deploy.steps.find((entry) => entry.uses?.startsWith('actions/checkout@'));
  assert.equal(checkout.with.ref, 'main');
  assert.equal(checkout.with['fetch-depth'], 0);
  assert.equal(checkout.with['persist-credentials'], false);
});

test('workflow_rejects_invalid_unknown_and_non_main_sha_before_delivery', (t) => {
  const { root, sha } = repository(t);
  const script = step('select').run;
  const output = join(root, 'output');
  const accepted = shell(script, root, { REQUESTED_SHA: sha, GITHUB_OUTPUT: output });
  assert.equal(accepted.status, 0, accepted.stderr);
  assert.equal(readFileSync(output, 'utf8'), `sha=${sha}\n`);
  git(root, 'checkout', '-b', 'feature');
  writeFileSync(join(root, 'feature.txt'), 'feature only');
  git(root, 'add', 'feature.txt');
  git(root, 'commit', '-m', 'not main');
  for (const requested of ['main', 'f'.repeat(40), `${sha}; touch injected`, git(root, 'rev-parse', 'HEAD')]) {
    rmSync(output, { force: true });
    const rejected = shell(script, root, { REQUESTED_SHA: requested, GITHUB_OUTPUT: output });
    assert.notEqual(rejected.status, 0, `must reject ${requested}`);
    assert.equal(existsSync(output), false);
    assert.equal(existsSync(join(root, 'injected')), false);
  }
});

test('workflow_packages_selected_git_tree_without_env_dependencies_or_artifacts', (t) => {
  const { root, sha } = repository(t);
  const temp = join(root, 'runner-temp');
  mkdirSync(temp);
  // Local changes and untracked files must never enter a release.
  writeFileSync(join(root, 'partner-portal/src/app.js'), 'local change');
  writeFileSync(join(root, 'partner-portal/untracked.txt'), 'local file');
  const result = shell(step('package').run, root, { RELEASE_SHA: sha, RUNNER_TEMP: temp });
  assert.equal(result.status, 0, result.stderr);
  const archive = join(temp, 'staging-package/release.tar.gz');
  const listing = spawnSync('tar', ['-tzf', archive], { encoding: 'utf8' });
  assert.equal(listing.status, 0, listing.stderr);
  const files = listing.stdout.trim().split('\n').filter((name) => !name.endsWith('/')).sort();
  assert.deepEqual(files, ['.env.example', '.env.staging.example', 'compose.yaml', 'deploy/staging/deploy.sh', 'src/app.js']);
  assert.equal(spawnSync('tar', ['-xOzf', archive, 'src/app.js'], { encoding: 'utf8' }).stdout, 'portal');
  assert.equal(readFileSync(join(temp, 'staging-package/deploy.sh'), 'utf8'), '#!/bin/bash\nexit 0\n');
});

test('workflow_uses_environment_ssh_credentials_and_safe_diagnostics', () => {
  const delivery = step('deliver');
  assert.deepEqual(delivery.env, {
    RELEASE_SHA: '${{ steps.select.outputs.sha }}',
    STAGING_HOST: '${{ vars.STAGING_HOST }}', STAGING_USER: '${{ vars.STAGING_USER }}',
    STAGING_SSH_PRIVATE_KEY: '${{ secrets.STAGING_SSH_PRIVATE_KEY }}',
    STAGING_KNOWN_HOSTS: '${{ secrets.STAGING_KNOWN_HOSTS }}',
  });
  const scripts = workflow().jobs.deploy.steps.map((entry) => entry.run ?? '').join('\n');
  assert.doesNotMatch(scripts, /set\s+-[^\n]*x|ssh-keyscan|StrictHostKeyChecking=(?:no|accept-new)|docker\s+(?:compose\s+)?(?:logs|inspect)|printenv|upload-artifact|cat\s+[^\n]*(?:staging\.env|known_hosts|private_key)/);
  assert.match(delivery.run, /set \+x/);
  assert.match(delivery.run, /StrictHostKeyChecking=yes/);
  assert.match(delivery.run, /UserKnownHostsFile=/);
  assert.match(delivery.run, /GlobalKnownHostsFile=\/dev\/null/);
  assert.match(delivery.run, /IdentitiesOnly=yes/);
  assert.match(delivery.run, /::add-mask::/);
  assert.match(delivery.run, /trap cleanup EXIT/);
  for (const entry of workflow().jobs.deploy.steps.filter((entry) => entry.run)) {
    const syntax = spawnSync('bash', ['-n'], { input: entry.run, encoding: 'utf8' });
    assert.equal(syntax.status, 0, `${entry.id}: ${syntax.stderr}`);
  }
});

test('workflow_cleans_ssh_secrets_and_transfer_directory_on_ssh_failure', (t) => {
  const root = mkdtempSync(join(tmpdir(), 'staging-ssh-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const bin = join(root, 'bin');
  mkdirSync(bin);
  const calls = join(root, 'calls');
  writeFileSync(join(bin, 'ssh'), `#!/bin/bash\nprintf '%s\\n' "$*" >> "$SSH_CALLS"\nexit 255\n`, { mode: 0o700 });
  writeFileSync(join(bin, 'scp'), '#!/bin/bash\nexit 98\n', { mode: 0o700 });
  const result = shell(step('deliver').run, root, {
    PATH: `${bin}:${process.env.PATH}`, RUNNER_TEMP: root,
    RELEASE_SHA: 'a'.repeat(40), STAGING_HOST: 'staging.example.invalid', STAGING_USER: 'deploy',
    STAGING_SSH_PRIVATE_KEY: 'fixture-private-material', STAGING_KNOWN_HOSTS: 'fixture-pinned-host', SSH_CALLS: calls,
  });
  assert.notEqual(result.status, 0);
  assert.equal(readdirSync(root).some((name) => name.startsWith('staging-ssh.')), false);
  const args = readFileSync(calls, 'utf8');
  assert.match(args, /StrictHostKeyChecking=yes/);
  assert.match(args, /UserKnownHostsFile=/);
  assert.match(args, /GlobalKnownHostsFile=\/dev\/null/);
  assert.doesNotMatch(args, /fixture-private-material|fixture-pinned-host/);
  // add-mask workflow commands are consumed by GitHub; no ordinary output may expose secrets.
  assert.doesNotMatch(result.stderr, /fixture-private-material|fixture-pinned-host/);
  assert.doesNotMatch(result.stdout.split('\n').filter((line) => !line.startsWith('::add-mask::')).join('\n'), /fixture-private-material|fixture-pinned-host/);
});

test('workflow_deploys_exact_sha_and_removes_transferred_archive_after_success_or_failure', (t) => {
  const root = mkdtempSync(join(tmpdir(), 'staging-transfer-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const bin = join(root, 'bin');
  mkdirSync(bin);
  // Exercise the workflow's remote Bash body locally; replace only SSH transport.
  writeFileSync(join(bin, 'ssh'), `#!/bin/bash
set -euo pipefail
while [[ $1 == -o || $1 == -i ]]; do shift 2; done
[[ $1 == deploy@staging.example.invalid ]] || exit 97
shift
if [[ $1 == bash ]]; then
  "$@"
else
  bash -c "$1"
fi
`, { mode: 0o700 });
  writeFileSync(join(bin, 'scp'), `#!/bin/bash
set -euo pipefail
[[ $1 == -q ]] && shift
while [[ $1 == -o || $1 == -i ]]; do shift 2; done
transfer=\${3#*:}
printf '%s\\n' "$transfer" > "$TRANSFER_RECORD"
[[ \${FAIL_TRANSFER:-false} == false ]] || exit 96
cp "$1" "$2" "$transfer"
`, { mode: 0o700 });
  const script = step('deliver').run;
  for (const mode of ['success', 'transfer-failure', 'deploy-failure']) {
    const runner = join(root, mode);
    const packageDir = join(runner, 'staging-package');
    mkdirSync(packageDir, { recursive: true });
    writeFileSync(join(packageDir, 'release.tar.gz'), 'fixture archive');
    writeFileSync(join(packageDir, 'deploy.sh'), `#!/bin/bash
set -euo pipefail
[[ $1 == aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa ]]
[[ $(cat "$2") == 'fixture archive' ]]
printf 'deployed\\n' > "$DEPLOY_RECORD"
exit "\${DEPLOY_RESULT:-0}"
`);
    const transferRecord = join(runner, 'transfer-record');
    const deployRecord = join(runner, 'deploy-record');
    const result = shell(script, root, {
      PATH: `${bin}:${process.env.PATH}`, RUNNER_TEMP: runner,
      RELEASE_SHA: 'a'.repeat(40), STAGING_HOST: 'staging.example.invalid', STAGING_USER: 'deploy',
      STAGING_SSH_PRIVATE_KEY: 'fixture-private-material', STAGING_KNOWN_HOSTS: 'fixture-pinned-host',
      TRANSFER_RECORD: transferRecord, DEPLOY_RECORD: deployRecord,
      FAIL_TRANSFER: String(mode === 'transfer-failure'), DEPLOY_RESULT: mode === 'deploy-failure' ? '42' : '0',
    });
    assert.equal(result.status === 0, mode === 'success', `${mode}: ${result.stderr}`);
    assert.equal(existsSync(deployRecord), mode !== 'transfer-failure');
    assert.equal(existsSync(readFileSync(transferRecord, 'utf8').trim()), false, `${mode} must remove VM archive directory`);
    assert.equal(readdirSync(runner).some((name) => name.startsWith('staging-ssh.')), false);
  }
});

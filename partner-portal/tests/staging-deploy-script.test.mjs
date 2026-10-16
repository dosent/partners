import assert from 'node:assert/strict';
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const script = new URL('../deploy/staging/deploy.sh', import.meta.url).pathname;
const sha = 'a'.repeat(40);
function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), 'staging-deploy-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const bin = join(root, 'bin');
  const home = join(root, 'home');
  const releases = join(root, 'releases');
  const envFile = join(home, '.config/partner-portal/staging.env');
  mkdirSync(bin); mkdirSync(releases); mkdirSync(join(home, '.config/partner-portal'), { recursive: true });
  writeFileSync(envFile, 'DB_PASSWORD=never-print-this\n', { mode: 0o600 });
  const payload = join(root, 'payload'); mkdirSync(payload);
  writeFileSync(join(payload, 'compose.yaml'), 'services: {}\n');
  const archive = join(root, 'release.tar.gz');
  assert.equal(spawnSync('tar', ['-czf', archive, '-C', payload, '.']).status, 0);
  const log = join(root, 'docker.log');
  writeFileSync(join(bin, 'docker'), `#!/bin/bash
printf '%s\\n' "$*" >> "$CALL_LOG"
case " $* " in
  *" up "*) echo 'never-print-this application output'; exit "\${FAIL_UP:-0}" ;;
  *" port "*) echo '0.0.0.0:18088' ;;
  *" ps "*) echo 'backend healthy' ;;
  *" config "*) exit "\${FAIL_CONFIG:-0}" ;;
esac
`);
  writeFileSync(join(bin, 'curl'), `#!/bin/bash
printf '%s\\n' "$*" >> "$CURL_LOG"
exit "\${FAIL_SMOKE:-0}"
`);
  chmodSync(join(bin, 'docker'), 0o755); chmodSync(join(bin, 'curl'), 0o755);
  return { root, envFile, releases, archive, log,
    calls: () => existsSync(log) ? readFileSync(log, 'utf8') : '',
    run: (revision = sha, extra = {}, file = archive) => spawnSync('bash', [script, revision, file], {
      encoding: 'utf8', env: { ...process.env, HOME: home, PATH: `${bin}:${process.env.PATH}`,
        STAGING_RELEASES_DIR: releases, CALL_LOG: log, CURL_LOG: join(root, 'curl.log'), ...extra },
    }),
  };
}

test('rejects_invalid_sha', (t) => {
  const f = fixture(t);
  const result = f.run('../../escape');
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /SHA/);
  assert.equal(f.calls(), '');
  assert.deepEqual(readdirSync(f.releases), []);
});

test('requires_runtime_env_before_compose', (t) => {
  const f = fixture(t); rmSync(f.envFile);
  const result = f.run();
  assert.notEqual(result.status, 0); assert.match(result.stderr, /runtime env/);
  assert.equal(f.calls(), '');
});

test('rejects_insecure_env_permissions', (t) => {
  const f = fixture(t); chmodSync(f.envFile, 0o644);
  const result = f.run();
  assert.notEqual(result.status, 0); assert.match(result.stderr, /0600/);
  assert.equal(f.calls(), '');
});

test('keeps_data_volumes', (t) => {
  const f = fixture(t);
  for (const extra of [{}, {}, { FAIL_UP: '1' }, { FAIL_SMOKE: '1' }]) {
    const result = f.run(sha, extra);
    assert.equal(result.status === 0, !extra.FAIL_UP && !extra.FAIL_SMOKE, result.stderr);
    assert.doesNotMatch(result.stdout + result.stderr, /never-print-this/);
  }
  const lines = f.calls().trim().split('\n');
  assert(lines.every((line) => line.includes('--project-name partner-portal-staging')));
  assert(lines.every((line) => line.includes(`--env-file ${f.envFile}`)));
  assert(lines.some((line) => line.endsWith('up --build --detach --wait')));
  assert(lines.some((line) => line.includes('ps --all --format')));
  assert.doesNotMatch(f.calls(), /\b(down|prune|logs|volume|rm)\b/);
  assert.match(readFileSync(join(f.root, 'curl.log'), 'utf8'), /http:\/\/127\.0\.0\.1:18088\//);
  assert(existsSync(join(f.releases, sha, 'compose.yaml')));
});

test('prunes_old_releases_only_after_success', (t) => {
  const f = fixture(t);
  for (const digit of ['1', '2', '3', '4', '5']) mkdirSync(join(f.releases, digit.repeat(40)));
  mkdirSync(join(f.releases, 'operator-notes'));
  assert.notEqual(f.run(sha, { FAIL_UP: '1' }).status, 0);
  assert.equal(readdirSync(f.releases).filter((name) => /^[a-f0-9]{40}$/.test(name)).length, 6);
  assert.notEqual(f.run(sha, { FAIL_SMOKE: '1' }).status, 0);
  assert.equal(readdirSync(f.releases).filter((name) => /^[a-f0-9]{40}$/.test(name)).length, 6);
  assert.equal(f.run().status, 0);
  assert.equal(readdirSync(f.releases).filter((name) => /^[a-f0-9]{40}$/.test(name)).length, 4);
  assert(existsSync(join(f.releases, sha)));
  assert(existsSync(join(f.releases, 'operator-notes')));
});

test('rejects_missing_archive_and_incomplete_configuration_before_up', (t) => {
  const f = fixture(t);
  const absent = f.run(sha, {}, join(f.root, 'absent.tar.gz'));
  assert.notEqual(absent.status, 0); assert.match(absent.stderr, /archive/);
  assert.equal(f.calls(), '');
  const incomplete = f.run(sha, { FAIL_CONFIG: '1' });
  assert.notEqual(incomplete.status, 0); assert.match(incomplete.stderr, /configuration/);
  assert.doesNotMatch(f.calls(), / up /);
});

test('rejects_archive_traversal', (t) => {
  const f = fixture(t);
  const malicious = join(f.root, 'malicious.tar');
  assert.equal(spawnSync('python3', ['-c', `import tarfile,io,sys
with tarfile.open(sys.argv[1], 'w') as t:
 m=tarfile.TarInfo('../escape'); m.size=1; t.addfile(m,io.BytesIO(b'x'))`, malicious]).status, 0);
  const result = f.run(sha, {}, malicious);
  assert.notEqual(result.status, 0); assert.match(result.stderr, /archive/);
  assert(!existsSync(join(f.releases, 'escape')));
  assert.equal(f.calls(), '');
});

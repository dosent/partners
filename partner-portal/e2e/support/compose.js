import { execFile } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';

const exec = promisify(execFile);
const projectDir = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const composeArgs = ['compose', '-f', 'compose.e2e.yaml'];

async function compose(...args) {
  return exec('docker', [...composeArgs, ...args], {
    cwd: projectDir,
    maxBuffer: 10 * 1024 * 1024,
  });
}

async function assertFreshDatabase() {
  const sql = `SELECT
    (SELECT count(*) FROM staff_role_grant) = 1
    AND (SELECT count(*) FROM staff_role_grant
         WHERE username = 'ivan.neshin' AND role = 'SYSTEM_ADMIN') = 1
    AND (SELECT count(*) FROM staff_role_audit) = 1
    AND (SELECT count(*) FROM partner_application) = 0
    AND (SELECT count(*) FROM partner_application_revision) = 0
    AND (SELECT count(*) FROM partner_application_consent) = 0
    AND (SELECT count(*) FROM partner_application_status_history) = 0
    AND (SELECT count(*) FROM registration_content_draft) = 0
    AND (SELECT count(*) FROM registration_content_publication) = 0
    AND (SELECT count(*) FROM registration_content_current) = 0;`;
  const { stdout } = await compose('exec', '-T', 'postgres', 'psql',
    '-U', 'partners_e2e', '-d', 'partners_e2e', '-Atc', sql);
  if (stdout.trim() !== 't') {
    throw new Error('E2e database contains data beyond the bootstrap administrator');
  }
}

export async function startE2eStack() {
  await compose('down', '-v', '--remove-orphans');
  await compose('up', '--build', '--wait');

  const response = await fetch('http://127.0.0.1:18088/');
  if (!response.ok) {
    throw new Error(`E2e frontend is not ready: HTTP ${response.status}`);
  }
  await compose('exec', '-T', 'backend', 'wget', '-q', '-O', '/dev/null',
    'http://127.0.0.1:8080/actuator/health/readiness');
  await assertFreshDatabase();
}

export async function stopE2eStack(reportDir) {
  await mkdir(reportDir, { recursive: true });
  try {
    const { stdout, stderr } = await compose('logs', '--no-color', '--timestamps');
    await writeFile(resolve(reportDir, 'e2e-services.log'), stdout + stderr);
  } finally {
    await compose('down', '-v', '--remove-orphans');
  }
}

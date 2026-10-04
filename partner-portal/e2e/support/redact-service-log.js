import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

export async function redactGeneratedDevelopmentPassword(reportDir) {
  const path = resolve(reportDir, 'e2e-services.log');
  try {
    const log = await readFile(path, 'utf8');
    const sanitized = log.replace(/Using generated security password: [^\s]+/g,
      'Using generated security password: [REDACTED]');
    if (sanitized !== log) await writeFile(path, sanitized);
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
  }
}

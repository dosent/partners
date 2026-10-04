import { resolve } from 'node:path';
import { stopE2eStack } from './support/compose.js';
import { redactGeneratedDevelopmentPassword } from './support/redact-service-log.js';

export default async function globalTeardown() {
  const reportDir = resolve(process.cwd(), process.env.PLAYWRIGHT_REPORT_DIR ?? 'playwright-report');
  try {
    await stopE2eStack(reportDir);
  } finally {
    await redactGeneratedDevelopmentPassword(reportDir);
  }
}

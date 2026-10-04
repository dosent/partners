import { resolve } from 'node:path';
import { startE2eStack, stopE2eStack } from './support/compose.js';
import { redactGeneratedDevelopmentPassword } from './support/redact-service-log.js';

export default async function globalSetup() {
  try {
    await startE2eStack();
  } catch (error) {
    try {
      const reportDir = resolve(process.cwd(), process.env.PLAYWRIGHT_REPORT_DIR ?? 'playwright-report');
      try {
        await stopE2eStack(reportDir);
      } finally {
        await redactGeneratedDevelopmentPassword(reportDir);
      }
    } catch (cleanupError) {
      error.message += `\nE2E stack cleanup also failed: ${cleanupError.message}`;
    }
    throw error;
  }
}

import { defineConfig } from '@playwright/test';
import { E2E_BASE_URL } from './e2e/support/base-url.js';

const reportDir = process.env.PLAYWRIGHT_REPORT_DIR ?? 'playwright-report';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  reporter: [['list'], ['html', { outputFolder: `${reportDir}/html`, open: 'never' }]],
  globalSetup: './e2e/global-setup.js',
  globalTeardown: './e2e/global-teardown.js',
  use: {
    baseURL: E2E_BASE_URL,
    browserName: 'chromium',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
});

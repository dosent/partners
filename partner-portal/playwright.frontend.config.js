import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  testMatch: '**/*.spec.js',
  workers: 1,
  reporter: 'list',
  outputDir: 'test-results/frontend',
  use: { baseURL: 'http://127.0.0.1:5178', browserName: 'chromium',
    trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  webServer: {
    command: 'npm run preview -- --host 127.0.0.1 --port 5178 --strictPort',
    url: 'http://127.0.0.1:5178',
  },
});

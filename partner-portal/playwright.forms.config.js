import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './tests', testMatch: 'candidate-forms.spec.js', workers: 1, reporter: 'list',
  outputDir: 'test-results/forms',
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }, { name: 'webkit', use: { browserName: 'webkit' } }],
  use: { baseURL: 'http://127.0.0.1:5179', trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  webServer: { command: 'npm run preview -- --host 127.0.0.1 --port 5179 --strictPort', url: 'http://127.0.0.1:5179' },
});

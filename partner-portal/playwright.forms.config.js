import { defineConfig } from '@playwright/test';
import frontend from './playwright.frontend.config.js';

export default defineConfig({
  ...frontend,
  grep: /form keeps|profile form/,
  outputDir: 'test-results/forms',
  projects: [
    { name: 'chromium', use: { browserName: 'chromium' } },
    { name: 'webkit', use: { browserName: 'webkit' } },
  ],
});

/**
 * CG-DOC: End-to-end test configuration or scenario: playwright.config.
 */
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './',
  timeout: 30_000,
  use: {
    // Login and questionnaire flows run against the Expo web client. Gateway-only
    // checks use GATEWAY_URL explicitly inside the access-validation spec.
    baseURL:
      process.env.WEB_URL ||
      process.env.GATEWAY_URL ||
      'http://localhost:8081',
  },
});

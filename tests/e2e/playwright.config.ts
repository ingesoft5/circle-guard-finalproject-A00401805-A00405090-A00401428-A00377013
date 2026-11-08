/**
 * CG-DOC: End-to-end test configuration or scenario: playwright.config.
 */
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './',
  timeout: 30_000,
  use: {
    baseURL: process.env.GATEWAY_URL || 'http://localhost:8087',
  },
});

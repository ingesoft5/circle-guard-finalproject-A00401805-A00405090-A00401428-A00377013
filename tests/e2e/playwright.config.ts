/**
 * CG-DOC: End-to-end test configuration or scenario: playwright.config.
 *
 * On-prem the k3d Traefik ingress listens on :30080 and matches the `Host` header.
 * The CI job passes E2E_HOST_RESOLVER (e.g. `MAP dev.circleguard.local 127.0.0.1`)
 * so `dev.circleguard.local:30080` resolves in-browser without touching /etc/hosts,
 * and full URLs in the specs stay consistent with WEB_URL=http://dev.circleguard.local:30080.
 */
import { defineConfig } from '@playwright/test';

const hostResolver = process.env.E2E_HOST_RESOLVER;

export default defineConfig({
  testDir: './',
  timeout: 30_000,
  use: {
    // Login and questionnaire flows run against the Expo web client. Gateway-only
    // checks use GATEWAY_URL explicitly inside the access-validation spec.
    baseURL:
      process.env.WEB_URL ||
      process.env.GATEWAY_URL ||
      'http://dev.circleguard.local:30080',
    ...(hostResolver
      ? { launchOptions: { args: [`--host-resolver-rules=${hostResolver}`] } }
      : {}),
  },
});

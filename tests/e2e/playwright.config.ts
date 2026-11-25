/**
 * CG-DOC: End-to-end test configuration. On-prem the k3d Traefik ingress listens
 * on :30080 and matches the Host header. CI passes E2E_HOST_RESOLVER
 * (e.g. `MAP dev.circleguard.local 127.0.0.1`) so `dev.circleguard.local:30080`
 * resolves in-browser without touching /etc/hosts.
 */
import { defineConfig } from '@playwright/test';

const hostResolver = process.env.E2E_HOST_RESOLVER;
const isCI = Boolean(process.env.CI || process.env.JENKINS_URL);

export default defineConfig({
  testDir: './',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  // Two retries absorb transient ingress/pod restarts; failures are still
  // reported by the JUnit reporter and by the anti-regression assertions.
  retries: isCI ? 2 : 0,
  workers: isCI ? 1 : undefined,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['junit', { outputFile: 'test-results/junit.xml' }],
  ],
  outputDir: 'test-results/artifacts',
  globalSetup: './global-setup.ts',
  globalTeardown: './global-teardown.ts',
  use: {
    baseURL:
      process.env.WEB_URL ||
      process.env.GATEWAY_URL ||
      'http://dev.circleguard.local:30080',
    ...(hostResolver
      ? { launchOptions: { args: [`--host-resolver-rules=${hostResolver}`] } }
      : {}),
    // Diagnostics are produced locally; the pipeline does not publish captures
    // from failed runs (see the archive rules in the Jenkinsfile).
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
});

/**
 * CG-DOC: fail fast when the ingress is unreachable, before running any spec.
 * The health check uses the loopback address with the ingress Host header so it
 * does not depend on the browser-only --host-resolver-rules flag.
 */
import { request } from '@playwright/test';

export default async function globalSetup(): Promise<void> {
  const healthUrl = process.env.E2E_HEALTHCHECK_URL || 'http://127.0.0.1:30080';
  const context = await request.newContext({
    baseURL: healthUrl,
    extraHTTPHeaders: { Host: 'dev.circleguard.local' },
  });
  try {
    const response = await context.get('/', { timeout: 15_000 });
    console.log(`[e2e] ingress ${healthUrl} responded HTTP ${response.status()}`);
  } finally {
    await context.dispose();
  }
}

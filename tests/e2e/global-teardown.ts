/**
 * CG-DOC: closes the E2E run. Reports are written by the Playwright reporters;
 * no credentials, tokens or test data are emitted here.
 */
export default async function globalTeardown(): Promise<void> {
  const runId = process.env.E2E_ANONYMOUS_ID || 'local';
  console.log(`[e2e] finished run ${runId}`);
}

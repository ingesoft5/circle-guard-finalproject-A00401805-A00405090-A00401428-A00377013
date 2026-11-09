/**
 * CG-DOC: End-to-end test configuration or scenario: access-validation.spec.
 */
import { test, expect } from '@playwright/test';

const gateway = process.env.GATEWAY_URL || 'http://localhost:8087';

test('el gateway está saludable', async ({ request }) => {
  const response = await request.get(`${gateway}/actuator/health`);
  expect(response.ok()).toBeTruthy();
  expect(await response.text()).toContain('UP');
});

test('el gateway rechaza una petición sin token de personal', async ({ request }) => {
  const response = await request.post(`${gateway}/api/v1/gate/validate`, {
    data: { token: 'invalid' },
  });
  expect([401, 403]).toContain(response.status());
});

test('el gateway rechaza un JWT de personal malformado', async ({ request }) => {
  const response = await request.post(`${gateway}/api/v1/gate/validate`, {
    headers: { Authorization: 'Bearer not-a-valid-jwt' },
    data: { token: 'invalid' },
  });
  expect([401, 403]).toContain(response.status());
});

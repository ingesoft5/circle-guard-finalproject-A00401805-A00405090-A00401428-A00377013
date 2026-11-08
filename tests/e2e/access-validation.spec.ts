import { test, expect } from '@playwright/test';

test('el gateway está saludable', async ({ request }) => {
  const response = await request.get('/actuator/health');
  expect(response.ok()).toBeTruthy();
  expect(await response.text()).toContain('UP');
});

test('el gateway rechaza una petición sin token de personal', async ({ request }) => {
  const response = await request.post('/api/v1/gate/validate', {
    data: { token: 'invalid' },
  });
  expect([401, 403]).toContain(response.status());
});

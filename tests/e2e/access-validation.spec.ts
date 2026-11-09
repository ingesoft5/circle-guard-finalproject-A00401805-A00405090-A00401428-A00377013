/**
 * CG-DOC: End-to-end test configuration or scenario: access-validation.spec.
 */
import { test, expect } from '@playwright/test';

const AUTH_URL = process.env.AUTH_URL || 'http://localhost:8180';

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

test('el gateway rechaza un JWT de personal malformado', async ({ request }) => {
  const response = await request.post('/api/v1/gate/validate', {
    headers: { Authorization: 'Bearer not-a-valid-jwt' },
    data: { token: 'invalid' },
  });
  expect([401, 403]).toContain(response.status());
});

test('auth-service está saludable', async ({ request }) => {
  const response = await request.get(`${AUTH_URL}/actuator/health`);
  expect(response.ok()).toBeTruthy();
  const body = await response.json();
  expect(body.status).toBe('UP');
});

test('auth-service rechaza credenciales inválidas con 401', async ({ request }) => {
  const response = await request.post(`${AUTH_URL}/api/v1/auth/login`, {
    data: { username: 'staff_guard', password: 'wrong-password' },
  });
  expect(response.status()).toBe(401);
});

test('flujo E2E completo: login RBAC en auth-service, generación de QR y validación en gateway', async ({ request }) => {
  // 1. Login en auth-service (fallback Dual-Chain -> PostgreSQL local_users)
  const loginRes = await request.post(`${AUTH_URL}/api/v1/auth/login`, {
    data: { username: 'staff_guard', password: 'password' },
  });
  expect(loginRes.status()).toBe(200);
  const loginData = await loginRes.json();
  expect(loginData.token).toBeTruthy();
  expect(loginData.type).toBe('Bearer');
  expect(loginData.anonymousId).toBeTruthy();

  const authHeader = { Authorization: `Bearer ${loginData.token}` };

  // 2. Generación de token QR efímero firmado en auth-service
  const qrRes = await request.get(`${AUTH_URL}/api/v1/auth/qr/generate`, {
    headers: authHeader,
  });
  expect(qrRes.status()).toBe(200);
  const qrData = await qrRes.json();
  expect(qrData.qrToken).toBeTruthy();
  expect(qrData.expiresIn).toBe('300');

  // 3. Consulta de usuarios por permiso RBAC en auth-service
  const permRes = await request.get(`${AUTH_URL}/api/v1/users/permissions/alert:receive_priority`, {
    headers: authHeader,
  });
  expect(permRes.status()).toBe(200);
  const users = await permRes.json();
  expect(Array.isArray(users)).toBeTruthy();
  expect(users.some((u: { username: string }) => u.username === 'health_user')).toBeTruthy();

  // 4. Validación cruzada en gateway usando el JWT de staff_guard (con gate:scan) y el qrToken emitido por auth-service
  const gateRes = await request.post('/api/v1/gate/validate', {
    headers: authHeader,
    data: { token: qrData.qrToken },
  });
  expect(gateRes.status()).toBe(200);
  const gateData = await gateRes.json();
  expect(['GREEN', 'RED']).toContain(gateData.status);
});

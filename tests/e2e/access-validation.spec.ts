/**
 * CG-DOC: End-to-end test configuration or scenario: access-validation.spec.
 */
import { test, expect } from '@playwright/test';

const AUTH_URL = process.env.AUTH_URL || 'http://localhost:8180';
const IDENTITY_URL = process.env.IDENTITY_URL || 'http://localhost:8083';
const GATEWAY_URL = process.env.GATEWAY_URL || 'http://localhost:8087';
const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

test('el gateway está saludable', async ({ request }) => {
  const response = await request.get(`${GATEWAY_URL}/actuator/health`);
  expect(response.ok()).toBeTruthy();
  expect(await response.text()).toContain('UP');
});

test('el gateway rechaza una petición sin token de personal', async ({ request }) => {
  const response = await request.post(`${GATEWAY_URL}/api/v1/gate/validate`, {
    data: { token: 'invalid' },
  });
  expect([401, 403]).toContain(response.status());
});

test('el gateway rechaza un JWT de personal malformado', async ({ request }) => {
  const response = await request.post(`${GATEWAY_URL}/api/v1/gate/validate`, {
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
  const gateRes = await request.post(`${GATEWAY_URL}/api/v1/gate/validate`, {
    headers: authHeader,
    data: { token: qrData.qrToken },
  });
  expect(gateRes.status()).toBe(200);
  const gateData = await gateRes.json();
  expect(['GREEN', 'RED']).toContain(gateData.status);
});

test('identity-service está saludable', async ({ request }) => {
  const response = await request.get(`${IDENTITY_URL}/actuator/health/liveness`);
  expect(response.ok()).toBeTruthy();
  const body = await response.json();
  expect(body.status).toBe('UP');
});

test('identity-service anonimiza identidad real y visitante de forma idempotente y bloquea lookup sin autorización', async ({ request }) => {
  // 1. Mapeo idempotente en la bóveda criptográfica
  const firstMap = await request.post(`${IDENTITY_URL}/api/v1/identities/map`, {
    data: { realIdentity: 'student_user' },
  });
  expect(firstMap.status()).toBe(200);
  const firstData = await firstMap.json();
  expect(firstData.anonymousId).toMatch(UUID_REGEX);

  const secondMap = await request.post(`${IDENTITY_URL}/api/v1/identities/map`, {
    data: { realIdentity: 'student_user' },
  });
  expect(secondMap.status()).toBe(200);
  const secondData = await secondMap.json();
  expect(secondData.anonymousId).toBe(firstData.anonymousId);

  // 2. Registro de visitante temporal sin exponer PII
  const visitorRes = await request.post(`${IDENTITY_URL}/api/v1/identities/visitor`, {
    data: {
      name: 'Visitante Externo',
      email: 'visitante@externo.org',
      reason_for_visit: 'Conferencia académica',
    },
  });
  expect(visitorRes.status()).toBe(200);
  const visitorData = await visitorRes.json();
  expect(visitorData.anonymousId).toMatch(UUID_REGEX);
  expect(JSON.stringify(visitorData)).not.toContain('visitante@externo.org');

  // 3. Intento de des-anonimización sin autoridad identity:lookup -> 401
  const lookupRes = await request.get(`${IDENTITY_URL}/api/v1/identities/lookup/${firstData.anonymousId}`);
  expect([401, 403]).toContain(lookupRes.status());
});

test('flujo E2E de anonimización: auth-service delega en identity-service y el JWT emitido no expone PII', async ({ request }) => {
  // 1. Consultar el anonymousId persistido en identity-service para health_user
  const mapRes = await request.post(`${IDENTITY_URL}/api/v1/identities/map`, {
    data: { realIdentity: 'health_user' },
  });
  expect(mapRes.status()).toBe(200);
  const { anonymousId: vaultAnonymousId } = await mapRes.json();
  expect(vaultAnonymousId).toMatch(UUID_REGEX);

  // 2. Iniciar sesión en auth-service con health_user y verificar correspondencia exacta con la bóveda
  const loginRes = await request.post(`${AUTH_URL}/api/v1/auth/login`, {
    data: { username: 'health_user', password: 'password' },
  });
  expect(loginRes.status()).toBe(200);
  const loginData = await loginRes.json();
  expect(loginData.anonymousId).toBe(vaultAnonymousId);

  // 3. Decodificar el payload del JWT y verificar que solo transporta el anonymousId (<UUID>) sin PII
  const payloadBase64 = loginData.token.split('.')[1];
  const decodedPayload = Buffer.from(payloadBase64, 'base64url').toString('utf-8');
  const claims = JSON.parse(decodedPayload);
  expect(claims.sub).toBe(vaultAnonymousId);
  expect(decodedPayload).not.toContain('health_user');
  expect(decodedPayload).not.toContain('health@univ.edu');
});

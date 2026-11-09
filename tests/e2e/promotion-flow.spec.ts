/**
 * CG-DOC: End-to-end scenario for the promotion service: questionnaire, Kafka, status.
 *
 * The questionnaire reaches promotion-service as a Kafka event, so this spec
 * publishes the same records form-service publishes and then reads the aggregated
 * status counts back over HTTP. The visitors have to exist before the run: the
 * pipeline seeds them with deploy/scripts/seed-visitor.sh, which is why
 * E2E_ANONYMOUS_ID is required instead of silently generating an unused id.
 */
import { test, expect, type APIRequestContext } from '@playwright/test';
import { Kafka, logLevel } from 'kafkajs';

const PROMOTION_URL = process.env.PROMOTION_URL || 'http://localhost:8088';
const KAFKA_BROKERS = (process.env.KAFKA_BROKERS || 'localhost:29092').split(',');

const kafka = new Kafka({
  clientId: 'circleguard-e2e',
  brokers: KAFKA_BROKERS,
  logLevel: logLevel.NOTHING,
  connectionTimeout: 10_000,
  retry: { retries: 3 },
});

function visitorId(role: string): string {
  const base = process.env.E2E_ANONYMOUS_ID?.trim();
  if (!base) {
    throw new Error(
      'Falta E2E_ANONYMOUS_ID: los visitantes se siembran antes de la prueba con deploy/scripts/seed-visitor.sh.',
    );
  }
  return `${base}-${role}`;
}

async function publish(topic: string, event: Record<string, unknown>): Promise<void> {
  const producer = kafka.producer();
  await producer.connect();
  try {
    await producer.send({ topic, messages: [{ value: JSON.stringify(event) }] });
  } finally {
    await producer.disconnect();
  }
}

async function statusCounts(request: APIRequestContext): Promise<Record<string, number>> {
  const response = await request.get(`${PROMOTION_URL}/api/v1/health-status/stats`);
  expect(response.ok(), 'el endpoint de estadísticas debe responder 200').toBeTruthy();
  return response.json();
}

test('promotion-service está saludable en el entorno', async ({ request }) => {
  const response = await request.get(`${PROMOTION_URL}/actuator/health`);
  expect(response.ok()).toBeTruthy();
  expect(await response.text()).toContain('"status":"UP"');
});

test('el cuestionario con síntomas promueve al visitante a SUSPECT', async ({ request }) => {
  const anonymousId = visitorId('symptoms');
  const before = await statusCounts(request);

  await publish('survey.submitted', { anonymousId, hasSymptoms: true });

  await expect
    .poll(async () => (await statusCounts(request)).suspectCount ?? 0, {
      message: 'el visitante debe quedar en SUSPECT tras el cuestionario',
      timeout: 30_000,
      intervals: [500, 1_000, 2_000],
    })
    .toBe((before.suspectCount ?? 0) + 1);
});

test('el cuestionario sin síntomas no cambia el estado del visitante', async ({ request }) => {
  const anonymousId = visitorId('control');
  const before = await statusCounts(request);

  await publish('survey.submitted', { anonymousId, hasSymptoms: false });

  // Sin una espera positiva no se distingue "no promovió" de "todavía no procesó",
  // así que se deja pasar la ventana en la que un cambio habría aparecido.
  await new Promise((resolve) => setTimeout(resolve, 5_000));

  const after = await statusCounts(request);
  expect(after.suspectCount ?? 0).toBe(before.suspectCount ?? 0);
  expect(after.probableCount ?? 0).toBe(before.probableCount ?? 0);
});

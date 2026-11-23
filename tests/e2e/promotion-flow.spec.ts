/**
 * CG-DOC: End-to-end scenario for the promotion service: questionnaire, Kafka, status.
 *
 * The questionnaire reaches promotion-service as a Kafka event, so this spec
 * publishes the same records form-service publishes and then reads the aggregated
 * status counts back over HTTP. Kafka has no route through the Ingress and the
 * events must go through the same channel form-service uses, so they are produced
 * with `kubectl exec` against the broker pod (apache/kafka image ships
 * kafka-console-producer.sh). The visitors have to exist before the run: the
 * pipeline seeds them with `kubectl exec ... cypher-shell`, which is why
 * E2E_ANONYMOUS_ID is required instead of silently generating an unused id.
 */
import { execSync } from 'node:child_process';
import { test, expect, type APIRequestContext } from '@playwright/test';

const PROMOTION_URL = process.env.PROMOTION_URL || 'http://dev.circleguard.local:30080';
const KUBE_NS = process.env.KUBE_NS || 'cg-dev';

function visitorId(role: string): string {
  const base = process.env.E2E_ANONYMOUS_ID?.trim();
  if (!base) {
    throw new Error(
      'Falta E2E_ANONYMOUS_ID: los visitantes se siembran antes de la prueba con `kubectl exec ... cypher-shell`.',
    );
  }
  return `${base}-${role}`;
}

function shellEscape(value: string): string {
  return value.replace(/'/g, `'\\''`);
}

async function publish(topic: string, event: Record<string, unknown>): Promise<void> {
  const payload = JSON.stringify(event);
  execSync(
    `kubectl exec -n ${KUBE_NS} deploy/kafka -- bash -c 'printf "%s\\n" '"'"'${shellEscape(payload)}'"'"' | /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic ${topic}'`,
    { stdio: ['ignore', 'pipe', 'pipe'] },
  );
}

async function statusCounts(request: APIRequestContext): Promise<Record<string, number>> {
  const response = await request.get(`${PROMOTION_URL}/api/v1/health-status/stats`);
  expect(response.ok(), 'el endpoint de estadísticas debe responder 200').toBeTruthy();
  return response.json();
}

test('promotion-service responde vía ingress', async ({ request }) => {
  const response = await request.get(`${PROMOTION_URL}/api/v1/health-status/stats`);
  expect(response.ok()).toBeTruthy();
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

  // Without a positive wait, "did not promote" cannot be distinguished from "not processed yet",
  // so the window in which a change would have appeared is allowed to pass.
  await new Promise((resolve) => setTimeout(resolve, 5_000));

  const after = await statusCounts(request);
  expect(after.suspectCount ?? 0).toBe(before.suspectCount ?? 0);
  expect(after.probableCount ?? 0).toBe(before.probableCount ?? 0);
});

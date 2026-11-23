/**
 * CG-DOC: End-to-end scenario for notification-service: questionnaire, Kafka,
 * promotion cascade and multi-channel dispatch.
 *
 * notification-service exposes no HTTP API beyond actuator and no Ingress route, so
 * every assertion runs inside the cluster with `kubectl exec`, the same channel
 * promotion-flow uses to publish its events. The spec seeds its own visitor (the
 * pipeline only seeds the two visitors promotion-flow needs), publishes the same
 * `survey.submitted` record form-service publishes, and reads `notification.audit`
 * back from the broker: a SUCCESS record for the run's anonymousId proves that
 * promotion emitted `promotion.status.changed`, the `notification-group` consumer
 * picked it up and at least one channel completed the dispatch.
 *
 * On dev the channels stay in MOCK mode (`AC_MOCK_SID` / `MOCK_TOKEN`), which is
 * where that SUCCESS record comes from: EMAIL depends on an SMTP server dev does
 * not provide, so a real send would end in RETRY/FAILED and never reach SUCCESS.
 */
import { execSync } from 'node:child_process';
import { test, expect } from '@playwright/test';

const KUBE_NS = process.env.KUBE_NS || 'cg-dev';
const NOTIFICATION_DEPLOYMENT = process.env.NOTIFICATION_DEPLOYMENT || 'notification';
const KAFKA_DEPLOYMENT = process.env.KAFKA_DEPLOYMENT || 'kafka';

const CONSUMED_TOPIC = 'promotion.status.changed';
const AUDIT_TOPIC = 'notification.audit';
const CONSUMER_GROUP = 'notification-group';
const DISPATCH_TIMEOUT_MS = 120_000;

function anonymousId(role: string): string {
  const base = process.env.E2E_ANONYMOUS_ID?.trim();
  if (!base) {
    throw new Error(
      'Falta E2E_ANONYMOUS_ID: la corrida siembra su propio visitante y necesita un id único por build.',
    );
  }
  return `${base}-${role}`;
}

function shellEscape(value: string): string {
  return value.replace(/'/g, `'\\''`);
}

function kube(args: string): string {
  return execSync(`kubectl -n ${KUBE_NS} ${args}`, {
    stdio: ['ignore', 'pipe', 'pipe'],
    encoding: 'utf8',
  });
}

function seedVisitor(id: string): void {
  const encoded = execSync(
    `kubectl -n ${KUBE_NS} get secret cg-secrets -o jsonpath='{.data.NEO4J_PASSWORD}'`,
    { stdio: ['ignore', 'pipe', 'pipe'], encoding: 'utf8' },
  ).trim();
  const password = Buffer.from(encoded, 'base64').toString('utf8').trim();
  kube(
    `exec deploy/neo4j -- cypher-shell -a bolt://localhost:7687 -u neo4j -p '${shellEscape(password)}' ` +
      `"MERGE (u:User {anonymousId: '${id}'}) SET u.status = 'ACTIVE', u.statusUpdatedAt = timestamp()"`,
  );
}

function publish(topic: string, event: Record<string, unknown>): void {
  const payload = JSON.stringify(event);
  execSync(
    `kubectl -n ${KUBE_NS} exec deploy/${KAFKA_DEPLOYMENT} -- bash -c 'printf "%s\\n" '"'"'${shellEscape(payload)}'"'"' | /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic ${topic}'`,
    { stdio: ['ignore', 'pipe', 'pipe'] },
  );
}

/** Reads the audit topic from the broker. `notification.audit` is small on dev and the
 *  consumer self-terminates on `--timeout-ms`, so a scan is cheaper than an admin client. */
function auditRecords(): Array<Record<string, unknown>> {
  let output = '';
  try {
    output = execSync(
      `kubectl -n ${KUBE_NS} exec deploy/${KAFKA_DEPLOYMENT} -- /opt/kafka/bin/kafka-console-consumer.sh ` +
        `--bootstrap-server localhost:9092 --topic ${AUDIT_TOPIC} --from-beginning --timeout-ms 5000`,
      { stdio: ['ignore', 'pipe', 'pipe'], encoding: 'utf8' },
    );
  } catch (error) {
    // The consumer exits non-zero when the topic does not yet exist.
    output = String((error as { stdout?: unknown })?.stdout ?? '');
  }
  return output
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.startsWith('{'))
    .flatMap((line) => {
      try {
        return [JSON.parse(line) as Record<string, unknown>];
      } catch {
        return [];
      }
    });
}

test('notification-service está saludable dentro del clúster', async () => {
  const health = kube(
    `exec deploy/${NOTIFICATION_DEPLOYMENT} -- wget -qO- http://127.0.0.1:8082/actuator/health`,
  );
  expect(health, 'el actuator debe reportar UP en el pod de notification').toContain('"status":"UP"');
});

test('el consumidor notification-group está suscrito a promotion.status.changed', async () => {
  await expect
    .poll(
      async () => {
        try {
          return kube(
            `exec deploy/${KAFKA_DEPLOYMENT} -- /opt/kafka/bin/kafka-consumer-groups.sh ` +
              `--bootstrap-server localhost:9092 --describe --group ${CONSUMER_GROUP}`,
          );
        } catch (error) {
          // The group does not exist until the consumer joins the coordinator.
          return String((error as { stderr?: unknown })?.stderr ?? '');
        }
      },
      {
        message: `notification-service debe consumir ${CONSUMED_TOPIC} con el grupo ${CONSUMER_GROUP}`,
        timeout: 60_000,
        intervals: [2_000, 5_000],
      },
    )
    .toContain(CONSUMED_TOPIC);
});

test('el cuestionario llega a notification-service y el despacho deja un audit SUCCESS', async () => {
  test.setTimeout(DISPATCH_TIMEOUT_MS + 60_000);
  const id = anonymousId('notification');

  seedVisitor(id);
  publish('survey.submitted', { anonymousId: id, hasSymptoms: true });

  let lastAudit: Array<Record<string, unknown>> = [];
  await expect
    .poll(
      async () => {
        lastAudit = auditRecords().filter((record) => record.userId === id);
        return lastAudit.filter((record) => record.status === 'SUCCESS').length;
      },
      {
        message: `el despacho multicanal debe dejar un registro SUCCESS en ${AUDIT_TOPIC} para ${id}`,
        timeout: DISPATCH_TIMEOUT_MS,
        intervals: [2_000, 5_000],
      },
    )
    .toBeGreaterThanOrEqual(1);

  const delivered = lastAudit.filter((record) => record.status === 'SUCCESS');
  expect(
    delivered.map((record) => record.channel),
    'el canal del audit debe ser uno de los canales del dispatcher',
  ).toEqual(expect.arrayContaining([expect.stringMatching(/^(EMAIL|SMS|PUSH)$/)]));
});

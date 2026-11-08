<!-- CG-DOC: Evidence index for HU-PROMO-04 (DCG-309): promotion deployment and E2E. -->
# HU-PROMO-04 — Deploy `promotion-service` to dev, stage and prod

Covers `DCG-335`, `DCG-336`, `DCG-337` and `DCG-338` of `DCG-309`.

## What ships

| File | Purpose |
|---|---|
| `deploy/compose/{dev,stage,prod}/docker-compose.yml` | `promotion` plus PostgreSQL, Neo4j, Kafka and Redis, every container with a healthcheck |
| `deploy/compose/{dev,stage,prod}/.env.example` | Keys each environment needs, including the two new middleware passwords |
| `infra/bootstrap/bootstrap-envs.sh` | Generates the middleware passwords and records the host address Kafka advertises |
| `deploy/scripts/deploy.sh` | Pulls both application images and waits for the whole stack to be healthy |
| `deploy/scripts/seed-visitor.sh` | Creates the visitors an end-to-end run is going to promote |
| `jenkins/vars/ciServices.groovy` | `promotion` is a buildable service; `all` and `main` cover every service with CI |
| `Jenkinsfile` | The Docker stage packages every image, not only the gateway |
| `jenkins/vars/ci.groovy` | Reads the environment addresses, seeds visitors and runs the E2E against dev |
| `tests/e2e/promotion-flow.spec.ts` | Publishes the questionnaire event to Kafka and asserts the status cascade |

## Verified before the pull request

Every check below was executed on the branch head.

```text
$ docker compose -f deploy/compose/<env>/docker-compose.yml --env-file .env config --quiet
dev OK      stage OK      prod OK

$ docker compose up -d --wait
cg-redis-dev      Healthy
cg-postgres-dev   Healthy
cg-neo4j-dev      Healthy
cg-kafka-dev      Healthy
cg-promotion-dev  Healthy

$ curl http://localhost:8088/actuator/health
{"status":"UP","groups":["liveness","readiness"]}

$ curl http://localhost:8088/api/v1/health-status/stats
{"unknownCount":1,"totalUsers":1,"timestamp":"..."}
```

The stack booting surfaced two defects that a `docker compose config` check cannot
see. Both are fixed in this branch:

1. **Neo4j refused to start.** Neo4j reads every `NEO4J_*` variable as a server
   setting, so a separate `NEO4J_PASSWORD` aborted the container with
   `Unrecognized setting. No declared setting with name: PASSWORD`. The password
   now travels only inside `NEO4J_AUTH` and the healthcheck strips the user prefix
   off that value.
2. **The Kafka listener consumed nothing.** With no default value type, the
   `JsonDeserializer` rejected every record with `No type information in headers and
   no default type provided`. The service started and reported healthy while the
   questionnaire never reached it. `application.yml` now sets
   `spring.json.value.default.type`, which is the same contract the Kafka
   integration test already asserted.

A third defect was caught while writing the end-to-end suite: `promotion-service`
matches visitors by `anonymousId` and never creates them, so a run that publishes an
event for a new visitor updates nothing. That is why `seed-visitor.sh` exists.

## Pending capture on the environments

These require a pipeline run on `main` with `DEPLOY`, which only happens after this
pull request merges. Each one is a screenshot or log to drop in this folder:

| Evidence | How to obtain |
|---|---|
| `01-deploy-dev.png` | Console of the `Deploy dev` stage, ending in `Despliegue OK: dev tag=...` |
| `02-compose-ps-dev.png` | `docker compose ps` on `cg-dev`, all six containers `healthy` |
| `03-e2e-promotion.png` | Console of the `E2E` stage with the three Playwright tests passing |
| `04-deploy-stage.png` | Console of the `Deploy stage` stage |
| `05-approval-prod.png` | `Approval prod` waiting for the manual input |
| `06-deploy-prod.png` | Console of the `Deploy prod` stage |
| `07-rollback.png` | `ROLLBACK_TAG=<previous>` run and its `Despliegue OK: prod` output |

## Reproduce the local checks

```bash
# Validate the three stacks without starting anything
for env in dev stage prod; do
  docker compose -f deploy/compose/$env/docker-compose.yml \
    --env-file deploy/compose/$env/.env config --quiet
done

# Start only the promotion stack
cd deploy/compose/dev
docker compose up -d --wait redis postgres neo4j kafka promotion

# Seed a visitor and publish the questionnaire event
docker compose exec -T neo4j cypher-shell -u neo4j -p "$NEO4J_PASSWORD" \
  "MERGE (u:User {anonymousId: 'e2e-local-symptoms'}) SET u.status = 'ACTIVE'"
docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic survey.submitted <<< \
  '{"anonymousId":"e2e-local-symptoms","hasSymptoms":true}'

curl -s http://localhost:8088/api/v1/health-status/stats   # suspectCount: 1
```

## Cost note

The stack needs roughly 3.5 GiB across the six containers, which fits the
`m7i-flex.large` of each environment. Only the promotion stack is started for a
local check: bringing up the tools stack (SonarQube, Nexus, Jenkins) at the same
time is what saturates a workstation.

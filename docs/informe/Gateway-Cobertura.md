<!-- CG-DOC: Gateway coverage, integration, E2E, and quality evidence report. -->
# Gateway Coverage and Quality Report

## Scope

Only `circleguard-gateway-service`: unit tests, Redis integration, Playwright E2E, JaCoCo, SonarQube, and Nexus publication.

## Unit and integration coverage

```bash
./gradlew :services:circleguard-gateway-service:clean \
  :services:circleguard-gateway-service:test \
  :services:circleguard-gateway-service:jacocoTestReport \
  :services:circleguard-gateway-service:jacocoTestCoverageVerification
```

Target: line coverage >= 60%.

Current result:

- instruction: 97.44 %
- branch: 72.73 %
- line: 95.92 %

![JaCoCo coverage](evidencia/gateway-jacoco.png)

## Integration test

`GatewayRedisIntegrationTest` runs against a real `redis:7.2-alpine` container started by Testcontainers (no mocks). It validates:

- `ACTIVE` and `RECOVERED` -> access allowed (`GREEN`).
- `SUSPECT`, `CONTAGIED`, `PROBABLE`, `CONFIRMED`, and `POTENTIAL` -> access denied (`RED`).
- Missing status -> access denied (`RED`).
- Expired token -> access denied (`RED`, `Invalid or Expired Token`).

The dedicated Gradle task is `:services:circleguard-gateway-service:integrationTest`; the `Integration Test` Jenkins stage publishes the JUnit report and archives the Redis container log. See [`GW-CI-02-Integracion-Redis.md`](GW-CI-02-Integracion-Redis.md) and [`evidencia/gw-ci-02/`](evidencia/gw-ci-02/) for results and evidence.

## E2E tests

Playwright validates:

1. Health endpoint returns `UP`.
2. Request without staff JWT returns 401/403.
3. Malformed staff JWT returns 401/403.

![E2E tests](evidencia/gateway-e2e.png)

## SonarQube

![SonarQube quality gate](evidencia/gateway-sonar.png)

## Nexus

Published artifact and image:

```text
<TOOLS_PRIVATE_IP>:5000/circleguard-docker/gateway:<TAG>
```

![Nexus artifacts](evidencia/gateway-nexus.png)

## Pipeline

![Gateway pipeline](evidencia/gateway-pipeline.png)

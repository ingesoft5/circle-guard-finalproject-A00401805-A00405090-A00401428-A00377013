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

`GatewayRedisIntegrationTest` validates:

- `ACTIVE` status -> access allowed.
- `SUSPECT` status -> access denied.
- Missing status -> access denied.

![Integration test](evidencia/gateway-integration.png)

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

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

Target: line coverage >= 60 % (GW-CI-01). The threshold is enforced by
`jacocoTestCoverageVerification` (`minimum = 0.60`) and wired to the Gradle
`check` lifecycle.

Current result (JaCoCo 0.8.12, 24 tests, 0 failures):

| Metric | Covered / Total | Coverage |
|---|---:|---:|
| Instructions | 195 / 195 | 100.00 % |
| Branches | 18 / 22 | 81.82 % |
| **Lines** | **49 / 49** | **100.00 %** |
| Methods | 9 / 9 | 100.00 % |
| Classes | 4 / 4 | 100.00 % |

![JaCoCo coverage](evidencia/gateway-jacoco.png)

### Acceptance criteria -> tests

| GW-CI-01 criterion | Test |
|---|---|
| `ACTIVE` / `RECOVERED` -> `GREEN` | `QrValidationServiceTest.shouldAllowAllowedStatuses` |
| Risk statuses, `UNKNOWN` and `CLEAR` -> `RED` | `QrValidationServiceTest.shouldDenyRiskOrUnknownStatuses` |
| Missing Redis status -> `RED` | `QrValidationServiceTest.shouldDenyWhenStatusIsMissing` |
| Expired token -> `Invalid or Expired Token` | `QrValidationServiceTest.shouldDenyExpiredToken` |
| Invalid signature -> `Invalid or Expired Token` | `QrValidationServiceTest.shouldDenyTokenWithInvalidSignature` |
| Malformed, empty or null token -> `Invalid or Expired Token` | `QrValidationServiceTest.shouldDenyMissingOrMalformedToken` |
| Token without subject -> `Invalid or Expired Token` | `QrValidationServiceTest.shouldDenyTokenWithoutSubject` |
| Redis failure propagates | `QrValidationServiceTest.shouldPropagateRedisFailure` |
| Controller returns the service result | `GateControllerTest.shouldReturnValidationResult`, `shouldReturnDeniedResult` |
| Controller handles a missing `token` field | `GateControllerTest.shouldReturnDeniedWhenTokenIsMissing` |
| JWT filter builds authorities from `permissions` | `JwtAuthenticationFilterTest.shouldAuthenticateTokenWithPermissions` |
| JWT filter accepts a token without `permissions` | `JwtAuthenticationFilterTest.shouldAuthenticateTokenWithoutPermissions` |
| JWT filter ignores an invalid token | `JwtAuthenticationFilterTest.shouldNotAuthenticateInvalidToken` |

## Integration test

`GatewayRedisIntegrationTest` runs against a real `redis:7.2-alpine` container started by Testcontainers (no mocks). It validates:

- `ACTIVE` and `RECOVERED` -> access allowed (`GREEN`).
- `SUSPECT`, `CONTAGIED`, `PROBABLE`, `CONFIRMED`, and `POTENTIAL` -> access denied (`RED`).
- Missing status -> access denied (`RED`).
- Expired token -> access denied (`RED`, `Invalid or Expired Token`).

The dedicated Gradle task is `:services:circleguard-gateway-service:integrationTest`; the `Integration Test` Jenkins stage publishes the JUnit report and archives the Redis container log. See [`GW-CI-02-Integracion-Redis.md`](GW-CI-02-Integracion-Redis.md) and [`evidencia/gw-ci-02/`](evidencia/gw-ci-02/) for results and evidence.

### Post-rebase verification (8 November 2026)

The suite was re-run three consecutive times after rebasing the feature branch onto `main` (PR #22):

- Runs: 24 s, 25 s and 24 s; JUnit time 3.541 s, 3.628 s and 3.496 s.
- 9 tests per run, 0 failures, 0 errors, 0 skipped.
- 0 Testcontainers containers left after the Ryuk cleanup window.

![Three consecutive post-rebase runs](evidencia/gw-ci-02/validacion/15-tres-corridas-post-rebase.png)

![JUnit HTML report](evidencia/gw-ci-02/validacion/16-reporte-junit-html.png)

## E2E tests

Playwright validates:

1. Health endpoint returns `UP`.
2. Request without staff JWT returns 401/403.
3. Malformed staff JWT returns 401/403.

## SonarQube

![SonarQube quality gate](evidencia/sonarqube-quality-gate.png)

## Nexus

Published artifact and image:

```text
<TOOLS_PRIVATE_IP>:5000/circleguard-docker/gateway:<TAG>
```

![Nexus artifacts](evidencia/nexus-repositories.png)

## Pipeline

The multibranch Jenkinsfile runs `ci.build`, `ci.unitTest` (JUnit + JaCoCo
gate) and `ci.integrationTest` for the gateway, followed by
`ci.archive(services)`, which publishes the JUnit XML and the JaCoCo HTML/XML
reports for every build, including failed builds. The shared library is loaded
from `main` (`jenkins/vars`).

![Gateway pipeline](evidencia/jenkins-pr15.png)

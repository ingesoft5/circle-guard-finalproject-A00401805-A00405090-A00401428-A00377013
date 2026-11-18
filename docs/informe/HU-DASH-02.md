<!-- CG-DOC: Dashboard unit-test and JaCoCo verification record for HU-DASH-02. -->
# HU-DASH-02: dashboard-service tests and coverage

The existing controller suite covered two routes. This HU adds tests for all five analytics routes, privacy masking at the configured threshold, analytics queries and fallback data, and responses when `promotion-service` is unavailable. The tests use mocked dependencies and do not require PostgreSQL or a running Promotion service.

## Run locally

From the repository root on Windows:

```powershell
.\gradlew.bat :services:circleguard-dashboard-service:test --console=plain
.\gradlew.bat :services:circleguard-dashboard-service:jacocoTestReport --console=plain
```

The second command runs `test` before generating the report. Test results are in `services/circleguard-dashboard-service/build/reports/tests/test/index.html`; JaCoCo HTML and XML are in `services/circleguard-dashboard-service/build/reports/jacoco/test/html/index.html` and `services/circleguard-dashboard-service/build/reports/jacoco/test/jacocoTestReport.xml`.

## Verified result

On 17 November 2026, `jacocoTestReport` finished with `BUILD SUCCESSFUL`: 19 tests, 0 failures, and 73 of 75 lines covered (97.3%) in the module report. Dashboard has no required 60% coverage gate in this HU. Gradle also reported deprecated features that may be incompatible with Gradle 9.

## Current Promotion failure response

If the HTTP request to `promotion-service` fails, `PromotionClient` returns an object with `error: "Service unavailable"` and a timestamp. The department variant also includes the requested department. The analytics controller currently returns that object with HTTP 200. Tests document this existing behavior; they do not change the API contract.

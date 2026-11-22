# CI/CD Pipeline — CircleGuard

**Jenkins:** multibranch job `circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013`
**Shared Library:** `circleguard@main`
**File:** `Jenkinsfile`

## Stages

```mermaid
flowchart LR
    P[Preflight resources] --> C[Checkout]
    C --> R[Resolve services]
    R --> B[Build Gradle]
    B --> U[Unit tests + JaCoCo]
    U --> S[SonarQube + Quality Gate]
    S --> I[Integration tests]
    I --> P2[Package]
    P2 --> T[Git tag semver]
    T --> N[Release notes]
    N --> PU[Publish Nexus]
    PU --> D[Docker images]
    D --> DW[Docker web]
    DW --> DEV[Deploy dev]
    DEV --> E[E2E Playwright]
    E --> ST[Deploy stage]
    ST --> AP[Approval prod]
    AP --> PR[Deploy prod]
    PR --> OK[Optional rollback]
```

## Service selection

The `SERVICE` parameter accepts:

- `auto`: **only the modified services**. Rules of `ciServices.selectFromFiles()`:
  - `services/<service>/...` → that service (one, two, or as many as were touched).
  - `deploy/**` with the service name in a path segment → that service (for example
    `promotion-alerts.yml`, `form-storage.yml`, `components/form-observability/`,
    `form06-dev/canary/`). A suffix `-`, `_`, `.` or a digit is required, so
    `form06-dev` maps to `form` but `platform` does not map to `promotion`.
  - Changes that affect the compilation of every module (`build.gradle.kts`,
    `settings.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/`,
    `sonar-project.properties`) → all eight services.
  - Shared `deploy/**` configuration (overlays, monitoring, base manifests without a
    service name) → **no Java service**; it is validated by the Trivy IaC gate instead
    of recompiling all eight modules.
  - `Jenkinsfile`, `jenkins/` → no Java service: they change the pipeline definition,
    not microservice code. `ciServices.verifySelection()` still validates the library.
  - `mobile/`, `docs/`, `tests/`, `infra/` → no Java service; the build stays green and
    skips compilation, tests, Sonar and integration.
- `all`: all eight services.
- A short name: `gateway`, `auth`, `identity`, `promotion`, `form`, `notification`,
  `file` or `dashboard`.

The catalog lives in `jenkins/vars/ciServices.groovy` and declares the `docker`,
`deploy`, `jacoco`, `integration`, `publish` and `healthcheck` flags per service.
`ciServices.verifySelection()` runs the critical cases on every build (one service,
two services, a service-specific deploy manifest, a global change, a CI-only change,
a client-only change, and shared deploy paths) and fails the pipeline if selection
regresses to "all services". The standalone runner
`jenkins/test/TestCiServices.groovy` reproduces those cases without Jenkins and serves
as local regression evidence.

> The library is pinned to `@main`, so a PR cannot see library changes it introduces
> itself. Until the selective-execution fix is merged into `main`, `Jenkinsfile`
> carries a temporary local copy of the selection logic (marked
> `REMOVE after merging to main`). Delete it once the library fix reaches `main`.

**Traceability to the assignment:** E1.4 requires *"execution per service (modified or
all)"* and criterion P1.4 grades *"selective execution per service"* versus a
*"monolithic Jenkinsfile"*. This selection is therefore an evaluated requirement, not
an optional optimization.

## Promotion

| Stage | Condition | Action |
|---|---|---|
| Deploy dev | `main` + `DEPLOY=true` | Deploys the tag to dev |
| E2E | `main` + `DEPLOY=true` + integration enabled | Login, questionnaire, access |
| Deploy stage | `main` + `DEPLOY=true` | Promotes the same tag |
| Approval prod | `main` + `DEPLOY=true` | Manual `input` |
| Deploy prod | Approval | Deploys the tag |
| Rollback prod | `ROLLBACK_TAG` | Deploys an earlier tag without rebuilding |

## Versioning

- `resolveSemver` reads the latest `vX.Y.Z` tag and conventional commits.
- `feat` bumps minor; `fix`/`docs`/`ci` bump patch; `!` or
  `BREAKING CHANGE` bumps major.
- `Git tag` publishes the tag on `main`.
- `Release notes` generates `docs/release-notes/vX.Y.Z.md`.

## Quality

- Unit tests: `./gradlew :service:check`; JaCoCo ≥ 60 % for auth, identity, gateway
  and form; promotion ≥ 70 %.
- SonarQube: blocking quality gate per project.
- Integration: Testcontainers in auth, form, gateway, identity and promotion.
- E2E: Playwright against the web client and the gateway.
- Image security: verifies the image does not run as root; services with
  `healthcheck: true` must declare `HEALTHCHECK`.

## Failure notifications and rollback

- `ciNotify.failure` comments on GitHub with the **real failing stage** (captured in
  `env.FAILED_STAGE` when each stage starts; `post.failure` restores it because
  Jenkins has already replaced `STAGE_NAME` with `Declarative: Post Actions`) and sends
  automatic notifications through **all configured channels**: Slack
  (`SLACK_WEBHOOK_URL`), Discord (`DISCORD_WEBHOOK_URL`) and email
  (`EMAIL_RECIPIENTS`, comma-separated list). If none is configured, the build warns
  explicitly that the assignment notification (E1.5, E2.3, P1.5, P2.5) is incomplete.
  The three variables are injected into the Jenkins container from
  `deploy/compose/tools/.env`.
 - Email delivery uses Office365 SMTP (`smtp.office365.com:587`, STARTTLS) with
   authentication. `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD` and
   `MAIL_FROM` are injected into Jenkins and applied through JCasC
   (`unclassified.mailer` plus `location.adminAddress`). `SMTP_PASSWORD` is a local
   secret and is intentionally empty in the tracked examples; without it the
   `mail` step fails (the failure is caught by `notifyAll`, so the build stays green
   but no email is delivered).
- `ROLLBACK_TAG` skips build, tests, Sonar, publish and Docker; it only deploys to
  production.
- Logs, JaCoCo reports, JUnit results and digests are archived on every build.

## Resource guardrails

- `Preflight resources` reports `/proc/loadavg` and `MemAvailable` at the start of
  every build. It warns above load 14 or below 2048 MB and fails only above load 24
  or below 512 MB, so host pressure is visible without turning into a spurious failure.
- JCasC sets `numExecutors: 2`: four concurrent Gradle + Testcontainers runs pushed the
  host to load ~13 with swap in use, which made container health checks time out.
- The promotion integration suite has a 15-minute timeout so a cold start under load
  finishes before Jenkins kills it, while a real hang still fails with a clear message
  and leaves JUnit XML behind.

## Evidence

- `docs/informe/evidencia/jenkins-pr15.png`
- `docs/informe/evidencia/sonarqube-quality-gate.png`
- `docs/informe/evidencia/nexus-repositories.png`
- `docs/informe/evidencia/gateway-jacoco.png`
- `docs/agil/sprint-1/review.md`

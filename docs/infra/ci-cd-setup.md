<!-- CG-DOC: Four-host CI/CD setup, exposure, credentials, and full-stack pipeline. -->
# CI/CD Platform - Jenkins, SonarQube, and Nexus

## Requirements

- AWS CLI v2 with EC2 and SSM permissions.
- `jq`, `ssh`, `curl`, and `openssl`.
- `ADMIN_CIDR` and `TEACHER_CIDR`.
- GitHub token with repository, workflow, and webhook permissions.

## Variables

```bash
export AWS_REGION=us-east-2
export ADMIN_CIDR="$(curl -s https://checkip.amazonaws.com)/32"
export TEACHER_CIDR="${ADMIN_CIDR}"
export GITHUB_USER=...
export GITHUB_TOKEN=...
```

## Exposure

- `80`: public ingress. It serves the dev gateway at `/` and the GitHub webhook at `/github-webhook/`.
- `8080`: Jenkins UI, restricted to `ADMIN_CIDR` and `TEACHER_CIDR`.
- `9000` and `8081`: SonarQube and Nexus, restricted to `ADMIN_CIDR` and `TEACHER_CIDR`.
- `443`: closed; there is no TLS listener.
- The bootstrap rejects `0.0.0.0/0` for `ADMIN_CIDR` and `TEACHER_CIDR`.
- Jenkins is triggered by the GitHub webhook, not by a timer. The webhook is reachable at `http://<TOOLS_EIP>/github-webhook/`; nginx forwards only that path to `jenkins:8080`, so port `8080` stays private.

## Bootstrap

```bash
./infra/bootstrap/bootstrap-check.sh
./infra/bootstrap/bootstrap-all.sh
```

Tools only:

```bash
./infra/bootstrap/bootstrap-tools.sh
```

Rotate GitHub/AWS credentials and synchronize the local state files:

```bash
./infra/bootstrap/update-credentials.sh --apply
```

`update-credentials.sh` writes `GITHUB_USER` and `GITHUB_TOKEN` to
`infra/state/tools.env` and `deploy/compose/tools/.env`, then recreates Jenkins when
`--apply` is used. The files are mode `600` and are not versioned.

Each environment host:

```bash
REGISTRY_HOST=<TOOLS_PRIVATE_IP>:5000 ./infra/bootstrap/bootstrap-envs.sh dev
```

## Jenkins

- URL: `http://<TOOLS_EIP>:8080` (admin/teacher networks only).
- Admin credentials: `infra/state/tools.env`.
- JCasC: `deploy/compose/tools/jenkins/casc/jenkins.yaml`.
- Multibranch job: `circleguard`, with GitHub Branch Source for branches and pull requests of all services (`SERVICE=auto`, `all`, or one service).
- Webhook: `http://<TOOLS_EIP>/github-webhook/`, events `push` and `pull_request`.
- Secret: `GITHUB_WEBHOOK_SECRET` in `infra/state/tools.env`; JCasC exposes it as `github-webhook-secret`.
- Shared Library: `circleguard`, loaded from `jenkins/vars/` (`libraryPath: jenkins`) and pinned to `main`.
- `professor` user: read-only.
- `cg-envs-ssh`: declared in JCasC.
- The webhook is created by `infra/bootstrap/bootstrap-github.sh` when the token has `admin:repo_hook`. Otherwise create it manually in **Settings → Webhooks** with content type `application/json`.

## Branch protection

`infra/bootstrap/bootstrap-github.sh` protects the default branch (`main`) with:

- Pull request required before merging.
- One approving review.
- Required status check: `continuous-integration/jenkins/pr-merge`.
- Conversation resolution required.
- Force pushes and branch deletion disabled.

If the token cannot manage branch protection, the script prints a warning and the settings must be applied manually.

## SonarQube and Nexus

- SonarQube: `http://<TOOLS_EIP>:9000`, teacher/admin only.
- Nexus UI: `http://<TOOLS_EIP>:8081`, teacher/admin only.
- Registry: `<TOOLS_PRIVATE_IP>:5000`, private.
- Repository: `circleguard-docker`.
- Quality gates: `CircleGuard Gateway`, `CircleGuard Services` and `CircleGuard Promotion`; line coverage ≥ 60 % except promotion, which uses the project's 70 % rule.
- Projects: `circleguard-gateway-service`, `circleguard-auth-service`, `circleguard-form-service`, `circleguard-identity-service`, `circleguard-promotion-service`.

## Application ingress

- URL: `http://<TOOLS_EIP>/`
- Nginx proxies to the dev gateway private IP on `8087`.
- Port `80` is public; `443` is reserved for a future domain/TLS.

## Pipeline

The `Jenkinsfile` is branch-aware: the multibranch job discovers `main`, feature branches, and pull requests. Each child build runs the same stages with the code of its branch or PR.

1. **Checkout**: `checkout scm` and commit metadata.
2. **Resolve services and version**: `SERVICE=auto` detects changed services from the diff; `SERVICE=all` builds every service; `ciVersion` calculates the semantic version from Git tags and Conventional Commits.
3. **Build**: Gradle compilation per service.
4. **Unit tests + JaCoCo**: tests and coverage; auth, identity, gateway and form enforce **line coverage ≥ 60 %**; promotion enforces ≥ 70 %.
5. **SonarQube + Quality Gate**: scanner and blocking `waitForQualityGate`; the pipeline stops if the gate is red.
6. **Integration tests**: Testcontainers tests, separate from unit tests.
7. **Package**: `bootJar`.
8. **Git tag** (on `main`): creates and pushes `vX.Y.Z` for publishable services.
9. **Release notes** (on `main`): generates `docs/release-notes/vX.Y.Z.md` from Conventional Commits.
10. **GitHub Release** (on `main`): publishes or updates the release for tag `vX.Y.Z` with those notes; retries are idempotent.
11. **Publish** (on `main`): Nexus artifact for publishable Maven services.
12. **Docker** (on `main`): builds and pushes every service with a Dockerfile using only the semver tag, never `latest`; verifies non-root user and HEALTHCHECK.
13. **Docker web** (on `main`): builds the Expo client with `mobile/Dockerfile` and publishes `web:<tag>`.
14. **Deploy** (optional): dev, E2E, stage, production approval, production, rollback. Only when `DEPLOY=true` on `main`.
15. **Archive**: JUnit, JaCoCo HTML/XML, `sonar-links.txt`, `image-digest.txt` and release notes.

### Selective execution

- `SERVICE=auto`: services touched under `services/<service>/`; global files (`build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `Jenkinsfile`, `sonar-project.properties`, `gradlew`, `gradle/`, `jenkins/`, `mobile/`, `deploy/`) select every service.
- `SERVICE=all`: the eight services.
- `SERVICE=<name>`: one service; for example `identity`.
- With no changed services the build is green and skips compilation.

### Artifacts

Every build archives JUnit XML, JaCoCo HTML/XML, the Sonar dashboard links, the published image digests and the generated release notes. The links are also printed in the build log.

## Evidence

Store screenshots in `docs/informe/evidencia/`, with the Markdown reports in `docs/informe/`. Never include passwords, tokens, or private keys.

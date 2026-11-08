<!-- CG-DOC: Jenkins, SonarQube, Nexus, credentials, and gateway pipeline setup. -->
# CI/CD Platform - Jenkins, SonarQube, and Nexus

## Requirements

- AWS CLI v2 with valid credentials.
- `jq`, `ssh`, `curl`, and `openssl`.
- `ADMIN_CIDR` for the administrator IP address.
- GitHub token with repository and webhook permissions (`GITHUB_TOKEN`).
- Optional `TEACHER_CIDR` for classroom access.

## Variables

```bash
export AWS_REGION=us-east-1
export ADMIN_CIDR="$(curl -s https://checkip.amazonaws.com)/32"
export GITHUB_USER=...
export GITHUB_TOKEN=...
export GITHUB_OWNER=ingesoft5
export GITHUB_REPO=circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013
```

## Rebuild from scratch

```bash
./infra/bootstrap-all.sh
```

The command:

1. Creates two EC2 instances, security groups, elastic IPs, and the key pair.
2. Installs Docker, Java 21, swap, and firewall rules on both hosts.
3. Starts Jenkins, SonarQube, PostgreSQL, and Nexus.
4. Configures SonarQube, Nexus, and the `circleguard-docker` registry.
5. Creates the `.env` files for `dev`, `stage`, and `prod`.

Partial runs:

```bash
./infra/bootstrap-all.sh --tools-only
./infra/bootstrap-all.sh --envs-only
```

Destroy:

```bash
CONFIRM=yes ./infra/bootstrap-destroy.sh
```

## Jenkins

- URL: `http://<TOOLS_IP>:8080`
- User: `admin`
- Password: `infra/state/tools.env`
- JCasC: `deploy/compose/tools/jenkins/casc/jenkins.yaml`
- Plugins: `deploy/compose/tools/jenkins/plugins.txt`
- Multibranch job: `circleguard-gateway`
- Shared Library: `vars/ci.groovy`, branch `LIBRARY_BRANCH` (default `dev`)
- Webhook: `http://<TOOLS_IP>:8080/github-webhook/`

Credentials created automatically:

| ID | Type | Purpose |
|---|---|---|
| `github-creds` | username/password | Repository and webhook |
| `sonar-token` | secret text | SonarQube analysis |
| `nexus-creds` | username/password | Nexus publishing and registry |
| `cg-envs-ssh` | SSH private key | Deployment to `cg-envs` |

`cg-envs-ssh` is declared in JCasC and uses the key copied to `/opt/circleguard/jenkins_ssh/cg-envs-key`.

## SonarQube

- URL: `http://<TOOLS_IP>:9000`
- Project: `circleguard-gateway-service`
- Quality gate: `Sonar way`, blocking in Jenkins.
- Report: `services/circleguard-gateway-service/build/reports/jacoco/test/jacocoTestReport.xml`

## Nexus

- UI: `http://<TOOLS_IP>:8081`
- Docker registry: `<TOOLS_IP>:8082`
- Repositories: `maven-releases`, `maven-snapshots`, `raw`, `circleguard-docker`.
- Jenkins user: `jenkins`, role `jenkins-publisher`.

## Notifications

Set `SLACK_WEBHOOK_URL` in `deploy/compose/tools/.env`. If empty, the pipeline skips notifications.

## Gateway pipeline

Stages in `Jenkinsfile`:

1. Checkout.
2. Build & Test with JaCoCo and coverage verification at or above 60%.
3. SonarQube.
4. Blocking quality gate.
5. Package `bootJar`.
6. Publish to Nexus.
7. Build and push the Docker image to `circleguard-docker`.
8. Deploy to dev.
9. Playwright E2E.
10. Deploy to stage.
11. Manual approval.
12. Deploy to prod.
13. Semantic tag.
14. Rollback through the `ROLLBACK_TAG` parameter.

## Evidence

Store in `docs/infra/evidence/`:

- Jenkins pipeline screenshot with a green build;
- SonarQube quality gate screenshot;
- Nexus screenshot with the JAR and image;
- `docker ps` output on `cg-envs`;
- health checks for dev, stage, and prod;
- rollback evidence;
- URLs and read-only user for the instructor.

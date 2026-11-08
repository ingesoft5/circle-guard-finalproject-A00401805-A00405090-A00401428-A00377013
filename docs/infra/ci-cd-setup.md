<!-- CG-DOC: Four-host CI/CD setup, exposure, credentials, and gateway pipeline. -->
# CI/CD Platform - Jenkins, SonarQube, and Nexus

## Requirements

- AWS CLI v2 with EC2 and SSM permissions.
- `jq`, `ssh`, `curl`, and `openssl`.
- `ADMIN_CIDR` and `TEACHER_CIDR`.
- GitHub token with repository and webhook permissions.

## Variables

```bash
export AWS_REGION=us-east-2
export ADMIN_CIDR="$(curl -s https://checkip.amazonaws.com)/32"
export TEACHER_CIDR="${ADMIN_CIDR}"
export GITHUB_USER=...
export GITHUB_TOKEN=...
```

## Exposure

- `80`: public ingress to the dev gateway.
- `8080`: Jenkins, restricted to `ADMIN_CIDR`, `TEACHER_CIDR`, and the current GitHub `hooks` CIDRs from `https://api.github.com/meta`.
- `9000` and `8081`: SonarQube and Nexus, restricted to `ADMIN_CIDR` and `TEACHER_CIDR`.
- `443`: closed; there is no TLS listener.
- The bootstrap rejects `0.0.0.0/0` for `ADMIN_CIDR` and `TEACHER_CIDR`.
- GitHub hook ranges are refreshed on every bootstrap. If `api.github.com/meta` is unreachable, the bootstrap aborts instead of breaking webhooks.

## Bootstrap

```bash
./infra/bootstrap/bootstrap-check.sh
./infra/bootstrap/bootstrap-all.sh
```

Tools only:

```bash
./infra/bootstrap/bootstrap-tools.sh
```

Each environment host:

```bash
REGISTRY_HOST=<TOOLS_PRIVATE_IP>:5000 ./infra/bootstrap/bootstrap-envs.sh dev
```

## Jenkins

- URL: `http://<TOOLS_EIP>:8080`
- Admin credentials: `infra/state/tools.env`
- JCasC: `deploy/compose/tools/jenkins/casc/jenkins.yaml`
- Multibranch job: `circleguard-gateway`
- Webhook: `http://<TOOLS_EIP>:8080/github-webhook/`
- `professor` user: read-only.
- `cg-envs-ssh`: declared in JCasC.

## SonarQube and Nexus

- SonarQube: `http://<TOOLS_EIP>:9000`, teacher/admin only.
- Nexus UI: `http://<TOOLS_EIP>:8081`, teacher/admin only.
- Registry: `<TOOLS_PRIVATE_IP>:5000`, private.
- Repository: `circleguard-docker`.

## Application ingress

- URL: `http://<TOOLS_EIP>/`
- Nginx proxies to the dev gateway private IP on `8087`.
- Port `80` is public; `443` is reserved for a future domain/TLS.

## Pipeline

1. Checkout.
2. Build and test with JaCoCo.
3. SonarQube and blocking quality gate.
4. Package and publish.
5. Docker build/push to `5000`.
6. Deploy dev.
7. E2E.
8. Deploy stage.
9. Manual production approval.
10. Deploy prod.
11. Tag and notify.
12. Rollback with `ROLLBACK_TAG`.

## Evidence

Store screenshots in `docs/infra/evidence/` and the Markdown report in `docs/informe/`. Never include passwords, tokens, or private keys.

<!-- CG-DOC: Step-by-step tutorial to clean, provision, verify, and operate the four-host gateway platform. -->
# Tutorial E1.2 - Create and Operate the Gateway Platform

## 1. Prerequisites

- AWS CLI with valid credentials.
- Region `us-east-2`.
- `jq`, `ssh`, `curl`, `openssl`.
- GitHub token with `repo` and `admin:repo_hook`.

## 2. Variables

```bash
export AWS_REGION=us-east-2
export ADMIN_CIDR="$(curl -s https://checkip.amazonaws.com)/32"
export TEACHER_CIDR="${ADMIN_CIDR}"
export GITHUB_USER=your_github_user
export GITHUB_TOKEN=your_github_token
export GITHUB_OWNER=ingesoft5
export GITHUB_REPO=circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013
export LIBRARY_BRANCH=feat/HU-GATEWAY-01-configurar-servidores
```

Do not print the token.

`ADMIN_CIDR` and `TEACHER_CIDR` restrict SSH, Jenkins, SonarQube, and Nexus. For now `TEACHER_CIDR` uses the administrator IP; replace it with the teacher CIDR when available. The bootstrap rejects `0.0.0.0/0` for both values. Jenkins also allows the current GitHub `hooks` CIDRs so webhooks keep working.

## 3. Clean Old Resources

```bash
aws ec2 describe-instances --region us-east-2 \
  --filters Name=tag:Project,Values=circleguard

aws ec2 release-address --region us-east-2 \
  --allocation-id eipalloc-0e4eab41a94fef61b
aws ec2 release-address --region us-east-2 \
  --allocation-id eipalloc-0aaddbe7f318daa20

aws ec2 delete-security-group --region us-east-2 \
  --group-id sg-08050cd6fb7971efd
aws ec2 delete-security-group --region us-east-2 \
  --group-id sg-07ddcc8cc2887151a

aws ec2 delete-key-pair --region us-east-2 --key-name circleguard-key
```

## 4. Provision

```bash
./infra/bootstrap/bootstrap-check.sh
./infra/bootstrap/bootstrap-all.sh
```

This creates four `m7i-flex.large` hosts, one Elastic IP, two security groups, the key pair, Jenkins, SonarQube, Nexus, the private registry on `5000`, and the ingress on `80`.

## 5. Verify

```bash
source infra/state/aws.env
./infra/bootstrap/bootstrap-ips.sh
```

Test:

```bash
curl -f "http://${TOOLS_IP}:8080/login"
curl -f "http://${TOOLS_IP}:9000/api/system/status"
curl -f "http://${TOOLS_IP}:8081/service/rest/v1/status"
```

Credentials:

```bash
grep -E 'JENKINS_ADMIN_PASSWORD|PROFESSOR_PASSWORD|SONAR_ADMIN_PASSWORD|NEXUS_PASSWORD' \
  infra/state/tools.env
```

## 6. Pipeline

- Open `http://<TOOLS_IP>:8080`.
- Job: `circleguard-gateway`.
- Run `Scan Multibranch Pipeline Now` if no branch appears.
- Trigger with a push or `Build with Parameters`.
- Approve production manually when requested.

## 7. Gateway Tests

```bash
./gradlew :services:circleguard-gateway-service:clean \
  :services:circleguard-gateway-service:test \
  :services:circleguard-gateway-service:jacocoTestReport \
  :services:circleguard-gateway-service:jacocoTestCoverageVerification
```

## 8. E2E

```bash
cd tests/e2e
npm install
npx playwright install --with-deps chromium
GATEWAY_URL=http://<DEV_PUBLIC_IP>:8087 npx playwright test
```

## 9. Idempotency

```bash
sha256sum infra/state/aws.env deploy/compose/tools/.env > /tmp/before.sha
./infra/bootstrap/bootstrap-all.sh
sha256sum infra/state/aws.env deploy/compose/tools/.env > /tmp/after.sha
diff -u /tmp/before.sha /tmp/after.sha
```

## 10. Stop, Start, Destroy

```bash
./infra/bootstrap/bootstrap-stop.sh
./infra/bootstrap/bootstrap-start.sh
CONFIRM=yes ./infra/bootstrap/bootstrap-destroy.sh
```

Private IPs remain stable. Environment public IPs change after a restart.

## 11. Evidence

Capture:

- Four EC2 instances.
- Security groups.
- Jenkins pipeline.
- SonarQube quality gate.
- Nexus artifacts.
- Gateway coverage.
- Playwright results.
- Application ingress.
- Idempotency comparison.

Store screenshots in `docs/informe/evidencia/`.

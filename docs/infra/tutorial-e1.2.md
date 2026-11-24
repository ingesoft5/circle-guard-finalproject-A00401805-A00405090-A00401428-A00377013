<!-- CG-DOC: Tutorial paso a paso para limpiar, aprovisionar, verificar y operar la plataforma del gateway de cuatro hosts. -->
# Tutorial E1.2 - Crear y operar la plataforma del gateway

> **Parte 1 — histórico (AWS EC2, cuatro hosts).** Ya no es operativo: la cuenta de AWS quedó
> inaccesible y la plataforma corre on-prem. Ver `infra/onpremise/ONPREMISE.md` para el camino vigente
> y `docs/infra/migracion-onpremise.md` para el anexo de migración. Este tutorial se conserva como
> evidencia de la Parte 1.

## 1. Requisitos previos

- AWS CLI con credenciales válidas.
- Región `us-east-2`.
- `jq`, `ssh`, `curl` y `openssl`.
- Token de GitHub con `repo` y `admin:repo_hook`.

## 2. Variables

```bash
export AWS_REGION=us-east-2
export ADMIN_CIDR="$(curl -s https://checkip.amazonaws.com)/32"
export TEACHER_CIDR="${ADMIN_CIDR}"
export GITHUB_USER=your_github_user
export GITHUB_TOKEN=your_github_token
export GITHUB_OWNER=ingesoft5
export GITHUB_REPO=circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013
export LIBRARY_BRANCH=main
```

No imprimas el token.

`ADMIN_CIDR` y `TEACHER_CIDR` restringen SSH, Jenkins, SonarQube y Nexus. Por ahora
`TEACHER_CIDR` usa la IP del administrador; sustitúyela por el CIDR del profesor cuando esté
disponible. El bootstrap rechaza `0.0.0.0/0` para ambos valores.

## 3. Limpiar recursos antiguos

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

## 4. Aprovisionar

```bash
./infra/bootstrap/bootstrap-check.sh
./infra/bootstrap/bootstrap-all.sh
```

Esto crea cuatro hosts `m7i-flex.large`, una Elastic IP, dos grupos de seguridad, el par de
claves, Jenkins, SonarQube, Nexus, el registro privado en `5000` y el ingreso en `80`.

## 5. Verificar

```bash
source infra/state/aws.env
./infra/bootstrap/bootstrap-ips.sh
```

Pruebas:

```bash
curl -f "http://${TOOLS_IP}:8080/login"
curl -f "http://${TOOLS_IP}:9000/api/system/status"
curl -f "http://${TOOLS_IP}:8081/service/rest/v1/status"
```

Credenciales:

```bash
grep -E 'JENKINS_ADMIN_PASSWORD|PROFESSOR_PASSWORD|SONAR_ADMIN_PASSWORD|NEXUS_PASSWORD' \
  infra/state/tools.env
```

## 6. Pipeline

- Abre `http://<TOOLS_IP>:8080`.
- Job: `circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013`.
- Ejecuta `Scan Multibranch Pipeline Now` si no aparece ninguna rama.
- Activa con un push o `Build with Parameters`.
- Aprueba la producción manualmente cuando se solicite.

## 7. Pruebas del gateway

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

## 9. Idempotencia

```bash
sha256sum infra/state/aws.env deploy/compose/tools/.env > /tmp/before.sha
./infra/bootstrap/bootstrap-all.sh
sha256sum infra/state/aws.env deploy/compose/tools/.env > /tmp/after.sha
diff -u /tmp/before.sha /tmp/after.sha
```

## 10. Detener, iniciar y destruir

```bash
./infra/bootstrap/bootstrap-stop.sh
./infra/bootstrap/bootstrap-start.sh
CONFIRM=yes ./infra/bootstrap/bootstrap-destroy.sh
```

Las IPs privadas permanecen estables. Las IPs públicas de los entornos cambian tras un
reinicio.

## 11. Evidencia

Captura:

- Cuatro instancias EC2.
- Grupos de seguridad.
- Pipeline de Jenkins.
- Compuerta de calidad de SonarQube.
- Artefactos de Nexus.
- Cobertura del gateway.
- Resultados de Playwright.
- Ingreso de la aplicación.
- Comparación de idempotencia.

Guarda las capturas en `docs/informe/evidencia/`.

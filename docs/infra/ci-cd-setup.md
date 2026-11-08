# Plataforma CI/CD — Jenkins, SonarQube y Nexus

## Requisitos

- AWS CLI v2 con credenciales.
- `jq`, `ssh`, `curl`, `openssl`.
- `ADMIN_CIDR` de la IP administrativa.
- Token de GitHub con permisos de repositorio y webhook (`GITHUB_TOKEN`).
- Opcional: `TEACHER_CIDR` para habilitar el acceso del docente.

## Variables

```bash
export AWS_REGION=us-east-1
export ADMIN_CIDR="$(curl -s https://checkip.amazonaws.com)/32"
export GITHUB_USER=...
export GITHUB_TOKEN=...
export GITHUB_OWNER=ingesoft5
export GITHUB_REPO=circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013
# Opcional: export TEACHER_CIDR="IP_DOCENTE/32"
```

## Reconstrucción desde cero

```bash
./infra/bootstrap-all.sh
```

El comando:

1. Crea 2 EC2, Security Groups, Elastic IPs y key pair.
2. Prepara Docker, Java 21, swap y firewall en ambas instancias.
3. Levanta Jenkins, SonarQube, PostgreSQL y Nexus.
4. Configura SonarQube, Nexus y el repositorio Docker `circleguard-docker`.
5. Prepara los archivos `.env` de `dev`, `stage` y `prod`.

Solo herramientas o solo ambientes:

```bash
./infra/bootstrap-all.sh --tools-only
./infra/bootstrap-all.sh --envs-only
```

Destrucción:

```bash
CONFIRM=yes ./infra/bootstrap-destroy.sh
```

## Jenkins

- URL: `http://<TOOLS_IP>:8080`
- Usuario: `admin`
- Contraseña: `infra/state/tools.env`
- JCasC: `deploy/compose/tools/jenkins/casc/jenkins.yaml`
- Plugins: `deploy/compose/tools/jenkins/plugins.txt`
- Job multibranch: `circleguard-gateway`
- Shared Library: `vars/ci.groovy`, rama `LIBRARY_BRANCH` (por defecto `dev`)
- Webhook: `http://<TOOLS_IP>:8080/github-webhook/`

Credenciales creadas automáticamente:

| ID | Tipo | Uso |
|---|---|---|
| `github-creds` | usuario/contraseña | Repositorio y webhook |
| `sonar-token` | secret text | SonarQube |
| `nexus-creds` | usuario/contraseña | Nexus |
| `cg-envs-ssh` | SSH private key | Despliegue en cg-envs |

`cg-envs-ssh` se declara en JCasC y usa la llave copiada a `/opt/circleguard/jenkins_ssh/cg-envs-key`.

## SonarQube

- URL: `http://<TOOLS_IP>:9000`
- Proyecto: `circleguard-gateway-service`
- Quality gate: `Sonar way`, bloqueante en Jenkins.
- Reporte: `services/circleguard-gateway-service/build/reports/jacoco/test/jacocoTestReport.xml`

## Nexus

- UI: `http://<TOOLS_IP>:8081`
- Docker registry: `<TOOLS_IP>:8082`
- Repositorios: `maven-releases`, `maven-snapshots`, `raw`, `circleguard-docker`.
- Usuario de Jenkins: `jenkins`, rol `jenkins-publisher`.

## Notificaciones

Definir `SLACK_WEBHOOK_URL` en `deploy/compose/tools/.env`. Si está vacío, el pipeline omite la notificación.

## Pipeline del gateway

Etapas en `Jenkinsfile`:

1. Checkout.
2. Build & Test con JaCoCo y verificación ≥ 60 %.
3. SonarQube.
4. Quality gate bloqueante.
5. Empaquetado `bootJar`.
6. Publicación en Nexus.
7. Build y push de imagen Docker a `circleguard-docker`.
8. Despliegue a dev.
9. E2E Playwright.
10. Despliegue a stage.
11. Aprobación manual.
12. Despliegue a prod.
13. Tag semver.
14. Rollback con el parámetro `ROLLBACK_TAG`.

## Evidencias

Guardar en `docs/infra/evidence/`:

- captura de Jenkins con el pipeline verde;
- captura de SonarQube con quality gate;
- captura de Nexus con jar e imagen;
- salida de `docker ps` en `cg-envs`;
- healthchecks de dev, stage y prod;
- evidencia de rollback;
- URLs y usuario de solo lectura para el docente.

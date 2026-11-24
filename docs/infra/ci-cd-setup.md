<!-- CG-DOC: Configuración de CI/CD de cuatro hosts en AWS de la Parte 1 (histórica). No operativa; se conserva como evidencia para la evaluación.
     Plataforma actual: infra/onpremise/ONPREMISE.md y docs/infra/migracion-onpremise.md -->
# Plataforma de CI/CD (Parte 1 — histórica, no operativa)

> **Estado: histórico.** Este documento describe el montaje sobre AWS de la Parte 1 (cuatro
> instancias EC2, `bootstrap-tools.sh`, secretos en `infra/state/*`). La plataforma se reprodujo on-premise; el flujo vigente es el de
> [`infra/onpremise/ONPREMISE.md`](../../infra/onpremise/ONPREMISE.md) y
> [`docs/infra/migracion-onpremise.md`](./migracion-onpremise.md). Se conserva para trazabilidad.

## Requisitos

- AWS CLI v2 con permisos de EC2 y SSM.
- `jq`, `ssh`, `curl` y `openssl`.
- `ADMIN_CIDR` y `TEACHER_CIDR`.
- Token de GitHub con permisos de repositorio, workflow y webhook.

## Variables

```bash
export AWS_REGION=us-east-2
export ADMIN_CIDR="$(curl -s https://checkip.amazonaws.com)/32"
export TEACHER_CIDR="${ADMIN_CIDR}"
export GITHUB_USER=...
export GITHUB_TOKEN=...
```

## Exposición

- `80`: ingreso público. Sirve el gateway de dev en `/` y el webhook de GitHub en `/github-webhook/`.
- `8080`: interfaz de Jenkins, restringida a `ADMIN_CIDR` y `TEACHER_CIDR`.
- `9000` y `8081`: SonarQube y Nexus, restringidos a `ADMIN_CIDR` y `TEACHER_CIDR`.
- `443`: cerrado; no hay un servicio de escucha TLS.
- El bootstrap rechaza `0.0.0.0/0` para `ADMIN_CIDR` y `TEACHER_CIDR`.
- Jenkins se activa mediante el webhook de GitHub, no por un temporizador. El webhook está
  disponible en `http://<TOOLS_EIP>/github-webhook/`; nginx reenvía solo esa ruta a
  `jenkins:8080`, por lo que el puerto `8080` permanece privado.

## Bootstrap

```bash
./infra/bootstrap/bootstrap-check.sh
./infra/bootstrap/bootstrap-all.sh
```

Solo tools:

```bash
./infra/bootstrap/bootstrap-tools.sh
```

Rota las credenciales de GitHub/AWS y sincroniza los archivos de estado locales:

```bash
./infra/bootstrap/update-credentials.sh --apply
```

`update-credentials.sh` escribe `GITHUB_USER` y `GITHUB_TOKEN` en `infra/state/tools.env` y
`deploy/compose/tools/.env`, y luego recrea Jenkins cuando se usa `--apply`. Los archivos
tienen modo `600` y no están versionados.

Cada host de entorno:

```bash
REGISTRY_HOST=<TOOLS_PRIVATE_IP>:5000 ./infra/bootstrap/bootstrap-envs.sh dev
```

## Jenkins

- URL: `http://<TOOLS_EIP>:8080` (redes de administrador/profesor únicamente).
- Credenciales de administrador: `infra/state/tools.env`.
- JCasC: `deploy/compose/tools/jenkins/cac/jenkins.yml`.
- Job multibranch: `circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013`, con GitHub Branch Source para ramas y pull requests de todos los servicios (`SERVICE=auto`, `all` o un servicio).
- Webhook: `http://<TOOLS_EIP>/github-webhook/`, eventos `push` y `pull_request`.
- Secreto: `GITHUB_WEBHOOK_SECRET` en `infra/state/tools.env`; JCasC lo expone como `github-webhook-secret`.
- Biblioteca compartida: `circleguard`, cargada desde `jenkins/vars/` (`libraryPath: jenkins`) y fijada a `main`.
- Usuario `professor`: solo lectura.
- ~~`cg-envs-ssh`~~: **eliminada** (on-prem); JCasC ya no contiene credencial SSH. Ver `docs/infra/migracion-onpremise.md` §3.4.
- El webhook lo crea `infra/bootstrap/bootstrap-github.sh` cuando el token tiene
  `admin:repo_hook`. De lo contrario, créalo manualmente en **Settings → Webhooks** con tipo
  de contenido `application/json`.

## Protección de ramas

`infra/bootstrap/bootstrap-github.sh` protege la rama predeterminada (`main`) con:

- Pull request obligatoria antes de fusionar.
- Dos revisiones aprobatorias.
- Comprobación de estado obligatoria: `continuous-integration/jenkins/pr-merge`.
- Resolución de conversaciones obligatoria.
- Push forzados y eliminación de ramas deshabilitados.

Si el token no puede gestionar la protección de ramas, el script imprime una advertencia y la
configuración debe aplicarse manualmente.

## SonarQube y Nexus

- SonarQube: `http://<TOOLS_EIP>:9000`, solo profesor/administrador.
- Interfaz de Nexus: `http://<TOOLS_EIP>:8081`, solo profesor/administrador.
- Registro: `<TOOLS_PRIVATE_IP>:5000`, privado.
- Repositorio: `circleguard-docker`.
- Compuertas de calidad: `CircleGuard Gateway`, `CircleGuard Services` y `CircleGuard Promotion`;
  cobertura de líneas ≥ 60 % excepto promotion, que aplica la regla del 70 % del proyecto.
- Proyectos: `circleguard-gateway-service`, `circleguard-auth-service`, `circleguard-form-service`, `circleguard-identity-service`, `circleguard-promotion-service`.

## Ingreso de la aplicación

- URL: `http://<TOOLS_EIP>/`
- Nginx actúa como proxy hacia la IP privada del gateway de dev en `8087`.
- El puerto `80` es público; `443` está reservado para un futuro dominio/TLS.

## Pipeline

El `Jenkinsfile` es consciente de la rama: el job multibranch descubre `main`, las ramas de
funcionalidad y las pull requests. Cada compilación hija ejecuta las mismas etapas con el
código de su rama o PR.

1. **Checkout**: `checkout scm` y metadatos del commit.
2. **Resolve services and version**: `SERVICE=auto` detecta los servicios modificados a partir del diff; `SERVICE=all` compila todos los servicios; `ciVersion` calcula la versión semántica a partir de las etiquetas de Git y Conventional Commits.
3. **Build**: compilación con Gradle por servicio.
4. **Unit tests + JaCoCo**: pruebas y cobertura; auth, identity, gateway y form exigen **cobertura de líneas ≥ 60 %**; promotion exige ≥ 70 %.
5. **SonarQube + Quality Gate**: escáner y `waitForQualityGate` obligatorio; el pipeline se detiene si la compuerta está en rojo.
6. **Integration tests**: pruebas con Testcontainers, separadas de las pruebas unitarias.
7. **Package**: `bootJar`.
8. **Git tag** (en `main`): crea y hace push de `vX.Y.Z` para los servicios publicables.
9. **Release notes** (en `main`): genera `docs/release-notes/vX.Y.Z.md` a partir de Conventional Commits.
10. **GitHub Release** (en `main`): publica o actualiza el release de la etiqueta `vX.Y.Z` con esas notas; los reintentos son idempotentes.
11. **Publish** (en `main`): artefacto Nexus para los servicios Maven publicables.
12. **Docker** (en `main`): compila y hace push de cada servicio con Dockerfile usando solo la etiqueta semver, nunca `latest`; verifica usuario no root y HEALTHCHECK.
13. **Docker web** (en `main`): compila el cliente Expo con `mobile/Dockerfile` y publica `web:<tag>`.
14. **Deploy** (opcional): dev, E2E, stage, aprobación de producción, producción, rollback. Solo cuando `DEPLOY=true` en `main`.
15. **Archive**: JUnit, JaCoCo HTML/XML, `sonar-links.txt`, `image-digest.txt` y notas de versión.

### Compuertas de seguridad

El `Jenkinsfile` ejecuta tres etapas adicionales; un error en cualquiera de ellas detiene la
compilación antes de `Approval prod`.

- `Trivy IaC Security Gate`: `trivy config` sobre `deploy/k8s/`, con error ante `CRITICAL`.
- `Trivy Image Security Gate`: `trivy image --severity CRITICAL --ignore-unfixed --exit-code 1` sobre cada imagen compilada en la ejecución (`service.docker`), incluida `promotion`.
- `OWASP ZAP Security Gate`: `zap-baseline.py` dentro de `cg-stage`, un pod por destino — `http://auth:8180/actuator/health` y `http://promotion:8088/actuator/health`. Los registros por destino se añaden a `build/reports/security/zap-stage.txt`, y la compilación produce un error cuando el informe agregado contiene una línea `FAIL-NEW`.

### Ejecución selectiva

- `SERVICE=auto`: servicios tocados en `services/<service>/` o en `deploy/**/<service>-*.yml`. Solo las rutas de alcance global (`build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `sonar-project.properties`, `gradlew`, `gradlew.bat`, `gradle/`) y los manifiestos compartidos de `deploy/` seleccionan todos los servicios.
- Los cambios en `Jenkinsfile` y `jenkins/` seleccionan **ningún** servicio Java: cambian la
  definición del pipeline, no los microservicios. `ciServices.verifySelection()` sigue
  validando la biblioteca compartida en cada compilación.
- `mobile/`, `docs/`, `tests/` e `infra/` tampoco seleccionan servicios Java.
- `SERVICE=all`: los ocho servicios. Una compilación en `main` también selecciona los ocho.
- `SERVICE=<name>`: un servicio; por ejemplo `identity`.
- Sin servicios modificados, la ejecución se mantiene en verde y omite la compilación.
- `ciServices.verifySelection()` ejecuta los invariantes de selección en cada compilación,
  implementando la ejecución "modificado o todos" exigida por E1.4 y el criterio de ejecución
  selectiva P1.4 del enunciado. El ejecutor independiente
  `jenkins/test/TestCiServices.groovy` reproduce los mismos casos sin Jenkins.

### Artefactos

Cada compilación archiva JUnit XML, JaCoCo HTML/XML, los enlaces del panel de Sonar, los
digests de las imágenes publicadas y las notas de versión generadas. Los enlaces también se
imprimen en el registro de compilación.

## Evidencia

Guarda las capturas en `docs/informe/evidencia/` y los informes Markdown en `docs/informe/`.
Nunca incluyas contraseñas, tokens ni claves privadas.

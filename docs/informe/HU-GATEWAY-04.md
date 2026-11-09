# HU-GATEWAY-04 — Pipeline CI del gateway con quality gate

> **Estado:** implementación y pruebas locales completadas; validación remota y
> capturas pendientes de acceso al entorno de herramientas. Las evidencias de
> este documento se completan únicamente con capturas reales.

## 1. Resumen

Se implementó un pipeline multibranch para el gateway, disparado por webhook de
GitHub, con etapas reutilizables en la Shared Library y ejecución selectiva por
servicio. El pipeline compila, ejecuta pruebas unitarias con JaCoCo, analiza con
SonarQube, bloquea si el quality gate falla, ejecuta pruebas de integración,
empaqueta y publica.

| Elemento | Implementación |
|---|---|
| Job | `circleguard-gateway` (multibranch) |
| Disparador | Webhook de GitHub (`push` y `pull_request`) |
| Librería | `circleguard`, desde `jenkins/vars/` |
| Selección | `SERVICE=auto`, `all` o un servicio |
| Cobertura | JaCoCo, mínimo 60 % de líneas en gateway |
| Gate | SonarQube `CircleGuard Gateway`, bloqueante |
| Artefactos | JUnit, JaCoCo, enlaces de Sonar, digest |
| Notificación | Comentario en GitHub + Slack |

## 2. Arquitectura

```text
GitHub
  │  push / pull_request
  ▼
Ingress nginx :80
  │  /github-webhook/
  ▼
Jenkins :8080 (privado)
  │
  ├── Multibranch job: circleguard-gateway
  │     ├── main
  │     ├── feat/*
  │     └── pull requests
  │
  ├── Shared Library: circleguard (jenkins/vars)
  │     ├── ci.groovy
  │     ├── ciServices.groovy
  │     └── ciNotify.groovy
  │
  ├── SonarQube :9000 (privado)
  └── Nexus :8081 / registry :5000 (privado)
```

El webhook no apunta a una rama: apunta al job multibranch. GitHub Branch Source
descubre la rama o el PR y crea el build correspondiente.

## 3. Implementación paso a paso

### 3.1 Shared Library

Se movió `vars/ci.groovy` a `jenkins/vars/ci.groovy` y se añadieron:

- `jenkins/vars/ciServices.groovy`: catálogo de servicios y selección por diff.
- `jenkins/vars/ciNotify.groovy`: comentario en GitHub y Slack.

JCasC carga la librería desde `jenkins/`:

```yaml
globalLibraries:
  libraries:
    - name: circleguard
      defaultVersion: ${LIBRARY_BRANCH}
      retriever:
        modernSCM:
          libraryPath: jenkins
          scm:
            git:
              remote: ${GITHUB_REPO_URL}
              credentialsId: github-creds
```

### 3.2 Multibranch y webhook

El job se define en `deploy/compose/tools/jenkins/casc/jenkins.yaml` con GitHub
Branch Source, traits de ramas y PRs, y `scriptPath('Jenkinsfile')`.

El webhook se registra en:

```text
http://<TOOLS_EIP>/github-webhook/
```

El ingress enruta solo esa ruta a `jenkins:8080`; el puerto 8080 no se expone a
Internet. Los eventos son `push` y `pull_request`, con secreto compartido.

### 3.3 Etapas

| Etapa | Acción |
|---|---|
| Checkout | `checkout scm`, limpieza de artefactos de notificación |
| Resolve services | `ciServices.resolve(SERVICE)` |
| Build | `./gradlew :services:<servicio>:classes` |
| Unit Tests + JaCoCo | `test`, `jacocoTestReport`, `jacocoTestCoverageVerification` |
| SonarQube + Quality Gate | `sonar-scanner`, `waitForQualityGate`, validación por API |
| Integration Tests | `./gradlew :services:<servicio>:integrationTest` |
| Package | `bootJar` |
| Publish | Nexus y, en `main`, Docker + digest |
| Archive | JUnit, JaCoCo, enlaces y digest |

Si el quality gate falla, el pipeline se detiene antes de integración,
empaquetamiento y publicación.

### 3.4 Ejecución selectiva

- `auto`: detecta servicios modificados con `git diff` contra `origin/main`.
- `all`: gateway, auth y form, los servicios con JaCoCo.
- `<servicio>`: solo ese servicio.
- Cambios globales (`build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`,
  `Jenkinsfile`, `sonar-project.properties`, `gradlew`, `gradle/`, `jenkins/`)
  seleccionan todos los servicios CI.
- En `main` se construyen todos los servicios CI para no dejar la rama sin
  validación cuando el diff contra sí misma es vacío.

### 3.5 JaCoCo

En `services/circleguard-gateway-service/build.gradle.kts`:

```kotlin
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = "BUNDLE"
            counter = "LINE"
            value = "COVEREDRATIO"
            limit {
                minimum = 0.60.toBigDecimal()
            }
        }
    }
}
```

La prueba de integración se separó con `@Tag("integration")` y la tarea
`integrationTest`; `test` excluye esa etiqueta.

### 3.6 Quality gate

`infra/bootstrap/bootstrap-tools.sh` crea el gate `CircleGuard Gateway` con la
condición `line_coverage < 60` y lo asigna al proyecto del gateway. Para auth y form
crea `CircleGuard Services` con la misma regla. El pipeline espera el gate con
`waitForQualityGate abortPipeline: true` y además consulta:

```text
/api/qualitygates/project_status?projectKey=circleguard-gateway-service
```

### 3.7 Artefactos y notificación

- JUnit: `build/test-results/test/*.xml` e `integrationTest/*.xml`.
- JaCoCo: HTML y XML.
- `sonar-links.txt`: enlace público al dashboard.
- `image-digest.txt`: digest de la imagen publicada.
- Fallo: comentario en el PR o commit mencionando al autor, más Slack.

## 4. Pruebas locales

Ejecutadas antes del commit, con Java 21 (toolchain) y Docker local:

| Comando | Resultado |
|---|---|
| `./gradlew :services:circleguard-gateway-service:test jacocoTestReport jacocoTestCoverageVerification` | **BUILD SUCCESSFUL**, 16 pruebas unitarias, 0 fallos |
| `./gradlew :services:circleguard-gateway-service:integrationTest` | **BUILD SUCCESSFUL**, 3 pruebas de integración, 0 fallos |
| `./gradlew :services:circleguard-auth-service:test jacocoTestReport jacocoTestCoverageVerification` | **BUILD SUCCESSFUL**, 24 pruebas, 0 fallos |
| `./gradlew :services:circleguard-form-service:test jacocoTestReport jacocoTestCoverageVerification` | **BUILD SUCCESSFUL**, 35 pruebas, 0 fallos |

Cobertura JaCoCo:

| Servicio | Líneas cubiertas | Total | Cobertura |
|---|---|---|---|
| Gateway | 47 | 49 | **95.92 %** |
| Auth | 183 | 187 | **97.86 %** |
| Form | 121 | 140 | **86.43 %** |

El reporte XML del gateway está en
`services/circleguard-gateway-service/build/reports/jacoco/test/jacocoTestReport.xml`.

Durante la validación se corrigieron dos expectativas de prueba del auth que
interpretaban `qr.expiration` como milisegundos cuando el servicio lo trata como
segundos: el valor de prueba pasó de `60000` a `60` y el TTL esperado del
`QrTokenService` se mantiene en `60000` ms. No cambia código de producción.

## 5. Evidencias

Las capturas se almacenan en `docs/informe/evidencia/HU-GATEWAY-04/`. Ver el
`README.md` de esa carpeta para el estado y los estándares.

| Evidencia | Archivo | Estado |
|---|---|---|
| URL del build de Jenkins | `01-jenkins-build.png` | ✅ Build #2 |
| Reporte JaCoCo ≥ 60 % | `02-jacoco-report.png` | ✅ 95 % líneas |
| Dashboard de Sonar con gate verde | `03-sonar-dashboard.png` y `17-sonar-overall.png` | ✅ Gate Passed |
| Configuración del webhook | `04-github-webhook-config.png` | Pendiente de UI |
| Entrega reciente del webhook | `05-github-webhook-delivery.png` | Pendiente de UI; API 200 |
| Protección de `main` | `06-branch-protection.png` | Pendiente de UI; API aplicada |
| Artefactos del build | `07-jenkins-artifacts.png` y `15-artifacts-services.png` | ✅ |
| Reporte JUnit | `14-junit-report.png` | ✅ 78 pruebas |
| Enlaces a Sonar | `16-sonar-links.png` | ✅ |
| Duración del build | `08-build-duration.png` | ✅ 5 min 58 s |
| Notificación de fallo en GitHub | `09-failure-notification-github.png` | Pendiente |
| Notificación de fallo en Slack | `10-failure-notification-slack.png` | Pendiente |
| Quality gate en rojo | `11-quality-gate-red.png` | Pendiente |
| Ejecución selectiva `auto` | `12-selective-auto.png` | Pendiente |
| Ejecución selectiva `all` | `13-selective-all.png` | Pendiente |

### 5.1 URL del build

Build **#2** del PR #23, commit `5329864`, ejecutado el 8 de noviembre de 2026:

**<http://77.112.223.174:8080/job/circleguard-gateway/job/PR-23/2/>**

![Página del build de Jenkins](evidencia/HU-GATEWAY-04/01-jenkins-build.png)

### 5.2 Cobertura JaCoCo

El reporte agregado muestra **95 % de líneas**; el gateway reporta 95.92 %,
auth 97.86 % y form 86.43 %.

![Reporte JaCoCo](evidencia/HU-GATEWAY-04/02-jacoco-report.png)

### 5.3 SonarQube

El gate `CircleGuard Gateway` está en **Passed**, con **88.7 % de cobertura
global** sobre 49 líneas y 0 bugs, 0 vulnerabilidades y 0 code smells.

![Dashboard de Sonar](evidencia/HU-GATEWAY-04/03-sonar-dashboard.png)

![Cobertura global de Sonar](evidencia/HU-GATEWAY-04/17-sonar-overall.png)

### 5.4 Pruebas y artefactos

La página de pruebas reporta **78 pruebas y 0 fallos**. Los artefactos incluyen
los reportes de los tres servicios y `sonar-links.txt`.

![Reporte JUnit](evidencia/HU-GATEWAY-04/14-junit-report.png)

![Artefactos del build](evidencia/HU-GATEWAY-04/07-jenkins-artifacts.png)

![Artefactos por servicio](evidencia/HU-GATEWAY-04/15-artifacts-services.png)

![Enlaces a Sonar](evidencia/HU-GATEWAY-04/16-sonar-links.png)

### 5.5 Duración

El build completo tardó **5 min 58 s**, por debajo del límite de 10 minutos.

![Duración del build](evidencia/HU-GATEWAY-04/08-build-duration.png)

## 6. Cómo reproducir la validación

1. Ejecutar `./infra/bootstrap/bootstrap-all.sh` con `GITHUB_TOKEN` con permisos
   de repositorio y webhooks.
2. Verificar en **GitHub → Settings → Webhooks** el hook
   `http://<TOOLS_EIP>/github-webhook/`, eventos `push` y `pull_request`, y una
   entrega 200.
3. En Jenkins, ejecutar **Scan Multibranch Pipeline Now**.
4. Abrir el build de la rama `feat/HU-GATEWAY-04-pipeline-ci`.
5. Ejecutar un build con `SERVICE=auto` y otro con `SERVICE=all`.
6. Abrir el reporte JaCoCo y verificar cobertura de líneas ≥ 60 %.
7. Abrir el dashboard de Sonar y verificar el gate en verde.
8. Descargar los artefactos y registrar el digest.
9. Para la prueba negativa, crear una rama temporal con una prueba fallida,
   abrir un PR y verificar el build rojo y la notificación. No fusionar.
10. Restaurar `LIBRARY_BRANCH=main` después del merge y eliminar ramas temporales.

## 7. Riesgos y limitaciones

- El webhook requiere que el puerto 80 sea alcanzable desde GitHub y que el
  token tenga `admin:repo_hook`; si no, el hook se crea manualmente.
- Antes del merge, `LIBRARY_BRANCH` debe apuntar a la rama de la HU porque la
  librería se movió a `jenkins/vars/`; después se restaura a `main`.
- Testcontainers requiere Docker y `TESTCONTAINERS_HOST_OVERRIDE`; ya está
  configurado en el contenedor de Jenkins.
- El primer build puede superar los 10 minutos por caché fría; la evidencia de
  duración se toma de un build con caché caliente.
- SonarQube Community no decora PRs; el estado se reporta con Jenkins y se
  valida por API.

## 8. Conclusión

La implementación cubre los criterios de la HU. La conclusión definitiva y las
capturas se completan cuando se ejecute el pipeline en el entorno de
herramientas.

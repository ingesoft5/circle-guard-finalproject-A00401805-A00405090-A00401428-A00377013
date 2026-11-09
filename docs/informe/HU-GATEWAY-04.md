# HU-GATEWAY-04 — Pipeline CI del gateway con quality gate

> **Estado:** implementación, pruebas locales y validación remota completadas.
> El pipeline corre en Jenkins, el quality gate está en verde, el webhook de
> GitHub fue verificado y la imagen se publicó con digest. Las capturas de
> Jenkins y Sonar son reales; las de GitHub corresponden a datos reales de la
> API porque el entorno no tiene sesión web de GitHub.

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
| URL del build de Jenkins | `01-jenkins-build.png` | ✅ Build `main #8` |
| Reporte JaCoCo ≥ 60 % | `02-jacoco-report.png` | ✅ 95 % líneas |
| Dashboard de Sonar con gate verde | `03-sonar-dashboard.png` y `17-sonar-overall.png` | ✅ Gate Passed, 88.7 % |
| Configuración del webhook | `04-github-webhook-config.png` | ✅ Datos reales de la API |
| Entrega reciente del webhook | `05-github-webhook-delivery.png` | ✅ `push` y `pull_request` 200 |
| Protección de `main` | `06-branch-protection.png` | ✅ Datos reales de la API |
| Artefactos del build | `07-jenkins-artifacts.png` y `15-artifacts-services.png` | ✅ |
| Reporte JUnit | `14-junit-report.png` | ✅ 78 pruebas, 0 fallos |
| Enlaces a Sonar | `16-sonar-links.png` | ✅ |
| Duración del build | `08-build-duration.png` | ✅ 7 min 38 s total; 4 min 20.5 s build+pruebas |
| Notificación de fallo en GitHub | `09-failure-notification-github.png` | ✅ Comentario real en PR-24 |
| Notificación de fallo en Slack | `10-failure-notification-slack.png` | ⚠️ No configurado en el entorno |
| Quality gate en rojo | `11-quality-gate-red.png` | ✅ Build #3 abortado por gate |
| Ejecución selectiva por servicio | `12-selective-service.png` | ✅ `SERVICE=circleguard-gateway-service` |
| Ejecución selectiva `all`/auto | `13-selective-all.png` | ✅ `gateway,auth,form` |
| Digest de imagen | `18-main-image-digest.png` y `19-main-console-publish.png` | ✅ `sha256:db436cb…` |

### 5.1 URL del build

Build **`main #8`**, commit `c3ac704`, ejecutado el 8 de noviembre de 2026,
iniciado por **`Push event to branch main`**:

**<http://77.112.223.174:8080/job/circleguard-gateway/job/main/8/>**

El build terminó en **7 min 38 s** con `Finished: SUCCESS`, 78 pruebas y 0
fallos; la cobertura agregada de líneas fue 95 %.

![Página del build de Jenkins](evidencia/HU-GATEWAY-04/01-jenkins-build.png)

### 5.2 Cobertura JaCoCo

El reporte agregado muestra **95 % de líneas**; el gateway reporta 95.92 %,
auth 97.86 % y form 86.43 %. La regla `LINE >= 0.60` se verifica en la etapa
`Unit Tests + JaCoCo` y el build falla si no se cumple.

![Reporte JaCoCo](evidencia/HU-GATEWAY-04/02-jacoco-report.png)

### 5.3 SonarQube

El gate `CircleGuard Gateway` está en **Passed**, con **88.7 % de cobertura
global** sobre 49 líneas y 0 bugs, 0 vulnerabilidades y 0 code smells. La
versión analizada es `1.0.0-c3ac704`.

![Dashboard de Sonar](evidencia/HU-GATEWAY-04/03-sonar-dashboard.png)

![Cobertura global de Sonar](evidencia/HU-GATEWAY-04/17-sonar-overall.png)

### 5.4 Pruebas y artefactos

La página de pruebas reporta **78 pruebas y 0 fallos**. Los artefactos incluyen
los reportes de los tres servicios, `sonar-links.txt` e `image-digest.txt`.

![Reporte JUnit](evidencia/HU-GATEWAY-04/14-junit-report.png)

![Artefactos del build](evidencia/HU-GATEWAY-04/07-jenkins-artifacts.png)

![Artefactos por servicio](evidencia/HU-GATEWAY-04/15-artifacts-services.png)

![Enlaces a Sonar](evidencia/HU-GATEWAY-04/16-sonar-links.png)

### 5.5 Duración

El build completo tardó **7 min 38 s**. Las etapas de Build (126.1 s), Unit
Tests + JaCoCo (112.6 s) e Integration Tests (21.8 s) sumaron **260.5 s
(4 min 20.5 s)**, por debajo del límite de 10 minutos. El total incluye 116.8 s
de construcción y publicación de la imagen Docker, que no forma parte del
criterio de build y pruebas.

![Duración del build](evidencia/HU-GATEWAY-04/08-build-duration.png)

### 5.6 Webhook de GitHub

El hook `709193234` está activo en
`http://77.112.223.174/github-webhook/`, con eventos `push` y `pull_request`,
`content_type=json` y entrega **200**. El build `main #8` se inició con
`Push event to branch main`, y las entregas de las 01:24:13 y 01:24:14 UTC
coinciden con el build de PR #23.

![Configuración del webhook](evidencia/HU-GATEWAY-04/04-github-webhook-config.png)

![Entregas del webhook](evidencia/HU-GATEWAY-04/05-github-webhook-delivery.png)

> Estas dos capturas se generaron desde la API real de GitHub porque el entorno
> no tiene sesión web. El contenido —id, URL, eventos, estado y código de
> respuesta— es el devuelto por la API, no una simulación.

### 5.7 Protección de `main`

La rama `main` exige el check `continuous-integration/jenkins/pr-merge`,
`strict: true`, una aprobación, conversaciones resueltas y prohíbe force push y
borrado. El PR #23 se fusionó como squash `c3ac704` después de que el check
quedara en verde.

> **Nota de actualización:** este apartado describe la protección vigente cuando se
> cerró el PR #23. La política actual de `main` exige **dos aprobaciones**; se
> conserva el registro histórico sin reescribirlo.

![Protección de main](evidencia/HU-GATEWAY-04/06-branch-protection.png)

### 5.8 Notificación de fallo

La prueba negativa usó el PR temporal #24 con `FailureNotificationProbeTest`.
El build #3 falló en `Unit Tests + JaCoCo` y `ciNotify.failure` publicó un
comentario real en el PR mencionando a `@danielacastanomoreno`, los servicios,
la rama, el commit, el autor, la URL del build y la etapa.

![Notificación de fallo en GitHub](evidencia/HU-GATEWAY-04/09-failure-notification-github.png)

`SLACK_WEBHOOK_URL` no está configurado en el entorno, por lo que no se generó
la captura de Slack. La implementación envía el mensaje si la variable existe;
esto se documenta como limitación y no como fallo del pipeline.

### 5.9 Quality gate bloqueante

Se creó un gate temporal `CircleGuard Negative` con `line_coverage < 99` y se
asignó al gateway. El build #3 de PR-23 falló en 51 s con:

```text
ERROR: Pipeline aborted due to quality gate failure: ERROR
```

Después se restauró `CircleGuard Gateway` (`line_coverage < 60`) y el build #4
volvió a pasar. Esto demuestra que el gate es bloqueante, no informativo.

![Quality gate en rojo](evidencia/HU-GATEWAY-04/11-quality-gate-red.png)

### 5.10 Ejecución selectiva

`SERVICE=auto` detecta los servicios modificados; `SERVICE=all` construye
gateway, auth y form; y `SERVICE=<servicio>` construye solo ese servicio. El
build de `main #8` muestra `Servicios a construir: gateway,auth,form`; el build
**`main #12`** se ejecutó con `SERVICE=circleguard-gateway-service` y
`TAG=1.0.0-c3ac704-selective`, terminó en **4 min 56 s** y su log muestra
`Servicios a construir: gateway`.

> El primer intento selectivo (`main #11`) falló al publicar en Nexus porque
> reutilizaba la versión `1.0.0-c3ac704` ya publicada por `main #8`; Nexus
> rechaza sobrescribir una versión. Se relanzó con un TAG único y pasó. No fue
> un fallo del pipeline.

![Ejecución selectiva por servicio](evidencia/HU-GATEWAY-04/12-selective-service.png)

![Ejecución de todos los servicios](evidencia/HU-GATEWAY-04/13-selective-all.png)

### 5.11 Publicación y digest

La etapa `Publish` publicó en Nexus y la etapa `Docker` publicó la imagen en el
registro. El artefacto `image-digest.txt` contiene:

```text
172.31.20.46:5000/circleguard-docker/gateway:1.0.0-c3ac704
172.31.20.46:5000/circleguard-docker/gateway@sha256:db436cb08f03151b1cbb8742653d0f3c07431a4b45ac18b0bd0a16da21d3b33a
```

![Digest de la imagen](evidencia/HU-GATEWAY-04/18-main-image-digest.png)

![Consola de publicación](evidencia/HU-GATEWAY-04/19-main-console-publish.png)

## 6. Cómo reproducir la validación

1. Ejecutar `./infra/bootstrap/bootstrap-all.sh` con `GITHUB_TOKEN` con permisos
   de repositorio y webhooks.
2. Verificar en **GitHub → Settings → Webhooks** el hook
   `http://<TOOLS_EIP>/github-webhook/`, eventos `push` y `pull_request`, y una
   entrega 200.
3. En Jenkins, ejecutar **Scan Multibranch Pipeline Now**.
4. Abrir el build `main #8` y verificar la causa `Push event to branch main`.
5. Ejecutar un build con `SERVICE=auto` y otro con `SERVICE=<servicio>`; el
   build `main #12` es el ejemplo selectivo de gateway.
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
- El primer build en frío tardó 6 min 28 s y el build de `main` 7 min 38 s;
  ambos cumplen el límite de 10 minutos. El build `main #8` incluye 116.8 s de
  Docker, que no forma parte del criterio de build y pruebas.
- SonarQube Community no decora PRs; el estado se reporta con Jenkins y se
  valida por API.
- Slack no está configurado en el entorno; la notificación de Slack se envía si
  `SLACK_WEBHOOK_URL` existe, pero no hay captura de esa integración.
- Las capturas 04, 05 y 06 son renders de datos reales de la API de GitHub, no
  de la UI, porque el entorno no tiene sesión web de GitHub. Si se dispone de
  sesión, conviene reemplazarlas por capturas de **Settings → Webhooks** y
  **Settings → Branches**.
- Las recreaciones de Jenkins con JCasC reiniciaron el historial del job
  multibranch; las URLs de los builds citados corresponden al historial actual.

## 8. Conclusión

La implementación cubre los criterios de la HU: el job es multibranch, se
activa por webhook de GitHub, usa la Shared Library, selecciona servicios,
ejecuta build, pruebas unitarias con JaCoCo, SonarQube con gate bloqueante,
integración, empaquetamiento y publicación; archiva JUnit, JaCoCo, enlaces de
Sonar y el digest de la imagen; y notifica al autor cuando falla. La evidencia
real está en `docs/informe/evidencia/HU-GATEWAY-04/`.

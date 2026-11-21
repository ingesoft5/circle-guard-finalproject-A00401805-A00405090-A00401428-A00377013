# HU-FILE-03 — Pipeline CI del file-service con quality gate e idempotencia

**Fecha:** 20 de noviembre de 2026  
**Rama:** `fix/HU-FILE-03-idempotent-publish` · PR #134 · Issue #135  
**Alcance:** Parte 1 (P1.4) y cierre on-premise (P1.2 / plan P2.4)

## 1. Validación de P1.4 — Excelente

| Elemento exigido | Evidencia en el repositorio | Estado |
|---|---|---|
| Multibranch + webhook | Job multibranch; `infra/bootstrap/bootstrap-github.sh` (GET → PATCH/POST de hooks); relay `smee-client` con doble barra en `SMEE_TARGET`; incidente 2026-11-18 documentado en `docs/infra/migracion-onpremise.md` §3.6 | Cumplido |
| Shared Library | `@Library('circleguard@main') _` en `Jenkinsfile`; pasos en `jenkins/vars/ci.groovy`, `ciServices.groovy`, `ciNotify.groovy` | Cumplido |
| JaCoCo | `services/circleguard-file-service/build.gradle.kts`: plugin `jacoco`, `jacocoTestReport`, `jacocoTestCoverageVerification` con mínimo `0.60`, conectado a `check` | Cumplido |
| Quality gate bloqueante | `jenkins/vars/ci.groovy` líneas 44-63: `waitForQualityGate abortPipeline: true` dentro de `timeout(5)` + verificación por API con `jq -e '.projectStatus.status == "OK"'` | Cumplido |
| Ejecución selectiva | Parámetro `SERVICE` en `Jenkinsfile` y `ciServices.resolve()`; ejecución previa con `SERVICE=circleguard-file-service`, `TAG=1.0.0-<sha>-file`, `SKIP_INTEGRATION=true` | Cumplido |
| Limpieza dinámica de ramas | Sin `headWildcardFilter`; webhook con `delete`; `orphanedItemStrategy` con `daysToKeep(0)`/`numToKeep(0)`; la rama borrada tras merge en `main` deja de indexarse y las demás se conservan | Versionado; verificación on-prem pendiente |

No se modifica `jenkins/vars/` en este PR: la librería está fijada a `main` y los cambios
de la rama no serían visibles para el job.

## 2. Idempotencia del pipeline

La semántica semver ya era idempotente: `resolveSemver` reutiliza el tag exacto cuando el
HEAD está etiquetado y `createSemverTag` omite la creación si el tag ya apunta al commit.
El fallo estaba en los efectos laterales: `maven-releases` rechaza el redeploy con HTTP 400
y el registro acepta sobrescribir el manifest, cambiando el digest y duplicando evidencia.

El PR #134 mantiene los guardas:

- `loadEnvironment('NEXUS_REGISTRY', 'NEXUS_URL', ...)` para consultar Nexus.
- `nexusArtifactExists()` y `dockerImageExists()` antes de publicar.
- Mensajes `Publicación omitida: ... ya existe en Nexus (reintento idempotente).`
- `image-digest.txt` conserva el digest real cuando se omite el push.

**El skip no se quita aunque se active inmutabilidad.** Sin él, el rerun con el mismo TAG
semver intentaría otra publicación y el pipeline caería en `400/409`, conforme a P1.4.

## 3. Política de tags

- Inmutabilidad aplicada solo al patrón `^\d+\.\d+\.\d+$` (por ejemplo `1.8.0`).
- `latest`, `dev`, `staging` y los tags de PR como `1.0.0-<sha>-file` permanecen mutables.
- El repositorio `circleguard-docker` del registro `:5000` está alojado en Nexus; antes de
  activar la política debe verificarse en la UI/API del host. Si el registro no expone esa
  opción, el control vigente es el skip en CI más el uso de semver, sin inventar configuración.
- Toda decisión manual queda registrada aquí y en `docs/infra/migracion-onpremise.md` §3.7,
  cumpliendo E1.2: *no se acepta configuración manual no documentada*.

## 4. Wrapper on-premise de un comando

`infra/onpremise/rebuild-onpremise.sh` cierra la frase literal de P1.2: valida el token de
SonarQube, ejecuta `bootstrap-onpremise.sh`, asegura todos los `projectKey` del catálogo de
la Shared Library —incluido `circleguard-file-service`, ausente en la lista histórica de
`bootstrap-tools.sh`— y converge el stack `cg-tools` con `config -q` + `up -d --remove-orphans`.

Ejecución de demostración en el host:

```bash
bash infra/onpremise/rebuild-onpremise.sh 2>&1 | tee /tmp/rebuild-run-1.log
bash infra/onpremise/rebuild-onpremise.sh 2>&1 | tee /tmp/rebuild-run-2.log
grep -E "already|existe|skipped|Created|updated|Running|Up to date" /tmp/rebuild-run-2.log | head -n 40
```

Criterio: la segunda corrida no crea duplicados, no pide secretos nuevos y no cambia
credenciales.

## 5. Evidencia

Los artefactos se organizan en `docs/informe/evidencia/HU-FILE-03/`. El índice de capturas
y el estado de cada una están en ese directorio. Las corridas del wrapper, el build
selectivo, el rerun idempotente y la quality gate negativa se enlazan desde allí.

> **Estado de ejecución:** el wrapper y la documentación quedan versionados. La ejecución en
> el host `docker@205m07`, la verificación de la política en Nexus y las corridas de Jenkins
> requieren la sesión on-premise; no se registran capturas como realizadas hasta que se
> obtengan en ese entorno.

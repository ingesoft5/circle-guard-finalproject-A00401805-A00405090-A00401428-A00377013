<!-- CG-DOC: Índice de evidencia de observabilidad y seguridad reproducida localmente para HU-DASH-05. -->
# HU-DASH-05 — Índice de evidencia local

Los resultados locales se obtuvieron en este equipo; la evidencia que requiere clúster, Jenkins o credenciales se identifica como remota.

Los JSON son resultados reales de las herramientas ejecutadas en este equipo; no son capturas de Jenkins ni de Grafana.

## 1. Archivos

| Archivo | Contenido |
| --- | --- |
| `trivy-config.json` | Escaneo `trivy config --severity HIGH,CRITICAL` sobre `deploy/k8s` con el componente `dashboard-observability` integrado. |
| `trivy-image.json` | Escaneo **anterior a la remediación** de la imagen construida desde el Dockerfile de dashboard (9 CRITICAL / 35 HIGH). |
| `trivy-image-0.57.1.json` | Escaneo posterior a la remediación con la versión que usa Jenkins (`0.57.1`): 0 HIGH / 0 CRITICAL. |
| `trivy-image-0.70.0.json` | Escaneo posterior a la remediación con la versión local más reciente (`0.70.0`): 0 HIGH / 0 CRITICAL. |

## 2. IaC (E2.3 / E2.6)

```bash
docker run --rm -v "$PWD":/work -w /work aquasec/trivy:0.70.0 \
  config --skip-version-check --severity CRITICAL --exit-code 1 deploy/k8s
# rc=0; Detected config files num=30; 0 CRITICAL

docker run --rm -v "$PWD":/work -w /work aquasec/trivy:0.70.0 \
  config --skip-version-check --severity HIGH,CRITICAL --format json \
  --output /work/docs/informe/evidencia/HU-DASH-05/trivy-config.json deploy/k8s
# rc=0; 31 archivos; HIGH=74, CRITICAL=0
```

Los `HIGH` son guías estándar (por ejemplo `KSV-0014`, `KSV-0118`, `KSV-0056`). La regla bloqueante del `Jenkinsfile` cuenta solo `CRITICAL`, por lo que este resultado **no bloquea** la etapa de IaC. El aumento frente al registro anterior de 27 archivos / 72 `HIGH` se explica por los tres manifiestos nuevos del componente `dashboard-observability` y por `dashboard-config.yml`, incorporado por HU-DASH-04 al hacer merge de `main`. Tras ese merge el escaneo se repitió: 31 archivos, 74 `HIGH`, 0 `CRITICAL` (el ConfigMap nuevo no añade `HIGH`).

## 3. Imagen (E2.3 / E2.6)

### 3.1 Antes de la remediación

El primer intento contra `circleguard-docker/dashboard:0.1.0` falló porque el contenedor Trivy no tiene acceso al socket Docker del host. Se construyó la imagen con el código original y se escaneó desde un tarball:

```bash
docker build -f services/circleguard-dashboard-service/Dockerfile \
  -t circleguard-docker/dashboard:hu-dash-05-local \
  --build-arg VERSION=hu-dash-05-local \
  --build-arg GIT_COMMIT=$(git rev-parse HEAD) \
  --build-arg SOURCE_URL=local .
# BUILD SUCCESSFUL; manifest list sha256:7a038045f65824b1879408e2086be9d1eb3792b7b8bec6fe9f0f08249686b233

docker run --rm -v "$PWD":/work -v /tmp/opencode:/scan \
  aquasec/trivy:0.70.0 image --skip-version-check \
  --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 \
  --input /scan/dashboard-hu-dash-05.tar \
  --format json --output /work/docs/informe/evidencia/HU-DASH-05/trivy-image.json
# rc=1; HIGH=35, CRITICAL=9; Alpine 3.24.4, 73 paquetes, 1 jar
```

Los 9 `CRITICAL` (todos con corrección disponible) son:

- 8 en `org.apache.tomcat.embed:tomcat-embed-core 10.1.19`: CVE-2025-24813, CVE-2026-41293, CVE-2026-43512, CVE-2026-43515, CVE-2026-55276, CVE-2026-65182, CVE-2026-65905, CVE-2026-68525.
- 1 en `org.springframework:spring-webmvc 6.1.5`: CVE-2026-47884 (corregida en 7.0.9).

Con ese resultado el gate real de Jenkins (`Jenkinsfile:328-347`, que cuenta `CRITICAL` con `jq` y falla si `criticals > 0`) habría bloqueado la entrega. No se bajó el umbral ni se añadieron excepciones.

### 3.2 Después de la remediación (Spring Boot 4.0.10)

Se actualizó la plataforma compartida a Spring Boot `4.0.10` en los ocho servicios y se reconstruyó la imagen:

```bash
docker build -f services/circleguard-dashboard-service/Dockerfile \
  -t circleguard-docker/dashboard:hu-dash-05-boot4 \
  --build-arg VERSION=hu-dash-05-boot4 \
  --build-arg GIT_COMMIT=$(git rev-parse HEAD) \
  --build-arg SOURCE_URL=local .
# BUILD SUCCESSFUL; image id sha256:ab6cb91f55ecf97e6b70f20ba8560876a33e7fa34c01587c046d40b8f01e256e

docker save circleguard-docker/dashboard:hu-dash-05-boot4 \
  -o /tmp/opencode/dashboard-hu-dash-05-boot4.tar
```

Escaneos sobre ese mismo tarball:

```bash
# Versión usada por Jenkins (0.57.1)
docker run --rm -v "$PWD":/work -v /tmp/opencode:/scan \
  aquasec/trivy:0.57.1 image \
  --severity HIGH,CRITICAL --ignore-unfixed \
  --input /scan/dashboard-hu-dash-05-boot4.tar \
  --format json \
  --output /work/docs/informe/evidencia/HU-DASH-05/trivy-image-0.57.1.json
# 0 HIGH, 0 CRITICAL

# Versión local más reciente (0.70.0)
docker run --rm -v "$PWD":/work -v /tmp/opencode:/scan \
  aquasec/trivy:0.70.0 image --skip-version-check \
  --severity HIGH,CRITICAL --ignore-unfixed \
  --input /scan/dashboard-hu-dash-05-boot4.tar \
  --format json \
  --output /work/docs/informe/evidencia/HU-DASH-05/trivy-image-0.70.0.json
# rc=0; 0 HIGH, 0 CRITICAL; ArtifactID sha256:464d39baf7b008e45b1d7a1744191b48953d14687dfe14371118eabffcf74aa9
```

**Conclusión:** la imagen reconstruida ya no tiene hallazgos `HIGH`/`CRITICAL` con ninguna de las dos versiones de Trivy, por lo que el gate de imagen **ya no bloquea** localmente. El umbral y la lógica del `Jenkinsfile` no se modificaron. La corrida oficial en Jenkins sigue siendo la única evidencia remota autorizada.

## 4. Observación sobre la versión de Trivy

Jenkins usa `aquasec/trivy:0.57.1` y la verificación local adicional usó `0.70.0`. Ambas versiones coinciden en **0 hallazgos `HIGH`/`CRITICAL`** para la imagen posterior a la remediación, así que la diferencia de versiones ya no es una limitación para este resultado local. La validación remota corresponde a la corrida oficial en Jenkins.

## 5. Evidencia remota

- Repetir los escaneos desde Jenkins con `SERVICE=circleguard-dashboard-service` y adjuntar la consola y los artefactos reales; el digest se publica en esa corrida.
- Verificar en el clúster: target de Prometheus, series `up` y promotion, panel Grafana, línea en Loki y disparo/recuperación de la alerta.
- No publicar renders que contengan Secrets ni copiar datos reales de logs o métricas en Git/Jira.

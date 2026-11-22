<!-- CG-DOC: HU-FILE-04 report on file-service real S3/MinIO integration with Testcontainers. -->
# HU-FILE-04 — Integración File–MinIO con Testcontainers

**Historia de usuario:** HU-FILE-04 — Integración File-MinIO con Testcontainers  
**Servicio:** `circleguard-file-service`  
**Entregable relacionado:** E1.6 — Pruebas automatizadas  
**Rama:** `feat/HU-FILE-04-integracion-file-minio-con-testcontainers`  
**Fecha:** 22 de noviembre de 2026
**Evidencia:** [`docs/informe/evidencia/HU-FILE-04/`](evidencia/HU-FILE-04/)  
**Última verificación:** 22 de noviembre de 2026, merge final `21c406b` (con `origin/main` `af451ff`, PR #158); tres corridas consecutivas + gate unitario/cobertura

---

## 1. Alcance y criterios de aceptación

Se valida el contrato de almacenamiento **S3-compatible real** usando el adaptador de producción del `file-service` contra un MinIO efímero. El servicio ya habla S3/MinIO mediante AWS SDK v2 cuando `file.storage.mode=s3`; la prueba de integración levanta el contexto Spring con ese modo, crea el bucket automáticamente y ejerce los endpoints HTTP reales (`upload`, `download`, `delete`) sobre el contenedor.

| Criterio de la HU | Cumplimiento | Evidencia |
|---|---|---|
| Testcontainers levanta MinIO y crea el bucket automáticamente | ✅ | `AbstractMinioIntegrationTest`: `@Container`, `Wait.forHttp("/minio/health/live")`, `createBucket` en `@BeforeAll`; caso `createsBucketAutomatically` |
| Contrato de carga y descarga | ✅ | Casos `uploadsObject` y `downloadsObject` vía `/api/v1/files/upload` y `/api/v1/files/download/{filename}` contra MinIO real |
| Borrado y archivo ausente «según el comportamiento actual» | ✅ | `DELETE /api/v1/files/{filename}` devuelve 204 al borrar y 404 si no existe; `missingObjectReturnsNotFound` valida 404 en S3 (HEAD/GET) y en HTTP (download/delete) |
| Etapa de integración en Jenkins | ✅ | `stage('Integration Tests')` existente + flag de catálogo + override local en `Jenkinsfile`; ver §7 |
| Menos de 3 minutos | ✅ | 53 s, 52 s y 52 s; límite explícito de 3 min en la tarea Gradle |
| Reporte publicado | ✅ | XML/HTML de JUnit en `build/test-results/integrationTest` y `build/reports/integrationTest`; `ci.integrationTest` publica JUnit y archiva `build/integration-test/*.log` |
| Sin contenedores residuales | ✅ | `docker ps` tras la ventana de Ryuk sin contenedores Testcontainers activos |
| Tres ejecuciones consecutivas | ✅ | Tres `BUILD SUCCESSFUL`, 5 pruebas / 0 fallos / 0 errores; ver `3-runs.txt` |
| Sin regresión unitaria ni de cobertura | ✅ | `test` + `jacocoTestReport` + `jacocoTestCoverageVerification` en verde; 32 pruebas, 81.31 % de líneas (gate ≥ 60 %) |

---

## 2. Decisión de alcance según enunciado

El enunciado (`Proyecto_Final_CircleGuard.tex`) define al `file-service` como *«Almacenamiento de certificados y documentos (compatible S3)»* (línea 180) y exige integración continua con reportes publicados para E1.6 (línea 254). También indica que en la Parte 1 este servicio solo se **compila, prueba y empaqueta**, y que su despliegue ocurre en la Parte 2 (línea 260).

Por eso la implementación queda así:

- **Producción/adaptador real:** `S3FileStorageService` usa AWS SDK v2 contra un endpoint S3-compatible (MinIO o AWS S3) cuando `file.storage.mode=s3`. Las credenciales, bucket, región, endpoint y path-style vienen por propiedades/entorno (`aws.s3.*`).
- **Compatibilidad P1:** el modo por defecto sigue siendo `local` (`FileStorageService`) para no forzar infraestructura S3 en la Parte 1. El perfil de integración activa `s3`.
- **Despliegue:** no se despliega el servicio en P1; esa responsabilidad sigue siendo Parte 2. La HU deja listo el contrato de almacenamiento y el borrado para ese despliegue.
- **Observabilidad conservada de `main`:** el merge final `21c406b` mantuvo el contador `circleguard_file_uploads_total{result="success"|"failure"}` (`MeterRegistry` en `FileUploadController`) y el bloque `management` de `application.yml` (Prometheus, health probes, métricas por tag y trazas OTLP), junto con `file.storage.mode` y `aws.s3.*` de esta rama.
- **Convención JaCoCo de `main` (#158):** el merge conservó `apply(from = "$rootDir/gradle/jacoco-convention.gradle")` junto al bloque local; por eso el gate del 60 % y la verificación de pruebas no omitidas aplican también a `file-service`.

---

## 3. Configuración implementada

### 3.1 Código de producción

- `FileStorage` (puerto): `saveFile`, `loadFile`, `deleteFile` y utilidades de nombre seguro.
- `FileStorageService` (modo `local`, `matchIfMissing=true`): mantiene el comportamiento filesystem existente y ahora soporta borrado con validación de traversal.
- `S3FileStorageService` (modo `s3`): `S3Client` real con `endpointOverride`, credenciales estáticas, región y `pathStyleAccessEnabled`. PUT/GET/DELETE/HEAD contra el bucket configurado; 404 de S3 se traduce a `null/false`.
- `FileUploadController`: usa `FileStorage`, conserva upload/download y agrega `DELETE /api/v1/files/{filename}` con 204/404.
- `application.yml`: `file.storage.mode=${FILE_STORAGE_MODE:local}` y `file.storage.path=${FILE_STORAGE_PATH:/app/uploads}` de esta rama, junto con el bloque `management` de `main` (Prometheus, health probes, métricas y trazas OTLP) y `aws.s3.*` desde entorno.

### 3.2 Dependencias y tarea Gradle

`services/circleguard-file-service/build.gradle.kts`:

- `implementation("software.amazon.awssdk:s3:2.29.52")` — dependencia de producción del adaptador S3.
- `testImplementation("org.testcontainers:junit-jupiter:1.21.4")` — servidor efímero para la prueba.
- `integrationTest`: `includeTags("integration")`, `shouldRunAfter(test)`, `timeout.set(Duration.ofMinutes(3))`, `outputs.upToDateWhen { false }`, XML + HTML habilitados. `test` excluye `integration`.

```bash
./gradlew :services:circleguard-file-service:integrationTest --no-daemon --no-build-cache --rerun-tasks --console=plain
```

### 3.3 Clase base y perfil de prueba

`src/test/java/com/circleguard/file/integration/AbstractMinioIntegrationTest.java`:

- Contenedor estático `GenericContainer` con imagen fijada, credenciales de prueba, puerto 9000, arranque hasta 60 s y espera `GET /minio/health/live → 200`.
- `@DynamicPropertySource` inyecta endpoint/puerto aleatorio real, credenciales, bucket, región, path-style y `file.storage.mode=s3`.
- `@BeforeAll` construye el `S3Client` de soporte y crea `circleguard-files`.
- `@AfterEach` vacía el bucket; `@AfterAll` escribe `build/integration-test/minio-container.log`.

`MinioStorageIntegrationTest` usa `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@Testcontainers`, `@Tag("integration")`, `@ActiveProfiles("test")`. Sube/descarga/borra por HTTP con `TestRestTemplate`, verifica que el bean activo sea `S3FileStorageService` y comprueba el 404 S3 subyacente para el caso de objeto ausente.

`src/test/resources/application-test.yml` activa `file.storage.mode=s3` y define defaults de prueba; `docker-java.properties` fija `api.version=1.44`.

### 3.4 Patrones aplicados (E2.4)

| Patrón | Implementación | Trade-off y evidencia |
|---|---|---|
| **Port/Adapter (Hexagonal)** | `FileStorage` es el puerto; `FileStorageService` (local) y `S3FileStorageService` (S3) son adaptadores; `FileUploadController` depende solo del puerto. | Permite cambiar el backend sin tocar la capa HTTP, a costa de una interfaz y dos implementaciones. Evidencia: `@DynamicPropertySource` fija `file.storage.mode=s3` y el test verifica que el bean activo es `S3FileStorageService`. |
| **Feature Toggle / External Configuration** | `file.storage.mode=${FILE_STORAGE_MODE:local}` mantiene `local` por defecto en P1 y activa S3 por entorno o perfil de prueba. | Evita exigir infraestructura S3 en P1 y reutiliza el mismo artefacto en P2; el riesgo de deriva de configuración se mitiga con `application-test.yml` y propiedades `aws.s3.*` externalizadas (sin secretos en el repo). |

---

## 4. Desviación documentada: imagen del servidor

La imagen oficial `minio/minio` **ya no se puede obtener públicamente** (retiro de Docker Hub, septiembre de 2026; `docker pull minio/minio:...` responde `pull access denied`). Se fijó `bitnamilegacy/minio:2025.4.22`, descargable y con la misma API S3 en puerto 9000. El log archivado confirma `MinIO Version: DEVELOPMENT.2025-04-22T22-12-26Z`.

Impacto: solo afecta la imagen del contenedor de prueba; el adaptador de producción es S3 real (AWS SDK v2) y puede apuntar a MinIO u otro S3 compatible por configuración. Antes de Parte 2 se recomienda publicar la imagen elegida en Nexus con digest fijo.

---

## 5. Casos cubiertos

| Caso | Acción | Aserción |
|---|---|---|
| `createsBucketAutomatically` | Arranque del contenedor + lista buckets + inspección del contexto | Bucket `circleguard-files` existe; endpoint/propiedades dinámicas apuntan al contenedor; bean activo es `S3FileStorageService`; path-style activo |
| `uploadsObject` | `POST /api/v1/files/upload` | Respuesta 200 con key generada; `headObject` confirma existencia en MinIO |
| `downloadsObject` | Upload + `GET /api/v1/files/download/{filename}` | 200 y bytes idénticos a los subidos |
| `deletesObject` | Upload + `DELETE /api/v1/files/{filename}` | 204; `headObject`/existencia pasa a 404/ausente |
| `missingObjectReturnsNotFound` | HEAD/GET S3 y HTTP download/delete de key inexistente | S3 lanza `S3Exception` 404; controller responde 404 en download y delete |

Pruebas unitarias agregadas/actualizadas: `S3FileStorageServiceTest` (mock de `S3Client`), `FileUploadControllerTest` (DELETE 204/404), `FileStorageServiceTest` (delete local y traversal).

---

## 6. Ejecución y evidencia

Tres corridas consecutivas sobre el merge final `21c406b`, en el mismo workspace y sin caché de resultados (`--no-build-cache --rerun-tasks`, además de `outputs.upToDateWhen { false }`):

| Corrida | Resultado | Tiempo total | XML JUnit |
|---|---|---|---|
| 1 | `BUILD SUCCESSFUL` | 53 s | - |
| 2 | `BUILD SUCCESSFUL` | 52 s | - |
| 3 | `BUILD SUCCESSFUL` | 52 s | `tests="5" skipped="0" failures="0" errors="0" time="3.556"` |

XML final con timestamp `2026-11-22T21:34:33.769Z`. `test` + `jacocoTestReport` + `jacocoTestCoverageVerification` también terminaron en verde (46 s): 32 pruebas unitarias, 0 omitidas, y **81.31 % de cobertura de líneas** (87 cubiertas / 107 totales), por encima del gate del 60 %. Los logs completos de consola quedan como artefactos locales (`*.log` ignorado por `.gitignore`); el resumen versionado está en `3-runs.txt`.

Archivos en `docs/informe/evidencia/HU-FILE-04/`:

- `3-runs.txt` — resumen de tiempos, XML y limpieza.
- `integrationTest-run1.log`, `integrationTest-run2.log`, `integrationTest-run3.log` — salida completa de Gradle.
- `junit-integrationTest.xml` — reporte JUnit final.
- `minio-container.log.txt` — log real del servidor efímero.
- `unit-and-coverage.log` — gate unitario y de cobertura.
- `jenkins-stage-config.txt` — configuración verificable de la etapa Jenkins.

---

## 7. Etapa de integración en Jenkins

El `Jenkinsfile` ya tenía `stage('Integration Tests')` (línea 806), que ejecuta `ci.integrationTest(service)` para servicios con `service.integration == true`. `jenkins/vars/ci.groovy` lanza la tarea Gradle, publica JUnit con `allowEmptyResults: false` y archiva `build/integration-test/*.log`.

Cambios complementarios:

1. `jenkins/vars/ciServices.groovy`: `integration: true` para `file-service`.
2. `Jenkinsfile.enrichServices()`: override local `if (service.short == 'file') { service.integration = true }`, necesario porque la librería está fijada a `@Library('circleguard@main')`.

> La evidencia de Jenkins se entrega como configuración verificable en texto. La captura de la etapa ejecutándose en Jenkins on-prem queda pendiente de ejecutar el job sobre esta rama.

Nota sobre los merges: el bloque `// TEMPORARY: the library is pinned to main ... REMOVE after merging to main` **no se eliminó en esta HU**; provino de `main` y sigue vigente allí (Jenkinsfile líneas 136-139 tras `af451ff`). El fix de selección selectiva (`6c20d8b`) ya está en `main`, por lo que ese bloque y el override local de `enrichServices()` (líneas 102-106) son redundantes; se propone limpiarlos en un PR aparte para no revalidar esta HU. El segundo merge (`21c406b`, con `af451ff`) resolvió el conflicto en `FileStorageServiceTest.java` conservando los 3 tests de delete/traversal de esta rama y los 9 tests nuevos de `main`.

---

## 8. Reproducibilidad

```bash
# Prueba de integración real contra MinIO efímero (requiere Docker)
./gradlew :services:circleguard-file-service:integrationTest --no-daemon --no-build-cache --rerun-tasks --console=plain

# Solo unitarias + cobertura
./gradlew :services:circleguard-file-service:test \
         :services:circleguard-file-service:jacocoTestCoverageVerification \
         --no-daemon --console=plain

# En WSL con el repo en /mnt/c (9p), si el classpath es lento:
./gradlew -I gradle/wsl-ext4.init.gradle.kts \
  :services:circleguard-file-service:integrationTest
```

---

## 9. Seguimiento para la Parte 2

- Desplegar el `file-service` con `FILE_STORAGE_MODE=s3` y credenciales/endpoints reales (MinIO/S3 del ambiente).
- Publicar la imagen del servidor en el registro interno con digest fijo y reemplazar `bitnamilegacy` si se adopta otra distribución soportada.
- Ejecutar la etapa en Jenkins on-prem y adjuntar la captura.
- Incluir el servicio en el smoke/E2E de Parte 2 con almacenamiento S3 real.

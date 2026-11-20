<!-- CG-DOC: Evidencia de pruebas unitarias y cobertura para FS-CI-01 (file-service). -->
# FS-CI-01 — Pruebas unitarias del file-service (Entregable E1.6)

## Descripción

Este documento evidencia la implementación de pruebas unitarias para el servicio de almacenamiento de archivos (`circleguard-file-service`, puerto 8085), cumpliendo con los criterios de la HU **FS-CI-01** del Entregable **E1.6**.

## Criterios de aceptación

- **Cobertura de líneas JaCoCo ≥ 60 %** (meta del equipo)
- **Casos de prueba**: carga válida, archivo vacío, descarga existente, descarga inexistente
- **Sin cambios de producción innecesarios**; bugs registrados y corregidos con arreglo mínimo
- **Pruebas ejecutables en local y en pipeline**

## Estado previo (bug detectado)

El servicio original (`FileStorageService`) tenía un método `loadFile(String)` que devolvía `null` (stub) y una interfaz `Resource` package-private. El controlador solo exponía `POST /api/v1/files/upload`. No existía endpoint de descarga, por lo que los criterios *descarga existente* y *descarga inexistente* eran imposibles de probar.

## Arreglo mínimo aplicado

Se implementó lo estrictamente necesario para cubrir la funcionalidad faltante:

| Archivo | Cambio |
|---------|--------|
| `FileStorageService.java` | `loadFile` real: valida ruta (path traversal), devuelve `org.springframework.core.io.PathResource` si existe y es legible, `null` si no. Eliminado `interface Resource {}` local. |
| `FileUploadController.java` | Añadido `GET /api/v1/files/download/{filename}` → 200 + bytes si existe, 404 si no. |
| `FileStorageServiceTest.java` | 6 pruebas unitarias de servicio. |
| `FileUploadControllerTest.java` | 4 pruebas unitarias de controlador (extiende 1 existente + 3 nuevas). |

## Resultados de pruebas

### Cobertura JaCoCo (línea 79.31 % — ✅ cumple umbral 60 %)

| Métrica | Cubiertas / Total | Cobertura |
|---------|-------------------|-----------|
| **Instrucciones** | 102 / 119 | **85.71 %** |
| **Ramas** | 8 / 10 | **80.00 %** |
| **Líneas** | **23 / 29** | **79.31 %** |
| Complejidad | 8 / 10 | 80.00 % |
| Métodos | 5 / 5 | 100.00 % |
| Clases | 2 / 2 | 100.00 % |

*Generado: `./gradlew :services:circleguard-file-service:jacocoTestReport`*

### Suites de pruebas JUnit

| Suite | Tests | Fallos | Errores | Tiempo |
|-------|-------|--------|---------|--------|
| `FileUploadControllerTest` | 4 | 0 | 0 | ~1.1 s |
| `FileStorageServiceTest` | 6 | 0 | 0 | ~0.1 s |

**Total: 10 pruebas, 0 fallos, 0 errores**

### Detalle por criterio de la HU

| Criterio FS-CI-01 | Test | Archivo |
|-------------------|------|---------|
| Carga válida | `shouldSaveValidFile()` | `FileStorageServiceTest` |
| Carga válida (controlador) | `shouldUploadFileSuccessfully()` | `FileUploadControllerTest` |
| Archivo vacío | `shouldSaveEmptyFile()` | `FileStorageServiceTest` |
| Archivo vacío (controlador) | `shouldUploadEmptyFileSuccessfully()` | `FileUploadControllerTest` |
| Descarga existente | `shouldLoadExistingFile()` | `FileStorageServiceTest` |
| Descarga existente (controlador) | `shouldDownloadExistingFile()` | `FileUploadControllerTest` |
| Descarga inexistente | `shouldReturnNullForNonExistingFile()` | `FileStorageServiceTest` |
| Descarga inexistente (controlador) | `shouldReturnNotFoundWhenFileDoesNotExist()` | `FileUploadControllerTest` |
| Defensa adicional | `shouldReturnNullForPathTraversal()` | `FileStorageServiceTest` |
| Descarga archivo vacío | `loadFileOnEmptyFileReturnsResource()` | `FileStorageServiceTest` |

## Evidencia adjunta

Todos los artefactos están versionados en el repositorio bajo `docs/informe/evidencia/fs-ci-01/`:

| Artefacto | Ruta |
|-----------|------|
| **JUnit XML (controller)** | `docs/informe/evidencia/fs-ci-01/junit/TEST-com.circleguard.file.controller.FileUploadControllerTest.xml` |
| **JUnit XML (service)** | `docs/informe/evidencia/fs-ci-01/junit/TEST-com.circleguard.file.service.FileStorageServiceTest.xml` |
| **JaCoCo XML** | `docs/informe/evidencia/fs-ci-01/jacoco/jacocoTestReport.xml` |
| **JaCoCo HTML** | `docs/informe/evidencia/fs-ci-01/jacoco/html/index.html` |

> Verificar cobertura: abrir `docs/informe/evidencia/fs-ci-01/jacoco/html/index.html` en navegador.

## Pipeline CI/CD

El catálogo Jenkins `jenkins/vars/ciServices.groovy` fue actualizado para el file-service:

```groovy
short         : 'file',
name          : 'circleguard-file-service',
path          : ':services:circleguard-file-service',
dir           : 'services/circleguard-file-service',
projectKey    : 'circleguard-file-service',
display       : 'CircleGuard File',
sonarSettings : 'services/circleguard-file-service/sonar-project.properties',
jacoco        : true,         // ← habilita check con umbral
integration   : false,
...
```

Esto hace que la etapa **Unit Tests + JaCoCo** ejecute `./gradlew :services:circleguard-file-service:check` (incluye `jacocoTestCoverageVerification` con umbral 0.60) y la etapa **SonarQube + Quality Gate** analice el servicio.

## Checklist de cumplimiento

- [x] Cobertura de líneas JaCoCo ≥ 60 % → **79.31 %**
- [x] Prueba: carga válida
- [x] Prueba: archivo vacío
- [x] Prueba: descarga existente
- [x] Prueba: descarga inexistente
- [x] Arreglo mínimo documentado
- [x] Pruebas corren en local (`./gradlew test`)
- [x] Pruebas corren en pipeline (`jacoco: true` en catálogo)
- [x] Reporte JUnit XML generado
- [x] Reporte JaCoCo XML + HTML generado
- [x] Evidencia archivada en `docs/informe/evidencia/fs-ci-01/`

## Referencias

- [Gateway-Cobertura.md](docs/informe/Gateway-Cobertura.md) — Estructura de informe similar
- [CHANGELOG.md](../CHANGELOG.md#unreleased) — Registro de cambios
- [JaCoCo 0.8.12](https://www.jacoco.org/jacoco/trunk/doc/) — Herramienta de cobertura

---

**Equipo CircleGuard** — 2026-11-18
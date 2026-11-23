<!-- CG-DOC: Índice de los reportes de cobertura versionados como evidencia de la Parte 2. -->
# Reportes de cobertura versionados

Este índice recoge únicamente artefactos presentes en el repositorio. No sustituye la ejecución del pipeline ni los informes publicados por Jenkins.

| Servicio | Evidencia versionada | Formato |
|---|---|---|
| Auth | `13-jacoco-auth-coverage.png` | Captura del reporte JaCoCo |
| Identity | `14-jacoco-identity-coverage.png` | Captura del reporte JaCoCo |
| Gateway | `gateway-jacoco.png` | Captura del reporte JaCoCo |
| Form | `jacoco-form-test-report.xml` | Reporte XML generado por `./gradlew :services:circleguard-form-service:test jacocoTestReport` |
| Todos | `10-sonarqube-quality-gate.png`, `10-sonarqube-quality-gate.jpg`, `16-sonarqube-9of9-projects-passed.png` | Capturas de Quality Gate y del tablero 9/9 de SonarQube |

Los reportes HTML completos de los demás servicios se generan en `services/<servicio>/build/reports/jacoco/` y no están versionados porque `build/` es un directorio ignorado por `.gitignore`. Para reproducirlos, ejecutar desde la raíz:

```bash
./gradlew :services:circleguard-form-service:test jacocoTestReport --no-daemon
```

No hay artefactos versionados del log del Quality Gate ni de las notificaciones de fallo del pipeline; ésos quedan registrados como evidencia pendiente en el informe final.

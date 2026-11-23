<!-- CG-DOC: Índice de los reportes de cobertura versionados como evidencia de la Parte 2. -->
# Reportes de cobertura versionados

Este índice recoge únicamente artefactos presentes en el repositorio. No sustituye la ejecución del pipeline ni los informes publicados por Jenkins.

| Servicio | Evidencia versionada | Formato |
|---|---|---|
| Auth | `13-jacoco-auth-coverage.png` | Captura del reporte JaCoCo |
| Identity | `14-jacoco-identity-coverage.png` | Captura del reporte JaCoCo |
| Gateway | `gateway-jacoco.png` | Captura del reporte JaCoCo |
| Form | `jacoco-form-test-report.xml` | Reporte XML generado por `./gradlew :services:circleguard-form-service:test jacocoTestReport` |
| Promotion | `jacoco-promotion-test-report.xml` | Reporte XML generado por `./gradlew :services:circleguard-promotion-service:test jacocoTestReport` (564/733 líneas = 76,9 %, sesión `fedora-daniela-96530654`) |
| Todos | `10-sonarqube-quality-gate.png`, `10-sonarqube-quality-gate.jpg`, `16-sonarqube-9of9-projects-passed.png` | Capturas de Quality Gate y del tablero 9/9 de SonarQube |

Los reportes HTML completos se generan en `services/<servicio>/build/reports/jacoco/`; los XML de form y promotion conservan la medición exacta dentro del repositorio. Para reproducirlos, ejecutar desde la raíz:

```bash
./gradlew :services:circleguard-form-service:test jacocoTestReport --no-daemon
./gradlew :services:circleguard-promotion-service:test jacocoTestReport --no-daemon
```

Las capturas `10-sonarqube-quality-gate.png` y `16-sonarqube-9of9-projects-passed.png` documentan el resultado del Quality Gate y el tablero 9/9 dentro del repositorio.

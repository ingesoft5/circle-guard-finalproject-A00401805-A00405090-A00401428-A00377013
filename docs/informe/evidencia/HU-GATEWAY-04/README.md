# Evidencia — HU-GATEWAY-04

Esta carpeta contiene las capturas reales del pipeline del gateway. **No se deben
incluir imágenes simuladas, recortadas o editadas.** Las capturas se toman después
de ejecutar el pipeline en el entorno de herramientas.

## Estado

| Evidencia | Archivo | Estado |
|---|---|---|
| URL del build de Jenkins | `01-jenkins-build.png` | Generada (`main #8`) |
| Reporte JaCoCo ≥ 60 % | `02-jacoco-report.png` | Generada (95 % de líneas) |
| Dashboard de Sonar con gate verde | `03-sonar-dashboard.png` | Generada (`Passed`, 88.7 %) |
| Configuración del webhook | `04-github-webhook-config.png` | Generada desde API real de GitHub |
| Entrega reciente del webhook (200) | `05-github-webhook-delivery.png` | Generada desde API real (push 200) |
| Protección de `main` | `06-branch-protection.png` | Generada desde API real de GitHub |
| Artefactos del build | `07-jenkins-artifacts.png` | Generada |
| Duración del build | `08-build-duration.png` | Generada (7 min 38 s; build+pruebas 4 min 20.5 s) |
| Notificación de fallo en GitHub | `09-failure-notification-github.png` | Generada (PR temporal #24) |
| Notificación de fallo en Slack | `10-failure-notification-slack.png` | No disponible: Slack sin configurar |
| Quality gate en rojo | `11-quality-gate-red.png` | Generada (gate temporal) |
| Ejecución selectiva por servicio | `12-selective-service.png` | Generada (`main #12`, solo gateway) |
| Ejecución de todos los servicios | `13-selective-all.png` | Generada (`main #8`, gateway/auth/form) |
| JUnit | `14-junit-report.png` | Generada (78 pruebas, 0 fallos) |
| Artefactos por servicio | `15-artifacts-services.png` | Generada |
| Enlaces a Sonar | `16-sonar-links.png` | Generada |
| Sonar Overall Code | `17-sonar-overall.png` | Generada |
| Digest de imagen | `18-main-image-digest.png` / `image-digest.txt` | Generada |
| Consola de publicación | `19-main-console-publish.png` | Generada |
| Enlace a Sonar (texto) | `sonar-links.txt` | Generado desde artefactos |

## Estándares de captura

- Ventana completa del navegador, con la barra de URL visible.
- En Jenkins: número de build, rama, commit y causa del build visibles.
- En SonarQube: nombre y clave del proyecto, quality gate y fecha del análisis.
- En GitHub: URL del repositorio, configuración del webhook y entrega reciente.
- Redactar tokens, contraseñas y secretos; no ocultar el resultado del gate.
- Guardar en PNG original, sin recortes que alteren el significado.

## Metadatos

Completar en `00-readme.md`:

- Fecha:
- Autor:
- Commit SHA:
- URL del build:
- URL de Sonar:
- Entorno (IPs):
- Navegador:

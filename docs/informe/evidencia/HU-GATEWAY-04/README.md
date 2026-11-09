# Evidencia — HU-GATEWAY-04

Esta carpeta contiene las capturas reales del pipeline del gateway. **No se deben
incluir imágenes simuladas, recortadas o editadas.** Las capturas se toman después
de ejecutar el pipeline en el entorno de herramientas.

## Estado

| Evidencia | Archivo | Estado |
|---|---|---|
| URL del build de Jenkins | `01-jenkins-build.png` | Pendiente de acceso al entorno |
| Reporte JaCoCo ≥ 60 % | `02-jacoco-report.png` | Pendiente de build remoto |
| Dashboard de Sonar con gate verde | `03-sonar-dashboard.png` | Pendiente de build remoto |
| Configuración del webhook | `04-github-webhook-config.png` | Pendiente de acceso a GitHub |
| Entrega reciente del webhook (200) | `05-github-webhook-delivery.png` | Pendiente de entrega real |
| Protección de `main` | `06-branch-protection.png` | Pendiente de acceso a GitHub |
| Artefactos del build | `07-jenkins-artifacts.png` | Pendiente de build remoto |
| Duración del build | `08-build-duration.png` | Pendiente de build remoto |
| Notificación de fallo en GitHub | `09-failure-notification-github.png` | Pendiente de prueba negativa |
| Notificación de fallo en Slack | `10-failure-notification-slack.png` | Pendiente de prueba negativa |
| Quality gate en rojo | `11-quality-gate-red.png` | Pendiente de prueba negativa |
| Ejecución selectiva `auto` | `12-selective-auto.png` | Pendiente de build remoto |
| Ejecución selectiva `all` | `13-selective-all.png` | Pendiente de build remoto |
| Digest de imagen | `image-digest.txt` | Pendiente de publicación |
| Enlace a Sonar | `sonar-links.txt` | Pendiente de build remoto |

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

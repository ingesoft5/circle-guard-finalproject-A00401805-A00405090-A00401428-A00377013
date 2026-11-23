# Metadatos de la evidencia — HU-GATEWAY-04

| Campo | Valor |
|---|---|
| Fecha | 2026-11-08 |
| Autor | Daniel Castaño Moreno (`danielacastanomoreno`) |
| Commit SHA | `c3ac704` (squash merge de PR #23 en `main`) |
| URL del build | http://77.112.223.174:8080/job/circleguard-gateway/job/main/8/ |
| URL de SonarQube | http://77.112.223.174:9000/dashboard?id=circleguard-gateway-service |
| Entorno | AWS `cg-tools`; pública `77.112.223.174`, privada `172.31.20.46` |
| Navegador | Chromium headless vía Playwright |

## Verificación

- [x] Las capturas corresponden al commit indicado.
- [x] La URL del build abre el mismo build de las capturas.
- [x] El reporte JaCoCo muestra cobertura de líneas ≥ 60 % (95 % agregado).
- [x] El quality gate de Sonar está en verde (`Passed`, 88.7 % de cobertura global).
- [x] El webhook tiene entregas recientes con respuesta 200 y el build `main #8` fue causado por `Push event to branch main`.
- [x] `main` exige PR, aprobación y el check `continuous-integration/jenkins/pr-merge`.
- [x] No hay secretos visibles en las imágenes.

## Notas

- Las capturas `04`, `05` y `06` son renders de respuestas reales de la API de GitHub porque el entorno no dispone de una sesión web de GitHub. Los JSON originales están en la misma carpeta.
- Slack no está configurado en el entorno (`SLACK_WEBHOOK_URL` vacío); por eso no existe `10-failure-notification-slack.png`. La implementación envía a Slack cuando la variable está definida.
- `image-digest.txt` y `sonar-links.txt` se descargaron directamente de los artefactos del build `main #8`.

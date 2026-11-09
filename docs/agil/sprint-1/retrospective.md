# Sprint 1 — Retrospectiva

**Sprint:** DCG Sprint 1
**Periodo:** 3–8 de noviembre de 2026
**Participantes:** Daniela Castaño Moreno, Santiago Alejandro Estrada, Faiber Piedrahita Perlaza y Alejandro Quiñones Caicedo

## Qué salió bien

- La integración de los servicios núcleo en una sola rama permitió resolver en conjunto
  los conflictos de compose y del catálogo de Jenkins.
- El pipeline ya publica las ocho imágenes y despliega los cinco servicios núcleo más el
  cliente web, en lugar de limitarse al gateway.
- Los Dockerfiles multi-stage con usuario no root y HEALTHCHECK quedaron homogéneos.
- Testcontainers y Playwright cubren integración y los tres flujos E2E exigidos.
- La generación de release notes desde Conventional Commits quedó dentro del pipeline.

## Qué se puede mejorar

- Las ramas de cada servicio evolucionaron en paralelo y generaron conflictos en
  `ciServices.groovy`, compose y `deploy.sh`; conviene integrar antes y con PR pequeños.
- La evidencia visual (Jira/GitHub) se dejó para el final; debe capturarse al cerrar cada
  historia.
- El token de GitHub y los archivos `tools.env`/`.env` pueden quedar desincronizados si
  se rota la credencial sin ejecutar el actualizador.
- El informe E1.2 quedó desactualizado respecto al pipeline real.

## Acciones

| Acción | Responsable | Fecha | Estado |
|---|---|---|---|
| Sincronizar credenciales con `update-credentials.sh` y recrear Jenkins | DC | 9 nov 2026 | Hecho en código; ejecución pendiente en el host |
| Integrar PR de parte 1 y resolver conflictos de compose/CI | Equipo | 9 nov 2026 | En curso |
| Capturar evidencia de Jira y GitHub al cerrar cada HU | Equipo | Antes de la entrega | Pendiente |
| Actualizar E1.2 y README con el estado real | DC | 9 nov 2026 | En curso |
| Mantener los PR pequeños y con checks verdes | Equipo | Permanente | En curso |

## Métricas

| Indicador | Valor |
|---|---|
| Velocidad | 7 ítems cerrados + 3 en revisión |
| Carryover | 3 ítems |
| Bloqueos | Conflictos de merge y evidencia pendiente |

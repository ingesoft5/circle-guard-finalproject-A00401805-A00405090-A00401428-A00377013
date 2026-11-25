<!-- CG-DOC: Lista de verificación de la evidencia de CI/CD requerida para la entrega del gateway. -->
# Evidencia de CI/CD

Este directorio reúne las capturas y las salidas de comandos de la evidencia de CI/CD. Cada
elemento enlaza con la captura o el registro que lo respalda.

- [x] Pipeline del gateway con una compilación en verde — [`07-jenkins-main-pipeline.png`](../../informe/evidencia/trazabilidad/07-jenkins-main-pipeline.png) y [`08-jenkins-stage-view.jpg`](../../informe/evidencia/trazabilidad/08-jenkins-stage-view.jpg).
- [x] Compuerta de calidad de SonarQube aprobada — [`09-sonarqube-projects-passed.png`](../../informe/evidencia/trazabilidad/09-sonarqube-projects-passed.png) y [`10-sonarqube-quality-gate.jpg`](../../informe/evidencia/trazabilidad/10-sonarqube-quality-gate.jpg).
- [x] Nexus con el JAR y la imagen en `circleguard-docker` — [`11-nexus-repositories.png`](../../informe/evidencia/trazabilidad/11-nexus-repositories.png); las etiquetas también se listan en [`../../arquitectura/despliegue-auth-service.md`](../../arquitectura/despliegue-auth-service.md).
- [x] Estados de los contenedores en `cg-tools` y `cg-envs` — descritos en [`../../arquitectura/despliegue-auth-service.md`](../../arquitectura/despliegue-auth-service.md).
- [x] Comprobaciones de salud de `dev`, `stage` y `prod` — captura del ingreso de `dev` ([`12-web-dev-ingress.png`](../../informe/evidencia/trazabilidad/12-web-dev-ingress.png)); `stage`/`prod` documentados en [`../runbook.md`](../runbook.md).
- [x] Rollback ejecutado mediante `ROLLBACK_TAG` en la plataforma on-prem actual — procedimiento en [`../rollback.md`](../rollback.md); registro en [`../../agil/sprint-2/retrospective.md`](../../agil/sprint-2/retrospective.md).
- [x] URLs y usuario de solo lectura para el docente — usuario `professor` de solo lectura en [`../ci-cd-setup.md`](../ci-cd-setup.md); URLs de acceso en [`../runbook.md`](../runbook.md).

Los archivos de evidencia no deben contener contraseñas ni claves privadas.

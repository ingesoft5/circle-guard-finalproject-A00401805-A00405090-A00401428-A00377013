<!-- CG-DOC: Checklist of CI/CD evidence required for the gateway delivery. -->
# CI/CD Evidence

Store screenshots and command outputs in this directory. Every item links to the capture or record that supports it.

- [x] Gateway pipeline with a green build — [`07-jenkins-main-pipeline.png`](../../informe/evidencia/trazabilidad/07-jenkins-main-pipeline.png) and [`08-jenkins-stage-view.jpg`](../../informe/evidencia/trazabilidad/08-jenkins-stage-view.jpg).
- [x] SonarQube quality gate approved — [`09-sonarqube-projects-passed.png`](../../informe/evidencia/trazabilidad/09-sonarqube-projects-passed.png) and [`10-sonarqube-quality-gate.jpg`](../../informe/evidencia/trazabilidad/10-sonarqube-quality-gate.jpg).
- [x] Nexus containing the JAR and the image in `circleguard-docker` — [`11-nexus-repositories.png`](../../informe/evidencia/trazabilidad/11-nexus-repositories.png); tags are also listed in [`../../arquitectura/despliegue-auth-service.md`](../../arquitectura/despliegue-auth-service.md).
- [x] Container states on `cg-tools` and `cg-envs` — described in [`../../arquitectura/despliegue-auth-service.md`](../../arquitectura/despliegue-auth-service.md).
- [x] Health checks for `dev`, `stage`, and `prod` — `dev` ingress capture ([`12-web-dev-ingress.png`](../../informe/evidencia/trazabilidad/12-web-dev-ingress.png)); `stage`/`prod` documented in [`../runbook.md`](../runbook.md).
- [x] Rollback executed through `ROLLBACK_TAG` on the current on-prem platform — procedure in [`../rollback.md`](../rollback.md); record in [`../../agil/sprint-2/retrospective.md`](../../agil/sprint-2/retrospective.md).
- [x] URLs and read-only user for the instructor — `professor` read-only user in [`../ci-cd-setup.md`](../ci-cd-setup.md); access URLs in [`../runbook.md`](../runbook.md).

Evidence files must not contain passwords or private keys.

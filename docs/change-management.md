<!-- CG-DOC: Gestión de cambios y notas de versión — CircleGuard. -->
# Gestión de cambios y notas de versión — CircleGuard

**Alcance:** cambios de código, infraestructura, configuración y despliegue de la Parte 1.
**Objetivo:** que todo cambio tenga solicitud, revisión, aprobación, trazabilidad y una
nota de versión verificable.

## 1. Flujo de cambio

```text
Issue Jira -> rama feat/fix -> PR -> checks Jenkins -> aprobación -> squash merge a main
-> tag semver -> release notes -> despliegue dev -> E2E -> stage -> aprobación -> prod
```

1. La solicitud se registra en Jira con historia, criterios de aceptación y riesgo.
2. La rama se crea desde `main` con el nombre `feat/HU-...`, `fix/...` o `ci/...`.
3. El PR usa la plantilla de `.github/pull_request_template.md` y enlaza la issue.
4. Jenkins ejecuta la compilación, pruebas, Sonar/quality gate, integración y empaquetamiento.
5. Un integrante distinto del autor aprueba; el merge es squash.
6. `main` calcula la versión semver y publica el tag `vX.Y.Z`.
7. El pipeline genera `docs/release-notes/vX.Y.Z.md` desde Conventional Commits.
8. El despliegue sigue la promoción dev → stage → prod con aprobación manual.

## 2. Plantilla de solicitud de cambio

```markdown
### Solicitud de cambio

- **ID Jira:**
- **Tipo:** feature | fix | infra | ci | docs | security
- **Rama:**
- **Servicios afectados:**
- **Descripción del cambio:**
- **Motivación / problema:**
- **Criterios de aceptación:**
- **Riesgo:** bajo | medio | alto
- **Plan de rollback:**
- **Evidencia esperada:**
- **Aprobador requerido:**
```

## 3. Política de aprobación

| Tipo de cambio | Aprobación mínima | Checks obligatorios | Despliegue |
|---|---|---|---|
| Documentación (`docs/`) | 1 integrante | Markdown/lint si aplica | No |
| Funcionalidad o corrección de servicio | 1 integrante distinto del autor | Compilación, unitarias, Sonar, quality gate | dev → stage → prod con aprobación |
| Infraestructura (`infra/`, `deploy/`) | 1 integrante + revisión de DC | Compilación, compose config, prueba de humo | dev → stage → prod con aprobación |
| CI/CD (`Jenkinsfile`, `jenkins/`) | 1 integrante + revisión de DC | Canalización completa en rama | Igual que para una funcionalidad |
| Seguridad o credenciales | 1 integrante + DC | Sin secretos en diff, escaneo | Aprobación explícita |
| Hotfix de producción | 1 integrante + DC | Compilación, pruebas y prueba de humo | prod con `ROLLBACK_TAG` listo |

Reglas adicionales:

- Nadie aprueba su propio PR.
- No se hace push directo a `main`; el hook y el workflow lo bloquean.
- No se usa `--admin` para saltar checks ni se desactiva la protección.
- Los secretos viven en `infra/state/` y `deploy/compose/*/.env`, nunca en Git.

## 4. Etiquetado de releases

| Tipo de commit | Incremento | Ejemplo |
|---|---|---|
| `fix:` | patch | `v1.0.1` |
| `feat:` | minor | `v1.1.0` |
| `feat!:` o `BREAKING CHANGE:` | major | `v2.0.0` |
| Solo `docs:`, `ci:`, `chore:` | patch | `v1.1.1` |

El tag se crea sobre el commit de `main` que pasó el pipeline. Nunca se publica `latest`;
las imágenes usan `${NEXUS_REGISTRY}/circleguard-docker/<servicio>:<semver>`.

## 5. Notas de versión

El stage `Release notes` del `Jenkinsfile` genera un archivo por versión:

```text
docs/release-notes/v1.2.0.md
```

Contenido mínimo:

- Versión y fecha.
- Servicios e imágenes publicadas.
- Cambios agrupados por `feat`, `fix`, `ci`, `docs`, `chore`.
- Migraciones o cambios de configuración.
- Riesgos y plan de rollback.
- Enlace al pipeline y al tag.

La plantilla base está en `docs/release-notes/RELEASE-NOTES.md`. El archivo generado se archiva
como artefacto de la compilación y se conserva en el repositorio cuando el PR lo incluya. El
stage `GitHub Release` publica esa misma nota en la publicación del tag `vX.Y.Z`; si la
publicación ya existe, actualiza el cuerpo en lugar de duplicarla.

## 6. Rollback

1. Identificar el tag estable anterior (`vX.Y.Z`).
2. Ejecutar el pipeline con `ROLLBACK_TAG=<tag>`; no se reconstruye ni se reetiqueta.
3. El stage `Rollback prod` despliega ese tag en producción.
4. Verificar healthchecks y el flujo E2E de acceso.
5. Registrar el incidente y la causa en la retrospectiva.

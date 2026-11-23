<!-- CG-DOC: Estrategia de ramas, convenciones de commit, reglas de pull request y hooks de validación. -->
# Estrategia de ramas y control de cambios — CircleGuard

Este documento define las reglas de ramas, integración y control de cambios del proyecto, para garantizar trazabilidad, integración continua y buenas prácticas DevOps.

## 1. Estrategia elegida: GitHub Flow

El proyecto aplica **GitHub Flow** de forma estricta, manteniendo `main` en estado desplegable (pipeline en verde). **Usamos GitHub Flow por practicidad y eficiencia**: las ramas efímeras y los PR pequeños reducen conflictos y tiempos de aprobación, sin el coste de mantener ramas de larga duración. `dev` es un entorno de despliegue (namespace), **no** una rama de este flujo.

- **`main` protegida:** no se permiten pushes directos. Toda integración requiere un Pull Request.
- **Ramas efímeras:** se crean desde `main` y se mantienen poco tiempo.
- **Auditoría y limpieza:** las ramas se configuran para borrarse automáticamente tras el merge en GitHub. La trazabilidad queda inmutable en la sección **Merged Pull Requests**, con código, aprobaciones y checks del pipeline.
- **Revisión por pares:** se requiere al menos **1 aprobación** de otro desarrollador antes del merge. Política actualizada en el Sprint 2 (ver CHANGELOG): esperar dos revisores era demasiado lento y retrasaba los PR; el check obligatorio `continuous-integration/jenkins/pr-merge` sigue siendo requerido, los pushes invalidan aprobaciones antiguas y el aprobador debe aprobar la revisión más reciente (ruleset `github-actions`).
- **Checks obligatorios:** el pipeline de Jenkins debe ejecutarse y pasar antes del merge.
- **Estrategia de merge:** **Squash and Merge** mantiene un historial atómico y limpio en `main`.

Esta sección es la **especificación canónica del GitHub Flow para el criterio P1.1 (E1.1)**; sustituye al informe eliminado `docs/informe/P1.1-Branching-GitHub-Flow.*`. Las ceremonias Scrum, el tablero Jira y las revisiones de sprint están documentadas en [`docs/agil/AGIL.md`](docs/agil/AGIL.md).

## 2. Nomenclatura de ramas (trazabilidad con Jira)

Las ramas deben seguir esta estructura para conservar la trazabilidad con las historias de Jira:

**Formato:** `[<tipo>/]HU-<MICROSERVICIO>-<número de historia>-<descripción corta>`

- **tipo (opcional):** prefijo de Conventional Commits: `feat/`, `fix/`, `docs/`, `ci/`, `chore/`, `test/`, `refactor/`, `build/`, `perf/`, `style/`.
- **MICROSERVICIO:** nombre del servicio afectado en **mayúsculas** (p. ej. `AUTH`, `GATEWAY`, `FRONTEND`, `INFRA`).
- **número de historia:** ID numérico de la tarea en Jira.
- **descripción corta:** palabras en minúsculas separadas por guiones.

**Ejemplos válidos:**

- `feat/HU-GATEWAY-01-configurar-servidores`
- `fix/HU-AUTH-03-corregir-expiracion-qr`
- `HU-GATEWAY-01-configurar-servidores`

## 3. Convención de commits (Conventional Commits)

El proyecto usa **Conventional Commits** para habilitar versionado semántico y notas de release. Los mensajes de commit locales **no incluyen el ID de Jira**, porque la trazabilidad la aportan el nombre de la rama y el título del PR.

**Estructura:** `<tipo> ( <ámbito> ) : <descripción>`

**Cambio incompatible:** `<tipo> ( <ámbito> ) !: <descripción>`

**Ámbito:** obligatorio en todos los commits.

**Idioma:** todos los mensajes de commit se escriben en **inglés**, aunque la documentación del repositorio esté en español.

**Regex de validación:**

```text
^(feat|fix|docs|ci|chore|test|refactor|build|perf|style)[[:space:]]+\([[:space:]]*[a-z-]+[[:space:]]*\)[[:space:]]+!?:[[:space:]]+.+$
```

**Ejemplos:**

```text
feat ( gateway ) : add secure gate validation
fix ( auth ) : correct QR expiration
feat ( auth ) !: replace role schema
```

**Tipos permitidos e impacto en SemVer:**

- `feat`: nueva funcionalidad (MINOR).
- `fix`: corrección de errores (PATCH).
- `feat!` o `BREAKING CHANGE`: cambio incompatible (MAJOR).
- `chore`, `ci`, `docs`, `test`, `refactor`, `build`, `perf`, `style`: sin incremento de versión.

**Ejemplos válidos:**

- `feat(auth): add JWT token validation`
- `fix(gateway): correct QR timestamp`
- `ci(jenkins): add SonarQube analysis stage`
- `chore: initialize folder structure`

## 4. Control de calidad y gestión de cambios en los Pull Requests

En GitHub Flow, el Pull Request es la compuerta de calidad principal. Para evitar merges incompletos:

1. **Plantilla obligatoria:** todos los PR deben usar `.github/pull_request_template.md` (campos: *Branch Name, Context, What changed, How to test it*).
2. **Título del PR (trazabilidad):** el título se escribe en **inglés** y debe incluir la clave de Jira (`DCG-###`); conservar el alias `HU-*` cuando exista. El nombre de la rama puede añadirse como contexto. El workflow `Jira sync` de GitHub Actions rechaza cualquier PR sin un identificador `DCG-\d+`. Con *Squash & Merge*, este título se convierte en el mensaje oficial del commit en `main`.
   - *Ejemplo:* `feat(auth): add login endpoint (HU-AUTH-12, DCG-81)`
3. **Resolución de conversaciones:** `main` exige *"Require conversation resolution before merging"*. Si un revisor pide contexto, el merge requiere la resolución antes de completarse.
4. **Compuerta del pipeline:** ningún PR puede mergearse mientras el pipeline de Jenkins esté fallando.

## 5. Herramientas locales de validación (hooks de Git)

Para evitar errores antes de llegar al remoto, el proyecto incluye hooks compartidos en `.githooks/`:

- `commit-msg`: valida Conventional Commits y mensajes en inglés.
- `pre-push`: bloquea pushes directos a `main` y valida el nombre de la rama contra `[<tipo>/]HU-<MICROSERVICIO>-<número>-<descripción>`.

*(Nota del equipo: activar los hooks una vez por máquina con `git config core.hooksPath .githooks`.)*

### Integración GitHub ↔ Jira

No hay checks ni workflows de Jira en los PR. La validación se deja a Jenkins; si se requiere trazabilidad de Jira, se gestiona por convención (título y plantilla) sin checks obligatorios en GitHub Actions.

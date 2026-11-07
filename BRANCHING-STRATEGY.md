# Estrategia de Branching y Control de Cambios - CircleGuard

Este documento define las reglas de ramificación, integración y control de cambios para el proyecto, asegurando la trazabilidad, la integración continua y las mejores prácticas de DevOps.

## 1. Estrategia Elegida: GitHub Flow
Para este proyecto, aplicamos estrictamente **GitHub Flow**, garantizando que la rama `main` siempre esté en un estado desplegable (pipeline en verde). Las reglas que rigen este flujo son:

*   **Rama `main` protegida:** No se permiten push directos. Toda integración requiere un Pull Request (PR).
*   **Ramas efímeras:** Se crean a partir de `main` y tienen un ciclo de vida corto.
*   **Auditoría y Limpieza:** Las ramas se configuran para **eliminarse automáticamente** tras el merge en GitHub (buena práctica DevOps). Sin embargo, toda la trazabilidad se conserva de forma inmutable en la sección de **Pull Requests Fusionados (Merged)**, donde queda el registro del código, las aprobaciones y los checks del pipeline.
*   **Revisión por pares (Peer Review):** Se requiere mínimo **1 aprobación** de otro desarrollador para habilitar el merge.
*   **Checks obligatorios:** El pipeline de CI (Jenkins) debe ejecutarse y aprobar antes de fusionar.
*   **Estrategia de Fusión:** Se utilizará *Squash and Merge* para mantener un historial atómico y limpio en `main`.

## 2. Nomenclatura de Ramas (Trazabilidad con Jira)
Para mantener un orden estricto y trazabilidad con las Historias de Usuario (HU) gestionadas en Jira, las ramas deben seguir la siguiente estructura:

**Formato:** `HU-<nombre del microservicio>-<número de HU>-<descripción corta de funcionalidad>`

*   **nombre del microservicio:** Nombre del servicio afectado (ej. `auth`, `gateway`, `frontend`, `infra`).
*   **número de HU:** El ID numérico de la tarea en Jira.
*   **descripción corta de funcionalidad:** Breve descripción en minúsculas y separada por guiones.

## 3. Convención de Commits (Conventional Commits)
Usamos el estándar **Conventional Commits** para permitir el versionado semántico (SemVer) automático y la generación de *release notes*. Los mensajes de commit locales **no llevan el ID de Jira**, ya que la trazabilidad se garantiza por el nombre de la rama y el título del PR.

**Estructura:** `<tipo>(<alcance>): <descripción>`

**Tipos permitidos e impacto en SemVer:**
*   `feat`: Nueva funcionalidad (incrementa MINOR).
*   `fix`: Corrección de bug (incrementa PATCH).
*   `feat!` o `BREAKING CHANGE`: Cambio incompatible (incrementa MAJOR).
*   `chore`, `ci`, `docs`, `test`, `refactor`, `build`, `perf`, `style`: No incrementan versión.

**Ejemplos válidos:**
*   `feat(auth): agrega validacion de token JWT`
*   `fix(gateway): corrige timestamp del QR`
*   `ci(jenkins): agrega etapa de analisis con SonarQube`
*   `chore: inicializa estructura de carpetas`

## 4. Control de Calidad y Change Management en Pull Requests
En GitHub Flow, el Pull Request es el corazón de la calidad. Para garantizar que no se fusione código incompleto:
1.  **Plantilla Obligatoria:** Todo PR debe ser creado usando la plantilla `.github/pull_request_template.md` (campos: *Branch Name, Context, What changed, How to test it*).
2.  **Título del PR (Trazabilidad):** El título del PR debe incluir el nombre de la rama (la HU) al final. Gracias al *Squash & Merge*, este título se convierte en el mensaje del commit oficial en `main`.
    *   *Ejemplo:* `feat(auth): agrega endpoint de login (HU-auth-12-implementar-login)`
3.  **Resolución de Conversaciones:** La rama `main` tiene activada la regla *"Require conversation resolution before merging"*. Si un revisor pide contexto, el merge queda bloqueado hasta que se resuelva.
4.  **Portero del Pipeline:** Ningún PR puede fusionarse si el pipeline de Jenkins no está en verde.

## 5. Herramientas de Validación Local (Git Hooks)
Para evitar errores humanos antes de llegar al repositorio remoto, el proyecto incluye hooks locales compartidos en la carpeta `.githooks/`:
*   `commit-msg`: Valida que el mensaje siga el estándar Conventional Commits.
*   `pre-push`: Bloquea intentos de push directo a `main` y advierte si el nombre de la rama no cumple el formato `HU-<nombre del microservicio>-<número de HU>-<descripción corta de funcionalidad>`.

*(Nota para el equipo: Para activar estos hooks en cada máquina local, ejecutar una vez: `git config core.hooksPath .githooks`)*

# CG-DOC: Chaos engineering bonus verification (enunciado L284)

Bonificación de **chaos engineering** del enunciado L284.

Evidencia **real** obtenida en el clúster on-prem `k3d-cg`.

## Alcance

- Chaos control plane (Litmus/Chaos Mesh) desplegado en namespace `cg-chaos`.
- Experimentos de inyección de fallos sobre los servicios críticos de `cg-dev`.

## Hipótesis y steady state

- **Hipótesis**: el sistema mantiene disponibilidad y tolerancia a fallos
  durante perturbaciones transitarias.
- **Steady state**: servicios UP, P99 < 500ms, sin errores 5xx.

## Dictamen

Verificación pendiente de ejecución sobre el clúster live.

**Nota**: los experimentos requieren acceso al clúster on-prem con
credentials de Chaos Engineering configuradas.

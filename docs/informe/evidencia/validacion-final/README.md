# Evidencia de validación final

Esta carpeta reúne las capturas y salidas crudas del cierre del proyecto. Cada archivo se genera desde el clúster o desde Jenkins; no se editan cifras ni se reconstruyen capturas.

## Estructura

- `cluster/`: nodos, pods, servicios, ingress, HPA, secretos y eventos de `cg-dev`, `cg-stage` y `cg-prod`.
- `jenkins/`: consola, stages, JUnit y Playwright de la corrida de `main`.
- `tests/`: resultados de integración y E2E.
- `security/`: reportes de Trivy y ZAP.
- `observability/`: alertas, traza y paneles de Grafana/Loki.

## Reproducción

```bash
./deploy/scripts/capture-evidence.sh docs/informe/evidencia/validacion-final
```

El script solo lee el clúster. No imprime valores de secretos: de `cg-secrets` únicamente registra el nombre del recurso.

## Convención de nombres

`<criterio>-<ambiente>-<recurso>-<timestamp>.<ext>`

Ejemplo: `p2.3-cg-dev-deployments-20261124T031500Z.txt`.

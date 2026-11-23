<!-- CG-DOC: Módulo `registry` (E2.1). -->
# Módulo `registry` (E2.1)

Declara el registro de contenedores del ambiente: PVC persistente, Deployment
de `registry:2` y Service `ClusterIP` en el puerto `5000`.

| Variable | Descripción | Default |
|---|---|---|
| `env` | Identificador del ambiente | — |
| `namespace` | Namespace donde se despliega | — |
| `image` | Imagen del registro | `registry:2` |
| `storage_size` | Tamaño del PVC | `5Gi` |

Salidas: `registry_endpoint`, `pvc_name`.

<!-- CG-DOC: Módulo `compute` (E2.1). -->
# Módulo `compute` (E2.1)

Declara el cómputo reutilizable del ambiente verificando el clúster k3d compartido
`cg` mediante un `null_resource` idempotente. El script
`scripts/verify-cluster.sh` instala el mirror HTTP de
`infra/onpremise/registries.yaml` si falta y valida que el clúster y su
kubeconfig estén disponibles. **No crea clústeres**: un segundo clúster chocaría
con el puerto `30080` y agotaría `fs.inotify.max_user_instances` en este host.

Los tres ambientes (`dev`, `stage`, `prod`) usan el mismo clúster y se separan
por namespaces, cumpliendo E2.2.

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `cluster_name` | Nombre del clúster k3d compartido | — |
| `agents` | Agentes documentados del clúster (no aplicados) | `1` |
| `k3s_version` | Tag documentado de `rancher/k3s` | `v1.35.5-k3s1` |
| `ingress_port` | Puerto host ya publicado por Traefik | `30080` |

Salida: `cluster_name`.

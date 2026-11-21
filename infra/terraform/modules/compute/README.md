# Módulo `compute` (E2.1)

Declara el clúster k3s/k3d de cómputo de cada ambiente mediante un
`null_resource` idempotente. El script `scripts/ensure-cluster.sh` instala el
mirror HTTP de `infra/onpremise/registries.yaml`, crea el clúster si no existe
y publica el puerto `ingress_port` en el host para Traefik.

| Variable | Descripción | Default |
|---|---|---|
| `cluster_name` | Nombre del clúster k3d | — |
| `agents` | Número de agentes | `1` |
| `k3s_version` | Tag de `rancher/k3s` | `v1.35.5+k3s1` |
| `ingress_port` | Puerto host del load balancer | `30080` |

Salida: `cluster_name`.

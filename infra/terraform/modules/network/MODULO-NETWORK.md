<!-- CG-DOC: Módulo `network` (E2.1). -->
# Módulo `network` (E2.1)

Declara el namespace de cada ambiente, su `ResourceQuota` y una `NetworkPolicy`
default-deny. Permite tráfico entrante desde el mismo namespace y desde los
namespaces declarados en `ingress_namespaces` (Traefik vive en `kube-system`),
y tráfico saliente hacia el mismo namespace y los CIDR de `egress_cidrs`
(registro HTTP del host y DNS del clúster).

| Variable | Descripción | Default |
|---|---|---|
| `env` | Identificador del ambiente | — |
| `namespace` | Namespace Kubernetes del ambiente | — |
| `max_pods` | Límite de pods | `100` |
| `cpu_request` | Cupo de CPU requests | `4` |
| `memory_request` | Cupo de memoria requests | `8Gi` |
| `ingress_namespaces` | Namespaces autorizados a hacer ingress | `["kube-system"]` |
| `egress_cidrs` | CIDR autorizados para egress | `10.0.0.0/8`, `10.147.20.67/32` |

Salidas: `namespace`, `resource_quota`, `network_policy`.

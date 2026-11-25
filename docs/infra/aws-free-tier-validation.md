<!-- CG-DOC: Dimensionamiento, exposición y validación de costos de una semana de los cuatro hosts de AWS. -->
# Validación de recursos de AWS - CircleGuard (obsoleto por bloqueo de cuenta AWS, se dejó en el repositorio para mostrar trazabilidad)

## Topología

| Host | Tipo | RAM | Disco | IP pública |
|---|---|---:|---:|---|
| `cg-tools` | `m7i-flex.large` | 8 GB | 40 GB | Elastic IP |
| `cg-dev` | `m7i-flex.large` | 8 GB | 30 GB | efímera |
| `cg-stage` | `m7i-flex.large` | 8 GB | 30 GB | efímera |
| `cg-prod` | `m7i-flex.large` | 8 GB | 30 GB | efímera |

Región: `us-east-2`. Ubuntu 24.04. Arquitectura: x86_64.

## Exposición

| Puerto | Origen | Servicio |
|---:|---|---|
| 22 | `ADMIN_CIDR` y `SG-tools` | SSH |
| 8080 | Internet | Jenkins, autenticado |
| 9000 | `TEACHER_CIDR` y administrador | SonarQube |
| 8081 | `TEACHER_CIDR` y administrador | Interfaz de Nexus |
| 80/443 | Internet | Ingreso de la aplicación |
| 5000 | `SG-envs` | Registro Docker privado |
| 8087 | `SG-tools` | Gateway |
| Middleware | `SG-tools` y administrador | PostgreSQL, Neo4j, Kafka, Redis, LDAP |

## Free Tier

Free Tier cubre `t3.micro` y `t2.micro`, que no pueden ejecutar Jenkins, SonarQube y Nexus
juntos. El crédito de USD 120 es el presupuesto real.

## Costo

```text
4 × m7i-flex.large × 0.096 × 180 h = 69.12
EBS 130 GB gp3 proportional         =  2.56
4 public IPv4 × 0.005 × 180 h       =  3.60
------------------------------------------------
Total 180 h                          = 75.28 USD
```

Estimación mensual 24/7: ~305.32 USD.
USD 120 cubre ~16 días 24/7 o más de un mes a 8 h/día.

## Verificación

```bash
export AWS_REGION=us-east-2
export ADMIN_CIDR="$(curl -s https://checkip.amazonaws.com)/32"
export TEACHER_CIDR=...
./infra/bootstrap/bootstrap-check.sh
```

## Controles de costos

```bash
./infra/bootstrap/bootstrap-stop.sh
./infra/bootstrap/bootstrap-start.sh
./infra/bootstrap/bootstrap-ips.sh
```

Detener las instancias elimina los cargos por cómputo. EBS y la Elastic IP de tools siguen
siendo facturables.

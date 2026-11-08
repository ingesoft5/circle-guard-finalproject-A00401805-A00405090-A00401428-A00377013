<!-- CG-DOC: AWS four-host sizing, exposure, and one-week cost validation. -->
# AWS Resource Validation - CircleGuard

## Topology

| Host | Type | RAM | Disk | Public IP |
|---|---|---:|---:|---|
| `cg-tools` | `m7i-flex.large` | 8 GB | 40 GB | Elastic IP |
| `cg-dev` | `m7i-flex.large` | 8 GB | 30 GB | ephemeral |
| `cg-stage` | `m7i-flex.large` | 8 GB | 30 GB | ephemeral |
| `cg-prod` | `m7i-flex.large` | 8 GB | 30 GB | ephemeral |

Region: `us-east-2`. Ubuntu 24.04. Architecture: x86_64.

## Exposure

| Port | Source | Service |
|---:|---|---|
| 22 | `ADMIN_CIDR` and `SG-tools` | SSH |
| 8080 | Internet | Jenkins, authenticated |
| 9000 | `TEACHER_CIDR` and admin | SonarQube |
| 8081 | `TEACHER_CIDR` and admin | Nexus UI |
| 80/443 | Internet | Application ingress |
| 5000 | `SG-envs` | Private Docker registry |
| 8087 | `SG-tools` | Gateway |
| Middleware | `SG-tools` and admin | PostgreSQL, Neo4j, Kafka, Redis, LDAP |

## Free Tier

Free Tier covers `t3.micro` and `t2.micro`, which cannot run Jenkins, SonarQube, and Nexus together. The USD 120 credit is the real budget.

## Cost

```text
4 × m7i-flex.large × 0.096 × 180 h = 69.12
EBS 130 GB gp3 proportional         =  2.56
4 public IPv4 × 0.005 × 180 h       =  3.60
------------------------------------------------
Total 180 h                          = 75.28 USD
```

Monthly 24/7 estimate: ~305.32 USD.
USD 120 covers ~16 days 24/7 or more than one month at 8 h/day.

## Verification

```bash
export AWS_REGION=us-east-2
export ADMIN_CIDR="$(curl -s https://checkip.amazonaws.com)/32"
export TEACHER_CIDR=...
./infra/bootstrap/bootstrap-check.sh
```

## Cost controls

```bash
./infra/bootstrap/bootstrap-stop.sh
./infra/bootstrap/bootstrap-start.sh
./infra/bootstrap/bootstrap-ips.sh
```

Stopping removes compute charges. EBS and the tools Elastic IP remain billable.

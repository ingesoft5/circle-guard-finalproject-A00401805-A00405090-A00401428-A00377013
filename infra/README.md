<!-- CG-DOC: Four-host AWS bootstrap, private registry, ingress, and cost controls. -->
# Infrastructure

## Hosts

| Host | Type | Disk | Public IP |
|---|---|---:|---|
| `cg-tools` | `m7i-flex.large` | 40 GB | Elastic IP |
| `cg-dev` | `m7i-flex.large` | 30 GB | ephemeral |
| `cg-stage` | `m7i-flex.large` | 30 GB | ephemeral |
| `cg-prod` | `m7i-flex.large` | 30 GB | ephemeral |

## Scripts

| Script | Purpose |
|---|---|
| `bootstrap-check.sh` | Validates AWS, tools, and instance types. |
| `bootstrap-aws.sh` | Creates four hosts, two SGs, one EIP, and the key pair. |
| `bootstrap-tools.sh` | Jenkins, SonarQube, Nexus, registry `5000`, ingress, and read-only access. |
| `bootstrap-envs.sh <env>` | Prepares one environment host. |
| `bootstrap-all.sh` | Runs AWS, tools, and all environments. |
| `bootstrap-start.sh` | Starts the four hosts and refreshes public IPs. |
| `bootstrap-stop.sh` | Stops the four hosts. |
| `bootstrap-ips.sh` | Prints private/public IPs and URLs. |
| `bootstrap-destroy.sh` | Destroys all created resources. |

Compatibility wrappers live in `infra/bootstrap/`.

## Prerequisites

```bash
export AWS_REGION=us-east-2
export ADMIN_CIDR="$(curl -s https://checkip.amazonaws.com)/32"
export TEACHER_CIDR=...
export GITHUB_USER=...
export GITHUB_TOKEN=...
```

## Usage

```bash
./infra/bootstrap/bootstrap-check.sh
./infra/bootstrap/bootstrap-all.sh
```

Per environment:

```bash
REGISTRY_HOST=<TOOLS_PRIVATE_IP>:5000 ./infra/bootstrap/bootstrap-envs.sh dev
```

## State

`infra/state/aws.env` stores IDs and IPs. `infra/state/tools.env` stores tool credentials. Both are ignored by Git.

## Cost

Four `m7i-flex.large` for 180 hours plus 130 GB gp3 and four IPv4 addresses cost approximately **75.28 USD**. See `docs/infra/aws-free-tier-validation.md`.

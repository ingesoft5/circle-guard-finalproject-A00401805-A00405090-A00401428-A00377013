<!-- CG-DOC: Four-host topology and isolated dev, stage, and prod environments. -->
# Dev, Stage, and Prod Environments

## Topology

```text
cg-tools (m7i-flex.large, Elastic IP)
├── Jenkins        :8080
├── SonarQube      :9000
├── Nexus          :8081
├── Registry       :5000 (private)
└── Ingress        :80

cg-dev   (m7i-flex.large, private 10.x.x.x) gateway :8087 + Redis
cg-stage (m7i-flex.large, private 10.x.x.x) gateway :8087 + Redis
cg-prod  (m7i-flex.large, private 10.x.x.x) gateway :8087 + Redis
```

## Network rules

- Jenkins deploys through the private IP of each environment.
- Only `cg-tools` has an Elastic IP.
- Environment public IPs are ephemeral and used for screenshots.
- Port 8080 is public for GitHub webhooks and Jenkins UI.
- Ports 9000 and 8081 are restricted to the teacher and administrator.
- Port 80 is public for the application ingress.
- Port 5000 and middleware ports are private.

## Commands

```bash
# Prepare one environment on its host
REGISTRY_HOST=<TOOLS_PRIVATE_IP>:5000 ./infra/bootstrap/bootstrap-envs.sh dev

# Deploy
COMPOSE_ROOT=/opt/circleguard/deploy/compose ./deploy/scripts/deploy.sh dev <TAG>

# Rollback
COMPOSE_ROOT=/opt/circleguard/deploy/compose ./deploy/scripts/rollback.sh prod <TAG>

# IPs for screenshots
./infra/bootstrap/bootstrap-ips.sh
```

## Isolation

Each environment has its own EC2 instance, Docker network, Redis volume, `.env`, private IP, and gateway container. `SG-envs` is shared, while application isolation is enforced by host and Docker network boundaries.

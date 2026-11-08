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

cg-dev   (m7i-flex.large, private 10.x.x.x)
cg-stage (m7i-flex.large, private 10.x.x.x)
cg-prod  (m7i-flex.large, private 10.x.x.x)
├── gateway    :8087   Redis
└── promotion  :8088   PostgreSQL, Neo4j, Kafka (external listener :19092)
```

Every environment runs the same stack with the same image tag: `gateway` and
`promotion` are pulled from the registry, and the four middleware containers are
pinned to fixed versions in the compose file. Isolation is by host and Docker
network, not by configuration differences.

## Network rules

- Jenkins deploys through the private IP of each environment.
- Only `cg-tools` has an Elastic IP.
- Environment public IPs are ephemeral and used for screenshots.
- Port 8080 is public for GitHub webhooks and Jenkins UI.
- Ports 9000 and 8081 are restricted to the teacher and administrator.
- Port 80 is public for the application ingress.
- Ports 5000, 5432, 7687 and 9092 stay private inside the Docker network.
- Port 19092 is Kafka's external listener: it advertises `ENV_HOST_IP`, so the
  end-to-end suite can publish a questionnaire event from Jenkins.

## Required variables

Each environment reads `deploy/compose/<env>/.env` (mode 600, never committed).
`deploy/compose/<env>/.env.example` lists the keys:

| Key | Purpose |
|---|---|
| `TAG` | Image tag the pipeline deploys |
| `NEXUS_REGISTRY` | Registry that serves the `gateway` and `promotion` images |
| `ENV_HOST_IP` | Address Kafka advertises on its external listener |
| `POSTGRES_PASSWORD` | Password for the promotion database |
| `NEO4J_PASSWORD` | Password for the promotion graph |
| `QR_SECRET`, `JWT_SECRET` | Signing secrets shared with the gateway |

`JWT_SECRET` must be at least 32 bytes: the service refuses to start with a
shorter key (`WeakKeyException` from the JWT library). `bootstrap-envs.sh`
generates 48 hex characters, which is enough.

## Commands

```bash
# Prepare one environment on its host
REGISTRY_HOST=<TOOLS_PRIVATE_IP>:5000 ./infra/bootstrap/bootstrap-envs.sh dev

# Deploy the whole stack
COMPOSE_ROOT=/opt/circleguard/deploy/compose ./deploy/scripts/deploy.sh dev <TAG>

# Rollback
COMPOSE_ROOT=/opt/circleguard/deploy/compose ./deploy/scripts/rollback.sh prod <TAG>

# Seed the visitors an end-to-end run is going to promote
COMPOSE_ROOT=/opt/circleguard/deploy/compose ./deploy/scripts/seed-visitor.sh dev <anonymousId>...

# IPs for screenshots
./infra/bootstrap/bootstrap-ips.sh
```

`deploy.sh` pulls the two application images and waits with
`docker compose up --wait`, so it only reports success when every container with
a healthcheck is healthy. A stack that starts but never becomes healthy fails the
deployment instead of leaving a half-up environment.

## Isolation

Each environment has its own EC2 instance, Docker network, volumes for Redis,
PostgreSQL, Neo4j and Kafka, `.env`, private IP, and application containers.
`SG-envs` is shared, while application isolation is enforced by host and Docker
network boundaries.

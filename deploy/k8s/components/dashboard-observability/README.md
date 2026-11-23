<!-- CG-DOC: Usage notes for the dashboard-service observability Kustomize components. -->
# Dashboard observability components

These Kustomize components extend the shared Prometheus, Alertmanager, Grafana and Loki stack already present in `deploy/k8s/monitoring`. They do not create another monitoring server, log collector or tracing backend. They follow the same pattern as `components/form-observability`.

## Integration

Each application overlay (`dev`, `stage`, `prod`) includes:

```yaml
components:
  - ../../components/form-observability/app
  - ../../components/dashboard-observability/app
```

`deploy/k8s/monitoring/kustomization.yml` includes:

```yaml
components:
  - ../components/form-observability/monitoring
  - ../components/dashboard-observability/monitoring
```

The application component adds pod scrape annotations for internal port `8084`, derives `DASHBOARD_ENVIRONMENT` from the namespace, and switches readiness/liveness probes to the Actuator groups. It does not change replicas, HPA, volumes or ingress routes.

The monitoring component projects two hashed ConfigMaps: alert rules for Prometheus and a Grafana dashboard. Its patch intentionally lists Auth, Form and dashboard sources so that adding this component after `form-observability` does not remove the Form assets.

## Dashboard panels

`monitoring/dashboard-overview.json` provides seven panels with a namespace selector:

1. Scrape availability (`up`).
2. HTTP requests per second.
3. HTTP 5xx per second.
4. Mean HTTP latency.
5. Promotion calls per second by outcome.
6. Promotion call p95 latency.
7. Centralized logs from the Loki datasource.

## Alerts

- `DashboardServiceDown` (critical): the dashboard scrape target is down for two minutes in dev or prod.
- `DashboardPromotionCallFailures` (warning): at least 0.2 failed promotion calls per second over five minutes, sustained for two minutes.

Stage is intentionally excluded from both expressions because, as with Form, it is stopped between validations. Alerts use the existing Prometheus-to-Alertmanager route; external delivery requires a configured receiver and must be verified separately.

## Verification

From `deploy/k8s/components/dashboard-observability/monitoring`:

```bash
promtool check rules dashboard-alerts.yml
promtool test rules dashboard-alerts.test.yml
```

From the repository root (Python with PyYAML and kubectl required):

```bash
python deploy/k8s/components/dashboard-observability/verify.py
```

The verifier renders all overlays and the monitoring stack with dummy Secrets and compares against the same configuration without this component. It never contacts the cluster or prints Secrets. For individual renders, use `kubectl kustomize deploy/k8s/overlays/<env>` and `kubectl kustomize deploy/k8s/monitoring`.

Local render and rule tests do not prove runtime scrape, panel data or notification delivery. Those require authorized deployment and cluster/Jenkins access.

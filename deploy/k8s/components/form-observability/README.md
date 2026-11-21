# Form observability components

These Kustomize components extend the shared stack merged in PR #93. They are selected by the application overlays and monitoring configuration in this branch. They do not create additional monitoring servers, a log collector, or a tracing backend.

## Integration and deployment

PR #93 has been incorporated from main. The components are wired into the repository configuration; deployment remains a separate, authorized action. HU-FORM-05 remains an independent PR: these changes do not copy its HPA or storage configuration. Offline verification confirms replicas, HPA and storage are preserved when applying this component.

Each application overlay's `kustomization.yml` (dev, stage and prod) includes:

```yaml
components:
  - ../../components/form-observability/app
```

`deploy/k8s/monitoring/kustomization.yml` includes:

```yaml
components:
  - ../components/form-observability/monitoring
```

The application component adds pod scrape annotations and derives `FORM_ENVIRONMENT` from the pod namespace. It does not change replicas, HPA, volumes or public ingress routes. Scraping uses the internal pod port 8086 and the shared Kubernetes service discovery; no public Actuator route is needed. It does not restrict access by other workloads inside the cluster.

The monitoring component generates hashed ConfigMaps and combines the Auth and Form ConfigMaps in the existing `alert-rules` and `dashboards` projected volumes. Mount paths remain unchanged: Prometheus reads `/etc/prometheus/rules/*.yml`; the Grafana provider reads `/var/lib/grafana/dashboards`. No nested mounts or additional servers are introduced. Preserve the default Prometheus datasource or select it when importing the dashboard. The dashboard includes a namespace selector and uses the shared `app` and `namespace` target labels.

Generated ConfigMap name changes update the projected-volume reference and pod template, so rule updates follow the normal authorized monitoring rollout. This component does not implement Prometheus hot reload. Validate Prometheus/Grafana health after deployment.

## Alert

`FormKafkaPublicationFailures` aggregates failed publication acknowledgements across both Form topics by namespace. It fires after at least three failures in five minutes remain above the threshold for one minute. It covers `cg-dev` and `cg-prod`; intentionally stopped stage is excluded. It detects publication failures while Form is running, not total service unavailability or missing scrape targets.

Rules use the existing Prometheus-to-Alertmanager connection. PR #93 currently defines a receiver with no external notification integration. A configured delivery destination and a received notification are prerequisites for claiming operational alert delivery.

## Local verification

With `promtool` from Prometheus 2.51.2 available, run from `monitoring/`:

```powershell
promtool check rules form-alerts.yml
promtool test rules form-alerts.test.yml
```

The rule test covers firing in dev, recovery, no failures in prod, and exclusion of stage. Run the offline verifier from the repository root (Python with PyYAML and kubectl required):

```powershell
python deploy/k8s/components/form-observability/verify.py
```

The verifier creates temporary copies with dummy Secrets, renders all three environments and monitoring, checks generated ConfigMap references and preserved Auth files, and compares against the same configuration without the components. It never contacts the cluster or prints rendered Secrets.

For individual renders with your local configuration (do not apply as part of verification):

```powershell
kubectl kustomize deploy/k8s/overlays/dev
kubectl kustomize deploy/k8s/overlays/stage
kubectl kustomize deploy/k8s/overlays/prod
kubectl kustomize deploy/k8s/monitoring
```

Use placeholder secrets in an isolated copy for offline rendering. Do not save rendered Secrets as evidence. Once deployment is separately authorized, verify Prometheus targets/series, Grafana panel data, rule loading and Alertmanager delivery; local render and rule tests do not prove those runtime results.

Centralized log collection and Jaeger/OTLP reception remain shared infrastructure dependencies. No log datasource or trace backend is provisioned here.

# Form observability components

These optional Kustomize components extend the shared stack from PR #93. They do not create Prometheus, Grafana, Alertmanager, a log collector, or a tracing backend.

## Activation prerequisites

Merge HU-FORM-05 before changing the application overlays, and merge the shared monitoring resources from PR #93 before enabling the monitoring component. Neither dependency is included by this directory. Components are prepared and validated locally; they are not currently selected by the default overlays.

After those prerequisites are met, add to each application overlay's `kustomization.yml` (dev, stage and prod):

```yaml
components:
  - ../../components/form-observability/app
```

Add to `deploy/k8s/monitoring/kustomization.yml`:

```yaml
components:
  - ../components/form-observability/monitoring
```

The application component adds pod scrape annotations and derives `FORM_ENVIRONMENT` from the pod namespace. It does not change replicas, HPA, volumes or public ingress routes. Scraping uses the internal pod port 8086 and the shared Kubernetes service discovery; no public Actuator route is needed. It does not restrict access by other workloads inside the cluster.

The monitoring component generates hashed ConfigMaps and mounts the Form rules and dashboard in the existing Prometheus and Grafana Deployments. The shared Prometheus `rule_files` glob reads `/etc/prometheus/rules/*.yml`; the shared Grafana provider reads `/var/lib/grafana/dashboards`. Preserve the default Prometheus datasource or select it when importing the dashboard. The dashboard includes a namespace selector and uses the shared `app` and `namespace` target labels.

ConfigMap name changes update the pod template; the alert mount uses `subPath`, so rule updates require the normal authorized monitoring rollout. This component does not implement hot reload. Validate Prometheus/Grafana health after activation.

## Alert

`FormKafkaPublicationFailures` aggregates failed publication acknowledgements across both Form topics by namespace. It fires after at least three failures in five minutes remain above the threshold for one minute. It covers `cg-dev` and `cg-prod`; intentionally stopped stage is excluded. It detects publication failures while Form is running, not total service unavailability or missing scrape targets.

Rules use the existing Prometheus-to-Alertmanager connection. PR #93 currently defines a receiver with no external notification integration. A configured delivery destination and a received notification are prerequisites for claiming operational alert delivery.

## Local verification

With `promtool` from Prometheus 2.51.2 available, run from `monitoring/`:

```powershell
promtool check rules form-alerts.yml
promtool test rules form-alerts.test.yml
```

The rule test covers firing in dev, recovery, no failures in prod, and exclusion of stage. After wiring the components into the shared configuration, render locally (do not apply as part of this verification):

```powershell
kubectl kustomize deploy/k8s/overlays/dev
kubectl kustomize deploy/k8s/overlays/stage
kubectl kustomize deploy/k8s/overlays/prod
kubectl kustomize deploy/k8s/monitoring
```

Use placeholder secrets in an isolated copy for offline rendering. Do not save rendered Secrets as evidence. Once deployment is separately authorized, verify Prometheus targets/series, Grafana panel data, rule loading and Alertmanager delivery; local render and rule tests do not prove those runtime results.

Centralized log collection and Jaeger/OTLP reception remain shared infrastructure dependencies. No log datasource or trace backend is provisioned here.

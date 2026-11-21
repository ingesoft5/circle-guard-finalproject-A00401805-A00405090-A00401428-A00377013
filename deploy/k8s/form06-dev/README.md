# HU-FORM-06: isolated cg-dev validation assets

This opt-in overlay reuses the monitoring stack from PR #93. It has not been
deployed. It is deliberately separate from the normal dev/stage/prod overlays
and the shared Jenkinsfile.

## Isolation and impact

- `canary/` creates only `Service/cg-dev/form-hu6` and
  `Deployment/cg-dev/form-hu6`. It has no Ingress. The existing `form` Service
  selects `app=form`, whereas the canary selects `app=form-hu6`.
- The canary uses only the dev PostgreSQL and Kafka Services. Flyway is disabled
  and Hibernate uses `validate` during the trial. A manually approved
  questionnaire submission would still write to the **dev** database and emit
  events to the **dev** broker; do not send production data or real identities.
- `monitoring/` renders the existing Prometheus, Grafana, Alertmanager, Auth
  rule/dashboard, plus Form assets, Loki, Alloy and Jaeger. It replaces global
  Prometheus RBAC with a Role in `cg-dev`. Alloy has only a `cg-dev` Role and
  selects `app=form-hu6`. The log pipeline drops all lines except bounded
  `FormEventPublisher` outcomes and rewrites accepted lines from allowlisted
  fields. No host log mounts, DaemonSet, public Ingress, prod or stage resources.
- The overlay limits Prometheus discovery to the canary in `cg-dev` and keeps
  the original Prometheus datasource, Auth rule and Auth dashboard unchanged.
  Auth panels are provisioned but remain empty in this isolated trial.
- Loki and Jaeger use ephemeral storage. Prometheus retention is two hours and
  its `emptyDir` is capped at 512 MiB; Loki's `emptyDir` is capped at 1 GiB.
  A restart may erase trial evidence. The new components and canary request
  about 1.1 GiB RAM and 400m CPU in total; actual use must be checked again
  before deployment.
- The default Alertmanager receiver from #93 has no external destination.
  Delivery is **not** complete until an approved receiver is configured through
  a Secret and a real notification is observed.

## Offline verification

From the repository root:

```powershell
python deploy/k8s/form06-dev/verify.py
kubectl kustomize deploy/k8s/form06-dev/monitoring
kubectl kustomize deploy/k8s/form06-dev/canary
# Optional synthetic log privacy test; requires local Docker Engine:
python deploy/k8s/form06-dev/smoke_logs.py
# Optional synthetic OTLP ingestion test; requires local Docker Engine:
python deploy/k8s/form06-dev/smoke_traces.py
```

The Kustomize output contains configuration but no Secret values. Do not apply
the canary manifest with its placeholder image tag. Use
`prepare_release.py --tag X.Y.Z-hu6.N --commit FULL_SHA --digest sha256:FULL_DIGEST
--jenkins-build-url URL` only after Jenkins has built and approved that exact
commit. The script requires a clean checkout and writes an inventory, hashes
and two JSON resource lists under `build/form06-release/`. Its input must be
checked against the Jenkins image digest artifact. It never contacts the
cluster.

## Deployment gate and recovery

Before any write, obtain separate approval for the exact two rendered resource
lists. Recheck that namespace `monitoring` and the new resource names are still
absent, that dev/prod/stage replicas remain as expected, that the Jenkins run
passed unit, coverage, Sonar, integration and image security checks, and that
the monitoring owner has reserved a test window. Stop if any preflight differs.
Do not run the normal `ci.deploy` here: it updates all services and promotes
stage/prod on main.

Apply monitoring first, wait for its six Deployments, then apply the canary.
For recovery, remove only `form-hu6` Service/Deployment after checking their
ownership and image. Review whether the shared monitoring namespace has gained
other users before reverting its deployments or configuration. Never delete
the namespace, a shared resource or a persistent volume as an automatic
rollback. The existing `form` Deployment remains untouched throughout.

Runtime evidence required before marking the story Done: real Prometheus
`up` and Form series; populated Grafana Form panels; an allowlisted Form log
line in Loki with no synthetic sensitive marker; a Jaeger trace across Form,
Promotion and Notification using the same trace ID; a real Alertmanager
delivery and recovery; and absence of the synthetic marker in metrics, logs
and spans. Promotion/Notification instrumentation and an approved notification
receiver are separate, gated changes.

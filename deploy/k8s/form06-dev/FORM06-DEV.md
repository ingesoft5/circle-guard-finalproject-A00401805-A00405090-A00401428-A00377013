<!-- CG-DOC: HU-FORM-06: isolated cg-dev validation assets. -->
# HU-FORM-06: isolated cg-dev validation assets

This opt-in overlay reuses the monitoring stack from merged PRs #93 and #138. It has not been
deployed. It is deliberately separate from the normal dev/stage/prod overlays
and the shared Jenkinsfile.

Use the official Jenkins pipeline for PR checks and image publication. The
PR check does not publish images. For canary validation use the Form image
and digest recorded by the official release build after its quality and image
security gates pass. Canary resources are applied separately with approval.

## Isolation and impact

- `canary/` creates only `Service/cg-dev/form-hu6` and
  `Deployment/cg-dev/form-hu6`. It has no Ingress. The existing `form` Service
  selects `app=form`, whereas the canary selects `app=form-hu6`.
- The canary uses only the dev PostgreSQL and Kafka Services. Flyway is disabled
  and Hibernate uses `validate` during the trial. A manually approved
  questionnaire submission would still write to the **dev** database and emit
  events to the **dev** broker; do not send production data or real identities.
- `monitoring/` renders the existing Prometheus, Grafana, Alertmanager, Auth,
  Identity and Promotion rules, shared dashboards, Form assets, Loki, Alloy and
  Jaeger. It does not create another Loki instance. It removes the inherited
  Promtail DaemonSet and its global RBAC because that collector watches all
  three environments. Prometheus RBAC is replaced with a Role in `cg-dev`.
  Alloy has only a `cg-dev` Role and
  selects `app=form-hu6`. The log pipeline drops all lines except bounded
  `FormEventPublisher` outcomes and rewrites accepted lines from allowlisted
  fields. No host log mounts, DaemonSet, public Ingress, prod or stage resources.
- The overlay limits Prometheus discovery to the canary in `cg-dev` and keeps
  the original Prometheus and Loki datasources, the shared rules and
  dashboards unchanged. Their panels are provisioned but remain empty in this
  isolated trial. Both configurations load Auth, Identity and Form rules
  from the root directory and Promotion rules from its existing subdirectory.
- The shared Loki from #138 uses a 2 GiB PVC. Jaeger is in-memory. Prometheus
  retention is two hours and its `emptyDir` is capped at 512 MiB. The total
  resource budget must be checked again before deployment. The shared Loki
  PVC must never be deleted as part of a Form rollback.
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
# Optional synthetic Alertmanager route test; requires local Docker Engine:
python deploy/k8s/form06-dev/smoke_alertmanager.py
```

The Kustomize output contains configuration but no Secret values. Do not apply
the canary manifest with its placeholder image tag. Use
`prepare_release.py --tag X.Y.Z --commit FULL_SHA --digest sha256:FULL_DIGEST
--jenkins-build-url URL` only after Jenkins has built and approved that exact
commit. The script pins the canary image to the recorded digest, requires a clean checkout and writes an inventory, hashes
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

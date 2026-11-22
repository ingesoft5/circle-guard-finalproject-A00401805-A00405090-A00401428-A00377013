"""Offline guard for the opt-in cg-dev Form observability trial.

Requires kubectl and PyYAML. Does not connect to the cluster or read Secrets.
"""
from pathlib import Path
import json
import subprocess

import yaml


ROOT = Path(__file__).resolve().parent


def render(directory):
    result = subprocess.run(
        ["kubectl", "kustomize", str(directory)],
        capture_output=True,
        text=True,
        check=True,
    )
    objects = [obj for obj in yaml.safe_load_all(result.stdout) if obj]
    return {(obj["kind"], obj["metadata"].get("namespace"), obj["metadata"]["name"]): obj
            for obj in objects}


def main():
    shared = render(ROOT.parent / "monitoring")
    dev = render(ROOT / "monitoring")
    canary = render(ROOT / "canary")

    assert not any(kind in {"ClusterRole", "ClusterRoleBinding"} for kind, _, _ in dev)
    assert {namespace for _, namespace, _ in dev if namespace} <= {"monitoring", "cg-dev"}
    assert {namespace for _, namespace, _ in canary} == {"cg-dev"}
    assert set(canary) == {("Service", "cg-dev", "form-hu6"),
                           ("Deployment", "cg-dev", "form-hu6")}
    assert ("Deployment", "cg-dev", "form") not in canary
    assert ("Ingress", "cg-dev", "circleguard") not in canary

    removed = set(shared) - set(dev)
    assert removed == {
        ("ClusterRole", None, "prometheus"),
        ("ClusterRoleBinding", None, "prometheus"),
        ("ClusterRole", None, "promtail"),
        ("ClusterRoleBinding", None, "promtail"),
        ("ServiceAccount", "monitoring", "promtail"),
        ("ConfigMap", "monitoring", "promtail-config"),
        ("DaemonSet", "monitoring", "promtail"),
    }, removed
    changed = {key for key in set(shared) & set(dev) if shared[key] != dev[key]}
    assert changed == {("ConfigMap", "monitoring", "prometheus-config"),
                       ("ConfigMap", "monitoring", "grafana-datasources"),
                       ("Deployment", "monitoring", "prometheus")}, changed

    for name in ("auth-prometheus-alerts", "promotion-prometheus-alerts",
                 "grafana-dashboard-circleguard", "loki-config",
                 "alertmanager-config", "grafana-dashboards-provider"):
        key = ("ConfigMap", "monitoring", name)
        assert dev[key] == shared[key], f"Shared {name} was changed"
    for name in ("grafana", "alertmanager", "loki"):
        key = ("Deployment", "monitoring", name)
        assert dev[key] == shared[key], f"Shared {name} deployment was changed"
    assert dev[("Service", "monitoring", "loki")] == shared[("Service", "monitoring", "loki")]
    assert dev[("PersistentVolumeClaim", "monitoring", "loki-data")] == shared[
        ("PersistentVolumeClaim", "monitoring", "loki-data")]

    scrape = yaml.safe_load(dev[("ConfigMap", "monitoring", "prometheus-config")]["data"]["prometheus.yml"])
    shared_scrape = yaml.safe_load(shared[("ConfigMap", "monitoring", "prometheus-config")]["data"]["prometheus.yml"])
    assert scrape["rule_files"] == shared_scrape["rule_files"] == [
        "/etc/prometheus/rules/*.yml", "/etc/prometheus/rules/promotion/*.yml"]
    prometheus_pod = dev[("Deployment", "monitoring", "prometheus")]["spec"]["template"]["spec"]
    shared_pod = shared[("Deployment", "monitoring", "prometheus")]["spec"]["template"]["spec"]
    rule_volumes = {"alert-rules", "promotion-alert-rules"}
    for field, dev_spec, shared_spec in (
        ("volumes", prometheus_pod, shared_pod),
        ("volumeMounts", prometheus_pod["containers"][0], shared_pod["containers"][0]),
    ):
        dev_rules = {item["name"]: item for item in dev_spec[field] if item["name"] in rule_volumes}
        shared_rules = {item["name"]: item for item in shared_spec[field] if item["name"] in rule_volumes}
        assert set(dev_rules) == rule_volumes
        assert dev_rules == shared_rules
    jobs = {job["job_name"]: job for job in scrape["scrape_configs"]}
    assert set(jobs) == {"form06-canary", "prometheus"}
    job = jobs["form06-canary"]
    assert job["kubernetes_sd_configs"][0]["namespaces"]["names"] == ["cg-dev"]
    assert any(rule.get("action") == "keep" and rule.get("regex") == "form-hu6"
               for rule in job["relabel_configs"])

    sources = yaml.safe_load(dev[("ConfigMap", "monitoring", "grafana-datasources")]["data"]["datasource.yaml"])
    assert {source["name"] for source in sources["datasources"]} == {"Prometheus", "Loki", "Jaeger"}
    original = yaml.safe_load(shared[("ConfigMap", "monitoring", "grafana-datasources")]["data"]["datasource.yaml"])
    assert sources["datasources"][:2] == original["datasources"]

    deployment = canary[("Deployment", "cg-dev", "form-hu6")]
    pod = deployment["spec"]["template"]
    assert pod["metadata"]["labels"]["app"] == "form-hu6"
    assert canary[("Service", "cg-dev", "form-hu6")]["spec"]["selector"] == {"app": "form-hu6"}
    container = pod["spec"]["containers"][0]
    assert container["image"].endswith(":REPLACE_WITH_APPROVED_TAG")
    env = {entry["name"]: entry for entry in container["env"]}
    assert env["SPRING_JPA_HIBERNATE_DDL_AUTO"]["value"] == "validate"
    assert env["SPRING_FLYWAY_ENABLED"]["value"] == "false"
    assert env["FORM_TRACING_ENABLED"]["value"] == "true"
    assert "jaeger.monitoring.svc.cluster.local" in env["FORM_OTLP_TRACING_ENDPOINT"]["value"]
    assert "volumes" not in pod["spec"]

    alloy = dev[("ConfigMap", "monitoring", "form06-alloy-config")]["data"]["config.alloy"]
    assert 'names = ["cg-dev"]' in alloy
    assert 'label = "app=form-hu6"' in alloy
    assert 'stage.output' in alloy and 'source = "safe_line"' in alloy
    assert 'stage.label_keep' in alloy

    additions = set(dev) - set(shared)
    assert additions == {
        ("Role", "cg-dev", "form06-prometheus-reader"),
        ("RoleBinding", "cg-dev", "form06-prometheus-reader"),
        ("Role", "cg-dev", "form06-alloy-logs"),
        ("RoleBinding", "cg-dev", "form06-alloy-logs"),
        ("ServiceAccount", "monitoring", "form06-alloy"),
        ("ConfigMap", "monitoring", "form06-alloy-config"),
        ("Deployment", "monitoring", "form06-alloy"),
        ("Deployment", "monitoring", "jaeger"),
        ("Service", "monitoring", "jaeger"),
    }, additions
    print("PASS: cg-dev-only canary and namespaced discovery/log RBAC")
    print("PASS: shared Auth/Promotion rules, dashboards and Loki unchanged; Promtail excluded")
    print("PASS: canary has no public route, schema update or Flyway migration")
    print("PASS: scoped scrape and log collection; only Form operations are eligible")
    print(json.dumps({"monitoring_resources": len(dev), "canary_resources": len(canary),
                      "new_monitoring_resources": len(additions)}))


if __name__ == "__main__":
    main()

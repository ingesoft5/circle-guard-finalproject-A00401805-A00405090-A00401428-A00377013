"""Offline Kustomize verification; requires Python/PyYAML and kubectl, never a cluster.

Render isolated copies with dummy Secrets. Check that enabling Form observability
preserves other applications, replicas, storage, probes and the shared Auth assets.
"""
from copy import deepcopy
from pathlib import Path
import json
import shutil
import subprocess
import tempfile

import yaml


def render(directory):
    result = subprocess.run(
        ["kubectl", "kustomize", str(directory)],
        capture_output=True, text=True, check=True,
    )
    objects = [obj for obj in yaml.safe_load_all(result.stdout) if obj]
    return {(obj["kind"], obj["metadata"].get("namespace"), obj["metadata"]["name"]): obj
            for obj in objects}


def remove_components(path):
    config = yaml.safe_load(path.read_text(encoding="utf-8"))
    config.pop("components", None)
    path.write_text(yaml.safe_dump(config), encoding="utf-8")


def verify():
    source = Path(__file__).resolve().parents[2]
    with tempfile.TemporaryDirectory(prefix="form-observability-") as scratch:
        active, baseline = (Path(scratch) / name for name in ("active", "baseline"))
        shutil.copytree(source, active, ignore=shutil.ignore_patterns("secrets.env"))
        for env in ("dev", "stage", "prod"):
            overlay = active / "overlays" / env
            keys = [line.split("=", 1)[0] for line in
                    (overlay / "secrets.env.example").read_text().splitlines()
                    if "=" in line and not line.startswith("#")]
            (overlay / "secrets.env").write_text(
                "\n".join(key + "=offline-placeholder" for key in keys) + "\n"
            )
        shutil.copytree(active, baseline)
        for env in ("dev", "stage", "prod"):
            remove_components(baseline / "overlays" / env / "kustomization.yml")
            before = render(baseline / "overlays" / env)
            after = render(active / "overlays" / env)
            assert before.keys() == after.keys(), "Unexpected application resource changes"
            form_key = ("Deployment", "cg-" + env, "form")
            form = deepcopy(after[form_key])
            pod = form["spec"]["template"]
            annotations = pod["metadata"]["annotations"]
            assert annotations["prometheus.io/scrape"] == "true"
            assert annotations["prometheus.io/path"] == "/actuator/prometheus"
            assert annotations["prometheus.io/port"] == "8086"
            for key in ("prometheus.io/scrape", "prometheus.io/path", "prometheus.io/port"):
                annotations.pop(key)
            if not annotations and "annotations" not in before[form_key]["spec"]["template"]["metadata"]:
                pod["metadata"].pop("annotations")
            container = next(c for c in pod["spec"]["containers"] if c["name"] == "form")
            variable = next(v for v in container["env"] if v["name"] == "FORM_ENVIRONMENT")
            assert variable["valueFrom"]["fieldRef"]["fieldPath"] == "metadata.namespace"
            container["env"].remove(variable)
            after[form_key] = form
            assert before == after, "Unexpected changes beyond Form scrape and environment"
            print("PASS", env, "render; other resources, replicas, HPA and storage preserved")

        remove_components(baseline / "monitoring" / "kustomization.yml")
        before, after = render(baseline / "monitoring"), render(active / "monitoring")
        generated = [key for key in after if key not in before]
        assert len(generated) == 2 and all(key[0] == "ConfigMap" for key in generated)
        for server, volume, original, added in (
            ("prometheus", "alert-rules", "auth-prometheus-alerts", "form-prometheus-alerts"),
            ("grafana", "dashboards", "grafana-dashboard-circleguard", "grafana-dashboard-form"),
        ):
            key = ("Deployment", "monitoring", server)
            spec = after[key]["spec"]["template"]["spec"]
            projected = next(v for v in spec["volumes"] if v["name"] == volume)
            assert "configMap" not in projected, "Volume must have exactly one source type"
            refs = [v["configMap"]["name"] for v in projected["projected"]["sources"]]
            assert original in refs and len(refs) == 2
            generated_name = next(name for kind, ns, name in generated if name.startswith(added + "-"))
            assert generated_name in refs, "Generated ConfigMap hash not propagated to projection"
            filenames = [name for ref in refs for name in after[("ConfigMap", "monitoring", ref)]["data"]]
            assert len(filenames) == len(set(filenames)), "Projected files would shadow each other"
            for container in spec["containers"]:
                assert any(m["name"] == volume for m in container.get("volumeMounts", []))
            index = spec["volumes"].index(projected)
            original_volume = next(v for v in before[key]["spec"]["template"]["spec"]["volumes"]
                                   if v["name"] == volume)
            spec["volumes"][index] = deepcopy(original_volume)
        for key in generated:
            after.pop(key)
        for objects in (before, after):
            for key, obj in objects.items():
                if key[0] == "Deployment":
                    obj["spec"]["template"]["spec"]["volumes"].sort(key=lambda volume: volume["name"])
        assert before == after, [key for key in before if before[key] != after.get(key)]
        print("PASS monitoring: Auth retained, Form projections resolve hashed ConfigMaps")

        dashboard = json.loads((source / "components/form-observability/monitoring/form-overview.json").read_text())
        assert len(dashboard["panels"]) == len({p["id"] for p in dashboard["panels"]}) == 6
        assert all('namespace=~"$namespace"' in t["expr"] for p in dashboard["panels"] for t in p["targets"])
        print("PASS dashboard JSON: six unique panels with namespace filters")


if __name__ == "__main__":
    verify()

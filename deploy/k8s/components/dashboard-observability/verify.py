"""CG-DOC: Offline Kustomize verification for the dashboard observability components.

Renders isolated copies with dummy Secrets. Compares the shared stack with and
without this component, checks that Form and Auth assets survive, and validates
the Grafana JSON. It never contacts a cluster and never prints rendered Secrets.
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
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(f"kubectl kustomize {directory} failed:\n{result.stderr}")
    objects = [obj for obj in yaml.safe_load_all(result.stdout) if obj]
    return {(obj["kind"], obj["metadata"].get("namespace"), obj["metadata"]["name"]): obj
            for obj in objects}


def prepare_secrets(overlay):
    keys = [line.split("=", 1)[0] for line in
            (overlay / "secrets.env.example").read_text().splitlines()
            if "=" in line and not line.startswith("#")]
    (overlay / "secrets.env").write_text(
        "\n".join(key + "=offline-placeholder" for key in keys) + "\n"
    )


def remove_dashboard_component(path):
    config = yaml.safe_load(path.read_text(encoding="utf-8"))
    config["components"] = [component for component in config.get("components", [])
                            if "dashboard-observability" not in component]
    path.write_text(yaml.safe_dump(config), encoding="utf-8")


def verify():
    source = Path(__file__).resolve().parents[2]
    with tempfile.TemporaryDirectory(prefix="dashboard-observability-") as scratch:
        active, baseline = (Path(scratch) / name for name in ("active", "baseline"))
        shutil.copytree(source, active, ignore=shutil.ignore_patterns("secrets.env"))
        for env in ("dev", "stage", "prod"):
            prepare_secrets(active / "overlays" / env)
        shutil.copytree(active, baseline)
        for env in ("dev", "stage", "prod"):
            remove_dashboard_component(baseline / "overlays" / env / "kustomization.yml")
        remove_dashboard_component(baseline / "monitoring" / "kustomization.yml")

        def normalize_dashboard(deployment):
            """Strip observability-owned fields before comparing renders.

            HU-DASH-04 already puts the scrape annotations and split Actuator
            probes in the base apps.yml, so both renders carry them; only
            DASHBOARD_ENVIRONMENT is contributed exclusively by this component.
            Normalizing both sides keeps the check focused on unrelated drift.
            """
            result = deepcopy(deployment)
            pod = result["spec"]["template"]
            pod["metadata"].pop("annotations", None)
            container = next(c for c in pod["spec"]["containers"] if c["name"] == "dashboard")
            container["env"] = [v for v in container["env"] if v["name"] != "DASHBOARD_ENVIRONMENT"]
            for probe in ("readinessProbe", "livenessProbe"):
                container[probe]["httpGet"]["path"] = "/actuator/health"
            return result

        for env in ("dev", "stage", "prod"):
            before, after = (render(path / "overlays" / env) for path in (baseline, active))
            assert before.keys() == after.keys(), "Unexpected application resource changes"
            key = ("Deployment", "cg-" + env, "dashboard")
            active_deployment = after[key]
            pod = active_deployment["spec"]["template"]
            annotations = pod["metadata"]["annotations"]
            assert annotations["prometheus.io/scrape"] == "true"
            assert annotations["prometheus.io/path"] == "/actuator/prometheus"
            assert annotations["prometheus.io/port"] == "8084"
            container = next(c for c in pod["spec"]["containers"] if c["name"] == "dashboard")
            variable = next(v for v in container["env"] if v["name"] == "DASHBOARD_ENVIRONMENT")
            assert variable["valueFrom"]["fieldRef"]["fieldPath"] == "metadata.namespace"
            for probe in ("readinessProbe", "livenessProbe"):
                suffix = "readiness" if probe == "readinessProbe" else "liveness"
                assert container[probe]["httpGet"]["path"] == "/actuator/health/" + suffix

            before[key] = normalize_dashboard(before[key])
            after[key] = normalize_dashboard(after[key])
            assert before == after, [key for key in before if before[key] != after.get(key)]
            print("PASS", env, "render; replicas, HPA, storage and other applications preserved")

        before, after = render(baseline / "monitoring"), render(active / "monitoring")
        generated = [key for key in after if key not in before]
        assert len(generated) == 2 and all(key[0] == "ConfigMap" for key in generated)
        for server, volume, original, added in (
            ("prometheus", "alert-rules", "auth-prometheus-alerts", "dashboard-prometheus-alerts"),
            ("grafana", "dashboards", "grafana-dashboard-circleguard", "grafana-dashboard-dashboard"),
        ):
            key = ("Deployment", "monitoring", server)
            spec = after[key]["spec"]["template"]["spec"]
            projected = next(v for v in spec["volumes"] if v["name"] == volume)
            assert "configMap" not in projected, "Volume must have exactly one source type"
            refs = [source["configMap"]["name"] for source in projected["projected"]["sources"]]
            assert original in refs and len(refs) == 3, refs
            generated_name = next(name for _, _, name in generated if name.startswith(added + "-"))
            assert generated_name in refs, "Generated ConfigMap hash not propagated to projection"
            filenames = [name for ref in refs for name in after[("ConfigMap", "monitoring", ref)]["data"]]
            assert len(filenames) == len(set(filenames)), "Projected files would shadow each other"
            for container in spec["containers"]:
                assert any(m["name"] == volume for m in container.get("volumeMounts", []))
            index = spec["volumes"].index(projected)
            spec["volumes"][index] = deepcopy(
                next(v for v in before[key]["spec"]["template"]["spec"]["volumes"] if v["name"] == volume)
            )
        for key in generated:
            after.pop(key)
        for objects in (before, after):
            for key, obj in objects.items():
                if key[0] == "Deployment":
                    obj["spec"]["template"]["spec"]["volumes"].sort(key=lambda volume: volume["name"])
        assert before == after, [key for key in before if before[key] != after.get(key)]
        print("PASS monitoring: Auth, Form and Promotion assets retained with dashboard projections")

        dashboard = json.loads((source / "components/dashboard-observability/monitoring/dashboard-overview.json").read_text())
        panels = dashboard["panels"]
        assert len(panels) == len({p["id"] for p in panels}) == 7
        assert all('namespace=~"$namespace"' in target["expr"] for p in panels for target in p["targets"])
        assert any(p["type"] == "logs" and p["datasource"]["uid"] == "loki" for p in panels)
        print("PASS dashboard JSON: seven unique panels, namespace filters and Loki log panel")


if __name__ == "__main__":
    verify()

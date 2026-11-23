"""Exercise the rendered Alertmanager route locally with a synthetic Form alert.

Requires Docker, kubectl and PyYAML. No Kubernetes access, external receiver,
CircleGuard data or shared containers are used. Removes its own container.
"""
from datetime import datetime, timedelta, timezone
from pathlib import Path
from tempfile import TemporaryDirectory
import json
import subprocess
import time
import urllib.error
import urllib.request
import yaml


ROOT = Path(__file__).resolve().parent


def docker(*args):
    result = subprocess.run(["docker", *args], capture_output=True, text=True, timeout=180)
    if result.returncode:
        raise RuntimeError(result.stderr[-800:])
    return result.stdout.strip()


def main():
    rendered = subprocess.run(["kubectl", "kustomize", str(ROOT / "monitoring")],
                              capture_output=True, text=True, check=True)
    objects = {(obj["kind"], obj["metadata"]["name"]): obj
               for obj in yaml.safe_load_all(rendered.stdout) if obj}
    config = objects[("ConfigMap", "alertmanager-config")]["data"]["alertmanager.yml"]
    form_rules, = (obj["data"]["form-alerts.yml"] for (kind, name), obj in objects.items()
                   if kind == "ConfigMap" and name.startswith("form-prometheus-alerts-"))
    rule, = yaml.safe_load(form_rules)["groups"][0]["rules"]
    image = objects[("Deployment", "alertmanager")]["spec"]["template"]["spec"]["containers"][0]["image"]
    container = None
    with TemporaryDirectory(prefix="cg-form06-alert-") as directory:
        config_file = Path(directory) / "alertmanager.yml"
        config_file.write_text(config, encoding="utf-8")
        mount = f"{directory}:/etc/alertmanager:ro,Z"
        try:
            print(docker("run", "--rm", "-v", mount, "--entrypoint", "amtool", image,
                         "check-config", "/etc/alertmanager/alertmanager.yml"), flush=True)
            container = docker("run", "--rm", "-d", "--label", "cg.form06.smoke=true",
                               "-v", mount, "-p", "127.0.0.1::9093", image,
                               "--config.file=/etc/alertmanager/alertmanager.yml")
            port = docker("port", container, "9093/tcp").split(":")[-1]
            base = f"http://127.0.0.1:{port}"
            deadline = time.monotonic() + 45
            while True:
                try:
                    with urllib.request.urlopen(base + "/-/ready", timeout=3) as response:
                        if response.status == 200:
                            break
                except (urllib.error.URLError, TimeoutError):
                    if time.monotonic() >= deadline:
                        raise RuntimeError("Alertmanager did not become ready")
                    time.sleep(1)

            now = datetime.now(timezone.utc)
            alert = [{"labels": {**rule["labels"], "alertname": rule["alert"],
                                 "namespace": "cg-dev", "source": "local-hu6-smoke"},
                      "annotations": {"summary": "synthetic local HU6 alert"},
                      "startsAt": now.isoformat(),
                      "endsAt": (now + timedelta(minutes=2)).isoformat()}]
            request = urllib.request.Request(base + "/api/v2/alerts",
                                             data=json.dumps(alert).encode("utf-8"),
                                             headers={"Content-Type": "application/json"})
            with urllib.request.urlopen(request, timeout=5) as response:
                if response.status != 200:
                    raise RuntimeError(f"Alert POST returned HTTP {response.status}")
            deadline = time.monotonic() + 15
            while time.monotonic() < deadline:
                with urllib.request.urlopen(base + "/api/v2/alerts", timeout=5) as response:
                    current = json.load(response)
                if any(item.get("labels", {}).get("source") == "local-hu6-smoke" and
                       item.get("receivers") == [{"name": "default-receiver"}]
                       for item in current):
                    print("PASS: rendered route accepted synthetic alert; default receiver selected", flush=True)
                    print("External delivery remains pending an approved team webhook", flush=True)
                    return
                time.sleep(1)
            raise RuntimeError("Synthetic alert was not routed to the expected receiver")
        finally:
            if container:
                subprocess.run(["docker", "rm", "-f", container], capture_output=True, timeout=20)
                print("Removed local Alertmanager container", flush=True)


if __name__ == "__main__":
    main()

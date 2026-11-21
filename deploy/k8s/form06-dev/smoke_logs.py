"""Optional local synthetic privacy test of the actual Alloy processing block.

Requires Docker. Creates a short-lived local network, Loki and Alloy; never
contacts Kubernetes or uses real CircleGuard records. All resources are removed.
"""
from pathlib import Path
import json
import subprocess
import tempfile
import time
import urllib.parse
import urllib.request
import uuid

import yaml


HERE = Path(__file__).resolve().parent


def docker(*args):
    result = subprocess.run(["docker", *args], capture_output=True, text=True, timeout=45)
    if result.returncode:
        raise RuntimeError(f"docker {' '.join(args[:2])} failed: {result.stderr[-800:]}")
    return result.stdout.strip()


def wait_for(url, seconds=45):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=2) as response:
                if response.status == 200:
                    return
        except Exception:
            time.sleep(1)
    raise RuntimeError(f"Not ready: {url}")


def main():
    documents = [obj for obj in yaml.safe_load_all(
        (HERE / "monitoring" / "loki-alloy-jaeger.yml").read_text(encoding="utf-8")) if obj]
    configs = {obj["metadata"]["name"]: obj["data"] for obj in documents
               if obj["kind"] == "ConfigMap"}
    shared = [obj for obj in yaml.safe_load_all(
        (HERE.parent / "monitoring" / "loki.yml").read_text(encoding="utf-8")) if obj]
    loki_config = next(obj["data"]["loki.yml"] for obj in shared
                       if obj["kind"] == "ConfigMap" and obj["metadata"]["name"] == "loki-config")
    alloy_config = configs["form06-alloy-config"]["config.alloy"]
    process_and_writer = 'loki.process "safe_form_events" {' + alloy_config.split(
        'loki.process "safe_form_events" {', 1)[1]
    process_and_writer = process_and_writer.replace(
        "http://loki.monitoring.svc.cluster.local:3100", "http://loki:3100")
    test_config = '''loki.source.api "synthetic" {
  http {
    listen_address = "0.0.0.0"
    listen_port = 18080
  }
  forward_to = [loki.process.safe_form_events.receiver]
}
''' + process_and_writer

    network = "cg-form06-smoke-" + uuid.uuid4().hex[:8]
    containers = []
    docker("network", "create", network)
    print("Created isolated local Docker network", flush=True)
    try:
        with tempfile.TemporaryDirectory(prefix="form06-log-pipeline-") as directory:
            root = Path(directory)
            (root / "loki.yml").write_text(loki_config, encoding="utf-8")
            (root / "config.alloy").write_text(test_config, encoding="utf-8")

            loki = docker("run", "--rm", "-d", "--network", network,
                          "--network-alias", "loki", "--label", "cg.form06.smoke=true",
                          "-p", "127.0.0.1::3100",
                          "--tmpfs", "/loki:rw,size=1073741824,mode=1777",
                          "-v", f"{directory}:/etc/loki:ro", "grafana/loki:2.9.6",
                          "-config.file=/etc/loki/loki.yml")
            containers.append(loki)
            loki_port = docker("port", loki, "3100/tcp").split(":")[-1]
            loki_url = f"http://127.0.0.1:{loki_port}"
            wait_for(loki_url + "/ready")
            print("Loki ready", flush=True)

            alloy = docker("run", "--rm", "-d", "--network", network,
                           "--label", "cg.form06.smoke=true", "-p", "127.0.0.1::18080",
                           "-v", f"{directory}:/etc/alloy:ro", "grafana/alloy:v1.22.0",
                           "run", "/etc/alloy/config.alloy")
            containers.append(alloy)
            alloy_port = docker("port", alloy, "18080/tcp").split(":")[-1]
            alloy_url = f"http://127.0.0.1:{alloy_port}"
            print("Alloy started", flush=True)

            marker = "SYNTHETIC_PRIVATE_MARKER"
            trace = "a" * 32
            now = time.time_ns()
            lines = [
                f"INFO event_type=survey.submitted result=success trace_id={trace}",
                f"INFO anonymousId={marker} event_type=certificate.validated result=failure trace_id={trace}",
                f"INFO Received survey event: anonymousId={marker}",
            ]
            request_data = json.dumps({"streams": [{
                "stream": {"app": "form-hu6", "namespace": "cg-dev", "pod": "synthetic"},
                "values": [[str(now + index), line] for index, line in enumerate(lines)],
            }]}).encode()
            deadline = time.monotonic() + 30
            while True:
                try:
                    request = urllib.request.Request(
                        alloy_url + "/loki/api/v1/push", data=request_data,
                        headers={"Content-Type": "application/json"})
                    with urllib.request.urlopen(request, timeout=3) as response:
                        assert response.status in (200, 204)
                    break
                except Exception:
                    if time.monotonic() >= deadline:
                        raise RuntimeError("Alloy synthetic push did not become ready")
                    time.sleep(1)
            print("Synthetic lines submitted", flush=True)

            query = urllib.parse.urlencode({"query": '{app="form-hu6"}',
                                            "start": str(now - 1_000_000_000),
                                            "end": str(now + 60_000_000_000)})
            deadline = time.monotonic() + 30
            observed = []
            while time.monotonic() < deadline:
                try:
                    with urllib.request.urlopen(
                            loki_url + "/loki/api/v1/query_range?" + query,
                            timeout=3) as response:
                        result = json.load(response)
                    observed = [entry[1] for stream in result["data"]["result"]
                                for entry in stream["values"]]
                    if observed:
                        break
                except Exception:
                    pass
                time.sleep(1)
            if len(observed) != 2 or any(marker in line for line in observed) or not all(
                    line.startswith("event_type=") for line in observed):
                raise AssertionError(f"Expected two sanitized lines; observed: {observed!r}")
            print("PASS: two bounded events delivered; unsafe line dropped and marker removed")
    finally:
        for container in reversed(containers):
            subprocess.run(["docker", "rm", "-f", container], capture_output=True, timeout=20)
        subprocess.run(["docker", "network", "rm", network], capture_output=True, timeout=20)
        print("Removed local test containers and network", flush=True)


if __name__ == "__main__":
    main()

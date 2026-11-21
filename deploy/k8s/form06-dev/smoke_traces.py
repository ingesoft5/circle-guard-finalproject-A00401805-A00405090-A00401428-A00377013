"""Optional local Jaeger OTLP ingestion smoke test with a synthetic trace.

Requires Docker. Uses no CircleGuard data and removes its container afterward.
This verifies the backend protocol, not Form's exported spans or the full flow.
"""
import json
import subprocess
import time
import urllib.error
import urllib.request
import uuid


IMAGE = "cr.jaegertracing.io/jaegertracing/jaeger:2.23.0"


def docker(*args):
    result = subprocess.run(["docker", *args], capture_output=True, text=True, timeout=45)
    if result.returncode:
        raise RuntimeError(result.stderr[-800:])
    return result.stdout.strip()


def main():
    container = None
    try:
        container = docker("run", "--rm", "-d", "--label", "cg.form06.smoke=true",
                           "-p", "127.0.0.1::16686", "-p", "127.0.0.1::4318", IMAGE)
        query_port = docker("port", container, "16686/tcp").split(":")[-1]
        otlp_port = docker("port", container, "4318/tcp").split(":")[-1]
        query_url = f"http://127.0.0.1:{query_port}"
        otlp_url = f"http://127.0.0.1:{otlp_port}/v1/traces"

        trace_id = uuid.uuid4().hex
        span_id = uuid.uuid4().hex[:16]
        now = time.time_ns()
        payload = {"resourceSpans": [{
            "resource": {"attributes": [{"key": "service.name", "value": {"stringValue": "form06-smoke"}}]},
            "scopeSpans": [{"scope": {"name": "form06-smoke"}, "spans": [{
                "traceId": trace_id,
                "spanId": span_id,
                "name": "form.synthetic.operation",
                "kind": 1,
                "startTimeUnixNano": str(now),
                "endTimeUnixNano": str(now + 1_000_000),
                "attributes": [{"key": "event_type", "value": {"stringValue": "survey.submitted"}}],
            }]}],
        }]}
        data = json.dumps(payload).encode("utf-8")
        deadline = time.monotonic() + 45
        while True:
            try:
                request = urllib.request.Request(otlp_url, data=data,
                                                 headers={"Content-Type": "application/json"})
                with urllib.request.urlopen(request, timeout=3) as response:
                    if response.status not in (200, 202):
                        raise RuntimeError(f"Unexpected OTLP response {response.status}")
                print("OTLP HTTP trace accepted", flush=True)
                break
            except (urllib.error.URLError, TimeoutError):
                if time.monotonic() >= deadline:
                    raise RuntimeError("Jaeger OTLP endpoint did not become ready")
                time.sleep(1)

        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            try:
                with urllib.request.urlopen(query_url + "/api/traces/" + trace_id,
                                            timeout=3) as response:
                    result = json.load(response)
                if any(span.get("traceID") == trace_id for trace in result.get("data", [])
                       for span in trace.get("spans", [])):
                    print("PASS: Jaeger returned the synthetic OTLP trace", flush=True)
                    return
            except (urllib.error.URLError, TimeoutError, ValueError):
                pass
            time.sleep(1)
        raise RuntimeError("Jaeger did not return the submitted trace")
    finally:
        if container:
            subprocess.run(["docker", "rm", "-f", container], capture_output=True, timeout=20)
            print("Removed local Jaeger container", flush=True)


if __name__ == "__main__":
    main()

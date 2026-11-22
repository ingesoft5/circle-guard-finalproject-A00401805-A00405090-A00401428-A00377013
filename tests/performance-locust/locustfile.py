"""CG-DOC: Locust scenarios for E2.7 performance and stress validation.

Objectives declared for the final demo (baseline, 100 virtual users, 5 min):
- p95 latency <= 800 ms for QR access validation.
- p95 latency <= 1200 ms for health questionnaire submission.
- error rate < 1 % in both scenarios.
- sustained throughput >= 50 req/s combined.

Run with:
  locust -f tests/performance-locust/locustfile.py --headless \
    -u 100 -r 10 -t 5m --host http://localhost:8087 \
    --csv tests/performance-locust/results/access
"""

from locust import HttpUser, between, task


class CampusAccessUser(HttpUser):
    wait_time = between(1, 3)

    @task(2)
    def validate_access(self):
        """Exercise the short-lived signed QR validation endpoint."""
        self.client.get(
            "/api/v1/qr/validate",
            name="QR access validation",
            headers={"X-Anonymous-Id": "perf-test-user"},
        )

    @task(1)
    def submit_questionnaire(self):
        """Exercise the health questionnaire flow through the gateway."""
        self.client.post(
            "/api/v1/forms/responses",
            name="Health questionnaire submission",
            json={"symptoms": [], "status": "HEALTHY"},
        )

    @task(1)
    def health_probe(self):
        """Keep an actuator-backed availability signal in the sample."""
        self.client.get("/actuator/health", name="Gateway health")

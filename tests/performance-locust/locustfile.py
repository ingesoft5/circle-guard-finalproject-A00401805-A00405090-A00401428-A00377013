"""
CG-DOC: Locust scenarios for E2.6 (pruebas de carga): access validation through
circleguard-gateway-service and health questionnaire submission through
circleguard-form-service. Both flows are the same ones covered by tests/e2e.
"""

import os
import uuid

from locust import HttpUser, between, task

# The on-prem ingress selects the environment by Host header (see docs/infra/runbook.md).
HOST_HEADER = os.getenv("HOST_HEADER", "stage.circleguard.local")
AUTH_USER = os.getenv("LOADTEST_USER", "staff_guard")
AUTH_PASS = os.getenv("LOADTEST_PASSWORD", "password")


class CircleGuardUser(HttpUser):
    """Simulates a staff member that validates access and submits a health survey."""

    wait_time = between(1, 3)

    def _headers(self, token=None):
        headers = {"Host": HOST_HEADER, "Accept": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
            headers["Content-Type"] = "application/json"
        return headers

    def _login(self):
        with self.client.post(
            "/api/v1/auth/login",
            json={"username": AUTH_USER, "password": AUTH_PASS},
            headers=self._headers(),
            name="POST /api/v1/auth/login",
            catch_response=True,
        ) as response:
            if response.status_code != 200 or not response.json().get("token"):
                response.failure(f"login returned {response.status_code}")
                return None, None
            return response.json()["token"], response.json().get("anonymousId")

    @task(5)
    def access_validation(self):
        """Login -> QR token -> gateway validation (flujo E2E de acceso)."""
        token, anonymous_id = self._login()
        if not token:
            return

        with self.client.get(
            "/api/v1/auth/qr/generate",
            headers=self._headers(token),
            name="GET /api/v1/auth/qr/generate",
            catch_response=True,
        ) as response:
            if response.status_code != 200 or not response.json().get("qrToken"):
                response.failure(f"QR generation returned {response.status_code}")
                return
            qr_token = response.json()["qrToken"]

        with self.client.post(
            "/api/v1/gate/validate",
            json={"token": qr_token},
            headers=self._headers(token),
            name="POST /api/v1/gate/validate",
            catch_response=True,
        ) as response:
            if response.status_code != 200 or response.json().get("status") not in ("GREEN", "RED"):
                response.failure(f"gate validation returned {response.status_code}")
            else:
                response.success()

    @task(3)
    def health_questionnaire(self):
        """Active questionnaire lookup -> survey submission (envío de cuestionarios)."""
        token, anonymous_id = self._login()
        if not token:
            return

        with self.client.get(
            "/api/v1/questionnaires/active",
            headers=self._headers(token),
            name="GET /api/v1/questionnaires/active",
            catch_response=True,
        ) as response:
            # 404 means "no active questionnaire" and is a valid product state.
            if response.status_code not in (200, 404):
                response.failure(f"active questionnaire returned {response.status_code}")
            else:
                response.success()

        payload = {
            "anonymousId": anonymous_id or str(uuid.uuid4()),
            "hasFever": False,
            "hasCough": False,
            "otherSymptoms": "",
            "exposureDate": None,
            "responses": {"source": "locust", "questionnaire": "active"},
            "validationStatus": "PENDING",
        }
        with self.client.post(
            "/api/v1/surveys",
            json=payload,
            headers=self._headers(token),
            name="POST /api/v1/surveys",
            catch_response=True,
        ) as response:
            if response.status_code not in (200, 201):
                response.failure(f"survey submission returned {response.status_code}")
            else:
                response.success()

    @task(1)
    def rejects_invalid_access(self):
        """Negative control: the gateway must reject an unsigned QR token."""
        with self.client.post(
            "/api/v1/gate/validate",
            json={"token": "invalid"},
            headers=self._headers(),
            name="POST /api/v1/gate/validate (invalid)",
            catch_response=True,
        ) as response:
            if response.status_code not in (401, 403):
                response.failure(f"invalid token returned {response.status_code}")
            else:
                response.success()

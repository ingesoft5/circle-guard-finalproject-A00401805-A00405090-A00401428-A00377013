#!/usr/bin/env bash
# CG-DOC: Captures public evidence screenshots with Playwright when URLs are available.
set -euo pipefail

OUT="${EVIDENCE_DIR:-../../docs/informe/evidencia}"
mkdir -p "${OUT}"

capture() {
  local name="$1" url="$2"
  [ -n "${url}" ] || { echo "skip ${name}: URL empty"; return 0; }
  npx playwright screenshot --full-page "${url}" "${OUT}/${name}.png"
}

capture gateway-health "${GATEWAY_URL:+${GATEWAY_URL}/actuator/health}"
capture gateway-app "${GATEWAY_URL}"
capture jenkins "${JENKINS_URL}"
capture sonarqube "${SONAR_URL}"
capture nexus "${NEXUS_URL}"

for report in \
  "../../services/circleguard-gateway-service/build/reports/jacoco/test/html/index.html" \
  "../../services/circleguard-gateway-service/build/reports/tests/test/index.html"; do
  [ -f "${report}" ] || continue
  name="$(basename "$(dirname "$(dirname "${report}")")")"
  npx playwright screenshot --full-page "file://$(cd "$(dirname "${report}")" && pwd)/$(basename "${report}")" "${OUT}/gateway-${name}.png"
done

echo "Evidence saved in ${OUT}"

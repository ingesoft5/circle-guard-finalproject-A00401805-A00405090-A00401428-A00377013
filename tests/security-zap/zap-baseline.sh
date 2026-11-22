#!/usr/bin/env bash
# CG-DOC: OWASP ZAP baseline scan for E2.7.
set -euo pipefail

TARGET_URL="${1:?Uso: zap-baseline.sh <url>}"
REPORT_DIR="${REPORT_DIR:-tests/security-zap/reports}"
mkdir -p "$REPORT_DIR"

docker run --rm \
  -v "$PWD/$REPORT_DIR":/zap/wrk/:rw \
  ghcr.io/zaproxy/zaproxy:stable \
  zap-baseline.py -t "$TARGET_URL" \
  -r zap-baseline-report.html \
  -J zap-baseline-report.json \
  -I

echo "Reportes escritos en $REPORT_DIR"

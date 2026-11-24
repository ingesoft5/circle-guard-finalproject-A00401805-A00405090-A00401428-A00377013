#!/usr/bin/env bash
# Captura evidencia verificable del clúster para el cierre del proyecto.
# Uso: ./deploy/scripts/capture-evidence.sh [directorio-salida]
set -euo pipefail

OUT="${1:-docs/informe/evidencia/validacion-final}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
ROOT="${OUT}/cluster"
mkdir -p "${ROOT}"

echo "[capture] timestamp=${STAMP}"
echo "[capture] salida=${ROOT}"

kubectl get nodes -o wide > "${ROOT}/nodes-${STAMP}.txt"
kubectl top nodes > "${ROOT}/top-nodes-${STAMP}.txt" 2>&1 || true
kubectl get pods -A -o wide > "${ROOT}/pods-all-${STAMP}.txt"
kubectl get events -A --sort-by=.lastTimestamp 2>/dev/null | tail -150 > "${ROOT}/events-${STAMP}.txt" || true

for ns in cg-dev cg-stage cg-prod; do
  mkdir -p "${ROOT}/${ns}"
  kubectl -n "${ns}" get deploy,statefulset,pods,svc,ingress,hpa,secret -o wide \
    > "${ROOT}/${ns}/workloads-${STAMP}.txt" 2>&1 || true
  kubectl -n "${ns}" get pods -o json > "${ROOT}/${ns}/pods-${STAMP}.json" 2>&1 || true
  kubectl -n "${ns}" get hpa -o yaml > "${ROOT}/${ns}/hpa-${STAMP}.yaml" 2>&1 || true
  kubectl -n "${ns}" get ingress -o yaml > "${ROOT}/${ns}/ingress-${STAMP}.yaml" 2>&1 || true
  kubectl -n "${ns}" get secret cg-secrets -o jsonpath='{.metadata.name}{"\n"}' \
    > "${ROOT}/${ns}/secret-cg-secrets-${STAMP}.txt" 2>&1 || true
done

echo "[capture] listo: ${ROOT}"

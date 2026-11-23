#!/usr/bin/env bash
# CG-DOC: Install KEDA for the L284 FinOps bonus (scale-to-zero).
# Runs on the on-prem k3d host. Idempotent: the Helm release is skipped when
# already deployed. KEDA is installed in kube-system; the workload namespace
# cg-finops is created for the isolated file/notification scale-to-zero demo.
set -euo pipefail

KEDA_VERSION="${KEDA_VERSION:-2.15.1}"
KUBE_CONTEXT="${KUBE_CONTEXT:-k3d-cg}"
FINOPS_NS="cg-finops"

echo "==> Adding pinned KEDA Helm repository (${KEDA_VERSION})"
helm repo add kedacore https://kedacore.github.io/charts >/dev/null 2>&1 || true
helm repo update kedacore >/dev/null

echo "==> Installing KEDA ${KEDA_VERSION} into kube-system"
if helm status keda -n kube-system >/dev/null 2>&1; then
  echo "    [skip] keda release already deployed"
else
  helm upgrade --install keda kedacore/keda \
    --namespace kube-system \
    --version "${KEDA_VERSION}" \
    --wait --timeout 5m
fi

echo "==> Ensuring FinOps workload namespace ${FINOPS_NS}"
kubectl --context "$KUBE_CONTEXT" create ns "$FINOPS_NS" >/dev/null 2>&1 || true
kubectl --context "$KUBE_CONTEXT" label ns "$FINOPS_NS" finops=enabled --overwrite

echo "==> Waiting for KEDA operator and metrics server"
kubectl --context "$KUBE_CONTEXT" rollout status deploy/keda-operator -n kube-system --timeout=3m || true
kubectl --context "$KUBE_CONTEXT" rollout status deploy/keda-operator-metrics-apiserver -n kube-system --timeout=3m || true

echo "==> KEDA state"
kubectl --context "$KUBE_CONTEXT" get pods -n kube-system -l app.kubernetes.io/name=keda 2>&1 || true
echo "KEDA_INSTALL_DONE"
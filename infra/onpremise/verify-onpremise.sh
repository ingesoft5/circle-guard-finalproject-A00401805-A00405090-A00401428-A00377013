#!/usr/bin/env bash
# Chequeo read-only de la plataforma on-premise de CircleGuard.
# No crea, borra ni modifica nada: imprime OK/FALLA por cheque y sale != 0 si alguno falla.
# Uso: ./infra/onpremise/verify-onpremise.sh
# Opcional: DEMO_VERIFY=1 (intenta login JWT con staff_guard/password en dev — solo si ya existe)
set -uo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ARCHIVO_ENTORNO="${SEED_ENV:-$RAIZ/deploy/compose/tools/.env.onpremise}"
FALLAS=0
CHECKS_HECHOS=0

verde()  { printf '\033[32mOK\033[0m   %s\n' "$1"; CHECKS_HECHOS=$((CHECKS_HECHOS+1)); }
rojo()   { printf '\033[31mFALLA\033[0m %s\n' "$1"; FALLAS=$((FALLAS+1)); CHECKS_HECHOS=$((CHECKS_HECHOS+1)); }
info()   { printf '     %s\n' "$1"; }

valor_entorno() {
  [[ -f "$ARCHIVO_ENTORNO" ]] || return 0
  grep -m1 "^$1=" "$ARCHIVO_ENTORNO" | cut -d= -f2- || true
}

http_code() { # método url [usuario:contraseña]
  local -a auth=()
  [[ -n "${3:-}" ]] && auth=(-u "$3")
  curl -s -o /dev/null -w '%{http_code}' -X "$1" "${auth[@]}" "$2" || echo 000
}

http_code_host() { # Host header url
  curl -s -o /dev/null -w '%{http_code}' -H "Host: $1" "$2" || echo 000
}

echo "== Herramientas (docker compose cg-tools) =="
if [ "$(http_code GET http://localhost:8080/login)" = "200" ]; then
  verde "Jenkins responde en :8080/login"
else
  rojo "Jenkins no responde en :8080/login"
fi
if curl -fsS http://localhost:9000/api/system/status 2>/dev/null | grep -q '"status":"UP"'; then
  verde "SonarQube reporta status UP en :9000/api/system/status"
else
  rojo "SonarQube no responde status UP en :9000"
fi
if [ "$(http_code GET http://localhost:8081/service/rest/v1/status)" = "200" ]; then
  verde "Nexus reporta status en :8081"
else
  rojo "Nexus no responde en :8081"
fi

SONAR_TOKEN="$(valor_entorno SONAR_TOKEN)"
if [[ -n "${SONAR_TOKEN:-}" ]]; then
  if curl -fsS -u "${SONAR_TOKEN}:" http://localhost:9000/api/authentication/validate 2>/dev/null \
      | jq -e '.valid == true' >/dev/null; then
    verde "SONAR_TOKEN autentica en SonarQube (api/authentication/validate)"
  else
    rojo "SONAR_TOKEN no autentica en SonarQube (401 o valid=false)"
  fi
else
  info "Sin SONAR_TOKEN en $ARCHIVO_ENTORNO; se omite la validación de API de SonarQube."
fi

SONAR_ADMIN_PASSWORD="$(valor_entorno SONAR_ADMIN_PASSWORD)"
if [[ -n "${SONAR_ADMIN_PASSWORD:-}" ]]; then
  if curl -fsS -u "admin:${SONAR_ADMIN_PASSWORD}" http://localhost:9000/api/users/current 2>/dev/null \
      | jq -e '.login == "admin"' >/dev/null; then
    verde "SONAR_ADMIN_PASSWORD autentica como admin en SonarQube (/api/users/current)"
  else
    rojo "SONAR_ADMIN_PASSWORD no autentica como admin en SonarQube"
  fi
else
  info "Sin SONAR_ADMIN_PASSWORD en $ARCHIVO_ENTORNO; se omite la validación de admin."
fi

NEXUS_USER="$(valor_entorno NEXUS_USER)"; NEXUS_USER="${NEXUS_USER:-jenkins}"
NEXUS_PASSWORD="$(valor_entorno NEXUS_PASSWORD)"
if [[ -n "${NEXUS_PASSWORD:-}" ]]; then
  if curl -fsS -u "${NEXUS_USER}:${NEXUS_PASSWORD}" http://localhost:5000/v2/_catalog 2>/dev/null | grep -q circleguard-docker; then
    verde "Registro Nexus :5000 lista imágenes circleguard-docker/*"
  else
    rojo "No se pudo listar el catálogo de :5000 con ${NEXUS_USER} o aún no hay imágenes publicadas"
  fi
else
  info "Sin NEXUS_PASSWORD en $ARCHIVO_ENTORNO; se omite el cheque del registro."
fi

echo "== Clúster k3d (cg) =="
if k3d cluster list 2>/dev/null | awk 'NR>1 {print $1}' | grep -qx cg; then
  verde "El clúster cg existe (k3d cluster list)"
else
  rojo "No se encuentra el clúster cg (k3d cluster list)"
fi
if kubectl get namespaces 2>/dev/null | grep -q '^cg-dev'; then
  verde "Namespace cg-dev presente"
else
  rojo "Namespace cg-dev ausente"
fi
if kubectl get namespaces 2>/dev/null | grep -q '^cg-stage'; then
  verde "Namespace cg-stage presente"
else
  info "Namespace cg-stage ausente (dev/stage pueden estar apagados para ahorrar RAM)"
fi
if kubectl get namespaces 2>/dev/null | grep -q '^cg-prod'; then
  verde "Namespace cg-prod presente"
else
  info "Namespace cg-prod ausente (según la regla: máximo dos entornos encendidos a la vez)"
fi

for ns in cg-dev cg-stage cg-prod; do
  if kubectl get namespace "$ns" >/dev/null 2>&1; then
    TOTAL="$(kubectl get pods -n "$ns" --no-headers 2>/dev/null | wc -l)"
    READY="$(kubectl get pods -n "$ns" --no-headers 2>/dev/null | awk '$2 ~ /^[0-9]+\/[0-9]+$/ && $3=="Running" {split($2,a,"/"); if (a[1]==a[2]) n++} END {print n+0}')"
    if [[ "$TOTAL" -gt 0 && "$READY" -eq "$TOTAL" ]]; then
      verde "Todos los pods de ${ns} están Ready (${READY}/${TOTAL})"
    elif [[ "$TOTAL" -eq 0 ]]; then
      info "Namespace ${ns} vacío"
    else
      rojo "Namespace ${ns}: pods no Ready (${READY}/${TOTAL})"
    fi
  fi
done

echo "== Ingress (Traefik :30080) =="
for host in dev stage prod; do
  if ! kubectl get namespace "cg-${host}" >/dev/null 2>&1; then
    info "Namespace cg-${host} no existe; se omite su cheque de Ingress"
    continue
  fi
  CODE="$(http_code_host "${host}.circleguard.local" http://localhost:30080/)"
  if [[ "$CODE" = "200" ]]; then
    verde "Ingress responde 200 con Host ${host}.circleguard.local"
  else
    rojo "Ingress no responde 200 con Host ${host}.circleguard.local (HTTP $CODE)"
  fi
done

if [[ "${DEMO_VERIFY:-0}" = "1" ]]; then
  echo "== Login de demostración (demo only; no borra nada) =="
  RESP="$(curl -s -o /tmp/verify-onpremise-login.json -w '%{http_code}' -X POST \
    -H 'Content-Type: application/json' \
    -H 'Host: dev.circleguard.local' \
    -d '{"username":"staff_guard","password":"password"}' \
    http://localhost:30080/api/v1/auth/login || echo 000)"
  if [[ "$RESP" = "200" ]] && grep -q '"token"' /tmp/verify-onpremise-login.json; then
    verde "Login demo en dev emitió JWT"
  else
    rojo "Login demo en dev falló (HTTP $RESP). Ver logs: kubectl logs -n cg-dev deploy/auth"
  fi
  rm -f /tmp/verify-onpremise-login.json
fi

echo
echo "Cheques ejecutados: ${CHECKS_HECHOS}. Fallas: ${FALLAS}."
[[ "$FALLAS" -eq 0 ]] && exit 0 || exit 1

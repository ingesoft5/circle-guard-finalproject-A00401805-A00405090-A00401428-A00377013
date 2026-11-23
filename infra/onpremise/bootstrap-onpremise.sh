#!/usr/bin/env bash
# CG-DOC: Bootstraps the CircleGuard on-premise tooling stack.
# Prepares the local team tooling stack, including the smee.io relay.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PLANTILLA="$RAIZ/deploy/compose/tools/.env.onpremise.example"
ARCHIVO_ENTORNO="$RAIZ/deploy/compose/tools/.env.onpremise"
ARCHIVO_COMPOSE="$RAIZ/deploy/compose/tools/docker-compose.onpremise.yml"
source "$RAIZ/infra/bootstrap/bootstrap-common.sh"

[[ -f "$PLANTILLA" ]] || { echo "Falta $PLANTILLA"; exit 1; }
[[ -f "$ARCHIVO_COMPOSE" ]] || { echo "Falta $ARCHIVO_COMPOSE"; exit 1; }

# Replaces or adds a key without breaking values containing special characters.
establecer_valor() {
  local clave="$1" valor="$2"
  CLAVE="$clave" VALOR="$valor" awk '
    BEGIN {
      clave = ENVIRON["CLAVE"]
      valor = ENVIRON["VALOR"]
      encontrado = 0
    }
    index($0, clave "=") == 1 {
      print clave "=" valor
      encontrado = 1
      next
    }
    { print }
    END {
      if (!encontrado) print clave "=" valor
    }
  ' "$ARCHIVO_ENTORNO" > "${ARCHIVO_ENTORNO}.temporal"
  mv "${ARCHIVO_ENTORNO}.temporal" "$ARCHIVO_ENTORNO"
}

# The SonarQube image does not consume SONAR_ADMIN_PASSWORD. bootstrap-tools.sh
# applies it through the API for the Docker Compose stack; the on-prem bootstrap
# must do the same, otherwise the generated secret is never used and the UI keeps
# the default admin/admin credentials.
aplicar_credenciales_sonar() {
  local base="${SONAR_HOST_URL:-}"
  if [[ -z "$base" ]]; then
    base="$(grep -m1 '^SONAR_HOST_URL=' "$ARCHIVO_ENTORNO" | cut -d= -f2- || true)"
  fi
  base="${base:-http://localhost:9000}"
  base="${base%/}"

  local password profesor token
  password="$(grep -m1 '^SONAR_ADMIN_PASSWORD=' "$ARCHIVO_ENTORNO" | cut -d= -f2- || true)"
  [[ -n "$password" && "$password" != __* ]] || die "Falta SONAR_ADMIN_PASSWORD en $ARCHIVO_ENTORNO"

  wait_http "${base}/api/system/status" 600 '"status":"UP"'

  if curl -fsS -u "admin:${password}" "${base}/api/users/current" >/dev/null 2>&1; then
    log_info "SonarQube: la contraseña de admin ya coincide con .env.onpremise."
  elif curl -fsS -u admin:admin "${base}/api/users/current" >/dev/null 2>&1; then
    log_info "SonarQube: aplicando SONAR_ADMIN_PASSWORD al usuario admin."
    curl -fsS -u admin:admin -X POST "${base}/api/users/change_password" \
      --data-urlencode login=admin \
      --data-urlencode previousPassword=admin \
      --data-urlencode "password=${password}" >/dev/null
  else
    die "SonarQube: no autentica ni con admin/<SONAR_ADMIN_PASSWORD> ni con admin/admin."
  fi

  token="$(grep -m1 '^SONAR_TOKEN=' "$ARCHIVO_ENTORNO" | cut -d= -f2- || true)"
  if [[ -n "$token" ]] && curl -fsS -u "${token}:" "${base}/api/users/current" >/dev/null 2>&1; then
    log_info "SonarQube: SONAR_TOKEN sigue siendo válido tras el cambio de contraseña."
  else
    log_warn "SonarQube: SONAR_TOKEN no autentica; créalo de nuevo y actualiza .env.onpremise."
  fi

  profesor="$(grep -m1 '^PROFESSOR_SONAR_PASSWORD=' "$ARCHIVO_ENTORNO" | cut -d= -f2- || true)"
  if [[ -n "$profesor" && "$profesor" != __* ]]; then
    if curl -fsS -u "admin:${password}" "${base}/api/users/search?q=professor" \
        | jq -e '.users[]? | select(.login == "professor")' >/dev/null 2>&1; then
      curl -fsS -u "admin:${password}" -X POST "${base}/api/users/change_password" \
        --data-urlencode login=professor \
        --data-urlencode "password=${profesor}" >/dev/null
    else
      curl -fsS -u "admin:${password}" -X POST "${base}/api/users/create" \
        --data-urlencode login=professor \
        --data-urlencode name=Professor \
        --data-urlencode "password=${profesor}" \
        --data-urlencode local=true >/dev/null
    fi
    log_info "SonarQube: usuario professor sincronizado con .env.onpremise."
  fi
}

# Detects the ZeroTier address; falls back to the default route.
IP_EQUIPO="${IP_EQUIPO:-}"
if [[ -z "$IP_EQUIPO" ]]; then
  IP_EQUIPO="$(ip -o -4 addr show | awk '$2 ~ /^zt/ {print $4; exit}' | cut -d/ -f1)"
fi
if [[ -z "$IP_EQUIPO" ]]; then
  IP_EQUIPO="$(ip -4 route get 1.1.1.1 2>/dev/null | awk '
    { for (i = 1; i <= NF; i++) if ($i == "src") { print $(i + 1); exit } }
  ')"
fi
if [[ -z "$IP_EQUIPO" ]]; then
  IP_EQUIPO="$(hostname -I | awk '{print $1}')"
fi
[[ -n "$IP_EQUIPO" ]] || {
  echo "No pude detectar la dirección del equipo. Usa: IP_EQUIPO=10.x.x.x $0"
  exit 1
}

# Creates the real file only if missing or empty.
if [[ ! -s "$ARCHIVO_ENTORNO" ]]; then
  install -m 600 "$PLANTILLA" "$ARCHIVO_ENTORNO"
fi
chmod 600 "$ARCHIVO_ENTORNO"

# Generates only secrets that still contain the placeholder.
while IFS= read -r clave; do
  [[ -n "$clave" ]] || continue
  establecer_valor "$clave" "$(openssl rand -hex 24)"
done < <(grep -oE '^[A-Z0-9_]+=__GENERATE__$' "$ARCHIVO_ENTORNO" | cut -d= -f1 || true)

# Adjust the team addresses on each run.
for clave in TOOLS_IP TOOLS_PRIVATE_IP DEV_PRIVATE_IP STAGE_PRIVATE_IP PROD_PRIVATE_IP; do
  establecer_valor "$clave" "$IP_EQUIPO"
done
establecer_valor "JENKINS_URL" "http://${IP_EQUIPO}:8080/"

# The registry and webhook relay are fixed on this host.
establecer_valor "NEXUS_REGISTRY" "${IP_EQUIPO}:5000"
establecer_valor "SMEE_URL" "${SMEE_URL:-https://smee.io/nk4nvlnyB0u6Fqnn}"
establecer_valor "SMEE_TARGET" "${SMEE_TARGET:-http://localhost:8080/github-webhook//}"

# Prompts for the user and personal token only if missing or still placeholders.
USUARIO_PERSONAL="$(grep -m1 '^GITHUB_USER=' "$ARCHIVO_ENTORNO" | cut -d= -f2- || true)"
TOKEN_PERSONAL="$(grep -m1 '^GITHUB_TOKEN=' "$ARCHIVO_ENTORNO" | cut -d= -f2- || true)"

# The webhook secret is NEVER generated randomly: the hook already exists in
# GitHub (id 709307452) and validates the signature with this exact value. If it differs,
# Jenkins returns 403 and the PR build never starts.
SECRETO_HOOK="${GITHUB_WEBHOOK_SECRET:-$(grep -m1 '^GITHUB_WEBHOOK_SECRET=' "$ARCHIVO_ENTORNO" | cut -d= -f2- || true)}"
if [[ -z "$SECRETO_HOOK" || "$SECRETO_HOOK" == __* ]]; then
  if [[ -t 0 ]]; then
    read -rsp "Secreto del webhook de GitHub (debe coincidir con el del hook): " SECRETO_HOOK
    echo
  fi
fi
if [[ -z "$SECRETO_HOOK" || "$SECRETO_HOOK" == __* ]]; then
  echo "Falta GITHUB_WEBHOOK_SECRET."
  echo "Ponlo en $ARCHIVO_ENTORNO o pásalo al ejecutar:"
  echo "  GITHUB_WEBHOOK_SECRET=<secreto> $0"
  echo "El valor bueno está en infra/state/tools.env del host del repo (el que"
  echo "bootstrap-github.sh envió al crear/actualizar el hook)."
  exit 1
fi
if [[ -z "${GITHUB_WEBHOOK_SECRET:-}" && "$SECRETO_HOOK" != __GITHUB_* ]]; then
  echo "Aviso: se conserva el GITHUB_WEBHOOK_SECRET de $ARCHIVO_ENTORNO."
  echo "GitHub no expone el secreto del webhook; asegúrate de que coincida con el hook 709307452."
  echo "Para forzarlo: GITHUB_WEBHOOK_SECRET=<secreto> $0"
fi
establecer_valor "GITHUB_WEBHOOK_SECRET" "$SECRETO_HOOK"

# The SonarQube token is NEVER generated randomly: sonar-scanner responds
# "Not authorized" if the token was not created inside SonarQube. Create it with:
#   curl -u admin:admin -X POST http://localhost:9000/api/user_tokens/generate --data-urlencode 'name=jenkins-ci'
SECRETO_SONAR="${SONAR_TOKEN:-$(grep -m1 '^SONAR_TOKEN=' "$ARCHIVO_ENTORNO" | cut -d= -f2- || true)}"
if [[ -z "$SECRETO_SONAR" || "$SECRETO_SONAR" == __* ]]; then
  if [[ -t 0 ]]; then
    read -rsp "Token de API de SonarQube (créalo en SonarQube): " SECRETO_SONAR
    echo
  fi
fi
if [[ -z "$SECRETO_SONAR" || "$SECRETO_SONAR" == __* ]]; then
  echo "Falta SONAR_TOKEN."
  echo "Ponlo en $ARCHIVO_ENTORNO o pásalo al ejecutar:"
  echo "  SONAR_TOKEN=squ_... $0"
  echo "Un valor al azar hace que sonar-scanner falle con 'Not authorized'."
  exit 1
fi
establecer_valor "SONAR_TOKEN" "$SECRETO_SONAR"

if [[ -z "$USUARIO_PERSONAL" || "$USUARIO_PERSONAL" == __GITHUB_* ]]; then
  read -rp "Usuario personal de GitHub (cualquiera de los 4 integrantes): " USUARIO_PERSONAL
fi

if [[ -z "$TOKEN_PERSONAL" || "$TOKEN_PERSONAL" == __GITHUB_* ]]; then
  read -rsp "Token personal de GitHub (no se muestra): " TOKEN_PERSONAL
  echo
fi

[[ -n "$USUARIO_PERSONAL" && -n "$TOKEN_PERSONAL" ]] || {
  echo "El usuario y el token personal son obligatorios."
  exit 1
}

establecer_valor "GITHUB_USER" "$USUARIO_PERSONAL"
establecer_valor "GITHUB_TOKEN" "$TOKEN_PERSONAL"

# Verifies that no unresolved placeholders remain.
if grep -qE '=__(GENERATE|HOST_IP|GITHUB_|SONAR_)' "$ARCHIVO_ENTORNO"; then
  echo "Quedaron marcadores sin reemplazar en $ARCHIVO_ENTORNO:"
  grep -nE '=__(GENERATE|HOST_IP|GITHUB_|SONAR_)' "$ARCHIVO_ENTORNO"
  exit 1
fi

# Excludes the real file from the repository without modifying the tracked one.
EXCLUSIONES="$RAIZ/.git/info/exclude"
mkdir -p "$(dirname "$EXCLUSIONES")"
grep -qxF 'deploy/compose/tools/.env.onpremise' "$EXCLUSIONES" 2>/dev/null || \
  echo 'deploy/compose/tools/.env.onpremise' >> "$EXCLUSIONES"

# Validates and starts Jenkins, the smee.io relay, and the rest of the stack.
docker compose --env-file "$ARCHIVO_ENTORNO" -f "$ARCHIVO_COMPOSE" -p cg-tools config -q
docker compose --env-file "$ARCHIVO_ENTORNO" -f "$ARCHIVO_COMPOSE" -p cg-tools up -d --build --remove-orphans
aplicar_credenciales_sonar

echo "Stack iniciado."
echo "Jenkins: http://${IP_EQUIPO}:8080/"
echo "Archivo de secretos: $ARCHIVO_ENTORNO (permisos 600)."
echo "No subas ese archivo al repositorio."

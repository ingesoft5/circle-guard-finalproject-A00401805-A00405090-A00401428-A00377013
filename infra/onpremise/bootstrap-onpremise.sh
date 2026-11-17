#!/usr/bin/env bash
# Prepara el stack local de herramientas del equipo, incluido el relay de smee.io.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PLANTILLA="$RAIZ/deploy/compose/tools/.env.onpremise.example"
ARCHIVO_ENTORNO="$RAIZ/deploy/compose/tools/.env.onpremise"
ARCHIVO_COMPOSE="$RAIZ/deploy/compose/tools/docker-compose.onpremise.yml"

[[ -f "$PLANTILLA" ]] || { echo "Falta $PLANTILLA"; exit 1; }
[[ -f "$ARCHIVO_COMPOSE" ]] || { echo "Falta $ARCHIVO_COMPOSE"; exit 1; }

# Reemplaza o agrega una clave sin romper valores que contengan signos especiales.
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

# Detecta la dirección de ZeroTier; si no existe, usa la ruta por defecto.
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

# Crea el archivo real solo si no existe o está vacío.
if [[ ! -s "$ARCHIVO_ENTORNO" ]]; then
  install -m 600 "$PLANTILLA" "$ARCHIVO_ENTORNO"
fi
chmod 600 "$ARCHIVO_ENTORNO"

# Genera únicamente los secretos que todavía tengan el marcador.
while IFS= read -r clave; do
  [[ -n "$clave" ]] || continue
  establecer_valor "$clave" "$(openssl rand -hex 24)"
done < <(grep -oE '^[A-Z0-9_]+=__GENERATE__$' "$ARCHIVO_ENTORNO" | cut -d= -f1 || true)

# Ajusta las direcciones del equipo en cada ejecución.
for clave in TOOLS_IP TOOLS_PRIVATE_IP DEV_PRIVATE_IP STAGE_PRIVATE_IP PROD_PRIVATE_IP; do
  establecer_valor "$clave" "$IP_EQUIPO"
done
establecer_valor "JENKINS_URL" "http://${IP_EQUIPO}:8080/"

# El registro y el relay del webhook son fijos en este host.
establecer_valor "NEXUS_REGISTRY" "${IP_EQUIPO}:5000"
establecer_valor "SMEE_URL" "${SMEE_URL:-https://smee.io/nk4nvlnyB0u6Fqnn}"
establecer_valor "SMEE_TARGET" "${SMEE_TARGET:-http://localhost:8080/github-webhook/}"

# Pide el usuario y el token personal solo si faltan o siguen con marcador.
USUARIO_PERSONAL="$(grep -m1 '^GITHUB_USER=' "$ARCHIVO_ENTORNO" | cut -d= -f2- || true)"
TOKEN_PERSONAL="$(grep -m1 '^GITHUB_TOKEN=' "$ARCHIVO_ENTORNO" | cut -d= -f2- || true)"

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

# Verifica que no queden marcadores sin resolver.
if grep -qE '=__(GENERATE|HOST_IP|GITHUB_)' "$ARCHIVO_ENTORNO"; then
  echo "Quedaron marcadores sin reemplazar en $ARCHIVO_ENTORNO:"
  grep -nE '=__(GENERATE|HOST_IP|GITHUB_)' "$ARCHIVO_ENTORNO"
  exit 1
fi

# Excluye el archivo real del repositorio sin modificar el archivo versionado.
EXCLUSIONES="$RAIZ/.git/info/exclude"
mkdir -p "$(dirname "$EXCLUSIONES")"
grep -qxF 'deploy/compose/tools/.env.onpremise' "$EXCLUSIONES" 2>/dev/null || \
  echo 'deploy/compose/tools/.env.onpremise' >> "$EXCLUSIONES"

# Valida y arranca jenkins, el relay de smee.io y el resto del stack.
docker compose --env-file "$ARCHIVO_ENTORNO" -f "$ARCHIVO_COMPOSE" -p cg-tools config -q
docker compose --env-file "$ARCHIVO_ENTORNO" -f "$ARCHIVO_COMPOSE" -p cg-tools up -d --build --remove-orphans

echo "Stack iniciado."
echo "Jenkins: http://${IP_EQUIPO}:8080/"
echo "Archivo de secretos: $ARCHIVO_ENTORNO (permisos 600)."
echo "No subas ese archivo al repositorio."

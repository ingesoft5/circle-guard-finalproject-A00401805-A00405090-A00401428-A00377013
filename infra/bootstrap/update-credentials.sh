#!/usr/bin/env bash
# CG-DOC: Replaces the local AWS and GitHub credentials used by the CircleGuard bootstrap.
# Las claves se escriben en ~/.aws, en el llavero de gh y en los .env locales (600),
# nunca en el repositorio. Uso: update-credentials.sh [--apply]
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/bootstrap-common.sh"

GH_SCOPES="repo,admin:repo_hook,workflow,read:org"
APPLY="${1:-}"

sync_tools_credentials() {
  local user token file
  user="$(gh api user -q .login)"
  token="$(gh auth token)"
  [[ -n "${user}" && -n "${token}" ]] || die "No se pudo leer el usuario o el token de gh."

  for file in "${STATE_DIR}/tools.env" "${REPO_ROOT}/deploy/compose/tools/.env"; do
    if [[ ! -f "${file}" ]]; then
      log_warn "No existe ${file}; se omite. Ejecuta bootstrap-all.sh para generarlo."
      continue
    fi
    set_env_kv "${file}" GITHUB_USER "${user}"
    set_env_kv "${file}" GITHUB_TOKEN "${token}"
    chmod 600 "${file}"
    log_info "Credenciales de GitHub sincronizadas en ${file}."
  done
}

main() {
  require_cmd aws gh curl

  log_info "Paso 1/2: credenciales de AWS (perfil default, region us-east-2)."
  log_info "Pega el Access Key ID y el Secret Access Key nuevos cuando se soliciten."
  aws configure
  aws sts get-caller-identity >/dev/null || die "AWS no quedó configurado correctamente."
  log_info "AWS OK -> cuenta $(aws sts get-caller-identity --query Account --output text) / $(aws sts get-caller-identity --query Arn --output text)"

  log_info "Paso 2/2: token de GitHub."
  log_info "Autentícate con una cuenta que tenga permiso sobre ingesoft5/circle-guard-finalproject-*."
  gh auth login --hostname github.com --git-protocol ssh --scopes "${GH_SCOPES}"
  gh auth status >/dev/null || die "GitHub no quedó autenticado correctamente."
  log_info "GitHub OK -> $(gh api user -q .login)"
  sync_tools_credentials

  if [[ "${APPLY}" == "--apply" ]]; then
    log_info "Recreando tools para que Jenkins tome las credenciales nuevas..."
    "${BOOTSTRAP_DIR}/bootstrap-all.sh" --tools-only
  fi

  cat <<EOF

Credenciales actualizadas y sincronizadas con infra/state/tools.env y
deploy/compose/tools/.env (600, ignorados por Git).

Para aplicar el cambio en Jenkins ejecuta:

  ./infra/bootstrap/update-credentials.sh --apply

O, si prefieres hacerlo manualmente:

  export AWS_REGION=us-east-2
  export ADMIN_CIDR="\$(curl -s https://checkip.amazonaws.com)/32"
  export TEACHER_CIDR="\${ADMIN_CIDR}"
  export GITHUB_USER="\$(gh api user -q .login)"
  export GITHUB_TOKEN="\$(gh auth token)"
  ./infra/bootstrap/bootstrap-check.sh
  ./infra/bootstrap/bootstrap-all.sh --tools-only

El bootstrap conserva los secretos existentes de Jenkins, SonarQube, Nexus, JWT y QR.
EOF
}

main "$@"

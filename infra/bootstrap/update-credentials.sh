#!/usr/bin/env bash
# CG-DOC: Replaces the local AWS and GitHub credentials used by the CircleGuard bootstrap.
# Las claves se escriben directamente en ~/.aws y en el llavero de gh; nunca en el repositorio.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/bootstrap-common.sh"

GH_SCOPES="repo,admin:repo_hook,workflow,read:org"

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

  cat <<EOF

Credenciales actualizadas. Para aprovisionar la infraestructura exporta:

  export AWS_REGION=us-east-2
  export ADMIN_CIDR="\$(curl -s https://checkip.amazonaws.com)/32"
  export TEACHER_CIDR="\${ADMIN_CIDR}"
  export GITHUB_USER="\$(gh api user -q .login)"
  export GITHUB_TOKEN="\$(gh auth token)"

Luego ejecuta:

  ./infra/bootstrap/bootstrap-check.sh
  ./infra/bootstrap/bootstrap-all.sh

El bootstrap generará secretos nuevos para Jenkins, SonarQube, Nexus, JWT y QR en
infra/state/tools.env y deploy/compose/*/.env (ambos ignorados por Git).
EOF
}

main "$@"

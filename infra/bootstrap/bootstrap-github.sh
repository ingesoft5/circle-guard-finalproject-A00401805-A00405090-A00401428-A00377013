#!/usr/bin/env bash
# CG-DOC: Configures the GitHub webhook and protects main with the Jenkins PR check.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/bootstrap-common.sh"

ENV_FILE="${STATE_DIR}/tools.env"
[ -f "${ENV_FILE}" ] || ENV_FILE="${REPO_ROOT}/deploy/compose/tools/.env"
[ -f "${ENV_FILE}" ] || die "Falta ${ENV_FILE}; ejecuta bootstrap-tools.sh primero."
# shellcheck disable=SC1090
set -a; source "${ENV_FILE}"; set +a

: "${GITHUB_TOKEN:?Falta GITHUB_TOKEN}"
: "${GITHUB_OWNER:?Falta GITHUB_OWNER}"
: "${GITHUB_REPO:?Falta GITHUB_REPO}"
: "${TOOLS_IP:?Falta TOOLS_IP}"
: "${GITHUB_WEBHOOK_SECRET:?Falta GITHUB_WEBHOOK_SECRET}"

if [ "${GITHUB_TOKEN}" = "change-me" ]; then
  die "GITHUB_TOKEN sigue en change-me; configura un token con repo y admin:repo_hook."
fi

API="https://api.github.com"
HOOK_URL="http://${TOOLS_IP}/github-webhook/"
REQUIRED_CHECK="continuous-integration/jenkins/pr-merge"
REPO_PATH="repos/${GITHUB_OWNER}/${GITHUB_REPO}"

gh_api() {
  local method="$1" path="$2" data="${3:-}"
  if [ -n "${data}" ]; then
    curl -fsS -X "${method}" \
      -H "Authorization: Bearer ${GITHUB_TOKEN}" \
      -H "Accept: application/vnd.github+json" \
      -H "X-GitHub-Api-Version: 2022-11-28" \
      -H "Content-Type: application/json" \
      -d "${data}" "${API}/${path}"
  else
    curl -fsS -X "${method}" \
      -H "Authorization: Bearer ${GITHUB_TOKEN}" \
      -H "Accept: application/vnd.github+json" \
      -H "X-GitHub-Api-Version: 2022-11-28" \
      "${API}/${path}"
  fi
}

if ! gh_api GET user >/dev/null 2>&1; then
  die "El token de GitHub no es válido o no tiene permisos basicos."
fi

hook_payload="$(jq -n \
  --arg url "${HOOK_URL}" \
  --arg secret "${GITHUB_WEBHOOK_SECRET}" \
  '{
    name: "web",
    active: true,
    events: ["push", "pull_request"],
    config: {
      url: $url,
      content_type: "json",
      secret: $secret,
      insecure_ssl: "0"
    }
  }')"

hook_id="$(gh_api GET "${REPO_PATH}/hooks" 2>/dev/null \
  | jq -r --arg u "${HOOK_URL}" '.[]? | select(.config.url == $u) | .id' | head -n1 || true)"

if [ -n "${hook_id}" ] && [ "${hook_id}" != "null" ]; then
  if gh_api PATCH "${REPO_PATH}/hooks/${hook_id}" "${hook_payload}" >/dev/null; then
    log_info "Webhook de GitHub actualizado: ${HOOK_URL}"
  else
    log_warn "No se pudo actualizar el webhook ${hook_id}; revisa admin:repo_hook."
  fi
else
  if gh_api POST "${REPO_PATH}/hooks" "${hook_payload}" >/dev/null; then
    log_info "Webhook de GitHub creado: ${HOOK_URL}"
  else
    log_warn "No se pudo crear el webhook; crealo manualmente en Settings > Webhooks."
  fi
fi

default_branch="$(gh_api GET "${REPO_PATH}" | jq -r '.default_branch // "main"')"
protection_payload="$(jq -n --arg check "${REQUIRED_CHECK}" '{
  required_status_checks: {
    strict: true,
    contexts: [$check]
  },
  enforce_admins: false,
  required_pull_request_reviews: {
    dismiss_stale_reviews: true,
    require_code_owner_reviews: false,
    required_approving_review_count: 2
  },
  restrictions: null,
  allow_force_pushes: false,
  allow_deletions: false,
  required_conversation_resolution: true
}')"

if gh_api GET "${REPO_PATH}/branches/${default_branch}/protection" >/dev/null 2>&1; then
  if gh_api PUT "${REPO_PATH}/branches/${default_branch}/protection" "${protection_payload}" >/dev/null; then
    log_info "Branch protection actualizada en ${default_branch} (check ${REQUIRED_CHECK})."
  else
    log_warn "No se pudo actualizar branch protection; revisa permisos de administracion."
  fi
else
  if gh_api PUT "${REPO_PATH}/branches/${default_branch}/protection" "${protection_payload}" >/dev/null; then
    log_info "Branch protection creada en ${default_branch} (check ${REQUIRED_CHECK})."
  else
    log_warn "No se pudo crear branch protection; revisa permisos de administracion."
  fi
fi

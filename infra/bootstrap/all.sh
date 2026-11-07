#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"

"${DIR}/00-check-prerequisites.sh"
"${DIR}/01-provision-aws.sh"
"${DIR}/02-bootstrap-host.sh"
"${DIR}/03-start-tooling.sh"
"${DIR}/04-configure-tools.sh"
"${DIR}/05-create-environments.sh"

echo "Plataforma CircleGuard lista."
echo "Siguiente paso: crear los archivos .env de dev/stage/prod y ejecutar el pipeline de Jenkins."

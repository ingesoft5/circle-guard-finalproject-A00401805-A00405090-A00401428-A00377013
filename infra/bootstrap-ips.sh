#!/usr/bin/env bash
# CG-DOC: Prints private and public IPs and public tool URLs.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/bootstrap-common.sh"
load_state

echo "Jenkins   http://${TOOLS_IP}:8080"
echo "SonarQube http://${TOOLS_IP}:9000"
echo "Nexus     http://${TOOLS_IP}:8081"
echo "App       http://${TOOLS_IP}/"
echo "Registry  ${TOOLS_PRIVATE_IP}:5000 (private)"
for e in dev stage prod; do
  up="${e^^}"
  priv="${up}_PRIVATE_IP"; pub="${up}_PUBLIC_IP"
  echo "${e}: private=${!priv} public=${!pub}"
done

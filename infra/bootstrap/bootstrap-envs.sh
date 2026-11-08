#!/usr/bin/env bash
# CG-DOC: Compatibility wrapper for infra/bootstrap-envs.sh.
exec "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/bootstrap-envs.sh" "$@"

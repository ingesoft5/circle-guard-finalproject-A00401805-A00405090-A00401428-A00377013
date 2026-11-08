#!/usr/bin/env bash
# CG-DOC: Compatibility wrapper for infra/bootstrap-stop.sh.
exec "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/bootstrap-stop.sh" "$@"

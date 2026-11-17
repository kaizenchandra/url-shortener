#!/usr/bin/env bash
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
export BASE_URL="${BASE_URL:-http://localhost:${APP_PORT:-8081}}"
export KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:${KEYCLOAK_PORT:-9001}}"
TOKEN="$(bash "$here/token.sh")"
export TOKEN
python3 "$here/smoke.py"

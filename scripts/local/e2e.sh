#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

"$ROOT_DIR/scripts/local/health.sh"
"$ROOT_DIR/scripts/local/smoke.sh"

echo "[ok] 基础 E2E 冒烟通过"

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/common.sh"

load_env

TARGET_URL="${1:-http://127.0.0.1:${ZHIXUE_GATEWAY_PORT}/course/list?pageNum=1&pageSize=10}"
TOTAL_REQUESTS="${TOTAL_REQUESTS:-50}"
CONCURRENCY="${CONCURRENCY:-10}"

echo "[perf] target=$TARGET_URL total=$TOTAL_REQUESTS concurrency=$CONCURRENCY"

seq "$TOTAL_REQUESTS" | xargs -P "$CONCURRENCY" -I{} curl -fsS -o /dev/null "$TARGET_URL"

echo "[ok] 压测样例执行完成"

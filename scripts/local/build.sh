#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "[build] Maven tests"
(cd "$ROOT_DIR" && mvn test)

echo "[build] Admin web build"
(cd "$ROOT_DIR/zhixue-admin-web" && npm run build)

echo "[ok] 本地构建完成"

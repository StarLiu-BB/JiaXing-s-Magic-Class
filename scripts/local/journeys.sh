#!/usr/bin/env bash
# 三端联合旅程（老师端 / 管理端 / 学生端，经网关与真实服务，含本机 Ollama）。
# 前置：服务已启动（up.sh）、Ollama 已就绪、local.env 中 ZHIXUE_AUTH_WECHAT_MODE=sandbox。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/common.sh"
load_env

command -v node >/dev/null || { echo "[fail] 需要 Node.js 22+（使用内置 fetch 与 WebSocket）"; exit 1; }
export ENV_FILE COMPOSE_FILE
exec node "$SCRIPT_DIR/journeys.mjs"

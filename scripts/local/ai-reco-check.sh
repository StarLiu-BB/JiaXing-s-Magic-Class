#!/usr/bin/env bash
# 推荐效果验收（设计文档 5.3 的 5 个用例）：打印真实余弦分数，并经网关走一遍流式对话。
# 前置：服务已启动、Ollama 就绪、已执行 Java 课与知识资料的增量脚本、local.env 中 ZHIXUE_AUTH_WECHAT_MODE=sandbox。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/common.sh"
load_env

command -v node >/dev/null || { echo "[fail] 需要 Node.js 22+（使用内置 fetch）"; exit 1; }
export ENV_FILE COMPOSE_FILE
exec node "$SCRIPT_DIR/ai-reco-check.mjs"

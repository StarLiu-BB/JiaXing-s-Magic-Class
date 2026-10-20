#!/usr/bin/env bash
# 学生端网页版界面验收（设计文档 7.3）：真实浏览器 + 真实网关 + 本机 Ollama。
#
# 用法：
#   bash scripts/local/student-web-check.sh               # 跑全部已有分段
#   bash scripts/local/student-web-check.sh login course  # 只跑指定分段
# 分段在 scripts/local/ui/student-web-<名称>.sh，按 login → course → shell → chat 的顺序执行；
# 每个分段自成一体，结束时复原它改动的数据（购买会退款、会话会删除、停掉的 Ollama 会恢复）。
#
# 前置：服务已启动；zhixue-student-web 下已 npm ci；local.env 中 ZHIXUE_AUTH_WECHAT_MODE=sandbox；
#       已安装 playwright-cli（npm i -g @playwright/cli）。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
source "$SCRIPT_DIR/common.sh"
load_env
# common.sh 开启了 set -e：验收要跑完所有检查再汇总，这里关掉
set +e +u +o pipefail
source "$SCRIPT_DIR/lib/ui.sh"

WEB="http://localhost:3001"
GW="http://127.0.0.1:${ZHIXUE_GATEWAY_PORT:-19001}"
WEB_DIR="${ZHIXUE_STUDENT_WEB_DIR:-$ROOT_DIR/zhixue-student-web}"
OUT="${ZHIXUE_UI_OUT:-/tmp/zhixue-student-web-check}"
mkdir -p "$OUT"
FAIL=0
# 分段登记需要在退出时复原的数据
CLEANUP_CONVERSATIONS=()
CLEANUP_REFUND_COURSES=()
OLLAMA_STOPPED=0
VITE_PID=""

cleanup() {
  if [[ "$OLLAMA_STOPPED" == 1 ]]; then
    brew services start ollama >/dev/null 2>&1
  fi
  if [[ ${#CLEANUP_CONVERSATIONS[@]} -gt 0 ]]; then
    local ids
    ids=$(IFS=,; echo "${CLEANUP_CONVERSATIONS[*]}")
    docker exec zhixue-local-mysql mysql --default-character-set=utf8mb4 -uroot -p"$ZHIXUE_DB_PASSWORD" -N -e \
      "DELETE FROM zhixue_ai.ai_message WHERE conversation_id IN ($ids); DELETE FROM zhixue_ai.ai_conversation WHERE id IN ($ids);" \
      2>&1 | grep -v "Using a password"
    echo "[info] 已清理本次创建的 ${#CLEANUP_CONVERSATIONS[@]} 个会话"
  fi
  local course
  for course in "${CLEANUP_REFUND_COURSES[@]}"; do
    refund_course "$course"
  done
  playwright-cli close >/dev/null 2>&1
  [[ -n "$VITE_PID" ]] && kill "$VITE_PID" >/dev/null 2>&1
}
trap cleanup EXIT

command -v playwright-cli >/dev/null || { echo "[fail] 需要 playwright-cli（npm i -g @playwright/cli）"; exit 1; }
[[ -d "$WEB_DIR/node_modules" ]] || { echo "[fail] 先在 $WEB_DIR 下执行 npm ci"; exit 1; }
ensure_student_web || exit 1
# playwright-cli 会在当前目录写会话日志：在输出目录里运行，不污染仓库
cd "$OUT" || exit 1
playwright-cli open "$WEB/login" >/dev/null 2>&1 || playwright-cli goto "$WEB/login" >/dev/null 2>&1
playwright-cli resize 1440 900 >/dev/null 2>&1

# 已登录状态：直接写入令牌（登录表单本身由 login 分段验收）
login_as_student() {
  playwright-cli goto "$WEB/login" >/dev/null 2>&1
  playwright-cli localstorage-set zx_student_token "$(student_token ui-check)" >/dev/null 2>&1
}

# 课程 2007 必须是未购状态，购买流程才验收得了
require_not_purchased() {
  local owned
  owned=$(curl -s "$GW/course/detail/2007" -H "Authorization: Bearer $(student_token ui-check)" \
    | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["canStudy"])')
  if [[ "$owned" != "False" ]]; then
    echo "[fail] 学生已购买课程 2007，购买流程无法验收：先执行 source scripts/local/lib/ui.sh && refund_course 2007"
    FAIL=1
    return 1
  fi
}

ALL_SECTIONS=(login course shell chat)
REQUESTED=("$@")
[[ ${#REQUESTED[@]} -eq 0 ]] && REQUESTED=("${ALL_SECTIONS[@]}")
for name in "${ALL_SECTIONS[@]}"; do
  [[ " ${REQUESTED[*]} " == *" $name "* ]] || continue
  file="$SCRIPT_DIR/ui/student-web-$name.sh"
  if [[ ! -f "$file" ]]; then
    echo "[skip] ${name}：分段尚未实现（${file}）"
    continue
  fi
  echo "===================== $name ====================="
  source "$file"
  "section_$name"
done

echo
echo "截图（1440 与 390 两种宽度请人工核对）：$OUT"
ls "$OUT"/*.png 2>/dev/null
if [[ "$FAIL" == 0 ]]; then echo "[ok] 学生端网页版界面验收全部通过"; else echo "[fail] 学生端网页版界面验收有失败项"; fi
exit "$FAIL"

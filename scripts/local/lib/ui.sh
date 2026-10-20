# 本地界面验收共用函数（被 student-web-check.sh 等脚本 source）。依赖 playwright-cli、python3、docker。
#
# 用法：
#   pwf <<'JS'                      # 执行一段 Playwright 代码（函数参数为 page），输出返回值
#   async page => { return await page.title() }
#   JS
#   check "说明" "$实际" "$期望"       # 相等输出 [ok]，否则 [fail] 并把 FAIL 置 1
#
# 注意：调用这些函数的脚本要以文件方式执行（bash x.sh），不要用 `bash <<'SH'` 从标准输入喂脚本——
# playwright-cli 与 docker exec -i 会读走标准输入，吞掉后续脚本。

FAIL=${FAIL:-0}

check() {
  if [[ "$2" == "$3" ]]; then
    echo "[ok] $1"
  else
    echo "[fail] $1：期望 [$3]，实际 [$2]"
    FAIL=1
  fi
}

# 从 playwright-cli 输出中取返回值；失败时输出「ERROR: …」，让 check 显示原因
pw_result() {
  python3 -c '
import json, sys
text = sys.stdin.read()
lines = text.splitlines()
for i, line in enumerate(lines):
    if line.startswith("### Result") and i + 1 < len(lines):
        raw = lines[i + 1].strip()
        try:
            value = json.loads(raw)
        except ValueError:
            value = raw
        print(value if isinstance(value, str) else json.dumps(value, ensure_ascii=False))
        sys.exit(0)
    if line.startswith("### Error"):
        print("ERROR: " + " ".join(l.strip() for l in lines[i + 1:i + 4])[:300])
        sys.exit(0)
print("ERROR: 无结果 " + text[:200].replace("\n", " "))'
}

pwf() {
  local f
  f=$(mktemp /tmp/zx_pw_XXXXXX)
  cat > "$f"
  playwright-cli run-code --filename "$f" 2>&1 | pw_result
  rm -f "$f"
}

# json_get '<json>' 'a.0.b'：取字段；字符串原样输出，其他类型输出 JSON；取不到为空
json_get() {
  python3 -c '
import json, sys
try:
    v = json.loads(sys.argv[1])
    for k in sys.argv[2].split("."):
        v = v[int(k)] if isinstance(v, list) else v[k]
    print(v if isinstance(v, str) else json.dumps(v, ensure_ascii=False))
except Exception:
    print("")' "$1" "$2"
}

redis_get() {
  docker exec zhixue-local-redis redis-cli -a "$ZHIXUE_REDIS_PASSWORD" --no-auth-warning GET "$1" | tr -d '\r'
}

# 学生令牌：本地微信沙箱登录（local.env 中 ZHIXUE_AUTH_WECHAT_MODE=sandbox），映射到 student 账号
student_token() {
  curl -s -X POST "$GW/auth/login" -H 'Content-Type: application/json' \
    -d "{\"loginType\":\"wechat\",\"wechatCode\":\"${1:-ui-check}\"}" \
    | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["token"])'
}

# 管理员令牌：验证码从 Redis 读取
admin_token() {
  local uuid code
  uuid=$(curl -s "$GW/auth/captcha" | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["uuid"])')
  code=$(redis_get "zhixue:auth:captcha:$uuid")
  curl -s -X POST "$GW/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"admin\",\"password\":\"123456\",\"code\":\"$code\",\"uuid\":\"$uuid\",\"loginType\":\"password\"}" \
    | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["token"])'
}

# 退掉学生（1003）对某门课的已支付订单，让课程恢复为未购；没有则什么都不做
refund_course() {
  local course_id="$1" at oid
  at=$(admin_token)
  oid=$(curl -s "$GW/order/list?pageNum=1&pageSize=50&userId=1003" -H "Authorization: Bearer $at" \
    | python3 -c 'import json,sys
r = json.load(sys.stdin)["data"]["records"]
print(next((str(x["id"]) for x in r if str(x["productId"]) == sys.argv[1] and x["status"] == 1), ""))' "$course_id")
  if [[ -n "$oid" ]]; then
    curl -s -o /dev/null -X PUT "$GW/order/$oid/refund" -H "Authorization: Bearer $at" \
      -H 'Content-Type: application/json' -d '{"reason":"本地界面验收后复原"}'
    echo "[info] 已退款订单 ${oid}，课程 $course_id 恢复为未购"
  fi
}

# 学生（1003）用接口直接买下一门课（下单 → 模拟收银台 → 确认），界面购买流程由 course 分段验收
purchase_course() {
  local token order_no
  token=$(student_token "${2:-ui-check}")
  order_no=$(curl -s -X POST "$GW/order/create" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    -d "{\"productId\":\"$1\"}" | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["orderNo"])')
  curl -s -o /dev/null -X POST "$GW/pay" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    -d "{\"orderNo\":\"$order_no\",\"payChannel\":\"mock\"}"
  curl -s -X POST "$GW/pay/mock/confirm" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    -d "{\"orderNo\":\"$order_no\"}" | python3 -c 'import json,sys;print(json.load(sys.stdin)["code"])'
}

# 学生网页版开发服务器：没在运行就在后台启动，设置 VITE_PID（由调用方在退出时结束）
ensure_student_web() {
  local web="http://localhost:3001"
  curl -sf -m 3 "$web/" >/dev/null && return 0
  (cd "${WEB_DIR:-$ROOT_DIR/zhixue-student-web}" && BROWSER=none npx vite --port 3001 --strictPort > /tmp/zhixue-student-web-vite.log 2>&1) &
  VITE_PID=$!
  for _ in $(seq 1 30); do curl -sf -m 2 "$web/" >/dev/null && return 0; sleep 1; done
  echo "[fail] 学生网页版开发服务器没有启动，见 /tmp/zhixue-student-web-vite.log"
  return 1
}

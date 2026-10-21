#!/usr/bin/env bash
# 小程序端接口联调：按 pages/ 里的真实调用顺序，经网关跑一遍小程序各页依赖的接口。
# 只做只读与「可复原」的写操作（下单后不支付、收藏后取消），不改动种子数据。
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/../.." || exit 1
source scripts/local/common.sh && load_env
set +e +o pipefail

GW="http://127.0.0.1:${ZHIXUE_GATEWAY_PORT:-19001}"
MP="zhixue-app-mp/miniprogram"
FAIL=0
PASS=0

check() {
  local label expected actual
  label=${1:-}
  expected=${2:-}
  actual=${3:-}
  if [[ "$actual" == "$expected" ]]; then
    PASS=$((PASS + 1)); echo "[ok] $label"
  else
    FAIL=$((FAIL + 1)); echo "[fail] ${label}：期望 [${expected}]，实际 [${actual}]"
  fi
}

# 小程序登录：本地微信沙箱（所有微信用户映射为 student）
login_mp() {
  curl -s -X POST "$GW/auth/login" -H 'Content-Type: application/json' \
    -d "{\"loginType\":\"wechat\",\"wechatCode\":\"mp-$1\"}" \
    | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["token"])'
}

get() { curl -s "$GW$1" -H "Authorization: Bearer $TOKEN"; }
code_of() { python3 -c 'import json,sys;print(json.load(sys.stdin).get("code"))'; }

TOKEN=$(login_mp join)
check "微信沙箱登录拿到令牌" "True" "$([[ ${#TOKEN} -gt 100 ]] && echo True || echo False)"

# 首页：banner / 分类 / 热门 / 最新 / 课程列表
check "首页 banner" 200 "$(get '/course/banner/list' | code_of)"
check "分类列表" 200 "$(get '/course/category/list' | code_of)"
check "热门课程" 200 "$(get '/course/hot/list' | code_of)"
check "最新课程" 200 "$(get '/course/latest/list' | code_of)"
check "课程列表分页" 200 "$(get '/course/list?pageNum=1&pageSize=10' | code_of)"

# 详情与章节（含新的 Java 种子课）
BODY=$(get '/course/detail/2007')
check "课程详情（Java 并发编程实战）" "Java 并发编程实战" \
  "$(echo "$BODY" | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["title"])')"
check "章节列表返回 3 章" "3" \
  "$(get '/course/2007/chapters' | python3 -c 'import json,sys;print(len(json.load(sys.stdin)["data"]))')"
check "未购时锁定课时不下发视频地址" "0" \
  "$(get '/course/2007/chapters' | python3 -c '
import json,sys
d=json.load(sys.stdin)["data"]
locked=[s for c in d for s in (c.get("sections") or []) if s.get("locked")]
print(sum(1 for s in locked if s.get("videoUrl")))')"

# AI 对话（小程序用的是非流式接口，一期保持兼容）
check "AI 答疑（限定课程）" 200 \
  "$(curl -s -m 120 -G "$GW/ai/chat/course" --data-urlencode 'question=线程池参数怎么设' \
      --data-urlencode 'courseId=2007' -H "Authorization: Bearer $TOKEN" | code_of)"

# 收藏（后端是 toggle 语义，与小程序 api/course.js 一致）：收藏 → 查列表 → 再点一次复原
check "收藏课程（toggle）" "True" "$(curl -s -X POST "$GW/course/interaction/favorite/2007" \
  -H "Authorization: Bearer $TOKEN" | python3 -c 'import json,sys;print(json.load(sys.stdin).get("data") is True)')"
check "收藏列表含该课程" "True" \
  "$(get '/course/interaction/favorites?pageNum=1&pageSize=10' | python3 -c '
import json,sys
d=json.load(sys.stdin)["data"]
rows=d["records"] if isinstance(d,dict) else d
print(any(str(r.get("courseId") or r.get("id"))=="2007" for r in rows))')"
check "再点一次取消收藏（复原）" "False" "$(curl -s -X POST "$GW/course/interaction/favorite/2007" \
  -H "Authorization: Bearer $TOKEN" | python3 -c 'import json,sys;print(json.load(sys.stdin).get("data") is True)')"

# 优惠券（只读）
check "可用优惠券" 200 "$(get '/marketing/coupon/available' | code_of)"

# 下单但不支付，结束时按本次的 orderNo 取消（只动自己创建的那一笔）
ORDER=$(curl -s -X POST "$GW/order/create" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"productId":"2007"}')
check "创建订单" 200 "$(echo "$ORDER" | code_of)"
ORDER_NO=$(echo "$ORDER" | python3 -c 'import json,sys;d=json.load(sys.stdin).get("data") or {};print(d.get("orderNo") or "")')
ORDER_ID=$(echo "$ORDER" | python3 -c 'import json,sys;d=json.load(sys.stdin).get("data") or {};print(d.get("id") or "")')
check "订单详情" 200 "$(get "/order/detail?orderNo=$ORDER_NO" | code_of)"
check "我的订单列表" 200 "$(get '/order/list?pageNum=1&pageSize=10' | code_of)"
# 收尾：取消本次下单，避免在库里留下待支付订单（按 orderNo 逐笔核对，不碰种子里的示例订单）
if [[ -n "$ORDER_ID" ]]; then
  CANCELED=$(curl -s -X PUT "$GW/order/${ORDER_ID}/cancel" -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' -d '{"reason":"本地联调数据清理"}' | code_of)
  echo "   已取消本次订单 ${ORDER_NO}：code=${CANCELED}"
fi

echo
if [[ $FAIL -eq 0 ]]; then
  echo "[ok] 小程序接口联调全部通过（$PASS 项）"
else
  echo "[fail] 小程序接口联调：$FAIL 项失败，$PASS 项通过"
fi
exit $((FAIL > 0))

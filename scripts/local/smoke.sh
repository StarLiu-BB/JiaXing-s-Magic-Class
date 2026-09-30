#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/common.sh"

load_env

GATEWAY_URL="http://127.0.0.1:${ZHIXUE_GATEWAY_PORT}"

extract_json_field() {
  local field="$1"
  python3 -c '
import json
import sys

field = sys.argv[1]
payload = json.load(sys.stdin)
value = payload
for key in field.split("."):
    if isinstance(value, dict):
        value = value.get(key)
    else:
        value = None
        break
print("" if value is None else value)
' "$field"
}

assert_code() {
  local payload="$1"
  local expected="$2"
  local label="$3"
  local code
  code="$(printf '%s' "$payload" | extract_json_field code)"
  if [[ "$code" != "$expected" ]]; then
    echo "[fail] $label: code=${code:-empty}, payload=$payload"
    exit 1
  fi
  echo "[ok] $label"
}

assert_contains() {
  local haystack="$1"
  local needle="$2"
  local label="$3"
  if [[ "$haystack" != *"$needle"* ]]; then
    echo "[fail] $label"
    exit 1
  fi
  echo "[ok] $label"
}

assert_not_contains() {
  local haystack="$1"
  local needle="$2"
  local label="$3"
  if [[ "$haystack" == *"$needle"* ]]; then
    echo "[fail] $label"
    exit 1
  fi
  echo "[ok] $label"
}

login_and_fetch_info() {
  local username="$1"
  local password="$2"

  local captcha_json uuid code login_json token info_json
  captcha_json="$(curl -fsS "${GATEWAY_URL}/auth/captcha")"
  uuid="$(printf '%s' "$captcha_json" | extract_json_field data.uuid)"
  code="$(compose exec -T redis redis-cli -a "${ZHIXUE_REDIS_PASSWORD}" GET "zhixue:auth:captcha:${uuid}" | tr -d '\r')"

  login_json="$(curl -fsS "${GATEWAY_URL}/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${username}\",\"password\":\"${password}\",\"code\":\"${code}\",\"uuid\":\"${uuid}\",\"loginType\":\"password\"}")"
  token="$(printf '%s' "$login_json" | extract_json_field data.token)"
  if [[ -z "$token" ]]; then
    echo "[fail] ${username} 登录失败: $login_json" >&2
    exit 1
  fi
  info_json="$(curl -fsS "${GATEWAY_URL}/auth/user/info" -H "Authorization: Bearer ${token}")"
  assert_code "$info_json" "200" "${username} user info 返回成功" >/dev/null

  printf '%s\n%s\n' "$token" "$info_json"
}

echo "[smoke] 校验匿名公开资源"
public_list="$(curl -fsS "${GATEWAY_URL}/course/list?pageNum=1&pageSize=10")"
assert_code "$public_list" "200" "匿名课程列表返回成功"
assert_contains "$public_list" "Spring Cloud 微服务实战" "匿名课程列表返回公开课程"
assert_not_contains "$public_list" "课程运营草稿演示" "匿名课程列表不返回草稿"

public_detail="$(curl -fsS "${GATEWAY_URL}/course/detail/2001")"
assert_code "$public_detail" "200" "匿名课程详情返回成功"
assert_contains "$public_detail" "Spring Cloud 微服务实战" "匿名课程详情可访问公开课程"

draft_detail="$(curl -s "${GATEWAY_URL}/course/detail/2003")"
assert_contains "$draft_detail" "课程不存在" "匿名无法访问草稿课程"

echo "[smoke] 管理员登录与核心管理接口"
admin_result="$(login_and_fetch_info admin 123456)"
admin_token="$(printf '%s' "$admin_result" | sed -n '1p')"
admin_info="$(printf '%s' "$admin_result" | sed -n '2p')"
assert_contains "$admin_info" "ADMIN" "管理员角色返回正确"
assert_contains "$admin_info" "system:user:list" "管理员权限包含用户管理"

assert_code "$(curl -fsS "${GATEWAY_URL}/system/user/list?pageNum=1&pageSize=10" -H "Authorization: Bearer ${admin_token}")" "200" "管理员可访问用户列表"
assert_code "$(curl -fsS "${GATEWAY_URL}/system/role/list" -H "Authorization: Bearer ${admin_token}")" "200" "管理员可访问角色列表"
assert_code "$(curl -fsS "${GATEWAY_URL}/system/menu/list" -H "Authorization: Bearer ${admin_token}")" "200" "管理员可访问菜单列表"

echo "[smoke] 教师登录与课程权限"
teacher_result="$(login_and_fetch_info teacher teacher123)"
teacher_info="$(printf '%s' "$teacher_result" | sed -n '2p')"
assert_contains "$teacher_info" "TEACHER" "教师角色返回正确"
assert_contains "$teacher_info" "course:list" "教师权限包含课程列表"
assert_not_contains "$teacher_info" "system:user:list" "教师权限不包含系统用户管理"

echo "[smoke] 学生登录与最小权限"
student_result="$(login_and_fetch_info student student123)"
student_info="$(printf '%s' "$student_result" | sed -n '2p')"
assert_contains "$student_info" "STUDENT" "学生角色返回正确"
assert_not_contains "$student_info" "system:user:list" "学生权限不包含后台系统管理"
student_token="$(printf '%s' "$student_result" | sed -n '1p')"

echo "[smoke] 订单身份与核价（经网关）"
# 曾出现的回归：订单服务未配内部令牌，经网关转发的已登录请求被一律判为"非法内部请求"(401)。
# 单元测试覆盖不到这类配置问题，必须经网关用真实令牌验证。
sql_order() { compose exec -T mysql mysql -uroot "-p${ZHIXUE_DB_PASSWORD}" zhixue_order -e "$1" >/dev/null 2>&1; }
sql_order "delete from \`order\` where id=990101 or (user_id=1003 and product_id=2004);
  insert into \`order\` (id,order_no,user_id,product_id,product_type,amount,status,expire_time,create_time,update_time,deleted)
  values (990101,'ORD-SMOKE-ADMIN',1001,2004,0,99.00,1,date_add(now(),interval 1 day),now(),now(),0);"

student_orders="$(curl -fsS "${GATEWAY_URL}/order/list?pageNum=1&pageSize=50&userId=1001" -H "Authorization: Bearer ${student_token}")"
assert_code "$student_orders" "200" "学生可经网关查询自己的订单"
assert_not_contains "$student_orders" "ORD-SMOKE-ADMIN" "学生传 userId 也查不到他人订单"
assert_code "$(curl -fsS "${GATEWAY_URL}/order/detail?orderNo=ORD-SMOKE-ADMIN" -H "Authorization: Bearer ${student_token}")" "404" "学生读取他人订单返回不存在"
assert_code "$(curl -fsS -X PUT "${GATEWAY_URL}/order/990101/refund" -H "Authorization: Bearer ${student_token}")" "403" "学生无权退款"

created="$(curl -fsS -X POST "${GATEWAY_URL}/order/create" -H "Authorization: Bearer ${student_token}" \
  -H 'Content-Type: application/json' -d '{"productId":2004,"amount":0.01,"userId":1001}')"
assert_code "$created" "200" "学生下单成功"
assert_contains "$created" '"amount":99.00' "订单金额取自课程服务而非客户端"
assert_contains "$created" '"userId":1003' "下单人取自登录态而非请求体"
assert_contains "$(curl -fsS -X POST "${GATEWAY_URL}/order/create" -H "Authorization: Bearer ${student_token}" \
  -H 'Content-Type: application/json' -d '{"productId":2001}')" "已购买" "已购课程不能重复下单"

assert_code "$(curl -fsS "${GATEWAY_URL}/order/list?pageNum=1&pageSize=10&userId=1003" -H "Authorization: Bearer ${admin_token}")" "200" "管理员可按用户查询订单"
sql_order "delete from \`order\` where id=990101 or (user_id=1003 and product_id=2004);"

echo "[smoke] 付费课时访问控制"
# 课程 2001(199 元) 试看前 2 节；学生 1003 在种子中已购买
anon_chapters="$(curl -fsS "${GATEWAY_URL}/course/2001/chapters")"
assert_contains "$anon_chapters" "intro.mp4" "匿名可看试看课时"
assert_not_contains "$anon_chapters" "rbac.mp4" "匿名拿不到付费课时视频地址"
assert_contains "$(curl -fsS "${GATEWAY_URL}/course/2001/chapters" -H "Authorization: Bearer ${student_token}")" "rbac.mp4" "已购学生可看全部课时"
assert_code "$(curl -fsS "${GATEWAY_URL}/order/inner/purchased?userId=1001&productId=2001" -H "Authorization: Bearer ${student_token}")" "403" "外部无法调用内部已购查询"

echo "[smoke] 下单 -> 模拟收银台 -> 开课 -> 退款 -> 收回（需 ZHIXUE_PAY_MOCK_ENABLED=true）"
sql_course() { compose exec -T mysql mysql -uroot "-p${ZHIXUE_DB_PASSWORD}" zhixue_course -e "$1" >/dev/null 2>&1; }
# 课程 2004 仅 1 个课时且默认可试看，临时关闭试看以便观察"购买前锁定 / 购买后解锁"
sql_course "update course set allow_preview=0 where id=2004;"
assert_not_contains "$(curl -fsS "${GATEWAY_URL}/course/2004/chapters" -H "Authorization: Bearer ${student_token}")" "sandbox.mp4" "购买前课时锁定"
order_no="$(curl -fsS -X POST "${GATEWAY_URL}/order/create" -H "Authorization: Bearer ${student_token}" \
  -H 'Content-Type: application/json' -d '{"productId":2004}' | extract_json_field data.orderNo)"
# 请求体必须先放进变量：macOS 自带 bash 3.2 会对 "$(...)" 内双引号中的 {a,b} 做花括号展开，
# 把 JSON 按逗号拆成两个 -d 参数（实测踩过）
alipay_body="{\"orderNo\":\"${order_no}\",\"payChannel\":\"alipay\"}"
mock_body="{\"orderNo\":\"${order_no}\",\"payChannel\":\"mock\"}"
confirm_body="{\"orderNo\":\"${order_no}\"}"
assert_contains "$(curl -fsS -X POST "${GATEWAY_URL}/pay" -H "Authorization: Bearer ${student_token}" \
  -H 'Content-Type: application/json' -d "$alipay_body")" "暂未接入" "未接入的真实渠道明确拒绝"
assert_code "$(curl -fsS -X POST "${GATEWAY_URL}/pay" -H "Authorization: Bearer ${student_token}" \
  -H 'Content-Type: application/json' -d "$mock_body")" "200" "模拟收银台发起支付"
assert_code "$(curl -fsS -X POST "${GATEWAY_URL}/pay/mock/confirm" -H "Authorization: Bearer ${student_token}" \
  -H 'Content-Type: application/json' -d "$confirm_body")" "200" "模拟收银台确认付款"
assert_contains "$(curl -fsS "${GATEWAY_URL}/course/2004/chapters" -H "Authorization: Bearer ${student_token}")" "sandbox.mp4" "支付后课时解锁"
order_id="$(curl -fsS "${GATEWAY_URL}/order/detail?orderNo=${order_no}" -H "Authorization: Bearer ${student_token}" | extract_json_field data.id)"
assert_code "$(curl -fsS -X PUT "${GATEWAY_URL}/order/${order_id}/refund" -H "Authorization: Bearer ${admin_token}")" "200" "运营退款"
assert_not_contains "$(curl -fsS "${GATEWAY_URL}/course/2004/chapters" -H "Authorization: Bearer ${student_token}")" "sandbox.mp4" "退款后课时重新锁定"
sql_course "update course set allow_preview=1 where id=2004;"
sql_order "delete from \`order\` where user_id=1003 and product_id=2004;"

echo "[smoke] 秒杀 C 端：身份、权限、成交即生成可支付订单"
sql_marketing() { compose exec -T mysql mysql -uroot "-p${ZHIXUE_DB_PASSWORD}" zhixue_marketing -e "$1" >/dev/null 2>&1; }
redis_del() { compose exec -T redis redis-cli -a "${ZHIXUE_REDIS_PASSWORD}" DEL "$@" >/dev/null 2>&1; }
sql_marketing "delete from seckill_order where activity_id=63001;
  update seckill_activity set status=1, total_stock=50, start_time=date_sub(now(),interval 1 hour), end_time=date_add(now(),interval 5 hour) where id=63001;"
redis_del seckill:stock:63001 seckill:users:63001
pwn_activity='{"title":"PWN","productId":2001,"seckillPrice":0.01,"totalStock":999,"startTime":"2026-01-01T00:00:00","endTime":"2099-01-01T00:00:00","status":1}'
assert_code "$(curl -fsS -X POST "${GATEWAY_URL}/marketing/seckill" -H "Authorization: Bearer ${student_token}" \
  -H 'Content-Type: application/json' -d "$pwn_activity")" "403" "学生不能新建秒杀活动"
no_token_body='{"activityId":63001}'
assert_contains "$(curl -fsS -X POST "${GATEWAY_URL}/marketing/seckill/do" -H "Authorization: Bearer ${student_token}" \
  -H 'Content-Type: application/json' -d "$no_token_body")" "令牌不能为空" "不带令牌不能秒杀"
assert_contains "$(curl -fsS "${GATEWAY_URL}/marketing/seckill/online" -H "Authorization: Bearer ${student_token}")" '"remainingStock":50' "活动列表返回剩余库存（惰性装载）"
sk_token="$(curl -fsS -X POST "${GATEWAY_URL}/marketing/seckill/token?activityId=63001&userId=1001" \
  -H "Authorization: Bearer ${student_token}" | extract_json_field data)"
sk_body="{\"activityId\":63001,\"token\":\"${sk_token}\"}"
sk_result="$(curl -fsS -X POST "${GATEWAY_URL}/marketing/seckill/do" -H "Authorization: Bearer ${student_token}" \
  -H 'Content-Type: application/json' -d "$sk_body")"
assert_code "$sk_result" "200" "秒杀成交"
sk_order_no="$(printf '%s' "$sk_result" | extract_json_field data.orderNo)"
sk_detail="$(curl -fsS "${GATEWAY_URL}/order/detail?orderNo=${sk_order_no}" -H "Authorization: Bearer ${student_token}")"
assert_contains "$sk_detail" '"amount":49.00' "成交后订单服务存在按秒杀价的真实订单"
assert_contains "$sk_detail" '"userId":1003' "秒杀订单归属登录用户而非参数中的 userId"
assert_contains "$(curl -fsS -X POST "${GATEWAY_URL}/marketing/seckill/do" -H "Authorization: Bearer ${student_token}" \
  -H 'Content-Type: application/json' -d "$sk_body")" "令牌无效" "令牌一次性，不能重放"
sql_order "delete from \`order\` where user_id=1003 and product_id=2004;"
sql_marketing "delete from seckill_order where activity_id=63001;"
redis_del seckill:stock:63001 seckill:users:63001

echo "[smoke] 课程发布与下架"
compose exec -T mysql mysql -uroot "-p${ZHIXUE_DB_PASSWORD}" -e "update zhixue_course.course set status=0, shelf_status=0, publish_time=null where id=2003;" >/dev/null
assert_code "$(curl -fsS -X POST "${GATEWAY_URL}/course/publish/2003" -H "Authorization: Bearer ${admin_token}")" "200" "发布草稿课程"
assert_code "$(curl -fsS -X POST "${GATEWAY_URL}/course/shelf/2003?shelfStatus=0" -H "Authorization: Bearer ${admin_token}")" "200" "下架课程"
compose exec -T mysql mysql -uroot "-p${ZHIXUE_DB_PASSWORD}" -e "update zhixue_course.course set status=0, shelf_status=0, publish_time=null where id=2003;" >/dev/null

echo "[done] 核心链路冒烟通过"

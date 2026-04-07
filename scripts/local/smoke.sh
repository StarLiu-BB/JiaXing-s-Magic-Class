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
  code="$(compose exec -T redis redis-cli GET "zhixue:auth:captcha:${uuid}" | tr -d '\r')"

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

echo "[smoke] 课程发布与下架"
compose exec -T mysql mysql -uroot "-p${ZHIXUE_DB_PASSWORD}" -e "update zhixue_course.course set status=0, shelf_status=0, publish_time=null where id=2003;" >/dev/null
assert_code "$(curl -fsS -X POST "${GATEWAY_URL}/course/publish/2003" -H "Authorization: Bearer ${admin_token}")" "200" "发布草稿课程"
assert_code "$(curl -fsS -X POST "${GATEWAY_URL}/course/shelf/2003?shelfStatus=0" -H "Authorization: Bearer ${admin_token}")" "200" "下架课程"
compose exec -T mysql mysql -uroot "-p${ZHIXUE_DB_PASSWORD}" -e "update zhixue_course.course set status=0, shelf_status=0, publish_time=null where id=2003;" >/dev/null

echo "[done] 核心链路冒烟通过"

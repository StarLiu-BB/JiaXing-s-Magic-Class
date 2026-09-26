#!/usr/bin/env bash
# 前后端契约守门：提取前端所有 API 路径，逐个比对网关路由前缀。
# 任一路径无法被网关路由覆盖即判定失败——防止再次出现"页面在、接口 404"。
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GATEWAY_YML="$ROOT_DIR/zhixue-gateway/src/main/resources/application.yml"

# 从网关配置提取所有 Path 谓词前缀（形如 /course、/danmaku）
# 注：macOS 自带 bash 3.2，不支持 mapfile，这里用换行分隔的字符串保存
GATEWAY_PREFIXES=$(
  grep -oE '^[[:space:]]+- Path=.*' "$GATEWAY_YML" |
    sed 's/.*Path=//' | tr ',' '\n' |
    sed 's#^\(/[a-zA-Z0-9_-]*\).*#\1#' | sort -u
)

if [[ -z "$GATEWAY_PREFIXES" ]]; then
  echo "[error] 未能从网关配置解析出任何路由前缀: $GATEWAY_YML"
  exit 1
fi

echo "[info] 网关已声明前缀: $(echo "$GATEWAY_PREFIXES" | tr '\n' ' ')"
echo

covered() {
  local path="$1" prefix
  while IFS= read -r prefix; do
    [[ -n "$prefix" ]] || continue
    if [[ "$path" == "$prefix" || "$path" == "$prefix/"* ]]; then
      return 0
    fi
  done <<<"$GATEWAY_PREFIXES"
  return 1
}

fail_count=0
check_count=0

# 扫描一个前端目录下所有 request/wx.request 的 url 字面量
scan_dir() {
  local label="$1" dir="$2"
  [[ -d "$dir" ]] || return 0

  echo "=== $label ==="
  while IFS= read -r line; do
    local file="${line%%:*}"
    local rest="${line#*:}"
    local lineno="${rest%%:*}"
    local raw="${rest#*:}"

    # 取出路径字面量，支持两种写法：
    #   1) request({ url: '/xxx' })            —— admin-web
    #   2) get('/xxx') / post('/xxx') / del()  —— 小程序 api 层
    local path
    path=$(printf '%s' "$raw" | sed -nE "s#.*url:[[:space:]]*['\\\`]([^'\\\`\$]*).*#\1#p")
    if [[ -z "$path" ]]; then
      path=$(printf '%s' "$raw" | sed -nE "s#.*(get|post|put|del|upload)\('(/[^']*)'.*#\2#p")
    fi
    [[ -n "$path" ]] || continue
    [[ "$path" == /* ]] || continue
    # 小程序页面跳转（wx.navigateTo 等）不是后端调用，跳过
    [[ "$path" == /pages/* ]] && continue

    # 归一化：截掉查询串与路径参数占位
    path="${path%%\?*}"
    local first_seg
    first_seg=$(printf '%s' "$path" | sed -E 's#^(/[a-zA-Z0-9_-]*).*#\1#')

    check_count=$((check_count + 1))
    if covered "$first_seg"; then
      printf '  [ok]   %s\n' "$path"
    else
      printf '  [FAIL] %s  <- 网关无 %s 路由  (%s:%s)\n' \
        "$path" "$first_seg" "${file#"$ROOT_DIR"/}" "$lineno"
      fail_count=$((fail_count + 1))
    fi
  done < <(grep -rnE "url:|(get|post|put|del|upload)\('/" \
    "$dir" --include="*.js" --include="*.vue" 2>/dev/null)
  echo
}

scan_dir "admin-web" "$ROOT_DIR/zhixue-admin-web/src"
scan_dir "app-mp" "$ROOT_DIR/zhixue-app-mp/miniprogram"

echo "================ 契约检查结果 ================"
echo "已检查路径数: $check_count"
echo "未被网关覆盖: $fail_count"

if [[ $fail_count -gt 0 ]]; then
  echo
  echo "[error] 存在前端调用但网关无法路由的路径，必须修正后再交付。"
  exit 1
fi
echo "[ok] 全部前端 API 路径均被网关路由覆盖。"

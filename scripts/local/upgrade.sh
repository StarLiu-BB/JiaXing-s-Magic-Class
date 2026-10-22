#!/usr/bin/env bash
set -euo pipefail

DRY_RUN=0
if [[ $# -gt 0 ]]; then
  if [[ $# -ne 1 || "$1" != "--dry-run" ]]; then
    echo "用法：bash scripts/local/upgrade.sh [--dry-run]" >&2
    exit 2
  fi
  DRY_RUN=1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
MODULES=(zhixue-system zhixue-course zhixue-media zhixue-interaction zhixue-order zhixue-marketing zhixue-ai)
files=()
for module in "${MODULES[@]}"; do
  directory="$ROOT_DIR/zhixue-modules/$module/src/main/resources/sql/upgrade"
  [[ -d "$directory" ]] || continue
  for file in "$directory"/*.sql; do
    [[ -f "$file" ]] && files+=("$file")
  done
done

if [[ ${#files[@]} -eq 0 ]]; then
  echo "[upgrade] 没有找到增量脚本"
  exit 0
fi

if [[ "$DRY_RUN" -eq 1 ]]; then
  for file in "${files[@]}"; do
    echo "[upgrade][dry-run] ${file#"$ROOT_DIR/"}"
  done
  echo "[ok] 增量脚本预览完成（共 ${#files[@]} 个）"
  exit 0
fi

ENV_FILE="$ROOT_DIR/ops/local/local.env"
COMPOSE_FILE="$ROOT_DIR/ops/local/docker-compose.yml"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "[upgrade][fail] 缺少本地配置 ops/local/local.env；请先配置并启动本地 MySQL" >&2
  exit 1
fi

echo "[upgrade] 仅执行增量 SQL，不重建库或种子数据；请确认已有备份"
for file in "${files[@]}"; do
  echo "[upgrade] 执行 ${file#"$ROOT_DIR/"}"
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql \
    sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot' < "$file"
done

echo "[ok] 增量脚本执行完成（共 ${#files[@]} 个）"

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/common.sh"

load_env

# MinIO 官方镜像已停止公开分发（Docker Hub 仓库下线、quay.io 需认证）。
# 设 ZHIXUE_SKIP_MEDIA=1 可跳过 MinIO 与 media 服务，先跑通其余链路。
SKIP_MEDIA="${ZHIXUE_SKIP_MEDIA:-0}"

INFRA_SERVICES=(mysql redis nacos rabbitmq elasticsearch seata)
APP_SERVICES=(auth system course interaction order marketing ai gateway)

if [[ "$SKIP_MEDIA" == "1" ]]; then
  echo "[infra] 已跳过 MinIO 与 media 服务 (ZHIXUE_SKIP_MEDIA=1)"
else
  INFRA_SERVICES+=(minio)
  APP_SERVICES+=(media)
fi

echo "[infra] 启动 MySQL / Redis / Nacos"
compose up -d "${INFRA_SERVICES[@]}"

wait_for_port 127.0.0.1 "${ZHIXUE_DB_PORT}" "MySQL"
wait_for_port 127.0.0.1 "${ZHIXUE_REDIS_PORT}" "Redis"
wait_for_http "http://${ZHIXUE_NACOS_HOST}:${ZHIXUE_NACOS_PORT}/nacos/" "Nacos"
wait_for_port 127.0.0.1 "${ZHIXUE_RABBITMQ_PORT}" "RabbitMQ"
wait_for_http "http://127.0.0.1:${ZHIXUE_RABBITMQ_MANAGEMENT_PORT}" "RabbitMQ Management"
if [[ "$SKIP_MEDIA" != "1" ]]; then
  wait_for_http "${ZHIXUE_MINIO_ENDPOINT}/minio/health/live" "MinIO"
fi
wait_for_http "${ZHIXUE_ES_URIS}" "Elasticsearch"
wait_for_port 127.0.0.1 "${ZHIXUE_SEATA_PORT}" "Seata"

"$ROOT_DIR/scripts/local/db-init.sh"

echo "[build] 安装本地联调所需 Java 依赖"
mvn -DskipTests install

echo "[apps] 启动全量服务容器"
# 跳过 media 时必须加 --no-deps：gateway 的 depends_on 含 media，
# 否则 compose 会连带启动 media 并尝试拉取已下线的 minio 镜像。
# 中间件已在前面显式启动并等待就绪，不依赖 compose 的依赖解析。
if [[ "$SKIP_MEDIA" == "1" ]]; then
  compose up -d --force-recreate --no-deps "${APP_SERVICES[@]}"
else
  compose up -d --force-recreate "${APP_SERVICES[@]}"
fi

wait_for_health_up "http://127.0.0.1:${ZHIXUE_COURSE_PORT}/actuator/health" "Course"
wait_for_health_up "http://127.0.0.1:${ZHIXUE_SYSTEM_PORT}/actuator/health" "System"
wait_for_health_up "http://127.0.0.1:${ZHIXUE_AUTH_PORT}/actuator/health" "Auth"
if [[ "$SKIP_MEDIA" != "1" ]]; then
  wait_for_health_up "http://127.0.0.1:${ZHIXUE_MEDIA_PORT}/actuator/health" "Media"
fi
wait_for_health_up "http://127.0.0.1:${ZHIXUE_INTERACTION_PORT}/actuator/health" "Interaction"
wait_for_health_up "http://127.0.0.1:${ZHIXUE_ORDER_PORT}/actuator/health" "Order"
wait_for_health_up "http://127.0.0.1:${ZHIXUE_MARKETING_PORT}/actuator/health" "Marketing"
wait_for_health_up "http://127.0.0.1:${ZHIXUE_AI_PORT}/actuator/health" "AI"
wait_for_health_up "http://127.0.0.1:${ZHIXUE_GATEWAY_PORT}/actuator/health" "Gateway"

echo
echo "本地联调环境已启动。"
echo "日志目录: $LOG_DIR"
echo "健康检查: $ROOT_DIR/scripts/local/health.sh"
echo "基础冒烟: $ROOT_DIR/scripts/local/smoke.sh"

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/common.sh"

load_env

echo "[infra] 启动 MySQL / Redis / Nacos"
compose up -d mysql redis nacos rabbitmq minio elasticsearch seata

wait_for_port 127.0.0.1 "${ZHIXUE_DB_PORT}" "MySQL"
wait_for_port 127.0.0.1 "${ZHIXUE_REDIS_PORT}" "Redis"
wait_for_http "http://${ZHIXUE_NACOS_HOST}:${ZHIXUE_NACOS_PORT}/nacos/" "Nacos"
wait_for_port 127.0.0.1 "${ZHIXUE_RABBITMQ_PORT}" "RabbitMQ"
wait_for_http "http://127.0.0.1:${ZHIXUE_RABBITMQ_MANAGEMENT_PORT}" "RabbitMQ Management"
wait_for_http "${ZHIXUE_MINIO_ENDPOINT}/minio/health/live" "MinIO"
wait_for_http "${ZHIXUE_ES_URIS}" "Elasticsearch"
wait_for_port 127.0.0.1 "${ZHIXUE_SEATA_PORT}" "Seata"

"$ROOT_DIR/scripts/local/db-init.sh"

echo "[build] 安装本地联调所需 Java 依赖"
mvn -DskipTests install

echo "[apps] 启动全量服务容器"
compose up -d --force-recreate auth system course media interaction order marketing ai gateway

wait_for_health_up "http://127.0.0.1:${ZHIXUE_COURSE_PORT}/actuator/health" "Course"
wait_for_health_up "http://127.0.0.1:${ZHIXUE_SYSTEM_PORT}/actuator/health" "System"
wait_for_health_up "http://127.0.0.1:${ZHIXUE_AUTH_PORT}/actuator/health" "Auth"
wait_for_health_up "http://127.0.0.1:${ZHIXUE_MEDIA_PORT}/actuator/health" "Media"
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

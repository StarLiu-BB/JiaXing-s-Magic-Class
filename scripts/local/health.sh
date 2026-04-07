#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/common.sh"

load_env

compose ps

compose exec -T mysql mysqladmin ping -h127.0.0.1 -uroot "-p${ZHIXUE_DB_PASSWORD}" >/dev/null
echo "[ok] MySQL ping"

compose exec -T redis redis-cli -a "${ZHIXUE_REDIS_PASSWORD}" ping | grep -q PONG
echo "[ok] Redis ping"

wait_for_http "http://${ZHIXUE_NACOS_HOST}:${ZHIXUE_NACOS_PORT}/nacos/" "Nacos"
wait_for_port 127.0.0.1 "${ZHIXUE_RABBITMQ_PORT}" "RabbitMQ"
wait_for_http "http://127.0.0.1:${ZHIXUE_RABBITMQ_MANAGEMENT_PORT}" "RabbitMQ Management"
wait_for_http "${ZHIXUE_MINIO_ENDPOINT}/minio/health/live" "MinIO"
wait_for_http "${ZHIXUE_ES_URIS}" "Elasticsearch"
wait_for_port 127.0.0.1 "${ZHIXUE_SEATA_PORT}" "Seata"
wait_for_health_up "http://127.0.0.1:${ZHIXUE_COURSE_PORT}/actuator/health" "Course"
wait_for_health_up "http://127.0.0.1:${ZHIXUE_SYSTEM_PORT}/actuator/health" "System"
wait_for_health_up "http://127.0.0.1:${ZHIXUE_AUTH_PORT}/actuator/health" "Auth"
wait_for_health_up "http://127.0.0.1:${ZHIXUE_MEDIA_PORT}/actuator/health" "Media"
wait_for_health_up "http://127.0.0.1:${ZHIXUE_INTERACTION_PORT}/actuator/health" "Interaction"
wait_for_health_up "http://127.0.0.1:${ZHIXUE_ORDER_PORT}/actuator/health" "Order"
wait_for_health_up "http://127.0.0.1:${ZHIXUE_MARKETING_PORT}/actuator/health" "Marketing"
wait_for_health_up "http://127.0.0.1:${ZHIXUE_AI_PORT}/actuator/health" "AI"
wait_for_health_up "http://127.0.0.1:${ZHIXUE_GATEWAY_PORT}/actuator/health" "Gateway"

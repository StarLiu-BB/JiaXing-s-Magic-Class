#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OPS_DIR="$ROOT_DIR/ops/local"
ENV_FILE="$OPS_DIR/local.env"
ENV_EXAMPLE="$OPS_DIR/local.env.example"
COMPOSE_FILE="$OPS_DIR/docker-compose.yml"
LOG_DIR="$OPS_DIR/logs"
RUN_DIR="$OPS_DIR/run"

# 把 local.env 中的 __GENERATE__ 占位符替换为随机密钥，避免仓库内出现任何固定凭据
materialize_secrets() {
  grep -q '__GENERATE__' "$ENV_FILE" || return 0

  local tmp key secret
  tmp="$(mktemp)"
  while IFS= read -r line || [[ -n "$line" ]]; do
    if [[ "$line" == *"__GENERATE__"* && "$line" != \#* ]]; then
      key="${line%%=*}"
      case "$key" in
        ZHIXUE_INTERNAL_TOKEN) secret="$(openssl rand -hex 24)" ;;
        *) secret="$(openssl rand -base64 48 | tr -d '\n')" ;;
      esac
      printf '%s=%s\n' "$key" "$secret" >>"$tmp"
      echo "[init] 已为 $key 生成随机密钥"
    else
      printf '%s\n' "$line" >>"$tmp"
    fi
  done <"$ENV_FILE"
  mv "$tmp" "$ENV_FILE"
  chmod 600 "$ENV_FILE"
}

ensure_env_file() {
  mkdir -p "$LOG_DIR" "$RUN_DIR"
  if [[ ! -f "$ENV_FILE" ]]; then
    cp "$ENV_EXAMPLE" "$ENV_FILE"
    materialize_secrets
    return
  fi

  while IFS= read -r line; do
    [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
    local key="${line%%=*}"
    if ! grep -q "^${key}=" "$ENV_FILE"; then
      printf '\n%s\n' "$line" >>"$ENV_FILE"
    fi
  done <"$ENV_EXAMPLE"

  materialize_secrets
}

load_env() {
  ensure_env_file
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
  : "${ZHIXUE_REDIS_PASSWORD:=zhixue-local-redis}"
  export ZHIXUE_REDIS_PASSWORD
}

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

wait_for_http() {
  local url="$1"
  local label="$2"
  local attempts="${3:-60}"
  local delay="${4:-2}"
  for _ in $(seq 1 "$attempts"); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "[ok] $label"
      return 0
    fi
    sleep "$delay"
  done
  echo "[fail] $label: $url"
  return 1
}

wait_for_health_up() {
  local url="$1"
  local label="$2"
  local attempts="${3:-60}"
  local delay="${4:-2}"
  local response
  for _ in $(seq 1 "$attempts"); do
    response="$(curl -fsS "$url" 2>/dev/null || true)"
    if [[ "$response" == *'"status":"UP"'* || "$response" == *'"status": "UP"'* ]]; then
      echo "[ok] $label"
      return 0
    fi
    sleep "$delay"
  done
  echo "[fail] $label: $url"
  return 1
}

wait_for_port() {
  local host="$1"
  local port="$2"
  local label="$3"
  local attempts="${4:-60}"
  local delay="${5:-2}"
  for _ in $(seq 1 "$attempts"); do
    if (echo >"/dev/tcp/$host/$port") >/dev/null 2>&1; then
      echo "[ok] $label"
      return 0
    fi
    sleep "$delay"
  done
  echo "[fail] $label: $host:$port"
  return 1
}

service_pid_file() {
  local name="$1"
  echo "$RUN_DIR/${name}.pid"
}

start_java_service() {
  local name="$1"
  local module="$2"
  local health_url="$3"
  local module_pom="$ROOT_DIR/$module/pom.xml"
  local log_file="$LOG_DIR/${name}.log"
  local pid_file
  local max_attempts=3
  pid_file="$(service_pid_file "$name")"

  if [[ -f "$pid_file" ]] && ps -p "$(cat "$pid_file")" >/dev/null 2>&1; then
    echo "[skip] $name 已在运行"
    return 0
  fi

  for attempt in $(seq 1 "$max_attempts"); do
    echo "[start] $name (attempt ${attempt}/${max_attempts})"
    (
      cd "$ROOT_DIR"
      if command -v setsid >/dev/null 2>&1; then
        nohup setsid mvn -f "$module_pom" spring-boot:run -DskipTests \
          >"$log_file" 2>&1 < /dev/null &
      else
        nohup mvn -f "$module_pom" spring-boot:run -DskipTests \
          >"$log_file" 2>&1 < /dev/null &
      fi
      echo $! >"$pid_file"
      disown >/dev/null 2>&1 || true
    )

    if wait_for_service "$health_url" "$name" "$pid_file" 90 2; then
      return 0
    fi

    stop_java_service "$name"
    sleep 3
  done

  echo "[fail] $name 启动失败，日志: $LOG_DIR/${name}.log"
  return 1
}

stop_java_service() {
  local name="$1"
  local pid_file
  pid_file="$(service_pid_file "$name")"

  if [[ -f "$pid_file" ]]; then
    local pid
    pid="$(cat "$pid_file")"
    if ps -p "$pid" >/dev/null 2>&1; then
      kill "$pid" >/dev/null 2>&1 || true
      wait "$pid" 2>/dev/null || true
      echo "[stop] $name"
    fi
    rm -f "$pid_file"
  fi
}

wait_for_service() {
  local url="$1"
  local label="$2"
  local pid_file="$3"
  local attempts="${4:-60}"
  local delay="${5:-2}"
  for _ in $(seq 1 "$attempts"); do
    local response
    response="$(curl -fsS "$url" 2>/dev/null || true)"
    if [[ "$response" == *'"status":"UP"'* || "$response" == *'"status": "UP"'* ]]; then
      echo "[ok] $label"
      return 0
    fi
    if [[ -f "$pid_file" ]] && ! ps -p "$(cat "$pid_file")" >/dev/null 2>&1; then
      echo "[retry] $label 进程提前退出"
      return 1
    fi
    sleep "$delay"
  done
  echo "[fail] $label: $url"
  return 1
}

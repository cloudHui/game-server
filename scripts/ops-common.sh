#!/usr/bin/env bash
# 运维公共量：路径、访问唯一码、Nginx、互斥探测。由 ops.sh / hub.sh source。

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPTS="$ROOT/scripts"
NGINX_DIR="$SCRIPTS/nginx"
BUILD="$ROOT/build"
LOG_HOME="$ROOT/logs"
PATH_FILE="$SCRIPTS/web-path.txt"
DOMAIN="${SERVER_DOMAIN:-}"
WEB_PORT="${WEB_PORT:-8081}"
HUB_JAR="$BUILD/hub/hub.jar"
LEGACY_ORDER=(center gate lobby game web)

declare -A LEGACY_JAR_NAME=(
  [center]=Center.jar
  [gate]=Gate.jar
  [lobby]=Lobby.jar
  [game]=Game.jar
  [web]=Web.jar
)

need_java() {
  command -v java >/dev/null 2>&1 || { echo "未找到 java"; exit 1; }
}

web_path() {
  if [[ ! -f "$PATH_FILE" ]]; then
    local p
    p="$(tr -dc 'A-Za-z0-9' </dev/urandom | head -c 18)"
    echo "$p" >"$PATH_FILE"
  fi
  tr -d '[:space:]' <"$PATH_FILE"
}

print_entry() {
  local wp
  wp="$(web_path)"
  echo "本机入口: http://127.0.0.1:${WEB_PORT}/${wp}/"
  if [[ -n "$DOMAIN" ]]; then
    echo "外网入口: https://${DOMAIN}/${wp}/"
  else
    echo "外网入口: nginx-apply <域名> 或设置 SERVER_DOMAIN"
  fi
}

legacy_jar() {
  echo "$BUILD/$1/${LEGACY_JAR_NAME[$1]}"
}

pids_of_pattern() {
  pgrep -f "$1" 2>/dev/null || true
}

hub_pids() {
  local pids old
  pids="$(pids_of_pattern "$HUB_JAR")"
  old="$(pids_of_pattern 'weball[.]jar')"
  printf '%s\n%s\n' "$pids" "$old" | awk 'NF && !seen[$0]++'
}

legacy_pids() {
  local svc
  for svc in "${LEGACY_ORDER[@]}"; do
    pids_of_pattern "$(legacy_jar "$svc")"
  done
}

require_no_hub() {
  local pids
  pids="$(hub_pids)"
  if [[ -n "$pids" ]]; then
    echo "hub 仍在运行 ($pids)。先执行: $SCRIPTS/hub.sh stop" >&2
    return 1
  fi
}

require_no_legacy() {
  local pids
  pids="$(legacy_pids)"
  if [[ -n "$pids" ]]; then
    echo "旧五服务仍在运行。先执行: $SCRIPTS/ops.sh stop all" >&2
    return 1
  fi
}

cmd_clean_logs() {
  local days=7
  local removed=0
  local dirs=(
    "$LOG_HOME"
    "$LOG_HOME/hub"
    "$ROOT/build/logs"
    "$BUILD/web/logs"
    "$ROOT/web/logs"
  )
  local d
  for d in "${dirs[@]}"; do
    [[ -d "$d" ]] || continue
    while IFS= read -r -d '' f; do
      rm -f "$f"
      removed=$((removed + 1))
    done < <(find "$d" -type f \( -name '*.log' -o -name '*.log.gz' -o -name '*.zip' -o -name 'console.out' \) -mtime +$((days - 1)) -print0 2>/dev/null)
  done
  echo "已清理超过 ${days} 天的日志文件，删除 ${removed} 个（目录: $LOG_HOME）"
}

find_domain_conf() {
  local domain="$1"
  local candidate="/etc/nginx/conf.d/${domain}.conf"
  if [[ -f "$candidate" ]]; then
    echo "$candidate"
    return 0
  fi
  local f
  for f in /etc/nginx/conf.d/*.conf; do
    [[ -f "$f" ]] || continue
    if grep -qE "server_name[[:space:]]+.*${domain}" "$f" 2>/dev/null; then
      echo "$f"
      return 0
    fi
  done
  return 1
}

cmd_nginx_apply() {
  local domain="${1:-}"
  local port="${2:-$WEB_PORT}"
  if [[ -z "$domain" ]]; then
    echo "请传入域名，例如: nginx-apply www.example.com" >&2
    exit 1
  fi
  [[ "$port" =~ ^[0-9]+$ ]] && ((port >= 1 && port <= 65535)) || {
    echo "Web 端口非法: $port" >&2
    exit 1
  }
  command -v sudo >/dev/null 2>&1 || { echo "需要 sudo"; exit 1; }
  command -v python3 >/dev/null 2>&1 || { echo "需要 python3"; exit 1; }

  local wp conf map_src snippet_in snippet_out
  wp="$(web_path)"
  map_src="$NGINX_DIR/00-websocket-map.conf"
  snippet_in="$NGINX_DIR/game-web.snippet.conf.in"
  snippet_out="/etc/nginx/snippets/game-web.conf"

  [[ -f "$map_src" && -f "$snippet_in" ]] || { echo "缺少 nginx 模板，请确认 $NGINX_DIR"; exit 1; }

  if ! conf="$(find_domain_conf "$domain")"; then
    echo "未找到域名 $domain 的 Nginx conf（期望 /etc/nginx/conf.d/${domain}.conf）" >&2
    exit 1
  fi
  echo "域名 conf: $conf"
  echo "随机路径(访问唯一码): /$wp/"

  sudo mkdir -p /etc/nginx/snippets
  local tmp
  tmp="$(mktemp)"
  sed -e "s/@WEB_PATH@/${wp}/g" -e "s/@WEB_PORT@/${port}/g" "$snippet_in" >"$tmp"
  sudo install -m 644 "$tmp" "$snippet_out"
  rm -f "$tmp"

  sudo install -m 644 "$map_src" /etc/nginx/conf.d/00-websocket-map.conf

  sudo cp -a "$conf" "${conf}.bak.$(date +%Y%m%d%H%M%S)"
  tmp="$(mktemp)"
  cp -a "$conf" "$tmp" 2>/dev/null || sudo cat "$conf" >"$tmp"
  chmod u+w "$tmp"
  python3 "$NGINX_DIR/apply_game_web.py" "$tmp"
  sudo install -m 644 "$tmp" "$conf"
  rm -f "$tmp"

  if sudo nginx -t; then
    sudo systemctl reload nginx
    echo "Nginx 已应用并 reload"
    echo "统一上游: 127.0.0.1:${port}"
    echo "外网入口: https://${domain}/${wp}/"
  else
    echo "nginx -t 失败，已保留 .bak；请检查 $conf" >&2
    exit 1
  fi
}

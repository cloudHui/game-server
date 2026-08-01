#!/usr/bin/env bash
# Linux 运维入口：启停 / 状态 / 监控 / 打包 / 清日志
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPTS="$ROOT/scripts"
NGINX_DIR="$SCRIPTS/nginx"
BUILD="$ROOT/build"
LOG_HOME="$ROOT/logs"
PATH_FILE="$SCRIPTS/web-path.txt"
# 仅作 status 展示；nginx-apply 必须显式传入域名
DOMAIN="${SERVER_DOMAIN:-}"

ORDER=(center gate lobby game web)

declare -A JAR_NAME=(
  [center]=Center.jar
  [gate]=Gate.jar
  [lobby]=Lobby.jar
  [game]=Game.jar
  [web]=Web.jar
)

declare -A HEAP=(
  [center]=64m
  [gate]=96m
  [lobby]=128m
  [game]=128m
  [web]=192m
)

declare -A INITIAL_HEAP=(
  [center]=64m
  [gate]=96m
  [lobby]=128m
  [game]=128m
  [web]=64m
)

usage() {
  cat <<EOF
用法:
  $0 {start|stop|restart|status} [服务|all]
  $0 start-remaining     # 起尚未运行的游戏服（center/gate/lobby/game），受内存门禁
  $0 build
  $0 build-restart [服务|all]
  $0 monitor [刷新秒数|--once]
  $0 clean-logs
  $0 nginx-apply <域名>

服务: center | gate | lobby | game | web | all

示例:
  $0 start web           # 默认部署只起 web（学习+静态+本地小游戏）
  $0 start-remaining     # 内存足够时再起牌桌相关服务
  $0 start center        # 单独起某服务
  $0 restart web
  $0 monitor             # 默认每 1 秒清屏刷新
  $0 monitor 2           # 每 2 秒清屏刷新
  $0 monitor --once      # 只输出一次，不清屏
  $0 build
  $0 nginx-apply www.example.com
EOF
}

# MemAvailable+SwapFree（MB）；不足 900 时拒起非 web 服务
mem_headroom_mb() {
  awk '/MemAvailable:/ {a=$2} /SwapFree:/ {s=$2} END {printf "%d", (a+s)/1024}' /proc/meminfo 2>/dev/null || echo 0
}

ask_swap() {
  local ans=
  if [[ -r /dev/tty ]]; then
    printf '可用内存不足（当前约 %sMB）。是否追加 2G swap？(yes/no) [no]: ' "$(mem_headroom_mb)" >/dev/tty
    IFS= read -r ans </dev/tty || ans=
  fi
  case "${ans:-no}" in y|Y|yes|YES)
    local sf=/swapfile-gameserver-2g
    if [[ ! -f "$sf" ]]; then
      echo "创建 $sf ..."
      sudo dd if=/dev/zero of="$sf" bs=1M count=2048 status=progress
      sudo chmod 600 "$sf"
      sudo mkswap "$sf"
    fi
    sudo swapon "$sf" 2>/dev/null || true
    if ! grep -q "$sf" /etc/fstab 2>/dev/null; then
      echo "$sf none swap sw 0 0" | sudo tee -a /etc/fstab >/dev/null
    fi
    echo "当前可用约 $(mem_headroom_mb)MB"
    ;;
  esac
}

ensure_memory_for_game() {
  local head
  head="$(mem_headroom_mb)"
  if [[ "$head" -ge 900 ]]; then
    return 0
  fi
  echo "内存门禁：MemAvailable+SwapFree=${head}MB < 900MB，不能启动 center/gate/lobby/game" >&2
  ask_swap
  head="$(mem_headroom_mb)"
  if [[ "$head" -lt 900 ]]; then
    echo "仍不足 900MB，已拒绝起游戏服。可稍后执行: $0 start-remaining" >&2
    return 1
  fi
  return 0
}

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

resolve_targets() {
  local arg="${1:-all}"
  case "$arg" in
    all) echo "${ORDER[*]}" ;;
    center|gate|lobby|game|web) echo "$arg" ;;
    *) echo "未知服务: $arg" >&2; usage; exit 1 ;;
  esac
}

svc_dir() {
  echo "$BUILD/$1"
}

svc_jar() {
  echo "$(svc_dir "$1")/${JAR_NAME[$1]}"
}

pids_of() {
  local svc="$1"
  local jar
  jar="$(svc_jar "$svc")"
  pgrep -f "$jar" 2>/dev/null || true
}

stop_one() {
  local svc="$1"
  local pids
  pids="$(pids_of "$svc")"
  if [[ -z "$pids" ]]; then
    echo "[$svc] 未在运行"
    return 0
  fi
  echo "[$svc] 停止: $pids"
  # shellcheck disable=SC2086
  kill $pids 2>/dev/null || true
  local i=0
  while [[ -n "$(pids_of "$svc")" && $i -lt 15 ]]; do
    sleep 1
    i=$((i + 1))
  done
  pids="$(pids_of "$svc")"
  if [[ -n "$pids" ]]; then
    echo "[$svc] 强制结束: $pids"
    # shellcheck disable=SC2086
    kill -9 $pids 2>/dev/null || true
  fi
  echo "[$svc] 已停止"
}

start_one() {
  local svc="$1"
  local dir jar heap initial_heap logctx
  dir="$(svc_dir "$svc")"
  jar="$(svc_jar "$svc")"
  heap="${HEAP[$svc]}"
  initial_heap="${INITIAL_HEAP[$svc]}"

  if [[ ! -f "$jar" ]]; then
    echo "[$svc] 找不到 $jar，请先执行: $0 build"
    return 1
  fi
  if [[ -n "$(pids_of "$svc")" ]]; then
    echo "[$svc] 已在运行 (PID $(pids_of "$svc" | tr '\n' ' '))"
    return 0
  fi

  mkdir -p "$dir"
  mkdir -p "$LOG_HOME/$svc"

  local jvm=(
    java
    -Dfile.encoding=UTF-8
    "-DLOG_HOME=${LOG_HOME}"
    "-Xms${initial_heap}"
    "-Xmx${heap}"
    -XX:+UseG1GC
  )
  local workdir="$dir"
  if [[ "$svc" == "web" ]]; then
    local ctx
    ctx="/$(web_path)"
    jvm+=(
      -Xss256k
      "-Dserver.servlet.context-path=${ctx}"
    )
    # 保持仓库根为工作目录，使 application.yml 中 build/game/replay 路径有效
    workdir="$ROOT"
    echo "[$svc] 启动 context-path=${ctx} heap=${initial_heap}-${heap} stack=256k log=${LOG_HOME}/${svc}"
  else
    echo "[$svc] 启动 heap=${initial_heap}-${heap} log=${LOG_HOME}/${svc}"
  fi

  local console_out="$LOG_HOME/$svc/console.out"
  (
    cd "$workdir"
    nohup "${jvm[@]}" -jar "$jar" >>"$console_out" 2>&1 &
  )
  sleep 1
  if [[ -n "$(pids_of "$svc")" ]]; then
    echo "[$svc] 已启动 PID $(pids_of "$svc" | tr '\n' ' ')"
  else
    echo "[$svc] 启动失败，请检查 $console_out 与 jar / 依赖"
    return 1
  fi
}

cmd_start() {
  need_java
  local targets
  # shellcheck disable=SC2207
  targets=($(resolve_targets "${1:-all}"))
  local svc
  local need_gate=0
  for svc in "${targets[@]}"; do
    case "$svc" in center|gate|lobby|game) need_gate=1 ;; esac
  done
  if [[ "$need_gate" -eq 1 ]]; then
    ensure_memory_for_game || {
      # 若请求含 web，仍允许只起 web
      local only_web=()
      for svc in "${targets[@]}"; do
        [[ "$svc" == "web" ]] && only_web+=(web)
      done
      if [[ ${#only_web[@]} -eq 0 ]]; then
        return 1
      fi
      targets=("${only_web[@]}")
      echo "降级：仅启动 web"
    }
  fi
  for svc in "${targets[@]}"; do
    start_one "$svc" || true
    sleep 1
  done
  if [[ "${1:-all}" == "all" || "${1:-}" == "web" || " ${targets[*]} " == *" web "* ]]; then
    local wp
    wp="$(web_path)"
    if [[ -n "$DOMAIN" ]]; then
      echo "外网入口: https://${DOMAIN}/${wp}/"
    else
      echo "访问唯一码路径: /${wp}/ （外网请用 nginx-apply <域名> 或 SERVER_DOMAIN）"
    fi
  fi
}

cmd_start_remaining() {
  need_java
  ensure_memory_for_game || return 1
  local svc
  for svc in center gate lobby game; do
    if [[ -z "$(pids_of "$svc")" ]]; then
      start_one "$svc" || true
      sleep 1
    else
      echo "[$svc] 已在运行，跳过"
    fi
  done
  cmd_status all
}

cmd_stop() {
  local targets
  # shellcheck disable=SC2207
  targets=($(resolve_targets "${1:-all}"))
  # 反向停止
  local reversed=()
  local i
  for ((i=${#targets[@]}-1; i>=0; i--)); do
    reversed+=("${targets[i]}")
  done
  local svc
  for svc in "${reversed[@]}"; do
    stop_one "$svc"
  done
}

cmd_restart() {
  local arg="${1:-all}"
  cmd_stop "$arg"
  sleep 1
  cmd_start "$arg"
}

cmd_build_restart() {
  local arg="${1:-all}"
  cmd_build
  cmd_restart "$arg"
}

cmd_status() {
  local targets
  # shellcheck disable=SC2207
  targets=($(resolve_targets "${1:-all}"))
  local svc pids
  printf "%-8s %-10s %s\n" "SERVICE" "STATE" "PID"
  for svc in "${targets[@]}"; do
    pids="$(pids_of "$svc" | tr '\n' ' ')"
    if [[ -n "${pids// /}" ]]; then
      printf "%-8s %-10s %s\n" "$svc" "running" "$pids"
    else
      printf "%-8s %-10s %s\n" "$svc" "stopped" "-"
    fi
  done
  local wp
  wp="$(web_path)"
  echo "本机 web:  http://127.0.0.1:8081/${wp}/"
  if [[ -n "$DOMAIN" ]]; then
    echo "外网入口: https://${DOMAIN}/${wp}/"
  else
    echo "外网入口: 设置 SERVER_DOMAIN 或使用 nginx-apply 时的域名 + /${wp}/"
  fi
  echo "日志目录: $LOG_HOME/<服务>/{日期日志, error-*.log, console.out}"
  if [[ -f /etc/nginx/snippets/game-web.conf ]] && grep -q "/${wp}/" /etc/nginx/snippets/game-web.conf 2>/dev/null; then
    echo "Nginx 反代: snippets/game-web.conf 已是当前路径"
  elif [[ -n "$DOMAIN" && -f "/etc/nginx/conf.d/${DOMAIN}.conf" ]] && grep -q "/${wp}/" "/etc/nginx/conf.d/${DOMAIN}.conf" 2>/dev/null; then
    echo "Nginx 反代: 域名 conf 内已含当前路径（建议改用 nginx-apply）"
  else
    echo "Nginx 反代: 未检测到当前路径，可执行: $0 nginx-apply <域名>"
  fi
}

cmd_monitor() {
  local interval="${1:-1}"
  local once=0

  if [[ "$interval" == "--once" ]]; then
    once=1
    interval=1
  elif ! [[ "$interval" =~ ^[0-9]+([.][0-9]+)?$ ]] ||
       ! awk -v n="$interval" 'BEGIN { exit !(n > 0) }'; then
    echo "用法: $0 monitor [刷新秒数|--once]" >&2
    return 2
  fi

  monitor_snapshot() {
    local now
    now="$(date '+%F %T')"

    printf 'Java 与 xray/x-ui 服务实时资源监控  %s  刷新: %ss\n' "$now" "$interval"
    printf '%-9s %-8s %9s %9s %11s  %s\n' "服务" "PID" "CPU%" "MEM%" "RSS(MB)" "进程"
    printf '%s\n' '--------------------------------------------------------------------------'

    ps -eo pid=,comm=,%cpu=,%mem=,rss=,args= 2>/dev/null |
      awk '
      function service_name(comm, args, lower) {
          lower = tolower(args)
          if (comm == "java") {
              if (lower ~ /(^|[/[:space:]])center[.]jar([[:space:]]|$)/) return "center"
              if (lower ~ /(^|[/[:space:]])gate[.]jar([[:space:]]|$)/)   return "gate"
              if (lower ~ /(^|[/[:space:]])lobby[.]jar([[:space:]]|$)/)  return "lobby"
              if (lower ~ /(^|[/[:space:]])game[.]jar([[:space:]]|$)/)   return "game"
              if (lower ~ /(^|[/[:space:]])web[.]jar([[:space:]]|$)/)    return "web"
              return ""
          }
          if (comm == "x-ui" || lower ~ /(^|[/[:space:]])x-ui([[:space:]]|$)/) return "x-ui"
          if (comm == "xray" || lower ~ /(^|[/[:space:]])xray([[:space:]]|$)/) return "xray"
          return ""
      }
      {
          pid=$1
          comm=$2
          cpu=$3
          mem=$4
          rss=$5
          args=""
          for (i=6; i<=NF; i++) args=args (i == 6 ? "" : " ") $i

          name=service_name(comm, args)
          if (name == "") next

          found++
          total_cpu += cpu
          total_mem += mem
          total_rss += rss
          count[name]++
          printf "%-9s %-8s %8.1f%% %8.1f%% %11.1f  %s\n",
                 name, pid, cpu, mem, rss/1024, comm
      }
      END {
          if (!found)
              print "未发现目标进程（Center/Gate/Lobby/Game/Web.jar 或 xray/x-ui）"

          print "--------------------------------------------------------------------------"
          printf "%-9s %-8s %8.1f%% %8.1f%% %11.1f\n",
                 "合计", found, total_cpu, total_mem, total_rss/1024
          printf "进程数: center=%d gate=%d lobby=%d game=%d web=%d xray=%d x-ui=%d\n",
                 count["center"], count["gate"], count["lobby"],
                 count["game"], count["web"], count["xray"], count["x-ui"]
      }'

    printf '\n系统内存: '
    free -h 2>/dev/null | awk '/^Mem:/ {printf "%s / %s（可用 %s）\n", $3, $2, $7}'
    printf '系统负载: '
    awk '{printf "%s %s %s\n", $1, $2, $3}' /proc/loadavg 2>/dev/null
    [[ "$once" -eq 0 ]] && printf '按 Ctrl+C 退出\n'
  }

  clear_monitor_screen() {
    # 同时清除可见区域和终端回滚缓冲，再将光标归位。
    # 不调用 clear，避免依赖 TERM/terminfo。
    printf '\033[2J\033[3J\033[H'
  }

  if [[ "$once" -eq 1 ]]; then
    monitor_snapshot
    return
  fi

  while true; do
    if [[ -t 1 ]]; then
      clear_monitor_screen
    fi
    monitor_snapshot
    sleep "$interval"
  done
}

cmd_build() {
  command -v mvn >/dev/null 2>&1 || { echo "未找到 mvn"; exit 1; }
  cd "$ROOT"
  echo "打包中（跳过测试）..."
  mvn -q install -DskipTests
  # Maven 的 copy-dependencies 会排除 com.cloud 内部模块；这里必须显式同步，
  # 否则业务 JAR 更新后仍可能加载旧的 tool/utils/proto JAR。
  local module jar svc lib
  for module in utils proto tool; do
    jar="$ROOT/$module/target/$module-1.0-SNAPSHOT.jar"
    if [[ ! -f "$jar" ]]; then
      echo "缺少内部模块产物: $jar" >&2
      return 1
    fi
    for svc in center gate lobby game; do
      lib="$BUILD/$svc/lib"
      mkdir -p "$lib"
      cp -f "$jar" "$lib/$module-1.0-SNAPSHOT.jar"
    done
  done

  if [[ ! -f "$BUILD/game/app.properties" ]]; then
    cp "$ROOT/game/app.properties.example" "$BUILD/game/app.properties"
    echo "已创建游戏服外置配置: $BUILD/game/app.properties"
  fi

  # 校验改包后的运行时类确实来自最新 tool JAR。
  if ! jar tf "$BUILD/center/lib/tool-1.0-SNAPSHOT.jar" | grep -q '^tools/ServerManager.class$'; then
    echo "tool JAR 校验失败：未找到 tools/ServerManager.class" >&2
    return 1
  fi
  echo "内部模块依赖已同步并校验: utils / proto / tool"

  # 刷新 tablemodel 配置（与当前 tool 类一致）
  if [[ -f "$ROOT/tool/target/tool-1.0-SNAPSHOT.jar" ]]; then
    (cd "$ROOT" && java -cp "tool/target/tool-1.0-SNAPSHOT.jar:$BUILD/game/lib/*" tool.ConfigPacker >/dev/null 2>&1 || true)
    [[ -f "$ROOT/config/tablemodel_models.dat" ]] && mkdir -p "$BUILD/config" && cp -f "$ROOT/config/tablemodel_models.dat" "$BUILD/config/tablemodel_models.dat"
  fi
  echo "打包完成。产物目录: $BUILD"
  for svc in "${ORDER[@]}"; do
    if [[ -f "$(svc_jar "$svc")" ]]; then
      echo "  OK $(svc_jar "$svc")"
    else
      echo "  MISSING $(svc_jar "$svc")"
    fi
  done
}

cmd_clean_logs() {
  local days=7
  local removed=0
  local dirs=(
    "$LOG_HOME"
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
  echo "已清理超过 ${days} 天的日志文件，删除 ${removed} 个（统一目录: $LOG_HOME）"
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
  if [[ -z "$domain" ]]; then
    echo "请传入域名，例如: $0 nginx-apply www.example.com" >&2
    exit 1
  fi
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
  sed "s/@WEB_PATH@/${wp}/g" "$snippet_in" >"$tmp"
  sudo install -m 644 "$tmp" "$snippet_out"
  rm -f "$tmp"

  sudo install -m 644 "$map_src" /etc/nginx/conf.d/00-websocket-map.conf

  # 域名 conf：去掉旧 8081 location，写入 include（幂等替换）
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
    echo "外网入口: https://${domain}/${wp}/"
  else
    echo "nginx -t 失败，已保留 .bak；请检查 $conf" >&2
    exit 1
  fi
}

main() {
  local cmd="${1:-}"
  local arg="${2:-all}"
  case "$cmd" in
    start) cmd_start "$arg" ;;
    start-remaining) cmd_start_remaining ;;
    stop) cmd_stop "$arg" ;;
    restart) cmd_restart "$arg" ;;
    status) cmd_status "$arg" ;;
    monitor) cmd_monitor "${2:-1}" ;;
    build) cmd_build ;;
    build-restart) cmd_build_restart "$arg" ;;
    clean-logs) cmd_clean_logs ;;
    nginx-apply) cmd_nginx_apply "${2:-}" ;;
    -h|--help|help|"") usage; [[ -n "$cmd" ]] || exit 1 ;;
    *) echo "未知命令: $cmd"; usage; exit 1 ;;
  esac
}

main "$@"

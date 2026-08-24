#!/usr/bin/env bash
# hub 一体化服务：启停 / 构建 / 监控
set -euo pipefail

# shellcheck source=ops-common.sh
source "$(cd "$(dirname "$0")" && pwd)/ops-common.sh"

HEAP=256m
INITIAL_HEAP=96m

usage() {
  cat <<EOF
用法:
  $0 start|stop|restart|status
  $0 build|build-restart|deploy
  $0 monitor [刷新秒数|--once]
  $0 clean-logs
  $0 nginx-apply <域名> [Web端口]

示例:
  $0 deploy
  $0 start
  $0 status
  $0 stop
EOF
}

pack_table_models() {
  local tool_jar="$ROOT/tool/target/tool-1.0-SNAPSHOT.jar"
  local tool_lib="$ROOT/tool/target/lib"
  if [[ ! -f "$tool_jar" ]]; then
    echo "缺少 tool JAR，无法打包房间模板: $tool_jar" >&2
    return 1
  fi
  if [[ ! -d "$tool_lib" ]] || ! compgen -G "$tool_lib/*.jar" >/dev/null; then
    echo "缺少 ConfigPacker 依赖: $tool_lib" >&2
    return 1
  fi
  echo "刷新房间模板 config/tablemodel_models.dat"
  if ! (cd "$ROOT" && java -cp "$tool_jar:$tool_lib/*" tool.ConfigPacker); then
    echo "ConfigPacker 失败" >&2
    return 1
  fi
  if [[ ! -f "$ROOT/config/tablemodel_models.dat" ]]; then
    echo "未生成 config/tablemodel_models.dat" >&2
    return 1
  fi
  mkdir -p "$BUILD/config"
  cp -f "$ROOT/config/tablemodel_models.dat" "$BUILD/config/tablemodel_models.dat"
}

stop_hub() {
  local pids
  pids="$(hub_pids)"
  if [[ -z "$pids" ]]; then
    echo "[hub] 未在运行"
    return 0
  fi
  echo "[hub] 停止: $pids"
  # shellcheck disable=SC2086
  kill $pids 2>/dev/null || true
  local i=0
  while [[ -n "$(hub_pids)" && $i -lt 15 ]]; do
    sleep 1
    i=$((i + 1))
  done
  pids="$(hub_pids)"
  if [[ -n "$pids" ]]; then
    echo "[hub] 强制结束: $pids"
    # shellcheck disable=SC2086
    kill -9 $pids 2>/dev/null || true
  fi
  echo "[hub] 已停止"
}

start_hub() {
  local pids ctx console_out unit
  local -a java_args
  need_java
  require_no_legacy || return 1
  if [[ ! -f "$HUB_JAR" ]]; then
    echo "[hub] 找不到 $HUB_JAR，请先执行: $0 build"
    return 1
  fi
  pids="$(hub_pids)"
  if [[ -n "$pids" ]]; then
    echo "[hub] 已在运行 (PID $(echo "$pids" | tr '\n' ' '))"
    print_entry
    return 0
  fi

  mkdir -p "$BUILD/hub" "$LOG_HOME/hub"
  ctx="/$(web_path)"
  console_out="$LOG_HOME/hub/console.out"
  java_args=(
    -Dfile.encoding=UTF-8
    "-DLOG_HOME=${LOG_HOME}"
    "-Xms${INITIAL_HEAP}"
    "-Xmx${HEAP}"
    -XX:+UseG1GC
    -Xss256k
    "-Dserver.servlet.context-path=${ctx}"
    "-Dserver.port=${WEB_PORT}"
    "-Dhub.root=${ROOT}"
    -jar "$HUB_JAR"
  )
  echo "[hub] 启动 context-path=${ctx} heap=${INITIAL_HEAP}-${HEAP} stack=256k log=${LOG_HOME}/hub"
  if command -v systemd-run >/dev/null 2>&1 && systemctl --user is-system-running >/dev/null 2>&1; then
    unit="game-server-hub-$(date +%s)"
    systemd-run --user --quiet --collect --unit="$unit" \
      --property="WorkingDirectory=$ROOT" \
      --property="StandardOutput=append:$console_out" \
      --property="StandardError=append:$console_out" \
      java "${java_args[@]}"
  else
    (cd "$ROOT" && nohup java "${java_args[@]}" >>"$console_out" 2>&1 &)
  fi
  sleep 1
  pids="$(hub_pids)"
  if [[ -n "$pids" ]]; then
    echo "[hub] 已启动 PID $(echo "$pids" | tr '\n' ' ')"
    print_entry
  else
    echo "[hub] 启动失败，请检查 $console_out 与 $HUB_JAR" >&2
    return 1
  fi
}

cmd_status() {
  local pids
  pids="$(hub_pids | tr '\n' ' ')"
  printf "%-8s %-10s %s\n" "SERVICE" "STATE" "PID"
  if [[ -n "${pids// /}" ]]; then
    printf "%-8s %-10s %s\n" "hub" "running" "$pids"
  else
    printf "%-8s %-10s %s\n" "hub" "stopped" "-"
  fi
  print_entry
  echo "日志目录: $LOG_HOME/hub/"
}

cmd_build() {
  local build_started_epoch build_finished_epoch build_elapsed build_status
  command -v mvn >/dev/null 2>&1 || { echo "未找到 mvn"; exit 1; }
  cd "$ROOT"
  build_started_epoch="$(date +%s)"
  echo "Maven 开始时间: $(date '+%Y-%m-%d %H:%M:%S %Z')"
  echo "执行: mvn --batch-mode --show-version -pl hub -am install -DskipTests"
  if mvn --batch-mode --show-version -pl hub -am install -DskipTests; then
    build_status=0
  else
    build_status=$?
  fi
  build_finished_epoch="$(date +%s)"
  build_elapsed=$((build_finished_epoch - build_started_epoch))
  if [[ "$build_status" -ne 0 ]]; then
    printf 'Maven 打包失败（退出码 %s），耗时 %02d:%02d:%02d\n' \
      "$build_status" $((build_elapsed / 3600)) $(((build_elapsed % 3600) / 60)) $((build_elapsed % 60)) >&2
    return "$build_status"
  fi
  if [[ ! -f "$HUB_JAR" ]]; then
    echo "缺少打包产物: $HUB_JAR" >&2
    return 1
  fi
  pack_table_models || return 1
  build_finished_epoch="$(date +%s)"
  build_elapsed=$((build_finished_epoch - build_started_epoch))
  printf '打包完成: %s，总耗时 %02d:%02d:%02d\n' "$(date '+%Y-%m-%d %H:%M:%S %Z')" \
    $((build_elapsed / 3600)) $(((build_elapsed % 3600) / 60)) $((build_elapsed % 60))
  echo "  OK $HUB_JAR"
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
    printf 'hub / xray / x-ui  %s  刷新: %ss\n' "$now" "$interval"
    printf '%-9s %-8s %9s %9s %11s  %s\n' "服务" "PID" "CPU%" "MEM%" "RSS(MB)" "进程"
    printf '%s\n' '--------------------------------------------------------------------------'
    ps -eo pid=,comm=,%cpu=,%mem=,rss=,args= 2>/dev/null |
      awk '
      function service_name(comm, args, lower) {
          lower = tolower(args)
          if (comm == "java") {
              if (lower ~ /(^|[/[:space:]])hub[.]jar([[:space:]]|$)/) return "hub"
              if (lower ~ /(^|[/[:space:]])weball[.]jar([[:space:]]|$)/) return "hub"
              return ""
          }
          if (comm == "x-ui" || lower ~ /(^|[/[:space:]])x-ui([[:space:]]|$)/) return "x-ui"
          if (comm == "xray" || lower ~ /(^|[/[:space:]])xray([[:space:]]|$)/) return "xray"
          return ""
      }
      {
          pid=$1; comm=$2; cpu=$3; mem=$4; rss=$5; args=""
          for (i=6; i<=NF; i++) args=args (i == 6 ? "" : " ") $i
          name=service_name(comm, args)
          if (name == "") next
          found++; total_cpu += cpu; total_mem += mem; total_rss += rss; count[name]++
          printf "%-9s %-8s %8.1f%% %8.1f%% %11.1f  %s\n", name, pid, cpu, mem, rss/1024, comm
      }
      END {
          if (!found) print "未发现 hub.jar / xray / x-ui"
          print "--------------------------------------------------------------------------"
          printf "%-9s %-8s %8.1f%% %8.1f%% %11.1f\n", "合计", found, total_cpu, total_mem, total_rss/1024
          printf "进程数: hub=%d xray=%d x-ui=%d\n", count["hub"], count["xray"], count["x-ui"]
      }'
    printf '\n系统内存: '
    free -h 2>/dev/null | awk '/^Mem:/ {printf "%s / %s（可用 %s）\n", $3, $2, $7}'
    printf '系统负载: '
    awk '{printf "%s %s %s\n", $1, $2, $3}' /proc/loadavg 2>/dev/null
    [[ "$once" -eq 0 ]] && printf '按 Ctrl+C 退出\n'
  }

  if [[ "$once" -eq 1 ]]; then
    monitor_snapshot
    return
  fi
  while true; do
    [[ -t 1 ]] && printf '\033[2J\033[3J\033[H'
    monitor_snapshot
    sleep "$interval"
  done
}

main() {
  local cmd="${1:-}"
  case "$cmd" in
    start) start_hub ;;
    stop) stop_hub ;;
    restart) stop_hub; sleep 1; start_hub ;;
    status) cmd_status ;;
    monitor) cmd_monitor "${2:-1}" ;;
    build) cmd_build ;;
    build-restart) cmd_build; stop_hub; sleep 1; start_hub ;;
    deploy) cmd_build; start_hub ;;
    clean-logs) cmd_clean_logs ;;
    nginx-apply) cmd_nginx_apply "${2:-}" "${3:-$WEB_PORT}" ;;
    -h|--help|help|"") usage; [[ -n "$cmd" ]] || exit 1 ;;
    *) echo "未知命令: $cmd"; usage; exit 1 ;;
  esac
}

main "$@"

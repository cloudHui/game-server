#!/usr/bin/env bash
# hub 一体化服务：启停 / 构建 / 监控
set -euo pipefail

# shellcheck source=ops-common.sh
source "$(cd "$(dirname "$0")" && pwd)/ops-common.sh"

HEAP=256m
INITIAL_HEAP=96m

usage() {
  cat <<EOF
================================================================================
Hub 一体化服务运维脚本 ($0)
================================================================================

基础启停与状态:
  $0 start                 启动 Hub 服务 (后台运行，默认端口 8081)
  $0 stop                  安全停止 Hub 服务 (超时 15 秒强制杀进程)
  $0 restart               快速重启 Hub 服务 (stop -> start，不重新打包)
  $0 status                查看 Hub 状态、PID、当前 Git 版本与访问入口

构建与更新部署:
  $0 build                 仅执行 Maven 打包 (同屏同行显示耗时，报错才输出详情)
  $0 build-restart         打包并重启 (build -> stop -> start)
  $0 deploy                打包并启动 (若已运行则不重复启动)
  $0 pull-restart          一键更新重启 (停止 -> git pull -> 打包 -> 启动)
                           (别名: update-restart, update)
  $0 rollback [commit]     一键版本回退 (停止 -> git reset --hard [版本] -> 打包 -> 启动)
                           默认回退到上次更新前记录的版本或 HEAD~1

系统监控与运维:
  $0 monitor [秒数|--once] 实时同屏监控 hub / xray / x-ui 进程资源占用 (默认 1s 刷新)
  $0 clean-logs            清理超过 7 天的历史日志与 console.out 文件
  $0 nginx-apply <域名>    配置并下发 Nginx 反向代理与 WebSocket 路由

使用示例:
  $0 pull-restart          # 日常更新：停服务 -> 拉最新代码 -> 编译打包 -> 启动
  $0 rollback              # 紧急回退：回退到上次更新前版本并重新打包启动
  $0 rollback abc1234      # 回退到指定 commit 并重新打包启动
  $0 status                # 检查当前是否在运行及访问地址
  $0 monitor               # 查看 CPU、内存监控看板

================================================================================
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
  safe_stop_service "hub" hub_pids
}

start_hub() {
  need_java
  require_no_legacy || return 1
  [[ -f "$HUB_JAR" ]] || { echo "[hub] 找不到 $HUB_JAR，请先执行: $0 build" >&2; return 1; }

  local pids
  pids="$(hub_pids)"
  if [[ -n "$pids" ]]; then
    echo "[hub] 已在运行 (PID: $(echo "$pids" | tr '\n' ' '))"
    print_entry
    return 0
  fi

  local ctx="/$(web_path)"
  local console_out="$LOG_HOME/hub/console.out"
  local java_args=(
    java
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
  echo "[hub] 启动参数: port=${WEB_PORT} context=${ctx} heap=${INITIAL_HEAP}-${HEAP} stack=256k"
  launch_service "hub" "$ROOT" "$console_out" "hub_pids" "${java_args[@]}" || return 1
  print_entry
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
  if command -v git >/dev/null 2>&1 && git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    echo "当前版本: $(git -C "$ROOT" log -1 --format='%h (%ci) %s' 2>/dev/null)"
  fi
}

cmd_build() {
  command -v mvn >/dev/null 2>&1 || { echo "未找到 mvn" >&2; exit 1; }
  cd "$ROOT"
  local t0="$(date +%s)"
  # 多核并行编译(-T 1C)、跳过源码打包(-Dsource.skip=true)、package直出目标jar避免本地仓库写放大
  local mvn_args=(
    --batch-mode
    --show-version
    -pl hub
    -am
    package
    -DskipTests
    -Dsource.skip=true
    -Dmaven.source.skip=true
    -Dmaven.javadoc.skip=true
    -T 1C
  )
  echo "Maven 构建: mvn -pl hub -am package -DskipTests -Dsource.skip=true -T 1C"
  run_silent_timer "Maven 打包" mvn "${mvn_args[@]}"
  [[ -f "$HUB_JAR" ]] || { echo "缺少打包产物: $HUB_JAR" >&2; return 1; }
  pack_table_models
  local elapsed=$(( $(date +%s) - t0 ))
  printf '打包完成，耗时 %02d:%02d:%02d (OK %s)\n' \
    $((elapsed / 3600)) $(((elapsed % 3600) / 60)) $((elapsed % 60)) "$HUB_JAR"
}

# 核心生命周期封装：停止 -> 中间命令(如 git pull/reset) -> 打包 -> 启动
cycle_hub() {
  local t_total="$(date +%s)"

  echo "[步骤 1/4] 停止 hub 服务..."
  stop_hub

  local t_cmd="$(date +%s)"
  echo "[步骤 2/4] 执行: $*"
  "$@"
  echo "[步骤 2/4] 完成 (耗时 $(( $(date +%s) - t_cmd ))s)"

  echo "[步骤 3/4] 编译打包..."
  cmd_build

  echo "[步骤 4/4] 启动 hub 服务..."
  start_hub

  local total=$(( $(date +%s) - t_total ))
  printf '[hub] 全部流程完成！总耗时: %02d:%02d:%02d，当前版本: %s\n' \
    $((total / 3600)) $(((total % 3600) / 60)) $((total % 60)) \
    "$(git log -1 --oneline 2>/dev/null)"
}

cmd_restart() {
  local t0="$(date +%s)"
  echo "[hub] 快速重启: 停止 -> 启动"
  stop_hub
  start_hub
  echo "[hub] 重启完成 (总耗时 $(( $(date +%s) - t0 ))s)"
}

cmd_build_restart() {
  local t0="$(date +%s)"
  echo "[hub] 打包并重启: 打包 -> 停止 -> 启动"
  cmd_build
  stop_hub
  start_hub
  echo "[hub] 打包重启完成 (总耗时 $(( $(date +%s) - t0 ))s)"
}

# 一键更新与重启：停止 -> git pull -> 打包 -> 启动
cmd_pull_restart() {
  command -v git >/dev/null 2>&1 || { echo "[git] 错误: 未找到 git 命令" >&2; return 1; }
  cd "$ROOT"
  if [[ -n "$(git status --porcelain -uno 2>/dev/null)" ]]; then
    echo "[git] 错误: 检测到本地有未提交修改，终止更新以保护现场。" >&2
    return 1
  fi
  mkdir -p "$BUILD/hub"
  git rev-parse HEAD > "$BUILD/hub/prev_commit.txt" 2>/dev/null || true
  echo "[hub] 一键更新: 停止 -> git pull -> 打包 -> 启动"
  cycle_hub git pull
}

# 专有一键版本回退：停止 -> git reset --hard [版本] -> 打包 -> 启动
cmd_rollback() {
  command -v git >/dev/null 2>&1 || { echo "[git] 错误: 未找到 git 命令" >&2; return 1; }
  cd "$ROOT"
  local target="${1:-}"
  local record_file="$BUILD/hub/prev_commit.txt"
  if [[ -z "$target" ]]; then
    [[ -f "$record_file" ]] && target="$(tr -d '[:space:]' < "$record_file")"
    target="${target:-HEAD~1}"
  fi
  echo "[hub] 版本回退: 停止 -> git reset --hard $target -> 打包 -> 启动"
  cycle_hub git reset --hard "$target"
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
    restart) cmd_restart ;;
    status) cmd_status ;;
    monitor) cmd_monitor "${2:-1}" ;;
    build) cmd_build ;;
    build-restart) cmd_build_restart ;;
    pull-restart|update-restart|update) cmd_pull_restart ;;
    rollback) cmd_rollback "${2:-}" ;;
    deploy) cmd_build; start_hub ;;
    clean-logs) cmd_clean_logs ;;
    nginx-apply) cmd_nginx_apply "${2:-}" "${3:-$WEB_PORT}" ;;
    -h|--help|help|"") usage; [[ -n "$cmd" ]] || exit 1 ;;
    *) echo "未知命令: $cmd"; usage; exit 1 ;;
  esac
}

main "$@"

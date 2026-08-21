#!/usr/bin/env bash
# 旧五服务运维：center / gate / lobby / game / web
set -euo pipefail

# shellcheck source=ops-common.sh
source "$(cd "$(dirname "$0")" && pwd)/ops-common.sh"

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
  $0 start-remaining     # 起尚未运行的 center/gate/lobby/game
  $0 build [服务|all]
  $0 build-restart [服务|all]
  $0 monitor [刷新秒数|--once]
  $0 clean-logs
  $0 nginx-apply <域名> [Web端口]

服务: center | gate | lobby | game | web | all
一体化 hub 请用: $SCRIPTS/hub.sh

示例:
  $0 start web
  $0 start all
  $0 start-remaining
  $0 build web
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

  require_no_hub || return 1

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
      "-Dserver.port=${WEB_PORT}"
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
  cmd_build "$arg"
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
  local arg="${1:-all}"
  local targets
  local build_started_epoch build_finished_epoch build_elapsed build_status
  # 先复用服务参数校验，避免把任意内容传给 Maven。
  # shellcheck disable=SC2207
  targets=($(resolve_targets "$arg"))
  command -v mvn >/dev/null 2>&1 || { echo "未找到 mvn"; exit 1; }
  cd "$ROOT"
  build_started_epoch="$(date +%s)"
  echo "Maven 开始时间: $(date '+%Y-%m-%d %H:%M:%S %Z')"
  echo "下面实时显示 Maven 完整输出，可直接看到当前编译模块和停留位置。"
  if [[ "$arg" == "all" ]]; then
    echo "执行: mvn --batch-mode --show-version install -DskipTests"
    if mvn --batch-mode --show-version install -DskipTests; then
      build_status=0
    else
      build_status=$?
    fi
  else
    echo "执行: mvn --batch-mode --show-version -pl $arg -am install -DskipTests"
    if mvn --batch-mode --show-version -pl "$arg" -am install -DskipTests; then
      build_status=0
    else
      build_status=$?
    fi
  fi
  build_finished_epoch="$(date +%s)"
  build_elapsed=$((build_finished_epoch - build_started_epoch))
  if [[ "$build_status" -ne 0 ]]; then
    printf 'Maven 打包失败（退出码 %s），耗时 %02d:%02d:%02d\n' \
      "$build_status" $((build_elapsed / 3600)) $(((build_elapsed % 3600) / 60)) $((build_elapsed % 60)) >&2
    return "$build_status"
  fi
  printf 'Maven 编译完成，耗时 %02d:%02d:%02d；开始校验和同步产物。\n' \
    $((build_elapsed / 3600)) $(((build_elapsed % 3600) / 60)) $((build_elapsed % 60))

  local svc jar
  for svc in "${targets[@]}"; do
    jar="$(svc_jar "$svc")"
    if [[ ! -f "$jar" ]]; then
      echo "缺少 $svc 打包产物: $jar" >&2
      return 1
    fi
  done

  # Maven 的 copy-dependencies 会排除 com.cloud 内部模块；这里必须显式同步，
  # 否则业务 JAR 更新后仍可能加载旧的 tool/utils/proto JAR。
  local module lib
  for svc in "${targets[@]}"; do
    [[ "$svc" == "web" ]] && continue
    for module in utils proto tool; do
      jar="$ROOT/$module/target/$module-1.0-SNAPSHOT.jar"
      if [[ ! -f "$jar" ]]; then
        echo "缺少内部模块产物: $jar" >&2
        return 1
      fi
      lib="$BUILD/$svc/lib"
      mkdir -p "$lib"
      cp -f "$jar" "$lib/$module-1.0-SNAPSHOT.jar"
    done
  done

  if [[ " ${targets[*]} " == *" game "* && ! -f "$BUILD/game/app.properties" ]]; then
    cp "$ROOT/game/app.properties.example" "$BUILD/game/app.properties"
    echo "已创建游戏服外置配置: $BUILD/game/app.properties"
  fi

  # 校验改包后的运行时类确实来自最新 tool JAR。
  for svc in "${targets[@]}"; do
    [[ "$svc" == "web" ]] && continue
    if ! jar tf "$BUILD/$svc/lib/tool-1.0-SNAPSHOT.jar" | grep -q '^tools/ServerManager.class$'; then
      echo "$svc 的 tool JAR 校验失败：未找到 tools/ServerManager.class" >&2
      return 1
    fi
  done

  if [[ "$arg" != "web" ]]; then
    echo "内部模块依赖已同步并校验: utils / proto / tool"
  fi

  # 刷新 tablemodel 配置（与当前 tool 类一致）
  if [[ "$arg" != "web" && -f "$ROOT/tool/target/tool-1.0-SNAPSHOT.jar" ]]; then
    (cd "$ROOT" && java -cp "tool/target/tool-1.0-SNAPSHOT.jar:$BUILD/game/lib/*" tool.ConfigPacker >/dev/null 2>&1 || true)
    [[ -f "$ROOT/config/tablemodel_models.dat" ]] && mkdir -p "$BUILD/config" && cp -f "$ROOT/config/tablemodel_models.dat" "$BUILD/config/tablemodel_models.dat"
  fi
  build_finished_epoch="$(date +%s)"
  build_elapsed=$((build_finished_epoch - build_started_epoch))
  printf '打包全部完成: %s，总耗时 %02d:%02d:%02d\n' "$(date '+%Y-%m-%d %H:%M:%S %Z')" \
    $((build_elapsed / 3600)) $(((build_elapsed % 3600) / 60)) $((build_elapsed % 60))
  for svc in "${targets[@]}"; do
    echo "  OK $(svc_jar "$svc")"
  done
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
    build) cmd_build "$arg" ;;
    build-restart) cmd_build_restart "$arg" ;;
    clean-logs) cmd_clean_logs ;;
    nginx-apply) cmd_nginx_apply "${2:-}" "${3:-$WEB_PORT}" ;;
    -h|--help|help|"") usage; [[ -n "$cmd" ]] || exit 1 ;;
    *) echo "未知命令: $cmd"; usage; exit 1 ;;
  esac
}

main "$@"

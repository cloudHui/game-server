#!/usr/bin/env bash
# game-server 一键安装：环境 + 拉码；可选部署 hub/legacy 与 Nginx
# 非交互：INSTALL_NONINTERACTIVE=1 DEPLOY=yes|no DEPLOY_MODE=hub|legacy CONFIGURE_NGINX=yes|no SERVER_DOMAIN=... SKIP_GIT_CONFIG=1
set -euo pipefail

REPO_URL="${SERVER_REPO_URL:-https://github.com/cloudHui/game-server.git}"
INSTALL_DIR="${SERVER_INSTALL_DIR:-/opt/Server}"
COMMAND="${1:-install}"
NONINTERACTIVE="${INSTALL_NONINTERACTIVE:-0}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "请使用 sudo 执行，例如: sudo bash install.sh install" >&2
  exit 1
fi

RUN_USER="${SUDO_USER:-root}"

as_user() {
  if [[ "$RUN_USER" == "root" ]]; then
    "$@"
  else
    sudo -u "$RUN_USER" -H "$@"
  fi
}

ask() {
  local prompt=$1 default=$2
  if [[ "$NONINTERACTIVE" == "1" ]]; then
    ANSWER=$default
    return
  fi
  if [[ -r /dev/tty ]]; then
    printf '%s [%s]: ' "$prompt" "$default" >/dev/tty
    IFS= read -r ANSWER </dev/tty || ANSWER=
  else
    ANSWER=
  fi
  ANSWER=${ANSWER:-$default}
}

mem_headroom_mb() {
  awk '/MemAvailable:/ {a=$2} /SwapFree:/ {s=$2} END {printf "%d", (a+s)/1024}' /proc/meminfo 2>/dev/null || echo 0
}

install_packages() {
  if command -v apt-get >/dev/null 2>&1; then
    apt-get update
    DEBIAN_FRONTEND=noninteractive apt-get install -y git curl maven openjdk-8-jdk
  elif command -v dnf >/dev/null 2>&1; then
    dnf install -y git curl maven java-1.8.0-openjdk-devel
  elif command -v yum >/dev/null 2>&1; then
    yum install -y git curl maven java-1.8.0-openjdk-devel
  else
    echo "未识别的包管理器，请手动安装 Git、JDK 8、Maven、curl。" >&2
    exit 1
  fi
}

check_java8() {
  command -v java >/dev/null 2>&1 || return 1
  java -version 2>&1 | grep -Eq 'version "1\.8\.|openjdk version "8\.'
}

configure_git() {
  if [[ "${SKIP_GIT_CONFIG:-0}" == "1" ]]; then
    return
  fi
  local name email
  name="$(as_user git config --global user.name 2>/dev/null || true)"
  email="$(as_user git config --global user.email 2>/dev/null || true)"
  ask "Git 用户名" "${name:-}"
  name=$ANSWER
  ask "Git 邮箱" "${email:-}"
  email=$ANSWER
  [[ -n "$name" && -n "$email" ]] || { echo "Git 用户名和邮箱不能为空。" >&2; exit 1; }
  as_user git config --global user.name "$name"
  as_user git config --global user.email "$email"
}

clone_code() {
  mkdir -p "$(dirname "$INSTALL_DIR")"
  if [[ -d "$INSTALL_DIR/.git" ]]; then
    echo "代码目录已存在，更新代码: $INSTALL_DIR"
    as_user git -C "$INSTALL_DIR" pull --ff-only || true
  elif [[ -e "$INSTALL_DIR" ]]; then
    echo "目标目录已存在且不是 Git 仓库: $INSTALL_DIR" >&2
    exit 1
  else
    git clone "$REPO_URL" "$INSTALL_DIR"
  fi
  chown -R "$RUN_USER":"$(id -gn "$RUN_USER" 2>/dev/null || echo "$RUN_USER")" "$INSTALL_DIR"
  mkdir -p "$INSTALL_DIR/data/learning/resources" "$INSTALL_DIR/data/learning/datasets" "$INSTALL_DIR/data" "$INSTALL_DIR/logs"
  if [[ -d "$INSTALL_DIR/datasets" ]]; then
    echo "安装本地学习资源"
    as_user bash "$INSTALL_DIR/scripts/learning/install-datasets.sh" \
      "$INSTALL_DIR/datasets" "$INSTALL_DIR/data/learning/datasets"
  fi
  chown -R "$RUN_USER":"$(id -gn "$RUN_USER" 2>/dev/null || echo "$RUN_USER")" "$INSTALL_DIR/data" "$INSTALL_DIR/logs"
}

ensure_access_code() {
  local f="$INSTALL_DIR/scripts/web-path.txt"
  if [[ ! -f "$f" ]]; then
    as_user bash -c "tr -dc 'A-Za-z0-9' </dev/urandom | head -c 18 >'$f'"
  fi
  ACCESS_CODE="$(tr -d '[:space:]' <"$f")"
  echo "访问唯一码: $ACCESS_CODE"
}

maybe_deploy() {
  ask "是否现在部署" "${DEPLOY:-yes}"
  case "$ANSWER" in
    y|Y|yes|YES)
      ask "部署模式（hub=一体化，legacy=旧五服务）" "${DEPLOY_MODE:-hub}"
      local mode="$ANSWER"
      case "$mode" in
        hub|legacy) ;;
        *) echo "部署模式非法: $mode（应为 hub 或 legacy）" >&2; exit 1 ;;
      esac
      echo "当前可用内存约 $(mem_headroom_mb)MB；部署模式=$mode"
      if [[ "$mode" == "hub" ]]; then
        as_user bash -c "cd '$INSTALL_DIR' && ./scripts/hub.sh deploy"
      else
        as_user bash -c "cd '$INSTALL_DIR' && ./scripts/ops.sh build all && ./scripts/ops.sh start all"
      fi
      ;;
    *)
      echo "已跳过部署。稍后: cd $INSTALL_DIR && ./scripts/hub.sh deploy"
      ;;
  esac
}

maybe_nginx() {
  ask "是否配置 Nginx 反代（需已有域名 conf）" "${CONFIGURE_NGINX:-no}"
  case "$ANSWER" in
    y|Y|yes|YES)
      ask "域名（如 www.example.com）" "${SERVER_DOMAIN:-}"
      local domain=$ANSWER
      [[ -n "$domain" ]] || { echo "未提供域名，跳过 Nginx"; return; }
      if ss -lntp 2>/dev/null | grep -E ':443[[:space:]]' | grep -vq nginx; then
        echo "提示: 443 已被非 Nginx 进程占用（常见为 Xray）。请参阅 $INSTALL_DIR/scripts/nginx/XRAY-SNI.md"
      fi
      if ! command -v nginx >/dev/null 2>&1; then
        if command -v apt-get >/dev/null 2>&1; then
          DEBIAN_FRONTEND=noninteractive apt-get install -y nginx
        elif command -v dnf >/dev/null 2>&1; then
          dnf install -y nginx
        elif command -v yum >/dev/null 2>&1; then
          yum install -y nginx
        else
          echo "请先安装 nginx" >&2
          return
        fi
      fi
      bash "$INSTALL_DIR/scripts/ops.sh" nginx-apply "$domain"
      echo "外网入口: https://${domain}/${ACCESS_CODE}/"
      ;;
    *)
      echo "已跳过 Nginx。稍后: cd $INSTALL_DIR && sudo ./scripts/ops.sh nginx-apply <域名>"
      ;;
  esac
}

case "$COMMAND" in
  install)
    command -v git >/dev/null 2>&1 || install_packages
    check_java8 || install_packages
    command -v mvn >/dev/null 2>&1 || install_packages
    command -v curl >/dev/null 2>&1 || install_packages
    configure_git
    clone_code
    ensure_access_code
    maybe_deploy
    maybe_nginx
    echo
    echo "安装完成: $INSTALL_DIR"
    echo "常用:"
    echo "  ./scripts/hub.sh deploy"
    echo "  ./scripts/hub.sh status"
    echo "  ./scripts/hub.sh stop"
    echo "  ./scripts/ops.sh start all"
    echo "  ./scripts/ops.sh start-remaining"
    ;;
  *)
    echo "用法: sudo bash install.sh install" >&2
    exit 1
    ;;
esac

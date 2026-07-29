#!/bin/sh
set -eu

REPO_RAW_BASE=${REPO_RAW_BASE:-https://raw.githubusercontent.com/cloudHui/game-server/main/server-deploy}
GAME_SERVER_INSTALL_URL=${GAME_SERVER_INSTALL_URL:-https://raw.githubusercontent.com/cloudHui/game-server/main/install.sh}
SERVER_INSTALL_DIR=${SERVER_INSTALL_DIR:-/opt/Server}
XUI_INSTALL_URL=${XUI_INSTALL_URL:-https://raw.githubusercontent.com/MHSanaei/3x-ui/master/install.sh}
BASE=/opt/server-deploy
ENV_FILE=/etc/server-deploy/server.env
ACTION=${1:-install}
ROOT=
case "$0" in /*|./*|../*) ROOT=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd);; esac

say() { printf '%s\n' "[server-deploy] $*"; }
die() { say "错误：$*" >&2; exit 1; }
has() { command -v "$1" >/dev/null 2>&1; }
ask() { printf '%s ' "$1" >/dev/tty; IFS= read -r ANSWER </dev/tty || ANSWER=; }
ask_secret() { printf '%s ' "$1" >/dev/tty; stty -echo </dev/tty; IFS= read -r ANSWER </dev/tty || ANSWER=; stty echo </dev/tty; printf '\n' >/dev/tty; }

[ "$(id -u)" -eq 0 ] || exec sudo sh "$0" "$@"

pkg_install() {
  if has apt-get; then
    apt-get update
    DEBIAN_FRONTEND=noninteractive apt-get install -y "$@"
  elif has dnf; then
    dnf install -y "$@"
  elif has yum; then
    yum install -y "$@"
  else die "仅支持 apt、dnf、yum 系统"; fi
}

packages() {
  if has apt-get; then
    pkg_install curl ca-certificates git python3 nginx fail2ban ufw openssl certbot vnstat
  elif has dnf || has yum; then
    pkg_install curl ca-certificates git python3 nginx fail2ban firewalld openssl certbot vnstat
  else die "仅支持 apt、dnf、yum 系统"; fi
}

monitor_packages() {
  pkg_install curl ca-certificates python3 vnstat
}

command_audit_install() {
  # 任意登录用户都需能写入审计日志
  touch /var/log/cmd_audit.log
  chown root:root /var/log/cmd_audit.log
  chmod 666 /var/log/cmd_audit.log
  install -d -m 755 /etc/profile.d
  cat > /etc/profile.d/cmd_audit.sh <<'EOF'
# 命令操作审计：记录时间/用户/IP/目录/命令
export HISTTIMEFORMAT='%F %T '
export HISTSIZE=50000
export HISTFILESIZE=50000
shopt -s histappend 2>/dev/null
_cmd_audit_log() {
  local last_cmd client_ip
  client_ip=${SSH_CLIENT-}
  client_ip=${client_ip%% *}
  last_cmd=$(history 1 2>/dev/null | sed 's/^[[:space:]]*[0-9][0-9]*[[:space:]]*//; s/^[[:space:]]*[0-9-][0-9-]*[[:space:]]\+[0-9:][0-9:]*[[:space:]]*//')
  [ -z "$last_cmd" ] && return
  echo "$(date '+%F %T') user=${USER:-} ip=${client_ip} pwd=$(pwd) cmd=${last_cmd}" >> /var/log/cmd_audit.log 2>/dev/null
}
case ";${PROMPT_COMMAND-};" in
  *_cmd_audit_log*) ;;
  *) PROMPT_COMMAND="_cmd_audit_log; history -a${PROMPT_COMMAND:+; ${PROMPT_COMMAND}}" ;;
esac
EOF
  chown root:root /etc/profile.d/cmd_audit.sh
  chmod 644 /etc/profile.d/cmd_audit.sh
  say "命令操作审计已启用"
}

vnstat_setup() {
  has vnstat || die "vnstat 安装失败"
  iface=$(ip route get 1.1.1.1 2>/dev/null | awk '{for (i=1;i<=NF;i++) if ($i=="dev") {print $(i+1); exit}}')
  [ -n "$iface" ] || iface=$(ip -o link show | awk -F': ' '$2!="lo"{print $2; exit}')
  [ -n "$iface" ] || die "无法检测默认网卡"
  if systemctl list-unit-files vnstat.service >/dev/null 2>&1; then
    systemctl enable --now vnstat.service >/dev/null 2>&1 || true
  elif systemctl list-unit-files vnstatd.service >/dev/null 2>&1; then
    systemctl enable --now vnstatd.service >/dev/null 2>&1 || true
  fi
  vnstat --add -i "$iface" >/dev/null 2>&1 || true
  say "vnstat 已就绪，网卡：$iface"
}

asset() {
  name=$1
  install -d -m 755 "$BASE"
  if [ -f "${ROOT:-}/$name" ]; then install -m 755 "${ROOT}/$name" "$BASE/$name"; else curl -fsSL "$REPO_RAW_BASE/$name" -o "$BASE/$name"; chmod 755 "$BASE/$name"; fi
}

env_get() { [ -f "$ENV_FILE" ] && sed -n "s/^$1=//p" "$ENV_FILE" | tail -1 || true; }

detect_public_ip() {
  curl -4 -fsS --max-time 5 https://api.ipify.org 2>/dev/null \
    || curl -4 -fsS --max-time 5 https://ifconfig.me 2>/dev/null \
    || true
}

detect_private_ip() {
  ip -4 route get 1.1.1.1 2>/dev/null | awk '{for (i=1;i<=NF;i++) if ($i=="src") {print $(i+1); exit}}'
}

detect_iface() {
  ip route get 1.1.1.1 2>/dev/null | awk '{for (i=1;i<=NF;i++) if ($i=="dev") {print $(i+1); exit}}'
}

write_env_file() {
  install -d -m 750 /etc/server-deploy
  {
    printf 'DOMAIN=%s\n' "$DOMAIN"
    printf 'SMTP_HOST=%s\nSMTP_PORT=%s\nSMTP_USER=%s\n' "$SMTP_HOST" "$SMTP_PORT" "$SMTP_USER"
    printf 'SMTP_AUTH_B64=%s\nREPORT_RECIPIENT=%s\n' "$SMTP_AUTH_B64" "$REPORT_RECIPIENT"
    printf 'SSH_WHITELIST=%s\nSSH_WHITELIST_NETS=%s\n' "$SSH_WHITELIST" "$SSH_WHITELIST_NETS"
    printf 'CERT_EMAIL=%s\nCERT_PATH=%s\n' "$CERT_EMAIL" "$CERT_PATH"
    printf 'PUBLIC_IP=%s\nPRIVATE_IP=%s\nNET_IFACE=%s\n' "$PUBLIC_IP" "$PRIVATE_IP" "$NET_IFACE"
    printf 'SERVER_LOCATION=%s\nSERVER_EXPIRY=%s\n' "$SERVER_LOCATION" "$SERVER_EXPIRY"
    printf 'EXPECTED_UNITS=%s\nEXPECTED_PUBLIC_PORTS=%s\n' "$EXPECTED_UNITS" "$EXPECTED_PUBLIC_PORTS"
    printf 'DEPLOYED_AT=%s\n' "$DEPLOYED_AT"
  } > "$ENV_FILE"
  chmod 600 "$ENV_FILE"
}

ensure_env_defaults() {
  [ -f "$ENV_FILE" ] || return 0
  DOMAIN=$(env_get DOMAIN)
  SMTP_HOST=$(env_get SMTP_HOST)
  SMTP_PORT=$(env_get SMTP_PORT); SMTP_PORT=${SMTP_PORT:-465}
  SMTP_USER=$(env_get SMTP_USER)
  SMTP_AUTH_B64=$(env_get SMTP_AUTH_B64)
  REPORT_RECIPIENT=$(env_get REPORT_RECIPIENT)
  SSH_WHITELIST=$(env_get SSH_WHITELIST)
  SSH_WHITELIST_NETS=$(env_get SSH_WHITELIST_NETS)
  CERT_EMAIL=$(env_get CERT_EMAIL)
  CERT_PATH=$(env_get CERT_PATH)
  PUBLIC_IP=$(env_get PUBLIC_IP)
  PRIVATE_IP=$(env_get PRIVATE_IP)
  NET_IFACE=$(env_get NET_IFACE)
  SERVER_LOCATION=$(env_get SERVER_LOCATION)
  SERVER_EXPIRY=$(env_get SERVER_EXPIRY)
  EXPECTED_UNITS=$(env_get EXPECTED_UNITS)
  EXPECTED_PUBLIC_PORTS=$(env_get EXPECTED_PUBLIC_PORTS)
  DEPLOYED_AT=$(env_get DEPLOYED_AT)
  [ -n "$DEPLOYED_AT" ] || DEPLOYED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  [ -n "$PUBLIC_IP" ] || PUBLIC_IP=$(detect_public_ip)
  [ -n "$PRIVATE_IP" ] || PRIVATE_IP=$(detect_private_ip)
  [ -n "$NET_IFACE" ] || NET_IFACE=$(detect_iface)
  [ -n "$EXPECTED_UNITS" ] || EXPECTED_UNITS='x-ui nginx fail2ban'
  [ -n "$EXPECTED_PUBLIC_PORTS" ] || EXPECTED_PUBLIC_PORTS='22 80 443 2096 7393 7443'
  if [ -z "$CERT_PATH" ] && [ -n "$DOMAIN" ]; then
    CERT_PATH=/etc/letsencrypt/live/$DOMAIN/fullchain.pem
  fi
  write_env_file
  say "已补齐 server.env 运行时字段（IP/网卡/预期端口等）"
}

configure() {
  old_domain=$(env_get DOMAIN)
  old_recipient=$(env_get REPORT_RECIPIENT)
  old_whitelist=$(env_get SSH_WHITELIST)
  old_whitelist_nets=$(env_get SSH_WHITELIST_NETS)
  old_smtp_host=$(env_get SMTP_HOST)
  old_smtp_port=$(env_get SMTP_PORT); old_smtp_port=${old_smtp_port:-465}
  old_smtp_user=$(env_get SMTP_USER)
  old_auth_b64=$(env_get SMTP_AUTH_B64)
  old_cert_email=$(env_get CERT_EMAIL)
  old_location=$(env_get SERVER_LOCATION)
  old_expiry=$(env_get SERVER_EXPIRY)
  old_units=$(env_get EXPECTED_UNITS); old_units=${old_units:-x-ui nginx fail2ban}
  old_ports=$(env_get EXPECTED_PUBLIC_PORTS); old_ports=${old_ports:-22 80 443 2096 7393 7443}
  detected_public=$(detect_public_ip)
  detected_private=$(detect_private_ip)
  detected_iface=$(detect_iface)
  old_public=$(env_get PUBLIC_IP); old_public=${old_public:-$detected_public}
  old_private=$(env_get PRIVATE_IP); old_private=${old_private:-$detected_private}
  old_iface=$(env_get NET_IFACE); old_iface=${old_iface:-$detected_iface}

  ask "域名（直接回车使用公网 IP）[${old_domain}]"; DOMAIN=${ANSWER:-$old_domain}
  ask "SMTP 主机（不填则不发送日报）[${old_smtp_host}]"; SMTP_HOST=${ANSWER:-$old_smtp_host}
  ask "SMTP 端口 [${old_smtp_port}]"; SMTP_PORT=${ANSWER:-$old_smtp_port}
  ask "发件邮箱 [${old_smtp_user}]"; SMTP_USER=${ANSWER:-$old_smtp_user}
  ask_secret "SMTP 授权码（不是邮箱登录密码）"; SMTP_AUTH=$ANSWER
  if [ -z "$SMTP_AUTH" ] && [ -n "$old_auth_b64" ]; then SMTP_AUTH=$(printf '%s' "$old_auth_b64" | base64 -d); fi
  ask "收件邮箱 [${old_recipient}]"; REPORT_RECIPIENT=${ANSWER:-$old_recipient}
  CERT_EMAIL=${old_cert_email:-$SMTP_USER}
  [ -n "$DOMAIN" ] && { ask "证书通知邮箱 [${CERT_EMAIL}]"; CERT_EMAIL=${ANSWER:-$CERT_EMAIL}; }
  ask "SSH 白名单 IP（多个用空格分隔，可留空）[${old_whitelist}]"; SSH_WHITELIST=${ANSWER:-$old_whitelist}
  ask "SSH 白名单网段（CIDR，可留空）[${old_whitelist_nets}]"; SSH_WHITELIST_NETS=${ANSWER:-$old_whitelist_nets}
  ask "公网 IP [${old_public}]"; PUBLIC_IP=${ANSWER:-$old_public}
  ask "内网 IP [${old_private}]"; PRIVATE_IP=${ANSWER:-$old_private}
  ask "流量网卡 [${old_iface}]"; NET_IFACE=${ANSWER:-$old_iface}
  ask "服务器位置说明（可留空）[${old_location}]"; SERVER_LOCATION=${ANSWER:-$old_location}
  ask "服务器到期日 YYYY-MM-DD（可留空）[${old_expiry}]"; SERVER_EXPIRY=${ANSWER:-$old_expiry}
  ask "预期服务名 [${old_units}]"; EXPECTED_UNITS=${ANSWER:-$old_units}
  ask "预期对外端口 [${old_ports}]"; EXPECTED_PUBLIC_PORTS=${ANSWER:-$old_ports}
  ask "SSH 配置：0 不配置，1 自动生成密钥，2 输入已有公钥 [0]"; SSH_MODE=${ANSWER:-0}
  SSH_USER=${SUDO_USER:-${USER:-root}}
  case "$SSH_MODE" in
    1) ask "SSH 用户名 [${SSH_USER}]"; SSH_USER=${ANSWER:-$SSH_USER};;
    2) ask "SSH 用户名 [${SSH_USER}]"; SSH_USER=${ANSWER:-$SSH_USER}; ask "已有 SSH 公钥"; SSH_PUBLIC_KEY=$ANSWER;;
    0) ;;
    *) die "SSH 配置选项只能是 0、1 或 2";;
  esac
  [ -n "$DOMAIN" ] || DOMAIN=
  [ -z "$DOMAIN" ] || printf '%s' "$DOMAIN" | grep -Eq '^[A-Za-z0-9.-]+$' || die "域名格式不正确"
  printf '%s' "$SMTP_PORT" | grep -Eq '^[0-9]+$' || die "SMTP 端口格式不正确"
  [ -z "$SERVER_EXPIRY" ] || printf '%s' "$SERVER_EXPIRY" | grep -Eq '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' || die "到期日格式应为 YYYY-MM-DD"
  SMTP_AUTH_B64=$(printf '%s' "$SMTP_AUTH" | base64 | tr -d '\n')
  CERT_PATH=
  [ -n "$DOMAIN" ] && CERT_PATH=/etc/letsencrypt/live/$DOMAIN/fullchain.pem
  DEPLOYED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  write_env_file
  export DOMAIN SMTP_HOST SMTP_PORT SMTP_USER SMTP_AUTH REPORT_RECIPIENT CERT_EMAIL SSH_WHITELIST SSH_USER SSH_MODE SSH_PUBLIC_KEY
}

firewall() {
  if has ufw; then ufw allow 22/tcp >/dev/null; ufw allow 80/tcp >/dev/null; ufw allow 443/tcp >/dev/null; ufw --force enable >/dev/null
  elif has firewall-cmd; then systemctl enable --now firewalld; firewall-cmd --permanent --add-port=22/tcp >/dev/null; firewall-cmd --permanent --add-service=http >/dev/null; firewall-cmd --permanent --add-service=https >/dev/null; firewall-cmd --reload >/dev/null; fi
}

install_family() {
  say "安装/更新 game-server（Web 主鉴权，默认只起 web）"
  curl -fsSL "$GAME_SERVER_INSTALL_URL" -o /tmp/game-server-install.sh
  INSTALL_NONINTERACTIVE=1 SKIP_GIT_CONFIG=1 \
    SERVER_REPO_URL="${SERVER_REPO_URL:-https://github.com/cloudHui/game-server.git}" \
    SERVER_INSTALL_DIR="$SERVER_INSTALL_DIR" \
    DEPLOY=yes CONFIGURE_NGINX=$([ -n "$DOMAIN" ] && echo yes || echo no) \
    SERVER_DOMAIN="$DOMAIN" \
    MAIL_HOST="$SMTP_HOST" MAIL_PORT="$SMTP_PORT" MAIL_USERNAME="$SMTP_USER" MAIL_PASSWORD="$SMTP_AUTH" \
    REPORT_RECIPIENT="$REPORT_RECIPIENT" \
    sh /tmp/game-server-install.sh install
  rm -f /tmp/game-server-install.sh
}

install_xui() {
  say "安装/更新 3x-ui XRay v3.3.0"
  curl -fsSL "$XUI_INSTALL_URL" -o /tmp/3x-ui-install.sh
  bash /tmp/3x-ui-install.sh v3.3.0
  rm -f /tmp/3x-ui-install.sh
}

configure_proxy_protocol() {
  # The SNI stream proxy sends PROXY protocol to both the web and XRay backends.
  # Persist the XRay setting in x-ui's database because config.json is generated
  # from it whenever x-ui starts.
  [ -f /etc/x-ui/x-ui.db ] || return 0
  python3 - <<'PY'
import json
import sqlite3

path = '/etc/x-ui/x-ui.db'
db = sqlite3.connect(path)
rows = db.execute('select id, stream_settings from inbounds where port = 7443').fetchall()
for inbound_id, raw in rows:
    data = json.loads(raw or '{}')
    tcp = data.setdefault('tcpSettings', {})
    tcp['acceptProxyProtocol'] = True
    db.execute('update inbounds set stream_settings = ? where id = ?',
               (json.dumps(data, separators=(',', ':')), inbound_id))
db.commit()
db.close()
PY
  systemctl restart x-ui.service
}

ssh_key() {
  case "${SSH_MODE:-0}" in
  1)
    id "$SSH_USER" >/dev/null 2>&1 || die "用户不存在：$SSH_USER"
    home=$(getent passwd "$SSH_USER" | cut -d: -f6); install -d -m 700 -o "$SSH_USER" -g "$SSH_USER" "$home/.ssh"
    key="$home/.ssh/id_ed25519"; [ -e "$key" ] && key="$home/.ssh/id_ed25519_$(date +%s)"
    ssh-keygen -t ed25519 -f "$key" -N '' -C "$SSH_USER@$(hostname)" >/dev/null
    cat "$key.pub" >> "$home/.ssh/authorized_keys"
    chown "$SSH_USER:$SSH_USER" "$key" "$key.pub" "$home/.ssh/authorized_keys"; chmod 600 "$key" "$home/.ssh/authorized_keys"; chmod 644 "$key.pub"
    say "SSH 私钥仅显示一次，请立即保存："; cat "$key"; say "SSH 公钥："; cat "$key.pub";;
  2)
    id "$SSH_USER" >/dev/null 2>&1 || die "用户不存在：$SSH_USER"
    printf '%s' "$SSH_PUBLIC_KEY" | grep -Eq '^(ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp256) ' || die "SSH 公钥格式不正确"
    home=$(getent passwd "$SSH_USER" | cut -d: -f6); install -d -m 700 -o "$SSH_USER" -g "$SSH_USER" "$home/.ssh"
    auth="$home/.ssh/authorized_keys"; touch "$auth"; grep -Fqx "$SSH_PUBLIC_KEY" "$auth" || printf '%s\n' "$SSH_PUBLIC_KEY" >> "$auth"
    chown "$SSH_USER:$SSH_USER" "$auth"; chmod 600 "$auth"; say "已有 SSH 公钥已写入 $auth";;
  esac
}

fail2ban_config() {
  install -d -m 755 /etc/fail2ban/jail.d
  ignore="127.0.0.1/8 ::1"; for ip in $SSH_WHITELIST; do ignore="$ignore $ip"; done
  cat > /etc/fail2ban/jail.d/server-deploy-sshd.local <<EOF
[sshd]
enabled = true
port = ssh
filter = sshd
logpath = %(sshd_log)s
backend = systemd
ignoreip = $ignore
bantime = -1
findtime = 1h
maxretry = 1
EOF
  systemctl enable --now fail2ban; fail2ban-client reload >/dev/null 2>&1 || systemctl restart fail2ban
}

monitor_install() {
  asset install.sh; asset monitor.py; asset server_audit_report.py; asset server-deploy
  install -d -m 755 /var/lib/server-deploy
  install -m 755 "$BASE/monitor.py" /opt/server-deploy-monitor.py
  install -m 755 "$BASE/server_audit_report.py" /opt/server-audit-report.py
  install -m 755 "$BASE/server-deploy" /usr/local/bin/server-deploy
  # Replace legacy standalone monitor/report cron if present.
  rm -f /etc/cron.d/server-monitoring
  cat > /etc/cron.d/server-deploy <<EOF
SHELL=/bin/sh
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
*/5 * * * * root /usr/bin/python3 /opt/server-deploy-monitor.py >> /var/log/server-deploy-monitor.log 2>&1
10 0 * * * root /usr/bin/python3 /opt/server-audit-report.py >> /var/log/server-audit-report.log 2>&1
EOF
  chmod 644 /etc/cron.d/server-deploy
  ensure_env_defaults
}

configure_sni() {
  [ -n "$DOMAIN" ] || return 0
  # game-server 使用域名 conf + snippets；若仍有旧 family-learning.conf 则迁移监听
  if [ -f /etc/nginx/conf.d/family-learning.conf ]; then
    install -d -m 755 /etc/nginx/stream-conf.d
    sed -i 's/listen 443 ssl[^;]*;/listen 127.0.0.1:8443 ssl proxy_protocol;/; s/listen \[::\]:443 ssl[^;]*;/listen [::1]:8443 ssl proxy_protocol;/' /etc/nginx/conf.d/family-learning.conf
    if ! grep -q 'real_ip_header proxy_protocol' /etc/nginx/conf.d/family-learning.conf; then
      sed -i '/listen 127\.0\.0\.1:8443 ssl proxy_protocol;/a\    set_real_ip_from 127.0.0.1;\n    real_ip_header proxy_protocol;' /etc/nginx/conf.d/family-learning.conf
    fi
  fi
  install -d -m 755 /etc/nginx/stream-conf.d
  cat > /etc/nginx/stream-conf.d/xray-sni.conf <<EOF
map \$ssl_preread_server_name \$stream_backend {
    $DOMAIN 127.0.0.1:8443;
    default 127.0.0.1:7443;
}
server {
    listen 443;
    listen [::]:443;
    proxy_pass \$stream_backend;
    ssl_preread on;
    proxy_protocol on;
    proxy_connect_timeout 5s;
    proxy_timeout 1h;
}
EOF
  if ! grep -q 'stream-conf.d/\*.conf' /etc/nginx/nginx.conf; then
    tmp=$(mktemp); { printf 'stream { include /etc/nginx/stream-conf.d/*.conf; }\n'; cat /etc/nginx/nginx.conf; } > "$tmp"; install -m 644 "$tmp" /etc/nginx/nginx.conf; rm -f "$tmp"; fi
  install -d -m 755 /etc/letsencrypt/renewal-hooks/deploy
  cat > /etc/letsencrypt/renewal-hooks/deploy/server-deploy-nginx-reload <<'EOF'
#!/bin/sh
systemctl reload nginx
EOF
  chmod 755 /etc/letsencrypt/renewal-hooks/deploy/server-deploy-nginx-reload
  nginx -t && systemctl reload nginx
}

case "$ACTION" in
  install|configure)
    packages; vnstat_setup; configure; firewall; install_family; install_xui; configure_proxy_protocol; configure_sni; ssh_key; fail2ban_config; monitor_install; command_audit_install
    say "部署完成，运行 sudo server-deploy status 查看当前状态。";;
  update) monitor_packages; vnstat_setup; asset monitor.py; asset server_audit_report.py; asset server-deploy; asset install.sh; monitor_install; command_audit_install; say "总部署脚本已更新。可执行 sudo server-deploy preview 预览日报。";;
  *) die "用法：install.sh [install|configure|update]";;
esac

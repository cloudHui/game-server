#!/bin/sh
set -eu
fail=0
for unit in nginx.service x-ui.service fail2ban.service; do
  systemctl is-active --quiet "$unit" || { logger -t server-deploy "服务异常：$unit"; fail=1; }
done
pgrep -f 'build/hub/hub.jar' >/dev/null 2>&1 \
  || pgrep -f 'weball[.]jar' >/dev/null 2>&1 \
  || pgrep -f 'build/web/Web.jar' >/dev/null 2>&1 \
  || { logger -t server-deploy "服务异常：hub/web"; fail=1; }
df -P / | awk 'NR==2 {gsub("%","",$5); if ($5 >= 90) exit 1}' || { logger -t server-deploy "根分区磁盘使用率 >= 90%"; fail=1; }
exit "$fail"

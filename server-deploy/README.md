# Server Deploy

面向 Debian、Ubuntu、CentOS、Rocky、AlmaLinux、Amazon Linux 等 Linux 服务器的一键部署仓库。整合 **game-server**（学习+娱乐 Web）、3x-ui/XRay、Nginx HTTPS、Fail2ban、SSH、监控与邮件日报。

## 一键安装

```bash
curl -fsSL https://raw.githubusercontent.com/cloudHui/game-server/main/server-deploy/install.sh | sudo bash
```

需要 root/sudo，可访问 GitHub。自动识别 apt/dnf/yum；不使用 Docker。

## 安装时询问

1. 域名（可留空，用公网 IP）  
2. SMTP（监控/日报；可不配）  
3. 证书通知邮箱（有域名时）  
4. SSH 白名单  
5. SSH 密钥配置（0/1/2）

## 部署内容

- **game-server**：本仓库应用部分；默认构建并只起 **web**（主鉴权在 Web，读 `data/lobby.db`）  
- 学习 datasets 同步到 `data/learning/datasets`  
- XRay：3x-ui `v3.3.0`  
- Nginx：反代 `127.0.0.1:8081` + 访问唯一码；有域名时 HTTPS，并可 SNI 分流 XRay  
- 防火墙 22/80/443；Fail2ban；5 分钟监控；日报  

## 管理

```bash
sudo server-deploy
sudo server-deploy status
sudo server-deploy restart      # x-ui/nginx/fail2ban + web
sudo server-deploy logs         # Web 日志
sudo server-deploy report
sudo server-deploy configure
```

牌桌服（center/gate/lobby/game）内存足够时在安装目录执行：

```bash
cd /opt/Server && ./scripts/ops.sh start-remaining
```

## 路径

- `/opt/Server`：game-server 代码与运行目录  
- `/opt/Server/data/lobby.db`：账号与邀请码  
- `/opt/Server/data/learning/`：学习数据  
- `/etc/server-deploy/server.env`：部署配置  

默认管理员：`admin / admin123`（Web 首次启动创建）。

## 卸载

本仓库不默认卸载。先备份 `/opt/Server/data`、`/etc/x-ui`、`/etc/letsencrypt`、`/etc/server-deploy` 再停服清理。

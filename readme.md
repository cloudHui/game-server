# game-server — 休闲小游戏 + 家庭学习

Java 8 · Maven · 浏览器统一入口。学习与娱乐同一进程；外网走 Nginx，路径为访问唯一码。

```text
https://域名/访问唯一码/
```

默认管理员：`admin` / `admin123`（账号库为空时首次启动创建）。

## 能做什么

| 能力 | 说明 |
|------|------|
| 账号 | `data/lobby.db`；邀请码注册；会话绑定内置 Gateway |
| 学习 | 识字、算术、错题、统计、资源库；数据在 `data/learning/` |
| 娱乐 | 麻将、斗地主、跑得快、拖拉机、机器人局、Web 小游戏 |
| 管理 | 邀请、玩家启停、桌子、战绩、回放、图片改名删除、Shell |

## 目录

```text
hub/            一体化服务（推荐）
center/ gate/ lobby/ game/ web/   旧五服务源码
scripts/hub.sh  hub 启停构建
scripts/ops.sh  旧五服务启停构建
install.sh      应用安装
server-deploy/  整机（Xray/Nginx/Fail2ban）
```

## 安装

空机器：

```bash
curl -fsSL https://raw.githubusercontent.com/cloudHui/game-server/main/install.sh -o /tmp/gs-install.sh
sudo bash /tmp/gs-install.sh install
```

问：Git 用户、是否部署（hub 或旧五服务）、是否配 Nginx。含 Xray 的整机用 `server-deploy/install.sh`。

本仓库已有代码时不要重装，直接用下面命令。

## 怎么用

**推荐（hub，一个进程，默认 8081）：**

```bash
cd /opt/Server   # 或本仓库根目录
./scripts/hub.sh deploy    # 打包 + 启动
./scripts/hub.sh status
./scripts/hub.sh stop
./scripts/hub.sh start     # 已有 hub.jar 时
sudo ./scripts/hub.sh nginx-apply 你的域名
```

本机：`http://127.0.0.1:8081/<scripts/web-path.txt 里的唯一码>/`  
换端口：`WEB_PORT=18081 ./scripts/hub.sh start`

**旧五服务（center/gate/lobby/game/web，与 hub 互斥）：**

```bash
./scripts/ops.sh start web
./scripts/ops.sh start all
./scripts/ops.sh start-remaining
./scripts/ops.sh stop all
```

配置在 `hub/src/main/resources/application.yml`，可用环境变量覆盖。数据在仓库下 `data/`。

前端 Vue 3 迁移计划：`vue3-migration-plan.md`。

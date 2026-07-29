# game-server — 休闲小游戏 + 家庭学习

Java 8 · Maven 多模块 · Netty · Protobuf · Spring Boot（web）· SQLite

浏览器统一入口：**学习**与**娱乐**同属一个 Web 进程；外网经 Nginx，路径为访问唯一码（不暴露应用端口）。

```text
https://域名/访问唯一码/
```

---

## 能做什么

| 能力 | 说明 |
|------|------|
| 账号 | Web 直接校验 `data/lobby.db`；邀请码注册；Lobby 可达时后台默登拉房间 |
| 学习 | 原 family-learning：识字、算术、错题、统计、资源库；数据在 `data/learning/` |
| 娱乐 | 牌类 / 麻将（需 center+gate+lobby+game）；本地小游戏与 Web 内联机五子棋/象棋（仅需 web） |
| 管理 | 邀请码、玩家、桌子、战绩、回放；学习管理页（游戏管理员 = 学习管理员） |

---

## 仓库结构

```text
Server/
  center/ gate/ lobby/ game/   # 牌桌相关进程
  web/                         # HTTP + 静态页 + WS + 学习后端（已并入）
  utils/ proto/ tool/ ...
  datasets/                    # 学习数据包（安装时同步到 data/learning/datasets）
  data/learning/               # 运行时学习数据（SQLite、资源，不入库）
  server-deploy/               # 整机一键部署（Xray/Nginx/Fail2ban/监控），原独立仓并入
  scripts/ops.sh               # 启停 / 构建 / Nginx / start-remaining
  scripts/nginx/               # 反代模板 + Xray SNI 说明
  install.sh                   # 应用一键安装（环境+代码+可选起 web）
```

整机部署（含 Xray 等）：

```bash
curl -fsSL https://raw.githubusercontent.com/cloudHui/game-server/main/server-deploy/install.sh | sudo bash
```

---

## 一键安装

```bash
curl -fsSL https://raw.githubusercontent.com/cloudHui/game-server/main/install.sh -o /tmp/gs-install.sh
sudo bash /tmp/gs-install.sh install
```

交互：

1. Git 用户名 / 邮箱  
2. **是否部署**（构建并**只启动 web**）  
3. **是否配置 Nginx**（域名 + 访问唯一码反代；443 被 Xray 占用时只提示，见 `scripts/nginx/XRAY-SNI.md`）

默认管理员：`admin` / `admin123`（Web 首次启动若账号库为空则自动创建）。只起 web 即可登录、注册（邀请码）、学习。

---

## 日常运维

```bash
cd /opt/Server   # 或你的安装目录

./scripts/ops.sh build
./scripts/ops.sh start web              # 推荐默认
./scripts/ops.sh start-remaining        # 内存足够时起 center/gate/lobby/game
./scripts/ops.sh start center           # 单独起某一服务
./scripts/ops.sh status
./scripts/ops.sh stop all
sudo ./scripts/ops.sh nginx-apply www.example.com
```

### 内存门禁

起 `center/gate/lobby/game` 前检查 `MemAvailable + SwapFree`：

- **≥ 900MB**：允许  
- **不足**：拒绝，并可询问是否追加 **2G swap**；仍不足则只保留 web  

全套 Java 堆大约 600MB+，1.6G 机器建议默认只起 web。

### 敬请期待

前端每分钟请求 `/api/capabilities`：

- 联网牌桌 / 麻将未就绪 → 「敬请期待」  
- 本地小游戏、学习、Web 内联机小游戏 → 只要 web 起来即可用  

---

## 运行时串联

```text
浏览器 ──HTTP/WS──► web(:8081) ──TCP──► gate ──► lobby / game
                     └── 学习 API / 静态页（同进程）
```

推荐全套顺序：`center → gate → lobby → game → web`。  
默认安装只起 `web`；牌桌起来后前端轮询自动解除「敬请期待」。

---

## 学习模块说明

- 代码：`web/src/main/java/web/learning/`  
- 前端：`web/.../static/pages/learning/`  
- API 前缀：`/api/learning/**`（需已登录游戏会话 cookie）  
- 数据：`data/learning/family-learning.sqlite`、`resources/`、`datasets/`  
- 入口：大厅 → 学习 → 成长小课堂  

账号与游戏统一；不再使用独立学习登录页。

---

## 配置要点

| 项 | 说明 |
|----|------|
| 访问唯一码 | `scripts/web-path.txt`，启动 web 时作为 `context-path` |
| Web 监听 | `127.0.0.1:8081` |
| Gate | `5600` |
| Lobby | `5700`，管理 HTTP `5701` |
| Game | `5500` |
| Center | `5400` |

邮件等学习日报变量见 `web/.../application.yml`（`MAIL_*`、`REPORT_RECIPIENT`）。

---

## 构建与开发

```bash
mvn -pl proto,lobby,gate,game,web -am compile -DskipTests
./scripts/ops.sh build
./scripts/ops.sh start web
# 本机：http://127.0.0.1:8081/<访问唯一码>/
```

---

## 注意

- 不提交 Cursor / Codex / `AGENTS.md` 等 AI 工具文件。  
- 修改静态资源或路径后检查 Nginx、HTTPS 与浏览器缓存。  
- 变更前保留用户修改；操作后验证登录与关键接口。

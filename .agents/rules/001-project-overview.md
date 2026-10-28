---
name: 001-project-overview
description: game-server 项目概览、架构划分与工程规范
alwaysApply: true
---

# 项目概览与架构规范

## 1. 项目简介
`game-server` 是一个基于 Java 8 的轻量级休闲棋牌与家庭学习系统服务端，采用 Maven 多模块工程管理。
系统支持两种部署与运行形态：
- **一体化 Hub 模式（主线推荐）**：包含网关长连接、大厅服务、游戏对局引擎、Web 管理后台与家庭学习接口，统一在 `hub` 单一 Spring Boot 进程中运行（默认端口 8081），内存状态高效同步，运维部署最简。
- **分布式集群模式**：由 `center`、`gate`、`lobby`、`game`、`web` 五个独立子模块通过 Netty RPC 协同。

## 2. 模块结构与职责

```text
game-server/
├── hub/              一体化核心服务 (Spring Boot 2.5.1 + WebSocket + Netty + SQLite)
├── proto/            Protobuf 消息定义与注解式路由映射中心
├── utils/            网络层底层封装 (Netty/KCP)、通用工具类、序列化支持
├── tool/             数据库连接池与基础持久化依赖 (Druid, Jedis, MySQL)
├── servertool/       统一本地工具与统一 MCP 服务 (端口 18765)
├── center/           分布式中心服务
├── gate/             分布式网关服务
├── lobby/            分布式大厅服务
├── game/             分布式游戏对局服务
├── web/              分布式后台 Web 服务
├── scripts/          运维启停与部署脚本 (hub.sh, mcp.bat, ops.sh, deploy.bat)
└── data/             运行时本地数据目录 (lobby.db, learning/)
```

## 3. 统一 MCP 服务 (18765)
为了让 AI 与开发人员拥有统一的本地安全工具箱，`servertool` 模块作为常驻服务运行：
- 端口：`http://127.0.0.1:18765/mcp`
- 探活：`http://127.0.0.1:18765/health`
- 职责：只读或安全受限的数据查询、Excel 读取生成、日志安全切片。禁止通过外部 Stdio 子进程直接挂载，统一走该端口连接。

## 4. 关键研发命令约定
- **协议生成**：进入 `proto/src/main/java/` 执行 `genProto.bat` (Win) 或 `./genProto.sh` (Linux)。
- **Hub 运维**：根目录下执行 `deploy.bat` 或 `./scripts/hub.sh deploy/start/stop/status`。
- **MCP 运维**：根目录下执行 `scripts/mcp.bat start/stop/status`。
- **Maven 构建**：使用 `mvn clean package -DskipTests` 进行全工程打包。

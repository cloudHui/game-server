# game-server 项目 AI 助手指南

休闲小游戏 + 家庭学习综合服务端。基于 Java 8 与 Maven 多模块架构，集成了基于 Netty / WebSocket 的长连接网关与棋牌对局服务、Spring Boot 2.5.1 一体化 Hub 服务、SQLite 独立文件存储，以及统一的本地 MCP 开发运维工具链。

## 模块架构

| 模块 | 说明 | 核心技术栈 |
|------|------|------------|
| **hub** | **核心推荐服务**：一体化服务，整合账号、大厅、游戏、Web 管理与学习模块 | Spring Boot 2.5.1, WebSocket, HikariCP, SQLite |
| **proto** | 消息协议定义与路由注册中心 | Protobuf 3.20.3, 注解式消息映射 (`@ClassField`) |
| **utils** | 底层通信与通用基础工具库 | Netty 4.1.68, KCP, FastJson, Jackson, SLF4J |
| **tool** | 数据库连接与底层存储支持 | Druid, Jedis, MySQL Connector |
| **servertool**| **统一本地工具与 MCP 服务** | 轻量 HTTP 服务 (端口 18765), POI, 数据库安全查询, 日志切片 |
| **game / lobby / gate / center / web** | 历史分布式微服务形态（与 hub 互斥） | Netty, 自研 RPC |

## 规则文档 (`.agents/rules/`)

| 规则文档 | 内容说明 | 触发模式 |
|----------|----------|----------|
| [001-project-overview.md](.agents/rules/001-project-overview.md) | 工程模块划分、Hub 一体化架构、统一 MCP 服务、关键路径 | Always |
| [100-ai-interaction.md](.agents/rules/100-ai-interaction.md) | 代码优先风格、**提出风险必须带解决方案与推荐方案**的硬性约束、审查要点 | Always |
| [200-coding-style.md](.agents/rules/200-coding-style.md) | 4 空格缩进、UTF-8、import 排序、**字段/Javadoc/逻辑块“为什么”三层注释规范** | `**/*.java,**/*.proto` |
| [300-message-guidelines.md](.agents/rules/300-message-guidelines.md) | 消息协议链路：Proto 编写 → GMsg/LMsg 注解常量 → `@ProcessClass` 实现 `ConnectHandle` | `**/*.proto,**/proto/**,**/msg/**` |
| [301-storage-guidelines.md](.agents/rules/301-storage-guidelines.md) | 数据持久化规范：`data/` 目录隔离 (`lobby.db`, `data/learning/`)、SQLite 并发锁与安全 | `**/*db*/**,**/*dao*/**` |

## 技能体系 (`.agents/skills/`)

### 1. 通用思考、审查与可视化技能 (遵循 skills.sh 规范)
- **`canvas`**：生成独立的交互式 HTML/Tailwind/ECharts 分析看板，专门呈现复杂数据与 MCP 查询结果。
- **`grill-me`**：重大方案动手前，沿着决策树向用户进行单步深入盘问并附带推荐答案。
- **`grill-with-docs`**：结合项目文档（AGENTS.md、vue3-migration-plan.md）进行术语与架构对齐盘问。
- **`improve-codebase-architecture`**：运用删除测试法扫描浅薄模块，提出高内聚重构方案。
- **`zoom-out`**：全局宏观复盘，避免深陷局部细节偏离系统主线。
- **`code-review`**：提交前自动审查：三层注释完整度、SQLite 锁竞争、未关闭流与资源泄漏。

### 2. 业务开发专有技能
- **`proto-gen`**：规范编写 `.proto` 并调用 `genProto.bat`/`genProto.sh` 一键编译。
- **`msg-handler`**：新增网络接口全链路代码脚手架（Proto → GMsg → Handler）。
- **`hub-ops`**：Maven 多模块编译与 `scripts/hub.sh deploy/start/status` 运维管理。
- **`tool-utils`**：优先检索复用 `utils/` 与 `hub/common/` 内已有工具类，禁止重复造轮子。

### 3. 统一 MCP 工具技能 (端口 18765)
- **`mcp-service-ops`**：统一 MCP 服务的探活（`/health`）、启停与日志排查。
- **`mcp-db-query`**：通过统一 MCP 进行受控索引查库，联动 `canvas` 生成图表。
- **`mcp-excel-manage`**：通过统一 MCP 读取/写入 Excel 配置数据。
- **`mcp-log-copylog`**：基于 copylog 机制进行安全的时间/关键词日志切片。

## 统一 MCP 服务接入

统一 MCP 服务运行在本地端口 `18765`：
- **MCP 入口**：`http://127.0.0.1:18765/mcp`
- **探活入口**：`http://127.0.0.1:18765/health`
- **启停脚本**：`scripts/mcp.bat start/stop/status` (Windows) 或 `scripts/mcp.sh` (Linux)

在 Antigravity 全局配置（`~/.gemini/config/mcp_config.json`）中添加：
```json
{
  "mcpServers": {
    "game-server-tool": {
      "serverUrl": "http://127.0.0.1:18765/mcp"
    }
  }
}
```

## MCP 工具优先调用准则（核心约束）
> **在后续所有研发、排错、数据核对与代码审查中，AI 必须严格优先使用统一 MCP 工具（`game-server-tool`），严禁舍近求远：**

- **查库与数据诊断**：一律优先调用 `player_db_query`（查玩家）或 `local_sql_query` / `local_get_database_info`（查库表结构），配合 `canvas` 输出可视化报表；
- **配置与数值分析**：一律优先调用 `excel_read_sheet`、`excel_describe_sheets` 读取 Excel，禁止盲猜配表；
- **历史变更追溯**：一律优先调用 `git_log`、`git_show` 查看提交历史与文件变更；
- **日志切片取证**：优先使用统一 MCP 与 copylog 机制进行日志片段的安全检索。

## 常用命令速查

| 操作 | 命令 | 说明 |
|------|------|------|
| **编译整机** | `mvn clean package -DskipTests` | 根目录下执行 Maven 打包 |
| **一键部署启动 Hub** | `./scripts/hub.sh deploy` (Linux) 或 `deploy.bat` (Win) | 打包并启动一体化 Hub 进程 |
| **Hub 状态检查** | `./scripts/hub.sh status` | 默认监听 8081 端口 |
| **启动统一 MCP 服务** | `scripts/mcp.bat start` (Win) 或 `./scripts/mcp.sh start` | 启动本地常驻工具服务 (端口 18765) |
| **MCP 探活** | `curl http://127.0.0.1:18765/health` | 验证返回 OK |
| **编译 Proto 协议** | `cd proto/src/main/java && genProto.bat` | 重新生成 Java Proto 文件 |

## Git 审查约定
审查未提交改动时，项目配置、脚本、MCP 配置、IDE 工程文件以及生成产物均属于本次项目改动范围。不得仅因其不是业务 Java 源码就归类为“噪音”并忽略；应结合项目用途分析其正确性、兼容性和提交必要性。

---
name: mcp-db-query
description: >-
  通过统一 MCP 服务执行受控安全的玩家数据、业务库表与 Redis 缓存查询。
  当需要排查特定玩家数据、查看表结构/索引、核验 Redis 缓存，并联动 canvas 呈现可视化分析看板时调用。
---

# MCP-DB-Query 数据与缓存安全检索技能

## 1. 何时使用
- **玩家数据排障**：玩家金币、战绩、学习记录异常，需按 `roleId`/`userId` 定向查库；
- **库表结构核查**：查看 SQLite (`data/lobby.db`) 或 MySQL 业务表字段、主键及索引；
- **Redis 缓存诊断**：排查用户 Session、临时房间、排行榜或分布式锁等 Key-Value；
- **复杂数据可视化**：获取结构化流水或指标，交由 `canvas` 渲染图表看板。

## 2. 常用工具清单

| 分类 | MCP 工具 | 核心参数 | 用途说明 |
|------|----------|----------|----------|
| **玩家精准查询** | `player_db_query` | `action`: connections/schema/query<br>`roleId`: 完整正整数<br>`table`, `idColumn`, `maxRows` | 走有界索引的安全单表玩家数据检索（强门禁） |
| **库表与只读 SQL** | `local_get_database_info`<br>`local_sql_query` | `target`: local/test<br>`sql`: SELECT 语句 | 库表清单查看与安全只读 SQL 查询（严禁无条件全扫） |
| **权限与审计日志** | `local_check_permissions`<br>`local_get_ddl_sql_logs` | `target`: local/test | 账号权限检查与 DDL 变更记录追溯 |
| **Redis 缓存操作** | `get` / `list`<br>`set` / `delete` | `key`: string<br>`pattern`: 模式串（默认 *） | 键值读取与列举；生产环境慎用写/删，先 list 确认 |

## 3. 安全门禁与硬性红线（Guardrails）
1. **完整 roleId 与索引门禁**：
   - `player_db_query` 必须传入完整正整数 `roleId`（禁止模糊查询或扩散）；
   - EXPLAIN 必须验证命中有效索引（主键或唯一索引），拒绝 `ALL`（全表扫描）；
   - 工具全局 5 秒硬超时截断，单次返回默认最多 200 行，上限 1000 行。
2. **只读安全原则**：排障优先纯只读（SELECT / get / list），禁止擅自执行 DROP/DELETE/UPDATE。
3. **分步调用范式**：
   - 步骤一：先查元数据 `action=schema` 确认表示 `roleId` 的物理列名与索引；
   - 步骤二：发起定向查询 `action=query, table=..., idColumn=..., roleId=...`。

## 4. 与 Canvas 联动工作流
1. **调用 MCP 获取数据**：获取结构化 JSON 结果；
2. **单条直述，多维成图**：
   - 简单的单字段/单条记录：在对话中直接给出结论与证据；
   - 包含多条流水、战绩对比、阶段走势或复杂资产分布：**立即激活 `canvas` 技能**，渲染独立的交互式 HTML/ECharts 看板。

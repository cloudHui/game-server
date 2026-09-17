---
name: mcp-db-query
description: >-
  通过统一 MCP 服务执行受控安全的玩家数据与业务库表查询。
  当需要排查特定玩家数据、查看 SQLite/MySQL 表结构，并联动 canvas 呈现可视化分析看板时调用。
---

# MCP-DB-Query 统一数据库检索技能

## 何时使用
- 玩家反馈账号异常、金币不对、错题记录缺失，需要定向查库；
- 查看 `data/lobby.db` 中的表结构和索引配置；
- 获取结构化统计数据，并交由 `canvas` 渲染图表看板。

## 门禁与安全约束
1. **统一接口**：通过 MCP 暴露的 `db` 工具族操作，严禁手写拼接 SQL 脚本；
2. **严禁全表扫描**：查询必须命中有效索引（主键或唯一索引），EXPLAIN 检查必须通过；
3. **只读安全**：严禁在生产或未知环境直接执行 DDL/DML 写操作；
4. **单次返回上限**：默认最大 200 行，最多 1000 行。

## 常用工具清单
- `local_get_database_info`: 查看当前 MySQL/SQLite 数据库及所有库表清单；
- `local_sql_query`: 执行受控的安全只读 SQL 查询（支持表连接与聚集统计）；
- `player_db_query`: 针对特定 `roleId` 的全量/分表业务数据检索；
- `local_check_permissions`: 检查执行账号的权限范围；
- `local_get_ddl_sql_logs`: 查看 DDL 变更记录；
- `local_get_operation_logs`: 查看数据库操作日志审计。

## 与 Canvas 联动工作流
1. **调用 MCP 查询数据**：
   - 确认目标 `userId` 或表名；
   - 调用统一 MCP 工具获取结构化 JSON 数据；
2. **激活 Canvas 看板**：
   - 如果数据为简单的单条记录，可在对话中直述；
   - 如果数据为序列、多项对比、多维战绩流水或统计指标，**立即激活 `canvas` 技能**，生成带图表与统计卡片的 HTML 看板工件呈现给用户。

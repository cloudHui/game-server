---
name: code-review
description: >-
  严苛代码质量与安全审查技能。在代码编写完成、准备提交 Git 前，或用户明确要求 review 时调用。
  重点审查注释完整度、SQLite 锁隐患、资源连接释放、并发线程安全以及异常处理规范。
---

# Code-Review 严苛代码审查技能

## 何时使用
- 功能开发或 Bug 修复完成后，提交 Git 之前；
- 用户输入 `/review` 或 “帮我审查一下刚才改的代码”；
- 涉及底层网络长连接、数据库操作等高敏感模块变动。

## 审查清单（Checklist）

### 1. 注释三要素（硬性门禁）
- [ ] **字段注释**：新增/修改的实体类字段是否都有明确说明（含义、单位、范围）？Map 字段是否标清 key/value 含义？
- [ ] **方法 Javadoc**：公共方法是否有完整的方法描述、`@param` 和 `@return` 说明？
- [ ] **逻辑意图注释**：复杂算法、状态机切换、分支判断前，是否有解释“为什么这么写”的意图注释？

### 2. 存储与 SQLite 并发安全
- [ ] 事务块内是否杜绝了网络 I/O、HTTP 调用及大计算量耗时操作？
- [ ] 批量写入是否已用事务包裹，杜绝了循环无事务单条提交？
- [ ] 是否存在字符串拼接 SQL（强制 PreparedStatement 参数绑定）？

### 3. 连接与资源释放
- [ ] `ResultSet`、`PreparedStatement`、`Connection` 是否全部置于 `try-with-resources` 或严格的 `finally` 中？
- [ ] 字节流、文件流是否显式 `close()`？

### 4. 长连接与并发安全
- [ ] 全局 Handler 是否保持严格无状态（不包含用户级可变成员变量）？
- [ ] 多线程并发共享集合是否使用了线程安全容器（如 `ConcurrentHashMap`）或原子操作类？
- [ ] 是否捕获了 `Throwable` 或 `Exception` 却静默吞没（必须有完整的 `logger.error` 记录堆栈）？

### 5. MCP 辅助审查与提交
- [ ] 优先使用 MCP `git_log`（带 `since` 参数）拉取待审查 commit 列表与变更日志；
- [ ] 优先使用 MCP `git_show` 针对特定 commit 或文件比对变更细节；
- [ ] 审查无误后，优先通过 MCP `git_commit_files` 或 Git 工具完成标准原子提交。

## 输出格式
审查结论按严重等级统一以表格形式输出：

| 级别 | 文件与行号 | 缺陷描述 | 修复建议 |
|------|------------|----------|----------|
| Block | `ExampleHandler.java:42` | 缺少 PreparedStatement 占位符导致 SQL 注入 | 改用 `?` 参数占位符 |
| Warn  | `UserManager.java:108`   | HashMap 在多线程网络下无锁写入 | 改用 ConcurrentHashMap |
| Info  | `TableInfo.java:15`      | 缺少三层注释中的字段业务说明 | 补齐 Javadoc 注释 |

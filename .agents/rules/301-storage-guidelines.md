---
name: 301-storage-guidelines
description: 存储架构、SQLite 数据库安全、HikariCP 连接池与 data 目录规范
alwaysApply: false
globs: "**/*db*/**,**/*dao*/**,**/storage/**,**/sqlite/**"
---

# 存储与数据规范

## 1. 数据目录隔离原则
- **集中存储**：所有运行时动态生成的数据文件、数据库文件、本地缓存文件必须存放在工程根目录下的 `data/` 目录：
  - `data/lobby.db`：账号、网关会话与大厅基础数据库；
  - `data/learning/`：家庭学习模块相关错题、识字、统计数据；
  - `data/record/`：战绩与对局回放数据文件。
- **严禁乱放**：严禁将业务持久化数据写入 `src/`、`target/`、系统临时目录 `tmp/` 或用户桌面。
- **Git 忽略**：`data/` 目录下的实时数据库文件与运行时数据已由 `.gitignore` 排除，不得误提交测试脏数据。

## 2. SQLite 并发与事务准则
`hub` 采用 SQLite 作为轻量内嵌数据存储，由于 SQLite 拥有文件级锁机制（支持多读单写，写操作会独占排他锁），必须严格遵守以下准则：

1. **写事务严禁长耗时**：
   - 严禁在数据库写事务内部执行网络 I/O、HTTP 调用或耗时的复杂计算；
   - 必须先在内存完成数据校验、加密和组装，最后开启事务一次性写入并迅速提交释放写锁。
2. **批量写入使用事务包裹**：
   - 连续插入或更新多条记录时，严禁在无事务状态下循环执行单条 SQL（会导致频繁刷盘与锁竞争）；必须使用显式事务或 Batch 操作一次性提交。
3. **忙等待与锁超时防范**：
   - JDBC URL 中必须配置合适的超时等待时间（如 `busy_timeout=5000`）；
   - HikariCP 对 SQLite 的连接池大小需合理配置（写连接保持单线程化或低并发池），防止因争抢文件排他锁抛出 `database is locked`。

## 3. SQL 书写规范
- **禁止字符串拼接 SQL**：无论 SQLite 还是 MySQL，所有参数必须使用 `?` 占位符（PreparedStatement）传参，坚决杜绝 SQL 注入漏洞。
- **SQL 关键字大写**：`SELECT`, `INSERT INTO`, `UPDATE`, `DELETE`, `WHERE`, `ORDER BY`, `LIMIT` 等关键字一律使用大写。
- **敏感数据存储**：用户密码必须通过 `spring-security-crypto`（BCrypt）进行不可逆哈希存储，禁止明文或简单 MD5 存储。

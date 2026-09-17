# MCP 与日志工具

## 启动

- 仍只使用原来的 `McpServer.bat`，它启动同目录的 `McpServer.jar`，不需要 `-Dmcp.portable=true`。
- MCP 地址仍是 `http://127.0.0.1:18765/mcp`，无需修改项目 `.cursor/mcp.json`；旧 `DbConfig`、`local_sql_query`、Redis、Git 和文件工具不变。
- `McpServer.jar`、启动脚本、`McpDatabaseConfig.json` 放在同一目录。配置启动时从 JAR 同目录读取；修改后重启。可用 `-Dmcp.db.config=绝对路径` 覆盖默认位置。
- 文件根默认包含 WorkSpace、当前用户 `Desktop/log` 及存在的同级 DataLog。

## 数据库配置

源配置默认空数组；没有配置或配置无效不影响 MCP 启动，只跳过新工具的数据库能力，绝不回退到旧账号。配置文件无注释，每条恰好六项，名称唯一：

```json
{
  "connections": [
    {
      "name": "test-game",
      "host": "mysql-test.example.internal",
      "port": 3306,
      "database": "your_game_database",
      "user": "your_readonly_account",
      "password": "replace-with-password"
    }
  ]
}
```

按相同结构添加任意多个库/地址/账号；不固定用户名或库名。请配置仅有 SELECT 权限的测试环境账号，工具不创建账号、不改权限、不接生产库。文件含密码，只在允许共享这些凭据的组内 SVN 维护，不能发到公开仓库或日志。打包仅在目标配置不存在时复制空模板，不覆盖已有配置。

新连接强制 MySQL TLS 并验证证书及主机名：配置 host 要匹配服务端证书，签发 CA 须在运行 Java 的默认信任库中。不可信证书或不支持 TLS 时失败，不静默降级。不会使用本地旧配置兜底。

## 玩家查询

仅新技能使用 `player_db_query`，每次都必须传完整正整数 `roleId`：

```json
{"action":"connections","roleId":"91002000000411"}
{"action":"schema","connection":"test-game","roleId":"91002000000411","table":"role"}
{"action":"query","connection":"test-game","roleId":"91002000000411","table":"role","idColumn":"roleId","maxRows":200}
```

`schema` 不传 table 列出基础表，传 table 查看字段/索引。数据库元数据不返回配置密码。查询只生成单表 `SELECT ... WHERE idColumn = ?`，值参数绑定；列必须确实表示 roleId，不能拿 roleId 代替 uid/deviceCode。只查数据，不做关联 ID 扩散。

无表大小限制。EXPLAIN 必须有索引且访问方式为 const/eq_ref/ref/range/index_merge，拒绝 ALL/index、视图、跨库、任意 SQL、DDL/DML、模糊/范围条件。LIMIT 只限制返回量，不能绕过检查。默认 200 行，最多 1000 行；截断有标记，单字段超过 4000 字符也有标记。

查询在 MCP 进程内执行。连接、元数据、EXPLAIN、SELECT、读取结果合计 5 秒。只在 SELECT 前 `SET SESSION max_execution_time` 掐库上的语句；设失败不挡查询，JDBC 超时跟剩余时限对齐。不会结束其他查询或整个 MCP；旧数据库工具保持原行为。

## copylog / WindowsTools

源文件是 `servertool/src/com/gamer/data/file/copylog.sh`，`ant p-WindowsTools` 随 classes 打进 WindowsTools.jar 同一路径。使用 `sh` 和常规 Linux 命令（awk、find、GNU date/coreutils、tar、gzip），不依赖 Python 或 Bash 扩展。检索优先 `rg`，未安装才用 `grep`；两者结果一致。Windows 本地验证可使用 Git 自带的 `sh`。

```sh
sh src/com/gamer/data/file/copylog.sh
sh src/com/gamer/data/file/copylog.sh --condition 91002000000411 --all --compress
sh src/com/gamer/data/file/copylog.sh --from 2026-08-27 --to 2026-08-28 --condition "错误请求" --compress
```

- 默认只 game，选择昨天和今天的日志文件；`--all` 扩展到存在的 game/world/common。
- 按文件名中的 `yyyy-MM-dd` 选日期，起止日期都包含；只填 to 时从它的前一天开始。不按修改时间或日志行时间筛选。`net.log`、`sys.log`、`nohup.out` 等无日期的当前文件，仅在范围包含今天时带上。
- 空条件直接复制选中的整个文件，`.gz` 也原样复制，不解压或扫描内容。有条件才用 rg/grep，只保留命中行；条件为固定文本，纯数字按完整数字匹配。
- 保留原字节/换行、相对目录、文件名、扩展名；条件无命中则不生成该文件/目录。选中的文件不再逐行切时间，当前文件里即使积累了旧内容也按整个文件处理。
- 遍历三服全部子目录的**文件名**，范围外文件不读取、不解压；不搜索 ZIP/7z/tar 历史导出包。控制台显示选中文件数、正在处理的文件和压缩阶段，不生成 manifest.txt/hits.txt。
- 压缩输出 `log/logs_起日_止日.tar.gz`，同名只覆盖这个文件。目录输出仍为 `log/logs_起日_止日_唯一号/`，不覆盖、不删除已有下载目录。内部为 `gameserver/原相对目录/原文件名` 等。源日志不修改。
- 环境变量只用于部署路径：`COPYLOG_SOURCE` 默认 `/data/log/90001`，`COPYLOG_SERVER_ROOT` 默认 `/data/server/90001`，`COPYLOG_OUTPUT` 默认脚本旁 log。本地直跑可设为本地源目录及 `{用户主目录}/Desktop/log`。

WindowsTools 任务页填写日期、条件、game/all、压缩；空日期和条件合法，不要求玩家 ID。原 SSH/token/设备查询照旧。下载按钮用本机 OpenSSH 连接测服，把 jar 内 `com/gamer/data/file/copylog.sh` 经 stdin 交给远端 `sh -s`，**不必再把脚本上传到测服，也不必放在 jar 同目录**。未打包时才退回 src 同路径。结果仍落在远端 `gameserver/log/`，再 scp 回当前用户 `Desktop/log`。压缩包解到 `Desktop/log/gameserver`（及 world/common，若选了 all），不套日期目录；成功后删除压缩包。可用暂停/结束；结束保留压缩包和旧目录。目录结果不覆盖。下载失败的 `.download-*` 暂存供人工检查，不混入正式结果。

这里不自动 SSH 部署测服脚本。

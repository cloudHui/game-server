# servertool

KingdomWarships 本地工具工程：桌面 GUI、Excel→GD、opcode 生成、MCP 服务。

- 工程：[servertool/](.)
- 源码：[src/com/gamer/data/](src/com/gamer/data/)
- 打包：[build.xml](build.xml)
- 路径 / CMD：[PathConfig.java](src/com/gamer/data/file/config/PathConfig.java)
- 库连接 / BLOB：[DbConfig.java](src/com/gamer/data/file/config/DbConfig.java)（界面只读）
- 测服 SSH：[SshConfig.java](src/com/gamer/data/file/config/SshConfig.java)
- MCP 配置：[.cursor/mcp.json](../.cursor/mcp.json)
- 项目总览：[AGENTS.md](../AGENTS.md)

本工程是独立工具，**不是 gameserver**，不走热更新，可用 Java 8 lambda / Stream。

路径和账号写死本机（`D:\code\WorkSpace\...`）。换机器改对应 Config 类后重新编译。目录树 / CMD 下拉运行时追加只进内存，重启回常量；数据库连接和 BLOB 规则界面不能改。

---

## 目录

- [产物与输出目录](#产物与输出目录)
- [源码目录](#源码目录)
- [配置入口](#配置入口)
- [WindowsTools](#windowstools)
  - [根目录](#根目录)
  - [CMD](#cmd)
  - [任务](#任务)
  - [GD文件查看](#gd文件查看)
  - [客户端使用GD](#客户端使用gd)
  - [数据库](#数据库)
  - [analysis](#analysis)
- [客户端 GD 路径](#客户端-gd-路径)
- [StrategyTool / CodeTool](#strategytool--codetool)
- [NetOpcode](#netopcode)
- [MCP](#mcp)
- [编译与打包](#编译与打包)
- [jar 占用](#jar-占用)

---

## 产物与输出目录

`basedir` = [servertool/](.)。相对路径相对 **WorkSpace**（`Server` 的上一级）。

```
WorkSpace/
├── Server/servertool/          ← 本工程源码 + build.xml
├── Common/Tools/Bin/
│   ├── server/                 ← CodeTool.jar、McpServer.jar、管理脚本、limit/
│   └── opcode/                 ← NetOpcode.jar、ActionEnum_*.xml
└── Document/DevelopmentGD&ConfigurationExcel/
    ├── ConfigurationExcel/     ← Excel 配置表
    │   └── xls2gd_zh/          ← StrategyTool.jar（及可选 exe）
    └── GD/data/                ← 服务器 GD（gameserver data.path）

%USERPROFILE%\Desktop\work\tool\
└── WindowsTools.jar               ← 桌面 GUI
```

| ant 目标 | 产物 | Main-Class | 输出目录 |
|---|---|---|---|
| [p-WindowsTools](build.xml) | `WindowsTools.jar` | [WindowsTools](src/com/gamer/data/file/WindowsTools.java) | `%USERPROFILE%\Desktop\work\tool\` |
| [p-StrategyTool](build.xml) | `StrategyTool.jar` | [StrategyTool](src/com/gamer/data/StrategyTool.java) | `Document/.../ConfigurationExcel/xls2gd_zh/` |
| [p-StrategyTool-exe](build.xml) | `StrategyTool/` 目录 | 同上（jpackage，需本机 JDK17） | 同上 |
| [p-CodeTool](build.xml) | `CodeTool.jar` | [CodeTool](src/com/gamer/data/CodeTool.java) | `Common/Tools/Bin/server/` |
| [p-NetOpcode](build.xml) | `NetOpcode.jar` | [NetOpcodeMaker](src/com/gamer/data/message/NetOpcodeMaker.java) | `Common/Tools/Bin/opcode/` |
| [p-McpServer](build.xml) | `McpServer.jar` | [McpServerMain](src/com/gamer/data/mpcserver/McpServerMain.java) | `Common/Tools/Bin/server/` |

没有 `p-all`。MCP 改完只执行一次 `p-McpServer`。

Fat jar：把 [servertool/lib](lib/) 里需要的依赖打进包。切片白名单在 [build.xml](build.xml) 顶部（`WindowsTools.includes`、`excel.file`、`Mcp.base` 等），WindowsTools **不**打整包 excel。

---

## 源码目录

```
servertool/src/com/gamer/data/
├── ui/            三工具共用外观 ViewUi（不依赖 file/excel）
├── file/          WindowsTools GUI（Main-Class）
├── excel/         Excel → GD、Sheet 差异
├── gdg/           GD 文本解析 / 预览
├── map/           地图、坐标、Excel 读写
├── read/          读表、SVN 差异
├── message/       opcode XML → Java
├── mpcserver/     Cursor/Codex 共用 Streamable HTTP MCP 服务
├── StrategyTool.java  StrategyTool 入口（Excel/GD 对比）
└── CodeTool.java      CodeTool 入口（生成模型 / 看表）
```

### WindowsTools 子包

| 包 | 作用 |
|---|---|
| [file/WindowsTools.java](src/com/gamer/data/file/WindowsTools.java) | 主窗、页签、目录树、拖放 |
| [file/config](src/com/gamer/data/file/config/PathConfig.java) | PathConfig / DbConfig / SshConfig / HolidayConfig |
| [file/cmd](src/com/gamer/data/file/cmd/CmdPanel.java) | CMD 页、任务日志；共用外观 [ViewUi](src/com/gamer/data/ui/ViewUi.java)（StrategyTool / CodeTool 一并使用） |
| [file/task](src/com/gamer/data/file/task/TaskPanel.java) | 「任务」页：测服、客户端、端口、定时、轮询 |
| [file/tree](src/com/gamer/data/file/tree/Reveal.java) | 目录树展开 / 定位 |
| [file/search](src/com/gamer/data/file/search/SearchPanel.java) | 文件名检索（整行点开） |
| [file/poll](src/com/gamer/data/file/poll/PollCmdPanel.java) | 轮询命令（挂在任务页） |
| [file/port](src/com/gamer/data/file/port/Port.java) | 本机端口占用查询 / 结束 |
| [file/report](src/com/gamer/data/file/report/Pull.java) | 测服拉日志 |
| [file/ssh](src/com/gamer/data/file/ssh/Ssh.java) | SSH 执行 |
| [file/module](src/com/gamer/data/file/module/GdPreviewModule.java) | GD / 文本预览、异步任务 |
| [file/client/gd](src/com/gamer/data/file/client/gd/ClientGd.java) | 客户端使用 GD 三步 |
| [file/client/download](src/com/gamer/data/file/client/download/ClientDownloader.java) | 客户端包下载 |
| [file/client/deploy](src/com/gamer/data/file/client/deploy/DeployModule.java) | 解压并部署 |
| [file/db](src/com/gamer/data/file/db/BrowseModule.java) | 数据库浏览（懒加载、只读） |
| [file/analysis](src/com/gamer/data/file/analysis/AuthTokenPanel.java) | Token / 查设备 |
| [file/mcp](src/com/gamer/data/file/mcp/LogCleaner.java) | 清理 MCP / 工具日志 |
| [file/zip](src/com/gamer/data/file/zip/ZipArchiveModule.java) | zip 读写（覆盖客户端 GD） |

---

## 配置入口

永久改下面常量，再 `ant p-WindowsTools`。数据库页不能改配置；树 / CMD / 任务页的临时输入不写回代码。

| 类 | 管什么 | 运行时 |
|---|---|---|
| [PathConfig](src/com/gamer/data/file/config/PathConfig.java) | 目录树默认路径、CMD 工作目录/命令、Excel / GD / zip / limit | 树和 CMD 下拉可内存追加，重启丢失 |
| [DbConfig](src/com/gamer/data/file/config/DbConfig.java) | MySQL / proto jar、连接串、BLOB 表.列→PBxxx、每页行数 | 界面只读展示 |
| [SshConfig](src/com/gamer/data/file/config/SshConfig.java) | 测服 SSH、拉日志目录 | 任务页可改输入框，不写回本类 |
| [HolidayConfig](src/com/gamer/data/file/config/HolidayConfig.java) | 工作日 10:00 调度的节假日备用 | API 缓存只进进程 |

---

## WindowsTools

启动：

```bat
java -Dfile.encoding=UTF-8 -jar WindowsTools.jar
```

工作目录任意。生成 GD 用的 limit 目录走 [PathConfig.limitDir()](src/com/gamer/data/file/config/PathConfig.java)，不依赖 `user.dir`。

外观由 [ViewUi](src/com/gamer/data/ui/ViewUi.java) 统一（WindowsTools / StrategyTool / CodeTool）：页底、卡片、表格、树、左右/上下分栏。数据库结果表按格 `Ctrl+C` / 右键复制；文件检索表整行选中，方便点开。

页签在 [WindowsTools.createRightPanel()](src/com/gamer/data/file/WindowsTools.java)：

| Tab | 类 | 做什么 |
|---|---|---|
| 根目录 | [WindowsTools](src/com/gamer/data/file/WindowsTools.java) + [tree](src/com/gamer/data/file/tree/) + [SearchPanel](src/com/gamer/data/file/search/SearchPanel.java) | 多根目录树、文件检索、打开/拖放 GD |
| CMD | [CmdPanel](src/com/gamer/data/file/cmd/CmdPanel.java) | 常用 / 协议快捷命令、自定义 CMD、任务日志 |
| 任务 | [TaskPanel](src/com/gamer/data/file/task/TaskPanel.java) | 测服拉日志、客户端下载/部署、端口、轮询 |
| GD文件查看 | [GdPreviewModule](src/com/gamer/data/file/module/GdPreviewModule.java) | 打开或拖 `.gd` 看表格 |
| 客户端使用GD | [ClientGdPanel](src/com/gamer/data/file/client/gd/ClientGdPanel.java) / [ClientGd](src/com/gamer/data/file/client/gd/ClientGd.java) | Excel 目录 → 生成 GD → 覆盖 zip |
| 数据库 | [BrowseModule](src/com/gamer/data/file/db/BrowseModule.java) | 查库、筛选分页、BLOB/PB（首次打开才加载） |
| analysis | [AuthTokenPanel](src/com/gamer/data/file/analysis/AuthTokenPanel.java) | Token 分析、查设备 |

### 根目录

左右卡片：「目录树」|「文件检索」。

- 启动挂 [PathConfig.INITIAL_PATHS](src/com/gamer/data/file/config/PathConfig.java)（来自 `DEFAULT_INITIAL_PATHS` 里**已存在**的目录）
- 树卡片内「打开 GD」：文件选择框；也可把 `.gd` 拖到窗口（与选择框同一套打开逻辑）
- 单击文件：按扩展名打开（GD → GD 页，文本 → 内置预览，其余系统打开）；双击目录：资源管理器
- 选中目录后，在本页任意位置输入即可检索名称（[SearchKeyDispatcher](src/com/gamer/data/file/search/SearchKeyDispatcher.java)）；结果整行点开，不高亮格复制
- 树定位：[Reveal](src/com/gamer/data/file/tree/Reveal.java) → [WindowsTools.revealInDirectoryTree](src/com/gamer/data/file/WindowsTools.java)（「打开 Excel」走这条，不弹系统窗口）

### CMD

[CmdPanel](src/com/gamer/data/file/cmd/CmdPanel.java) 快捷命令（测服 / 下载 / 端口在「任务」页）：

| 区 | 按钮 | 实际命令 |
|---|---|---|
| 常用 | 执行 CodeTool | `java -jar CodeTool.jar`（工作目录 `Common/Tools/Bin/server`） |
| 常用 | 启动 MCP | `call McpServer.bat start`（同目录，已运行则直接返回） |
| 常用 | 更新 bin | `svn update` @ `Common/Tools/Bin` |
| 常用 | 更新 GD/文档 | `svn update` @ `Document` |
| 协议 | 生成 opcode | `call opcode_java.bat` |
| 协议 | 生成协议 | `call protoc_java.bat` |
| 协议 | 打包协议 / common | 对应工程下 `ant` |
| 协议 | 一键执行 | 上面协议相关串起来 |

底部可任选工作目录执行命令。下拉默认含 `ant p-WindowsTools`、`ant p-StrategyTool`、`ant p-CodeTool`、各 `p-Mcp*Server` 等，定义在 [PathConfig](src/com/gamer/data/file/config/PathConfig.java) `DEFAULT_CMD_COMMANDS`。工作目录候选见 `DEFAULT_CMD_PATHS`（含本工程，方便自己打自己）。

自定义命令有独立日志页。运行时新路径/新命令只进内存，重启回常量。

### 任务

[TaskPanel](src/com/gamer/data/file/task/TaskPanel.java)：须在 CMD 页创建之后装配（共用日志栏）。

| 区 | 做什么 | 入口 |
|---|---|---|
| 服务 | SSH 拉测服日志 | [Pull](src/com/gamer/data/file/report/Pull.java)，主机/密钥见 [SshConfig](src/com/gamer/data/file/config/SshConfig.java) |
| 客户端 | 下载、解压部署、清理旧日志 | [ClientDownloader](src/com/gamer/data/file/client/download/ClientDownloader.java)、[DeployModule](src/com/gamer/data/file/client/deploy/DeployModule.java)、[LogCleaner](src/com/gamer/data/file/mcp/LogCleaner.java) |
| 端口 | 查 LISTENING，确认后结束进程 | [Port](src/com/gamer/data/file/port/Port.java) |
| 定时 工作日 10:00 | 测服下载 / 客户端下载 / 清理日志，默认开，关程序后下次仍默认开 | [Scheduler](src/com/gamer/data/file/client/workday/Scheduler.java) |
| 轮询 | 填目录、命令、间隔、关键字；命中后通知并停 | [PollCmdPanel](src/com/gamer/data/file/poll/PollCmdPanel.java) |

部署 / 端口只手动，不跟工作日定时。

日志下载新增日期、固定文本条件、game/all、压缩；默认昨天至今，可不填玩家 ID。
只保留命中行并保持原路径/文件名，下载到当前账户 Desktop/log，不清空历史目录结果。
本机 SSH 执行打进 WindowsTools.jar 的 [copylog.sh](src/com/gamer/data/file/copylog.sh)（未打包时用 src 同路径），不必再上传到测服。压缩包解到 `Desktop/log` 下 gameserver/worldserver/commonserver，成功后删除压缩包。参数与依赖见 [McpServer.md](McpServer.md)。

### GD文件查看

本页「打开 GD」，或从根目录按钮 / 窗口拖放 `.gd`。表格分页展示。文本文件走 [FilePreviewModule](src/com/gamer/data/file/module/FilePreviewModule.java)。

### 客户端使用GD

同时只亮当前步；失败可重试；「重新开始」随时可点。逻辑在 [ClientGd](src/com/gamer/data/file/client/gd/ClientGd.java)。无 `gd-work` / `svn-base` / `gen`。

| 步 | 按钮 | 行为 |
|---|---|---|
| 1 | 打开 Excel | 在根目录树展开总 Excel 配置目录 |
| 2 | 生成 GD | `svn status` 收集变更 xlsx，写到 `GD/data`（无变更则跳过；svn 失败可重试；失败只丢 tmp，不删已有 gd） |
| 3 | 覆盖客户端 | `GD/data` 整包替换 `gddata.zip`，不改 LocalLow |

「查看」栏随时可点：打开 LocalLow、打开 zip 目录、整包覆盖 zip（同第 3 步）、删除 LocalLow GD（客户端缓存另清）。

### 数据库

首次点「数据库」才 [WindowsTools.ensureDbModule()](src/com/gamer/data/file/WindowsTools.java) 创建 [BrowseModule](src/com/gamer/data/file/db/BrowseModule.java)，避免启动就加载 JDBC / proto jar。

- 连接、BLOB 规则、jar 路径、每页行数只改 [DbConfig](src/com/gamer/data/file/config/DbConfig.java)，改完重新编译 WindowsTools；界面只展示，不能增删改
- 左：连接/表树 + BLOB 规则表；右：筛选、结果、分页、日志
- 按钮：测试连接、关闭连接、重载 proto jar
- 双击连接展开表，双击表分页查数据；BLOB 按 `BLOB_RULES` 解 PB
- 结果表只读，按格选中，`Ctrl+C` / 右键复制，不写库；双击 BLOB 格弹只读详情

### analysis

[AuthTokenPanel](src/com/gamer/data/file/analysis/AuthTokenPanel.java) 两个子页：Token 分析、查设备（[DeviceLookup](src/com/gamer/data/file/analysis/DeviceLookup.java)，连库读 [DbConfig](src/com/gamer/data/file/config/DbConfig.java)）。文本区右键可复制。

---

## 客户端 GD 路径

全部在 [PathConfig](src/com/gamer/data/file/config/PathConfig.java)。

| 用途 | 方法 / 常量 | 路径 |
|---|---|---|
| Excel 配置表 | `excelDir()` / `EXCEL_DIR` | `Document/DevelopmentGD&ConfigurationExcel/ConfigurationExcel` |
| 服务器 GD | `gdDataDir()` / `GD_DATA_DIR` | `Document/DevelopmentGD&ConfigurationExcel/GD/data` |
| 客户端 zip | `clientGdZip()` / `CLIENT_GD_ZIP` | `D:\download\fs\KingdomWarships\...\StreamingAssets\gddata.zip` |
| zip 所在目录 | `clientGdZipDir()` | 上面的 `StreamingAssets` |
| LocalLow | `clientLocalLowDataDir()` | `{user.home}\AppData\LocalLow\Fast\KingdomWarships\data` |
| limit | `limitDir()` / `LIMIT_DIR` | `Common/Tools/Bin/server/limit` |

```
Excel 配置表
    → Document/.../GD/data（服务器 GD）
        → gddata.zip（客户端；LocalLow 另清）
```

---

## StrategyTool / CodeTool

同一套转表核心：[XlsxGdParallelProcessor](src/com/gamer/data/gdg/generate/XlsxGdParallelProcessor.java)（及 [excel](src/com/gamer/data/excel/) / [gdg](src/com/gamer/data/gdg/)）。

| | StrategyTool | CodeTool |
|---|---|---|
| 入口 | [StrategyTool](src/com/gamer/data/StrategyTool.java) | [CodeTool](src/com/gamer/data/CodeTool.java) |
| 界面 | GUI（Excel/GD 对比） | GUI（生成模型 / 看表） |
| 产物 | `ConfigurationExcel/xls2gd_zh/StrategyTool.jar` | `Common/Tools/Bin/server/CodeTool.jar` |
| 启动脚本 | `xls2gd_zh/StrategyTool.bat`（旧 `ViewTools.bat` 仍转发） | `Common/Tools/Bin/server/CodeTool.bat`，或 WindowsTools CMD「执行 CodeTool」 |
| 工作目录约定 | jar 放在 `xls2gd_zh`，相对找 Excel / GD | 相对 WorkSpace 拼 `Document/...` |
| WindowsTools 里怎么调 | — | CMD「执行 CodeTool」 |

WindowsTools 生成 GD **显式传** `limitDir()`，输出目录是 `gdDataDir()`。StrategyTool / 命令行 CodeTool 未传时，gd 写在 xlsx 同目录，limit 按 `user.dir` 解析（StrategyTool 放在 `xls2gd_zh` 才能对上）。

界面与 WindowsTools 共用 [ViewUi](src/com/gamer/data/ui/ViewUi.java)（微软雅黑、页底、卡片、分栏）；`ant p-StrategyTool` / `p-CodeTool` 会打进 `com.gamer.data.ui`。

可选：[p-StrategyTool-exe](build.xml) 用本机 `D:/download/jdk-17.0.12` 的 jpackage 打目录版 exe。

---

## NetOpcode

[NetOpcodeMaker](src/com/gamer/data/message/NetOpcodeMaker.java) 读 [ActionEnum_*.xml](../../Common/Tools/Bin/opcode/)（相对本 README：`../../Common/Tools/Bin/opcode/`），生成 Java 枚举并拷到 `common` / `gameserver`。

日常更常用 CMD「生成 opcode」或 `Common/Tools/Bin/opcode_java.bat`。Skill：[opcode](../.cursor/skills/opcode/SKILL.md)。

---

## MCP

Cursor 与 Codex 共用 [mcp.json](../.cursor/mcp.json) 指向 `http://127.0.0.1:18765/mcp`。服务只监听 `127.0.0.1`。路径从 jar/运行位置定位 WorkSpace（可选 `-Dmcp.workspace`、`-Dmcp.datalog`），工具调用传工作区绝对路径；库 / Redis 仍读 [DbConfig](src/com/gamer/data/file/config/DbConfig.java) / [RedisConfig](src/com/gamer/data/file/config/RedisConfig.java)。

Skill：[excel](../.cursor/skills/excel/SKILL.md)、[mysql](../.cursor/skills/mysql/SKILL.md)、[filesystem](../.cursor/skills/filesystem/SKILL.md)、[redis](../.cursor/skills/redis/SKILL.md)、[git](../.cursor/skills/git/SKILL.md)。汇总：[mcp.md](../.cursor/commands/mcp.md)。

| jar | Main | 能力 |
|---|---|---|
| McpServer.jar | [McpServerMain](src/com/gamer/data/mpcserver/McpServerMain.java) | Excel、MySQL、Redis、文件、Git |

数据库工具用 `target=local|test` 选环境。构建执行 `ant p-McpServer`。产物目录双击 `McpServer.bat` 用菜单管理（启动/停止/登录启动）。完整说明见同目录 `McpServer.md`。

新通用 [player-log-check](../.cursor/skills/player-log-check/SKILL.md) 必须有 roleId，只用
`player_db_query`：同目录 [McpDatabaseConfig.json](McpDatabaseConfig.json) 多连接、索引等值查询、5 秒硬限时；本地旧模式和账号权限不变。

---

## 编译与打包

必须用 **cmd.exe**，先 cd 到 [servertool/](.)：

```bat
cd /d D:\code\WorkSpace\Server\servertool
chcp 65001 >nul 2>&1 && ant compile
chcp 65001 >nul 2>&1 && ant p-WindowsTools
chcp 65001 >nul 2>&1 && ant p-StrategyTool
chcp 65001 >nul 2>&1 && ant p-CodeTool
chcp 65001 >nul 2>&1 && ant p-McpServer
```

WindowsTools 开着时也可在 CMD 页、工作目录选本工程，执行 `ant p-WindowsTools`（jar 被自己占用则走下面的 `copy /Y`）。

改完 GUI 要 **关掉旧进程再开新的**。`copy /Y` 只换磁盘文件，已加载的类不会热更。若还有旧 `FileTools.jar` / `ViewTools.jar` / `GenTools.jar`，可删，改用 `WindowsTools.jar` / `StrategyTool.jar` / `CodeTool.jar`。

约定：[project-compile skill](../.cursor/skills/project-compile/SKILL.md)、[shell.md](../.cursor/commands/shell.md)。

---

## jar 占用

[build.xml](build.xml) `replace-jar`：先写成 `xxx.jar.tmp`，再 copy 原地覆盖。失败则 ant fail。

| 风险 | 处理方案 | 推荐 |
|---|---|---|
| WindowsTools 开着打包，界面仍是旧代码（`copy /Y` 只换文件，JVM 不热更） | 关 jar 再开；或接受「下次启动才是新包」 | **推荐**打完后关了再开 |
| MCP jar 正在运行，替换后进程仍是旧代码 | 双击 `McpServer.bat` 选重启 | **推荐** `p-McpServer` 后立即重启 |
| WindowsTools 路径/库账号写死本机，别人机器对不上 | 改 [PathConfig](src/com/gamer/data/file/config/PathConfig.java) / [DbConfig](src/com/gamer/data/file/config/DbConfig.java) / [SshConfig](src/com/gamer/data/file/config/SshConfig.java)，再编译 | **推荐**只改对应常量；MCP 路径不写盘符，换机不用改 Java |
| `copy /Y` 也失败（权限或强占用） | 结束进程后重跑；已留下 `.jar.tmp` | **推荐**先关占用再 `ant`，避免留下半成品 |

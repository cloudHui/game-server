# weball 一体化服务完整实施计划

## 1. 目标

新增 Maven 模块 `weball`，生成可独立启动的 `weball.jar`。单个 Java 进程提供当前 `center + gate + lobby + game + web` 对用户可见的全部功能，同时尽量取消单体内部不再需要的注册中心、服务发现、内部 TCP 转发、重复端口、线程池和指标服务。

现有 `center`、`gate`、`lobby`、`game`、`web` 模块不删除，原构建和原多服务部署继续可用。weball 与旧模式必须能够独立选择，不要求同时运行。

## 2. 完成标准

只有同时满足以下条件，weball 才算完成：

- [ ] 根项目能够正常构建旧模块和新增 `weball` 模块。
- [ ] 旧的 Center、Gate、Lobby、Game、Web 启动包及启动方式未被破坏。
- [ ] 只启动 `weball.jar`，无需再启动 Center、Gate、Lobby、Game、Web。
- [ ] 浏览器现有 URL、页面、HTTP API、WebSocket action 和返回结构保持兼容。
- [ ] 登录、注册、邀请码、用户管理、房间、牌桌、机器人、全部玩法、断线恢复、回放、战绩、学习、图片、小游戏、竞技场和后台管理通过验收。
- [ ] 内部不再依赖 Center 注册发现，也不依赖 Gate/Lobby/Game 间 TCP 转发。
- [ ] 数据目录、SQLite、回放和静态资源路径明确且兼容已有数据。
- [ ] 启动失败能够整体回滚；关闭时线程、端口、连接和数据库资源正常释放。
- [ ] 同等业务负载下，记录 weball 与旧五服务的进程数、RSS、堆、线程数和端口差异。
- [ ] 主机验收用同一时间段的 Java 进程、监听端口和日志交叉验证。

## 3. 当前代码盘点结论

原模块主要 Java 类数量：Center 12、Gate 18、Lobby 32、Game 121、Web 85；公共 Tool 67、Utils 95、Proto 25。

截至 2026-08-20，仓库已经存在第一版 `weball` 复制骨架，而非尚未开始：

- 根 `pom.xml` 已加入 `weball` 模块，`weball/pom.xml` 已配置 Spring Boot 打包。
- `weball` 当前约有 230 个主代码 Java 文件、356 个资源文件、0 个测试 Java 文件。
- 已复制 Game、Lobby、Web 大部分代码和静态资源，但复制不等于一体化完成。
- 当前代码仍直接包含 `GateClient`、`LobbyAdminClient`、`LobbyAdminHttp`、Center 注册发现、`ServerManager`、`ServerClientManager` 和旧端口探测。
- `Application`、产物名称、构建输出目录和计划目标需要在阶段 1 中再次核对，避免脚本按 `weball.jar` 查找、实际却生成 `WebAll.jar` 或输出到旧 Web 目录。
- 当前没有 weball 专属测试，不能根据“类已复制”勾选 Lobby、Game、Gateway 或全功能兼容任务。

因此后续工作性质是：先冻结现有复制骨架，再逐条建立进程内边界、替换旧网络调用、补齐测试和验收证据。

### 3.1 当前代码完成度审计（2026-08-20）

本表区分“文件已经迁入”和“功能已经接通”。只有构建、运行和验收均有证据时，才算功能完成。

| 范围 | 当前状态 | 已经做的工作 | 仍缺少的工作 | 结论 |
|---|---|---|---|---|
| Maven 模块 | 部分完成 | 根 POM 已加入 `weball`；独立 POM 已有 Spring Web/WS/Mail/SQLite/图片依赖和 Boot 打包 | 当前编译失败；jar 名为 `WebAll`、输出到 `build/web`，与目标 `weball.jar`/独立目录未对齐 | 约 50% |
| 启动入口 | 仅 Web 壳 | 已有 `com.cloud.Application`、`@SpringBootApplication`、调度启用 | 入口只执行 `SpringApplication.run`，未启动/装配 Lobby、Game、Gateway、storage 生命周期 | 约 15% |
| Web 代码 | 已迁入、未编通 | 78 个 Web Java 文件及 352 个静态资源已迁入；Controller、学习、图片、竞技场、小游戏代码存在 | 大量 import 仍指向 `web.*`；当前不能编译；未做页面/API 运行验收 | 文件覆盖高，功能完成 10%～20% |
| Lobby 代码 | 原样复制 | 32 个 Lobby Java 文件完整复制 | 32 个文件仍声明 `lobby.*`；仍启动 5700/5701、注册 Center、使用 ServerManager/静态单例；未接 Spring | 文件覆盖约 100%，一体化完成约 5% |
| Game 代码 | 已迁入、仍为独立服务 | 119 个 Game Java 文件已迁入；四种玩法、AI、战绩、回放、桌线程代码存在 | `Game.start()` 仍启动 TCP、注册 Center、指标端口；大量 `Game.getInstance()`、Gate/Lobby 网络通知未替换；未被 Application 调用 | 文件覆盖高，一体化完成约 10%～15% |
| Gateway/WS | 旧链路保留 | 浏览器 action、seq、格式化和 session 映射代码已存在 | `GameWebSocketHandler` 仍全部调用 TCP `GateClient`；没有内部 Gateway、事件总线或目标 session 推送替代 | 协议壳可复用，去 TCP 约 0% |
| Lobby 管理调用 | 旧链路保留 | Admin/Auth/Room Controller 与旧管理能力代码存在 | 仍通过 `LobbyAdminClient` 请求 5701；Lobby 仍创建 `LobbyAdminHttp` | 去 HTTP 中转约 0% |
| 配置与数据路径 | 有初稿 | `application.yml` 已包含 Web、账号、学习、图片、Gate/Lobby/Game/Capabilities 配置 | 仍保留旧内部地址/端口；Game/Lobby 读全局 `ConfigurationManager`；相对路径和同库事务未统一 | 约 25% |
| 生命周期/关闭 | 少量基础 | Game 有部分 `shutdown()`；部分线程池已有关闭方法 | 无统一 ready/degraded/回滚/逆序关闭；Lobby timer、网络、metrics、admin HTTP 未纳管 | 约 10% |
| 自动测试 | 未开始 | POM 已声明 JUnit 4 | `weball/src/test` 无 Java 测试；无契约、集成、E2E、故障或性能测试 | 0% |
| 部署与状态检查 | 未开始 | Boot 插件已有产物输出配置 | 无 weball 独立 ops 命令、数据互斥、健康检查、切换/回退演练 | 0% |

### 3.2 当次核验证据

- 执行命令：`mvn -pl weball -am -DskipTests package`。
- 结果：失败在 `weball` 编译阶段；`utils`、`proto`、`tool` 前置模块通过。
- 包声明统计：119 个 `com.cloud.game.*`、78 个 `com.cloud.web.*`、32 个旧 `lobby.*`、1 个入口类。
- 旧命名空间引用：66 个文件、197 行 `import web.*` / `game.*` / `lobby.*`。
- 运行链路证据：`Application.main()` 未引用 Lobby/Game；`GameWebSocketHandler` 注入 `GateClient`；`AppConfig` 创建 5600 Gate 客户端；`Lobby.start()` 仍启动网络、Center 注册和 5701 管理 HTTP；`Game.start()` 仍启动网络、Center 注册和独立 metrics。
- 测试证据：`weball/src/test` 当前无 Java 测试。

按“代码资产已搬入”估算，约完成 **45%～55%**；按第 2 节可运行、可验收标准估算，约完成 **8%～15%**。后续排期按后者计算，不能把复制文件数当成完成比例。

当前关键链路：

1. 浏览器通过 HTTP/WS 访问 Web。
2. Web 的 `/ws/game` 使用 `GateClient` 建立 TCP 连接到 Gate。
3. Gate 根据用户、桌号和消息类型转发到 Lobby 或 Game。
4. Lobby 负责账号、用户在线态、房间和建桌协调。
5. Game 负责牌桌、玩法、操作、广播、结算、战绩和回放。
6. Center 负责服务注册、发现、断线通知和地址分发。
7. Web 部分后台功能通过 `LobbyAdminClient` 调用 Lobby 的 5701 HTTP 端口。

已确认的合并风险：

- `ConfigurationManager` 是全局单例，只读取一个 `app.properties`，不能直接承载多服务配置。
- `HandleManager` 使用全局静态处理器表，同 JVM 注册多个服务处理器可能覆盖或串域。
- `MetricsCollector` 是全局单例，Game/Lobby 会相互覆盖服务名。
- Center、Gate、Lobby、Game 的启动方法面向独立进程，失败路径包含 `System.exit(1)`。
- 多处业务通过 `Center/Gate/Lobby/Game.getInstance()` 静态单例取依赖。
- Web 与 Lobby 共用账号 SQLite；Game 战绩也写入相关数据库，必须统一事务和路径。
- 现有 Web 已直接依赖 Game，但运行链路仍大量依赖 Gate TCP。

## 4. weball 目标结构

weball 保留一个进程、一个 Spring Boot 容器、一个主 HTTP 端口。WebSocket 使用同一 HTTP 端口下的现有路径。

内部逻辑分层，不再模拟五个独立进程：

- `bootstrap`：统一启动、启动顺序、健康状态、优雅关闭。
- `web`：现有页面、静态资源、HTTP API、鉴权和后台。
- `session/gateway`：浏览器 WebSocket 会话、协议转换、用户与连接映射。
- `lobby`：登录态、用户、邀请码、房间、自定义房、匹配、建桌协调。
- `game`：桌子、玩家、玩法、操作、机器人、结算、回放。
- `storage`：账号、邀请、房间、战绩、学习、图片和竞技场数据访问。
- `common`：Proto 模型、消息定义、通用工具；保留需要的公共能力，不保留进程间通信职责。

内部调用边界：

- 同步请求：Web/WS 直接调用应用服务并取得明确结果。
- 主动推送：Game 发布内部事件，Session/Gateway 转成现有浏览器 JSON 推送。
- 牌桌串行性：继续使用按桌/玩家分组的串行执行机制，不能改成无序直接调用。
- 持久化：通过统一存储层访问，不允许多个模块各自猜测相对路径。

## 5. 保留、改造和取消清单

### 5.1 完整保留的用户功能

- [ ] Web 静态页面、CSS、JS、图片及外部 context-path 兼容。
- [ ] Web 登录、token/cookie/session 恢复、退出和权限拦截。
- [ ] 邀请码注册、开放注册开关、用户启停和管理员权限。
- [ ] 房间列表、创建自定义房、加入房间、自动选桌。
- [ ] 麻将、斗地主、跑得快、拖拉机及代码中全部现有玩法。
- [ ] 准备、发牌、操作、托管/机器人、离桌、心跳和断线恢复。
- [ ] 局内结果、回合结果、总结果、分数和战绩。
- [ ] 回放生成、列表、详情和回放码。
- [ ] 管理后台：用户、邀请码、桌子、机器人比赛、记录、回放、Shell。
- [ ] 学习模块全部页面、API、内容、记录、错题、统计、日报和后台。
- [ ] 图片库上传、任务、列表、缩略图、原图、改名、删除和管理配置。
- [ ] 五子棋、象棋等 Web 内小游戏。
- [ ] 竞技场、合成、历练和竞技场后台。
- [ ] 能力探测 API，但实现改成内部模块健康状态。

### 5.2 保留但改变实现的组件

- [ ] `GameWebSocketHandler`：保留浏览器 action/JSON 契约，移除对 TCP `GateClient` 的依赖。
- [ ] `GameWsPushFormatter`：保留浏览器推送格式，输入改为内部游戏事件。
- [ ] Gate 会话映射和路由语义：保留必要行为，改成进程内 Session/Gateway。
- [ ] Lobby 用户、房间和桌子协调逻辑：保留业务规则，改成可注入服务。
- [ ] Game 桌子、线程模型和玩法：保留核心规则，改成明确生命周期组件。
- [ ] Proto：对外兼容或复用模型时保留；内部新调用不强制序列化再反序列化。
- [ ] 定时任务：保留业务定时器，合并调度资源并登记所有任务。
- [ ] 指标：改为 weball 单一指标视图，指标名按模块加前缀。
- [ ] 日志：统一配置，日志字段包含模块、桌号、用户和请求序号。
- [ ] SQLite：保留已有表和数据，统一绝对解析后的数据根目录。

### 5.3 weball 内取消的分布式外壳

- [ ] Center TCP/HTTP 监听。
- [ ] 服务注册、发现、心跳、地址分发和服务上下线广播。
- [ ] Gate 到 Center/Lobby/Game 的 TCP 客户端和重连。
- [ ] Lobby 到 Center/Game/Gate 的 TCP 客户端和重连。
- [ ] Game 到 Center/Lobby/Gate 的 TCP 客户端和重连。
- [ ] Web `GateClient` 的 TCP 连接池、请求超时匹配和断线重连。
- [ ] Lobby Admin 5701 HTTP 监听及 Web `LobbyAdminClient` HTTP 中转。
- [ ] Game、Lobby 独立 metrics HTTP 端口。
- [ ] 5400、5401、5500、5600、5601、5700、5701 等内部端口要求。
- [ ] 每个服务重复创建的 ServerManager、ServerClientManager、网络线程和 Timer。

说明：以上仅在 weball 运行路径取消；旧模块中的实现继续保留。

## 6. 分阶段执行清单

### 阶段 0：建立基线和功能矩阵

- [x] 盘点模块规模、入口类、主要配置文件和当前外部链路。
- [ ] 固定当前可用提交号、构建命令和运行配置，不提交用户现有无关改动。
- [ ] 收集旧模式启动后的 Java 进程、端口、日志、线程和内存基线。
- [ ] 建立 HTTP 路由清单：方法、路径、鉴权、请求和响应。
- [ ] 建立 WebSocket action 清单：输入字段、消息号、同步回复和主动推送。
- [ ] 建立 Center/Gate/Lobby/Game 全部 handler 消息矩阵。
- [ ] 建立页面功能清单，覆盖登录、大厅、房间、全部牌桌和后台页面。
- [ ] 建立数据库表、文件目录、回放目录和数据所有者清单。
- [ ] 建立线程池、Timer、Spring 定时任务和关闭资源清单。
- [ ] 为每项功能记录旧系统可复现验收步骤和预期结果。

验收：功能矩阵不存在“用途未知”的 handler、端口、数据库或定时任务。

### 阶段 1：新增模块与兼容骨架

- [ ] 根 `pom.xml` 增加 `weball`，不改变旧模块顺序和产物。
- [ ] 建立 `weball/pom.xml` 和唯一入口，产物名固定为 `weball.jar`。
- [ ] 明确依赖方向，禁止 weball 形成 Maven 循环依赖。
- [ ] 复用 Web 静态资源和 Spring Controller，避免复制后产生双份页面。
- [ ] 增加 weball 独立配置命名空间、日志配置和构建目录。
- [ ] 建立运行模式标识，确保旧模式不会误启 weball 内部组件。
- [ ] 建立统一生命周期状态：starting、ready、degraded、stopping、stopped。
- [ ] 增加整体启动失败回滚和优雅关闭顺序。
- [ ] 验证旧五服务和 weball 骨架均能独立构建。

验收：weball 可启动 Web 页面；旧五服务构建产物未变化。

### 阶段 2：配置、日志、数据和公共运行时

- [ ] 设计单一 `application.yml`，覆盖 Web、账号、Lobby、Game、机器人、学习、图片和邮件配置。
- [ ] 消除 weball 路径对当前工作目录的隐式依赖。
- [ ] 明确 `data/lobby.db`、学习数据、图片、竞技场和回放的默认位置。
- [ ] 增加旧数据只读探测、Schema 版本核验及备份说明。
- [ ] 统一 SQLite 连接策略，处理 Web/Lobby/Game 同库并发和事务边界。
- [ ] 统一日志后端，排除旧 Logback/SLF4J 冲突。
- [ ] 统一指标注册表，保留 `game.*`、`lobby.*` 等可识别前缀。
- [ ] 合并通用调度器；牌桌串行线程池与图片上传等阻塞任务仍隔离。
- [ ] 配置线程数和队列容量，避免按原五服务默认值简单相加。
- [ ] 为所有资源定义 owner 和关闭顺序。

验收：同一配置可重复启动；数据和日志落在确定路径；无端口与静态单例冲突。

### 阶段 3：迁入 Lobby 业务，移除管理 HTTP 中转

- [ ] 迁入账号、用户、邀请、注册开关和默认数据逻辑。
- [ ] 合并 Web `AccountService/UserService` 与 Lobby 用户来源，定义唯一身份真相。
- [ ] 保留现有 token、cookie、session 和管理员判定行为。
- [ ] 迁入房间模型、自定义房、房间列表和可加入桌逻辑。
- [ ] 建立 Lobby 应用服务接口，替代静态 `Lobby.getInstance()` 访问。
- [ ] AdminController 直接调用 Lobby 应用服务。
- [ ] RoomController 直接调用 Lobby 应用服务。
- [ ] 取消 weball 内的 LobbyAdminHttp 和 LobbyAdminClient 跳转。
- [ ] 验证邀请、用户启停、房间增删查和后台列表。

验收：不监听 5700/5701，也能完成登录、注册、房间和 Lobby 后台功能。

### 阶段 4：迁入 Game 核心与全部玩法

- [ ] 将 Game 初始化拆成配置、线程、存储、桌管理和玩法注册生命周期。
- [ ] 保留 TableManager 桌号、索引、恢复和销毁规则。
- [ ] 保留玩家/桌任务串行执行及并发安全保证。
- [ ] 迁入麻将完整状态、操作、结算和恢复逻辑。
- [ ] 迁入斗地主完整状态、操作、机器人、结算和恢复逻辑。
- [ ] 迁入跑得快完整状态、操作、机器人、结算和恢复逻辑。
- [ ] 迁入拖拉机完整状态、操作、机器人、结算和恢复逻辑。
- [ ] 盘点并迁入其他已注册玩法，禁止只覆盖页面上常用玩法。
- [ ] 迁入 GameRuntimeConfig 热加载行为。
- [ ] 迁入离线超时、桌心跳、定时循环和清桌行为。
- [ ] 迁入 ScoreRepository、异步数据库执行及失败处理。
- [ ] 迁入 ReplayRecorder、目录组织、审计事件和保存策略。
- [ ] 建立 Game 应用服务接口，替代静态 `Game.getInstance()` 访问。
- [ ] 为所有核心纯规则按 TDD 补齐回归测试后再调整实现。

验收：不监听 5500，应用服务测试可完成建桌到总结算的完整流程。

### 阶段 5：用内部 Gateway 替代 Gate 与服务间 TCP

- [ ] 定义统一请求上下文：sessionId、userId、clientId、tableId、sequence。
- [ ] 建立 WebSocket 会话注册、认证、关闭和用户唯一连接规则。
- [ ] 将 `auth` 保持为现有浏览器协议。
- [ ] 将 `enterTable` 映射为内部 Game 调用。
- [ ] 将 `refreshTable` 映射为权威快照调用。
- [ ] 将 `op` 映射为内部有序操作调用。
- [ ] 将 `leave` 映射为内部离桌调用。
- [ ] 将房间列表、创建和加入流程映射为 Lobby/Game 协调调用。
- [ ] 将 Game 广播改为内部事件并按目标 session 推送。
- [ ] 保留现有 sequence 语义、错误码和浏览器 JSON 字段。
- [ ] 保留 `seq=0` 主动推送语义。
- [ ] 保留断线、重连、重复登录、刷新页面和重新入桌行为。
- [ ] 保留 Gate 原有按 userId/tableId 路由中的必要校验。
- [ ] 删除 weball 对 `GateClient`、ServerManager、ServerClientManager 的运行时依赖。
- [ ] 删除 weball 对 Center handler、注册和发现的运行时依赖。

验收：仅监听 Web 端口，浏览器完整牌桌流程与旧模式协议一致。

### 阶段 6：迁入 Web 其余功能

- [ ] 静态首页、大厅、游戏入口和所有共享 JS/CSS 完整可访问。
- [ ] `/api/auth`、旧 `/api/login` 兼容接口行为一致。
- [ ] 回放 API 读取 weball 的统一回放目录。
- [ ] 学习模块 API、定时日报、邮件、资源和前端错误上报完整运行。
- [ ] 图片上传任务、元数据、归档、缩略图、缓存和权限完整运行。
- [ ] MiniGame WebSocket 与现有房间状态完整运行。
- [ ] 竞技场、合成、历练、库存和后台完整运行。
- [ ] Shell 管理能力保持原授权和安全边界。
- [ ] `/api/capabilities` 改为检查内部模块 ready 状态，不探测已取消端口。
- [ ] context-path、反向代理头、压缩、上传限制和安全响应头保持一致。

验收：Web 页面/API 清单逐项通过，浏览器控制台无新增运行错误。

### 阶段 7：兼容、故障和性能验证

- [ ] 单元测试覆盖配置、身份、房间、规则、结算和消息格式转换。
- [ ] 集成测试覆盖注册/登录→建房→入桌→准备→操作→结算→回放。
- [ ] 每种玩法至少完成正常局、断线重连、非法操作和机器人局。
- [ ] 验证重复请求、sequence 超时、WS 断开和页面刷新。
- [ ] 验证 SQLite 忙、写失败、回放目录不可写和配置错误。
- [ ] 验证某内部模块启动失败时 weball 不报告虚假 ready。
- [ ] 验证优雅关闭，不遗留游戏线程、上传任务、端口或损坏数据。
- [ ] 对照旧模式逐项比较 HTTP 状态、业务 code、JSON 和推送顺序。
- [ ] 进行并发桌、并发 WS、机器人和图片上传混合压力测试。
- [ ] 记录旧模式与 weball 的 RSS、堆、非堆、线程、GC 和启动时间。
- [ ] 检查内存泄漏：session、连接、桌、timer、回调 pending 和图片任务。

验收：功能矩阵全部通过；无高优先级差异；资源指标达到预期。

### 阶段 8：部署与回退

- [ ] 增加 weball 独立打包和启动脚本，不覆盖旧脚本。
- [ ] 增加 weball 独立日志目录和 PID/端口识别。
- [ ] 增加运行状态检查：Java 进程、Web 端口、健康 API 和同期日志。
- [ ] Nginx 仅需切换到 weball Web 端口；保留原配置备份。
- [ ] 明确数据备份、首次切换、观察和回退步骤。
- [ ] 禁止新旧模式同时写同一个 SQLite 和回放目录。
- [ ] 小流量验收后再切换；异常时停止 weball 并恢复旧五服务。
- [ ] 更新项目 README、端口表、架构图和故障排查说明。

验收：可在不改数据格式、不删除旧模块的前提下切换和回退。

## 7. 关键消息链路替换表

| 当前链路 | weball 替代 | 必须保持 |
|---|---|---|
| Web → Gate TCP | WS Handler → Gateway 应用服务 | action、seq、错误结构 |
| Gate → Lobby | Gateway → Lobby 应用服务 | 登录/房间路由和校验 |
| Gate → Game | Gateway → Game 应用服务 | tableId/userId 路由、顺序 |
| Lobby → Game 建桌 | Lobby 协调器 → Game | 桌模型、返回信息、失败处理 |
| Game → Lobby 桌状态 | Game 内部事件 → Lobby | 桌销毁、玩家离开、恢复 |
| Game/Gate → Web 推送 | Game 事件 → Session 推送器 | Proto 对应 JSON、seq=0 |
| Web → Lobby Admin HTTP | Controller → Lobby 管理服务 | API 字段、权限、错误码 |
| 各服务 → Center | 取消 | weball 内部 ready 状态替代 |
| Capabilities → 端口探测 | 模块健康注册表 | 前端看到的能力字段 |

每替换一条链路，必须先记录原消息类型、消息号、请求字段、响应字段、主动推送和异常分支，再实现和测试；不得只验证成功路径。

## 8. 数据兼容计划

- [ ] 确认 `data/lobby.db` 当前所有表、索引和创建逻辑。
- [ ] 确认 Lobby 与 Web 账号实体字段、密码算法和管理员判定完全一致。
- [ ] 确认 Game 分数写入与 Web 后台记录查询使用同一 Schema。
- [ ] 确认自定义房、邀请、竞技场、学习和图片数据库是否独立。
- [ ] 保留现有数据，不自动删除、重建或覆盖数据库。
- [ ] Schema 变更必须有版本、备份、前向迁移和回退说明。
- [ ] 所有目录在启动时规范化并记录最终绝对路径，但日志不得泄露凭据。
- [ ] weball 与旧服务切换前必须确保只有一个模式持有写权限。

## 9. 并发与资源计划

- [ ] HTTP 请求使用 Spring/Tomcat 工作线程。
- [ ] WebSocket I/O 不直接运行耗时游戏或数据库任务。
- [ ] 每桌操作保持串行；不同桌允许并行。
- [ ] 数据库写任务使用有界队列，定义队列满时行为。
- [ ] 图片处理使用独立有界线程池，避免拖慢牌桌。
- [ ] 学习日报、配置刷新、离线清理等任务统一登记和命名。
- [ ] session、桌、pending 请求、定时器和上传任务都有清理入口。
- [ ] 默认线程数按机器 CPU/内存设置，不沿用 Game 最小 32 线程后再叠加其他池。

## 10. 实施纪律

- 每次只处理一个未勾选任务或一个紧密相关的小组。
- 实现前说明本次目标、影响文件和验收方式。
- 核心纯函数及底层业务逻辑使用 TDD：先失败测试，再实现，再重构。
- 复杂故障使用诊断循环，以日志、请求、进程和端口证据定位。
- 每项只有在对应测试或人工验收证据通过后才能由 `[ ]` 改为 `[x]`。
- 失败或部分完成保持未勾选，并在任务后记录阻塞原因。
- 不删除旧模块，不用 weball 改造顺手重写无关功能。
- 不因状态判断冲突执行 restart、stop 或 kill；部署操作需用户明确授权。
- 每个阶段完成后先汇报差异、测试和风险，再进入下一阶段。

## 11. 工作量、操作内容与阶段产物

### 11.1 估算口径

- 单位为“人日”，1 人日按 6 小时有效编码、核验和记录计算，不按连续在线时长计算。
- 估算包含代码阅读、实现、测试、文档和一次正常返工；不包含线上等待时间、大规模历史数据修复及需求新增。
- 当前已有复制骨架可减少文件搬迁时间，但不会明显减少协议核对、静态单例拆分、并发验证和全功能回归时间。
- 从当前代码状态继续，单人串行剩余预计 **46～73 人日**；若只做到“能启动、能登录、能开一局”，约 **18～28 人日**，但不满足第 2 节完成标准。
- 两人并行不能直接除以二。阶段 0～2 可较好并行；阶段 3～6 存在接口依赖；阶段 7～8 需合流验收。合理日历周期约 **7～11 周**。
- 工时在阶段 0 完成功能/消息/数据矩阵后重新校准，允许误差目标为 ±20%。

### 11.2 分阶段工作量总表

| 阶段 | 预计人日 | 主要操作 | 明确产物 | 前置依赖 |
|---|---:|---|---|---|
| 0 基线与矩阵 | 4～6 | 扫描路由、WS action、handler、DB、目录、线程；运行旧模式取证 | 5 张清单、基线报告、验收用例表 | 无 |
| 1 骨架校正 | 3～5 | 修复包/import、校正 POM、入口、产物名、资源复用、生命周期 | 可构建/可启动骨架、旧构建对比 | 阶段 0 最小构建基线 |
| 2 公共运行时 | 4～7 | 统一配置、路径、日志、DB、指标、调度和关闭 | 配置说明、资源 owner 表、启动/关闭测试 | 阶段 1 |
| 3 Lobby 内部化 | 5～8 | 统一账号真相、建立 Lobby 服务、替换 5701 HTTP | Lobby API、Controller 改造、账号/房间测试 | 阶段 2 |
| 4 Game 内部化 | 8～13 | 拆 Game 生命周期、保持桌串行、迁移玩法/战绩/回放 | Game API、各玩法回归测试、完整局测试 | 阶段 2；接口与阶段 3 对齐 |
| 5 Gateway 去 TCP | 8～12 | 建 session/router/event，替换 GateClient 和服务间 TCP | 内部 Gateway、协议契约测试、重连测试 | 阶段 3、4 |
| 6 Web 全功能收口 | 4～7 | 学习、图片、小游戏、竞技场、后台、capabilities 验收 | Web 功能矩阵结果、前端错误记录 | 阶段 2；部分可并行 |
| 7 兼容与性能 | 7～10 | E2E、故障注入、并发/资源对比、泄漏检查 | 差异报告、性能报告、缺陷清单 | 阶段 3～6 |
| 8 部署与回退 | 3～5 | 独立脚本、健康检查、数据互斥、切换/回退演练 | 运维手册、脚本、演练记录 | 阶段 7 |
| **剩余合计** | **46～73** | 已扣除文件复制和初版 POM/config 工作，以全量完成标准为准 | 可切换、可回退的一体化服务 | 逐阶段门禁 |

### 11.3 每阶段具体操作

#### 阶段 0：4～6 人日

1. 记录当前 Git 提交、JDK/Maven 版本、构建命令、配置来源和数据目录；产出 `docs/weball/baseline-build.md`。
2. 从 Controller 注解生成 HTTP 清单；人工补充鉴权、请求体、返回码和调用服务；产出 `http-matrix.md`。
3. 扫描 `GameWebSocketHandler` 和前端 `send/request/onmessage`，建立 action、字段、seq、推送和异常矩阵；产出 `ws-matrix.md`。
4. 扫描各服务 handler 注册点和 Proto 消息号，建立旧链路到新应用服务映射；产出 `message-matrix.md`。
5. 扫描 SQLite 建表 SQL、Repository、文件读写、回放和上传目录；产出 `storage-matrix.md`。
6. 扫描 Executor、Timer、Scheduler、网络客户端和 shutdown；产出 `runtime-resource-matrix.md`。
7. 在用户允许的实际主机环境启动旧模式后，同一时间窗口记录 Java 进程、端口、日志、RSS、堆和线程；不因沙箱结果冲突执行重启或停止。

完成门槛：每个路由、action、handler、DB 和长期线程都有 owner、用途和验收方法；未知项为 0。

#### 阶段 1：3～5 人日

1. 先修复当前确定的命名空间断裂：32 个旧 `lobby.*` package、66 个文件中的旧模块 import；每批修改后执行编译，不能一次全局替换后直接进入业务改造。
2. 对照当前 `weball/pom.xml` 与根 POM，确定依赖方式继续采用“源码迁入 + tool/proto 依赖”，还是改为模块依赖；禁止两套同名包混用。
3. 将入口、artifactId、最终 jar 名和输出目录统一为运维脚本可稳定识别的约定；当前 `WebAll` 和 `build/web` 需要改为独立名称/目录。
4. 决定静态资源单一来源；当前资源已复制，需增加自动同步/差异检查，防止 Web 与 weball 页面漂移。
5. 新增 `WebAllLifecycle`，按 storage → lobby → game → gateway → web 标记 ready；失败时逆序关闭已启动资源。
6. 分别执行旧全模块构建、weball 单模块构建和 jar 启动冒烟；比较旧产物名称和数量。

完成门槛：`mvn -pl weball -am package` 通过；骨架只启动 Web 端口；`/api/capabilities` 能表达内部组件尚未就绪；旧产物无变化。

#### 阶段 2：4～7 人日

1. 把旧 `app.properties` 映射为 `weball.*` 配置对象，密码、密钥只支持外部注入，不写入仓库。
2. 建立 `DataPathResolver`，一次性解析 DB、学习、图片、回放、日志绝对路径并在启动日志记录非敏感部分。
3. 盘点 Web/Lobby/Game 是否连接同一 SQLite；确定单连接池/事务策略、busy timeout、WAL、写队列和关闭顺序。
4. 替换多服务指标 serviceName 覆盖问题，使用 `lobby.*`、`game.*`、`gateway.*` 前缀。
5. 建立线程资源注册表。桌任务、DB 写、图片处理分池；所有队列有容量、拒绝策略、线程名和 shutdown 超时。
6. 增加启动失败、重复启动、正常关闭测试；检查非 daemon 线程、端口和 DB lock 是否残留。

完成门槛：从任意工作目录启动均解析到相同数据；启动失败不留下半活组件。

#### 阶段 3：5～8 人日

1. 定义 `LobbyApplicationService`：登录、注册、在线态、房间、建桌协调、用户/邀请管理、记录查询。
2. 明确 `AccountService` 与 `UserRepository` 谁负责凭据、状态和权限；写一份字段级身份映射。
3. 先为邀请、启停、房间模型、自动选桌写回归测试，再把 handler 内逻辑移入服务。
4. 将 `AuthController`、`AdminController`、`RoomController` 改为直接调用 Lobby 服务。
5. 删除 weball 运行路径对 `LobbyAdminClient` 和 `LobbyAdminHttp` 的创建/调用；旧模块文件保持不动。
6. 验证不监听 5700/5701 时，注册、登录、管理用户、邀请和房间操作仍通过。

完成门槛：Weball 内搜索不到 Controller → 5701 HTTP 调用；身份只有一个持久化真相。

#### 阶段 4：8～13 人日

1. 定义 `GameApplicationService`：建桌、入桌、快照、操作、离桌、断线、房间桌查询和管理操作。
2. 把 `Game.getInstance()` 依赖逐个改为构造注入或显式上下文；先处理 TableManager、Repository、线程池、ReplayRecorder。
3. 保留“同桌串行、跨桌并行”执行器；补并发顺序、重复操作、桌销毁竞态测试。
4. 按麻将、斗地主、跑得快、拖拉机逐项执行：规则纯函数 TDD → 建桌到结算集成测试 → 机器人局 → 重连恢复。
5. 校验战绩与回放：成功写入、失败重试/报告、目录不可写、关闭时 pending 写入处理。
6. 核对旧 Game handler 的每种消息均有内部 API 或明确废弃理由。

完成门槛：不启动 Game TCP 5500，测试可完成四类玩法完整局；同桌操作顺序不变。

#### 阶段 5：8～12 人日

1. 定义 `GatewayRequestContext` 和 `SessionRegistry`；规定 userId/sessionId/tableId/seq 的绑定和清理。
2. 为现有 WS action 建契约测试，固定输入输出 JSON、错误码、seq 和主动推送格式。
3. 逐条替换 `auth`、房间、`enterTable`、`refreshTable`、`op`、`leave`，每次只切一条链路。
4. 建 `GameEventPublisher` → `SessionPushService`，按目标用户/桌广播，保留 `seq=0`。
5. 实现重复登录、断线、刷新页面、重新入桌、慢客户端和发送失败清理。
6. 断开 weball 对 `GateClient`、Center 注册、ServerManager/ServerClientManager 的运行时装配。
7. 用进程、端口和同期日志确认只监听 Web 端口；不能仅依赖 `ops.sh status`。

完成门槛：浏览器完整局无 TCP Gate；旧协议契约测试无差异。

#### 阶段 6：4～7 人日

1. 按页面功能矩阵验收静态资源、auth、回放、学习、图片、小游戏、竞技场和后台。
2. 每个页面同时检查：HTML 静态响应、浏览器 JS 错误、API 请求/响应、后端同期日志。
3. 将 capabilities 从端口探测改为生命周期组件状态；字段保持前端兼容。
4. 校验上传大小、反向代理头、context-path、权限拦截、Shell 授权和安全响应头。
5. 为学习日报、图片任务、小游戏 WS 增加关闭和失败恢复验证。

完成门槛：功能矩阵逐项有证据；浏览器控制台无新增错误；后台无越权变化。

#### 阶段 7：7～10 人日

1. 自动 E2E 覆盖注册/登录 → 房间 → 建桌 → 入桌 → 操作 → 结算 → 战绩 → 回放。
2. 四种玩法各测正常局、机器人局、非法操作、断线重连和重复请求。
3. 故障注入：DB busy/不可写、回放不可写、线程队列满、WS 断开、组件启动失败。
4. 压测并发桌、WS、机器人和图片上传；记录 P50/P95/P99、错误率、队列长度。
5. 相同主机、相同负载、同一时间窗口比较旧模式与 weball 的进程、端口、RSS、堆、线程、GC。
6. 对 session、桌、timer、pending callback、上传任务做运行前后计数，判断泄漏。

完成门槛：无 P0/P1 差异；P2 有明确接受或修复结论；资源数据可复现。

#### 阶段 8：3～5 人日

1. 增加独立 `build/start/status/stop weball` 操作，不覆盖旧五服务脚本。
2. status 同时检查 Java 进程、监听端口、健康 API 和同期日志；任一冲突显示 degraded，不擅自 restart。
3. 启动前检测旧模式是否占用同一数据目录；禁止双写 SQLite、回放和图片目录。
4. 编写备份、首次切换、观察指标、失败回退步骤，并在非生产数据上演练。
5. 更新 README、端口表、架构图、配置表和常见故障证据采集命令。

完成门槛：按文档可完成一次切换与回退；数据格式未变，旧模块仍可启动。

## 12. 建议的两人拆分

### 12.1 你可以先独立修改的低冲突工作

这些任务主要产出文档、清单或独立配置，适合你先做，不需要等待核心接口：

- 阶段 0 的 HTTP 路由、页面功能、数据目录和配置项清单。
- 逐页人工验收步骤：入口 URL、操作步骤、预期页面、关键 API、失败表现。
- 旧模式实际主机基线记录；只采集，不执行 restart/stop/kill。
- `application.yml` 配置项说明、默认值、是否敏感、旧配置来源映射。
- README、端口表、部署/备份/回退文档草稿。
- 静态资源差异清单：`web/src/main/resources/static` 与 `weball/.../static` 哪些不同。
- 回放、图片、学习、竞技场现有数据路径和备份容量核对。

建议每次提交只做一类清单，提交信息以 `docs(weball): ...` 开头；不要同时格式化或移动 Java 文件。

### 12.2 需要先对齐接口再修改的工作

- `LobbyApplicationService`、`GameApplicationService` 方法和 DTO。
- Account/Lobby 用户表、密码、权限的唯一真相。
- Gateway 的 session、重复登录、seq 和广播规则。
- SQLite 连接/事务策略及 Schema 变更。
- TableManager、牌桌执行器、结算、战绩和回放生命周期。
- POM 依赖方向、包名迁移、静态资源单一来源。

这些文件发生冲突的概率高。认领前在计划任务后加负责人，例如 `[ ] (owner: user)`；完成后附测试证据，再决定是否勾选。

### 12.3 推荐并行顺序

| 周期 | 你 | Codex/另一开发者 | 合流点 |
|---|---|---|---|
| 第 1 段 | 页面、HTTP、数据、部署清单 | WS、handler、线程资源、构建基线 | 阶段 0 矩阵评审 |
| 第 2 段 | 配置说明、人工验收脚本 | 骨架、生命周期、路径/DB/指标 | 阶段 2 API 评审 |
| 第 3 段 | Web 非牌桌功能验收与修复 | Lobby、Game 应用服务 | Lobby/Game 接口冻结 |
| 第 4 段 | 页面协议差异复测 | Gateway 去 TCP、重连/广播 | 完整局 E2E |
| 第 5 段 | 部署、备份、回退文档 | 故障、性能、泄漏测试 | 切换演练 |

## 13. 单任务操作模板

每次开始任务，先在本文件对应任务下记录：

```text
负责人：
目标：
影响文件：
不改范围：
预计工时：
操作步骤：
自动测试：
人工验收：
回退方式：
实际结果/证据：
```

任务拆分建议控制在 0.5～2 人日。超过 2 人日继续拆分；涉及核心规则时使用 TDD；复杂故障进入诊断循环。

## 14. 优先级与暂缓项

优先级：

1. 功能和数据不丢失。
2. 浏览器/API/WS 兼容。
3. 旧模块不受影响、可回退。
4. 正确取消内部网络层。
5. 降低线程、连接和内存。
6. 后续清理和代码美化。

首个可演示里程碑可以暂缓性能优化、指标美化和无关重构，但不能暂缓：数据互斥、牌桌串行性、身份一致性、WS 协议兼容、错误处理和回退能力。

## 15. 模块依赖与复用边界

### 15.1 Maven 依赖方向

目标依赖图：

```text
weball
  ├─ proto
  │    └─ utils
  ├─ tool
  │    ├─ proto
  │    └─ utils
  ├─ Spring Boot Web/WebSocket/Mail
  ├─ SQLite JDBC
  └─ 图片处理库

weball -X-> center
weball -X-> gate
weball -X-> lobby
weball -X-> game
weball -X-> web
```

执行要求：

- `weball/pom.xml` 可以继续引用 `tool`、`utils`、`proto`，不得引用 `center`、`gate`、`lobby`、`game`、`web` 服务模块。
- `tool` 已传递依赖 `utils` 和 `proto`；weball 是否保留显式 `proto` 依赖，以 Maven dependency analysis 结果决定。建议显式声明直接使用的 `proto`，表达真实依赖。
- 旧服务模块不得反向依赖 weball。需要修复的公共能力优先放 `utils/tool/proto`，但只有真正通用且旧服务也需要时才下沉，不能为了省一次复制把 weball 业务接口塞进公共模块。
- 不改变旧模块的包名、入口或依赖。weball 只在自身源码内重组。
- 使用 `mvn dependency:tree -pl weball` 记录实际依赖，检查重复 SLF4J/Logback、Netty、Jackson、Protobuf 版本；当前 POM 的 Logback exclusions 保留到依赖收敛验证通过。

### 15.2 `utils` 可复用内容

可直接复用：

- `threadtutil.thread.ExecutorPool`：有界队列、按 `Task.groupId()` 串行亲和、`CompletableFuture` 返回。
- `threadtutil.thread.Task`：作为 keyed/serial task 最低层协议。
- `threadtutil.timer.Timer`：仅在旧牌桌逻辑短期离不开时作为迁移适配；最终业务调度优先统一到 Spring/`ScheduledExecutorService` 管理。
- `utils.trace.TraceContext` 等 trace 工具：用于 requestId、userId、tableId 上下文。
- 不依赖网络状态的纯工具：时间、编码、哈希、IP/文件等，经逐项确认后复用。

限制复用：

- `ConfigurationManager`：旧服务可继续使用；weball 禁止作为主配置入口。原因：全局单例、单份 `app.properties`、无法表达多模块类型安全配置。
- `MetricsCollector`：短期可包装后复用底层 counter/gauge；不得让 Lobby/Game 重复 `setServiceName()`。最终由 weball 单一 metrics facade 管理。
- `net.*`、TCP codec/client/server：只供旧模式；weball 目标运行路径不得创建或连接这些对象。

### 15.3 `tool` 可复用内容

可复用：

- 与业务无关的数据结构、通用算法或确有必要的工具类。
- `ExecutorPool` 实际位于 `utils`；weball 不需要复制。

不得进入 weball 新核心链路：

- `ServerManager`、`ServerClientManager`、`ConnectHandle`：面向服务发现和 TCP 连接。
- `HandleManager`：内部使用全局静态 `handleMap`，适合旧网络消息回调，不适合同 JVM 多领域处理器注册。
- `AbstractRegisterHandler`、`AbstractAckServerInfoHandle`、心跳/注册 handler：仅旧分布式模式使用。

如果 weball 编译期间因遗留类暂时需要上述类型，必须放在 `legacy` 包且不被 Spring 扫描、不被生命周期启动；阶段 5 完成后删除 weball 内遗留适配代码。

### 15.4 `proto` 可复用内容

- `GameProto`、`LobbyProto`、`ModelProto`、`RuleProto`、`ConstProto`：保留浏览器格式转换、旧数据兼容、回放和规则模型。
- `GMsg/LMsg/CMsg` 消息号：仅用于协议矩阵、兼容测试、日志映射；进程内服务调用不再以消息号路由。
- 内部方法参数优先使用 weball 自有 command/result DTO。边界处一次性完成 DTO ↔ Proto 转换，禁止内部每层反复 serialize/parse。
- Proto Schema 不在一体化第一阶段改动；需要字段扩展时必须同时验证旧服务模式。

## 16. 目标包结构与类放置规则

统一根包改为 `com.cloud.weball`，避免当前 `com.cloud.game`、`com.cloud.web`、旧 `lobby.*` 混合。目标树：

```text
com.cloud.weball
├─ WebAllApplication.java
├─ bootstrap
│  ├─ WebAllLifecycle.java
│  ├─ ComponentState.java
│  ├─ ComponentHealthRegistry.java
│  ├─ StartupRollback.java
│  └─ ShutdownCoordinator.java
├─ config
│  ├─ WebAllProperties.java
│  ├─ GameProperties.java
│  ├─ StorageProperties.java
│  ├─ RuntimeProperties.java
│  ├─ PhotoProperties.java
│  └─ WebConfiguration.java
├─ runtime
│  ├─ RuntimeExecutors.java
│  ├─ ExecutorSpec.java
│  ├─ NamedThreadFactory.java
│  ├─ SerialTaskDispatcher.java
│  ├─ ScheduledTaskRegistry.java
│  ├─ RuntimeResourceRegistry.java
│  └─ RuntimeMetrics.java
├─ gateway
│  ├─ api
│  │  ├─ GatewayService.java
│  │  └─ GatewayResult.java
│  ├─ session
│  │  ├─ SessionRegistry.java
│  │  ├─ SessionBinding.java
│  │  └─ DuplicateLoginPolicy.java
│  ├─ command
│  │  ├─ GatewayCommandRouter.java
│  │  ├─ AuthCommandHandler.java
│  │  ├─ EnterTableCommandHandler.java
│  │  ├─ RefreshTableCommandHandler.java
│  │  ├─ OperationCommandHandler.java
│  │  ├─ LeaveTableCommandHandler.java
│  │  └─ HeartbeatCommandHandler.java
│  ├─ push
│  │  ├─ SessionPushService.java
│  │  ├─ GameEventSubscriber.java
│  │  └─ GameWsPushFormatter.java
│  └─ ws
│     ├─ GameWebSocketHandler.java
│     └─ WebSocketConfiguration.java
├─ lobby
│  ├─ api
│  │  ├─ LobbyApplicationService.java
│  │  ├─ LobbyAdminService.java
│  │  └─ dto/...
│  ├─ application
│  │  ├─ DefaultLobbyApplicationService.java
│  │  ├─ DefaultLobbyAdminService.java
│  │  └─ TableAllocationService.java
│  ├─ domain
│  │  ├─ user/...
│  │  ├─ room/...
│  │  └─ invite/...
│  └─ infrastructure
│     └─ repository/...
├─ game
│  ├─ api
│  │  ├─ GameApplicationService.java
│  │  ├─ GameCommand.java
│  │  ├─ GameResult.java
│  │  └─ dto/...
│  ├─ application
│  │  ├─ DefaultGameApplicationService.java
│  │  └─ GameCommandDispatcher.java
│  ├─ domain
│  │  ├─ table/...
│  │  ├─ mj/...
│  │  ├─ ddz/...
│  │  ├─ pdk/...
│  │  └─ tractor/...
│  ├─ event
│  │  ├─ GameEvent.java
│  │  ├─ GameEventPublisher.java
│  │  └─ events/...
│  └─ infrastructure
│     ├─ repository/...
│     └─ replay/...
├─ storage
│  ├─ DataPathResolver.java
│  ├─ SqliteConnectionProvider.java
│  ├─ SchemaVerifier.java
│  ├─ TransactionRunner.java
│  └─ DataDirectoryLock.java
├─ web
│  ├─ config/...
│  ├─ controller/...
│  ├─ auth/...
│  ├─ learning/...
│  ├─ photo/...
│  ├─ arena/...
│  └─ minigame/...
└─ observability
   ├─ WebAllMetrics.java
   ├─ TraceEnricher.java
   └─ HealthController.java
```

放置规则：

- `api`：跨领域可见接口和稳定 DTO；不得返回可变 Table/User 内部对象。
- `application`：流程编排、事务边界、权限前置检查；不放牌型算法。
- `domain`：业务状态和规则；不得 import Spring Controller、WebSocket、TCP 或 Repository 实现。
- `infrastructure`：SQLite、文件、回放等外部资源实现；依赖方向指向 domain/api。
- `web/controller`：只做 HTTP 参数校验、身份解析、调用 application service、映射响应。
- `gateway/ws`：只处理 WS 文本协议和连接生命周期；游戏状态修改必须提交到 dispatcher。
- `runtime`：唯一线程/调度资源创建位置；其他包不得直接 `Executors.new*` 或 `new Thread`。
- 不追求一次移动全部 230 个类。先修编译，再通过适配接口逐片迁移，保持每次提交可构建。

## 17. 现有类迁移、改造、删除明细

### 17.1 启动与配置

| 当前类/文件 | 操作 | 目标位置/替代 |
|---|---|---|
| `com.cloud.Application` | 重命名并只保留 Boot 入口 | `com.cloud.weball.WebAllApplication` |
| `web/config/AppConfig` | 拆分 Gate Bean 与 MVC 配置；删除 `GateClient` Bean | `config/WebConfiguration` + auth config |
| `application.yml` | 改为 `weball.*` 类型安全命名；移除 gate/center/lobby admin 运行配置 | `WebAllProperties` 等 `@ConfigurationProperties` |
| `GameRuntimeConfig` | 保留热加载语义，去掉全局 `ConfigurationManager` | 注入 `GameProperties` + `RuntimeConfigReloader` |
| `ConfigurationManager` 使用点 | weball 路径全部替换 | Spring 配置对象；旧模块不动 |

### 17.2 Web 与 Gateway

| 当前类 | 操作 | 目标 |
|---|---|---|
| `GameWebSocketHandler` | 保留 action/JSON 外壳，拆出每个 action handler | `gateway.ws` + `gateway.command` |
| `GameWsPushFormatter` | 保留格式；改为接收内部 event/result 或边界 Proto | `gateway.push` |
| `GateClient` | 先以 `GatewayService` 接口替换注入，再删除 | 不保留运行时类 |
| `UserService` | 拆 session 管理与用户查询；不再依赖 Gate | `gateway.session.SessionRegistry` + Lobby API |
| `WebSocketConfig` | 修正包名，注册新 handler | `gateway.ws.WebSocketConfiguration` |
| `CapabilitiesController` | 移除端口 socket 探测 | 查询 `ComponentHealthRegistry` |
| `LobbyAdminClient` | Controller 改调 Lobby API 后删除 | `LobbyAdminService` |
| `ReplayService` | 统一回放根目录和只读查询 | `game.infrastructure.replay` 或 Web 查询 facade |
| `MiniGameWebSocketHandler` | 保留独立业务；使用 runtime 的 mini-game executor | `web.minigame` |

### 17.3 Lobby

| 当前类组 | 操作 | 目标 |
|---|---|---|
| `Lobby.java` | 拆掉 main、TCP、Center、metrics/admin HTTP；保留初始化所需业务 | `DefaultLobbyApplicationService` + lifecycle bean |
| `LobbyAdminHttp` | 将每个 endpoint 逻辑迁到 `LobbyAdminService`；HTTP 外壳删除 | Web Controller 直接调用服务 |
| `LobbyClient`、`ClientProto`、`ConnectProcessor` | 只用于旧 TCP；内部 API 覆盖后删除 | 无 |
| `connect/center/**` | 删除 weball 副本 | 无 |
| `connect/game/**` | 改成订阅 Game 内部事件 | `lobby.application.GameLifecycleSubscriber` |
| `client/handle/role/**` | 抽取 handler 内业务为 Lobby 方法；旧消息解析壳删除 | `LobbyApplicationService` |
| `client/handle/server/**` | 建桌、断线通知改内部调用/事件 | Lobby/Game API 与 event |
| `UserManager` | 去静态单例，变为 session/online repository | `lobby.domain.user.OnlineUserRegistry` |
| Lobby `TableManager` | 改名避免与 Game 冲突 | `RoomTableRegistry` |
| `UserRepository`、`InviteRepository`、`CustomRoomRepository`、`ScoreQueryRepository` | 保留 SQL 行为，注入连接 provider | `lobby.infrastructure.repository` |
| `SqliteDatabase` | 去静态单例，与账号 DB 统一 | `storage.SqliteConnectionProvider` |
| `AdminRobotMatchRules/Pending` | 保留规则，依赖内部 Lobby/Game API | `lobby.domain.room.robot` |

### 17.4 Game

| 当前类组 | 操作 | 目标 |
|---|---|---|
| `Game.java` | 拆 main/TCP/Center/metrics；构造注入 TableManager、runtime、repository、event publisher | Game lifecycle + application service |
| `GameClient`、`ClientProto` | 内部 API 接通后删除 | 无 |
| `client/handle/role/**` | 保留校验与业务语义，改成 command handler 或 Game API 方法 | `game.application` |
| `client/handle/server/**` | 建桌/查桌改 Lobby → Game 直接调用；断线改 session event | `GameApplicationService` |
| Game `TableManager` | 去 `Game.getInstance()`，注入 executor/repository/events | `game.domain.table.TableRegistry` |
| `GameThreadPoolManager` | 能力保留，创建职责移给 `RuntimeExecutors` | `runtime.SerialTaskDispatcher` + facade |
| `DatabaseExecutorManager` | 删除重复创建线程构造器，只接收 runtime executor | `storage` 写任务 facade |
| `ScoreRepository` | 去静态 singleton/initialize，构造注入 DB 与 executor | `game.infrastructure.repository` |
| `ReplayRecorder` 体系 | 保留格式与策略，注入绝对目录和 IO executor | `game.infrastructure.replay` |
| `Table`、`TableUser`、state 类 | 去 `Game.getInstance()`、Gate Client；注入 `TableRuntime`/event sink | `game.domain.table` |
| `mj/ddz/pdk/tractor` 规则与 AI | 原逻辑优先保留，只改依赖边界；纯规则补 TDD | `game.domain.<play>` |

### 17.5 Web 其他功能

- `learning/**`：修正包名后整体保留；文件存储通过 `DataPathResolver`；`@Scheduled` 任务登记到统一任务清单。
- `photo/**`：整体保留；上传处理提交 `photoIoExecutor`；定时清理登记；不得占用牌桌 executor。
- `arena/**`：整体保留；账号/库存 DB 接统一 connection provider 或明确独立 DB。
- `minigame/**`：整体保留；房间状态和 WS session 独立于牌桌 session，但共享统一资源注册/关闭。
- `ShellService`：保留现有权限边界；部署验收确认 weball 运行用户和命令白名单无放宽。

## 18. 统一线程池与任务处理设计

### 18.1 “统一”的定义

统一指：一个组件负责创建、配置、命名、监控、拒绝、关闭全部执行器；不是把所有任务塞进同一个线程池。牌桌、DB、图片等互相阻塞风险不同，必须物理隔离。

新增 `RuntimeExecutors`，成为唯一 executor factory/owner。业务类只注入能力接口：

```java
public interface SerialTaskDispatcher {
    CompletableFuture<Void> submitTable(long tableId, Runnable task);
    <T> CompletableFuture<T> submitTable(long tableId, Callable<T> task);
    CompletableFuture<Void> submitUser(long userId, Runnable task);
}

public interface BackgroundTaskExecutor {
    CompletableFuture<Void> submitDbWrite(String taskName, Runnable task);
    CompletableFuture<Void> submitFileIo(String taskName, Runnable task);
}
```

禁止业务代码调用 `RuntimeExecutors` 的原生 pool getter，避免绕过路由和指标。

### 18.2 执行器清单

| 执行器 | 默认线程 | 队列 | 任务 | 串行键 | 满载策略 |
|---|---:|---:|---|---|---|
| `tableExecutor` | `max(4, CPU)`，设上限 | 10,000 起，压测校准 | 入桌、操作、发牌、结算、桌 tick、离桌 | `tableId` | 拒绝新业务请求并返回繁忙；内部结算/清理不得丢 |
| `userExecutor` | `max(2, CPU/2)` | 5,000 | 登录态、重复登录、无 tableId 的用户任务 | `userId` | 短时反压；禁止 WS I/O 线程长时间 CallerRuns |
| `tableAdminExecutor` | 1 | 1,000 | 建桌索引、销毁、恢复、全局桌列表 | 单队列 | 拒绝外部管理请求，记录告警 |
| `dbWriteExecutor` | 1～2 | 2,000 | SQLite 写、战绩、邀请、账号状态 | DB/事务 | 明确失败，不在牌桌线程重试死循环 |
| `fileIoExecutor` | 2 | 1,000 | 回放、学习文件、轻量文件操作 | 无 | 返回失败/延迟，不阻塞牌桌 |
| `photoIoExecutor` | 2 或配置 | 100 | 图片解码、缩略图、归档 | taskId | 上传 API 返回排队满，不占用通用 IO |
| `scheduler` | 2 | 延迟队列 | 只触发定时任务 | taskName/tableId | 不执行耗时业务，只投递目标池 |
| `miniGameExecutor` | 1～2 | 1,000 | 五子棋/象棋房间操作 | miniRoomId | 返回繁忙 |

线程数不直接使用现有 Game 默认最少 32。首次默认值依据主机 CPU/内存基线设置，并允许 `weball.runtime.executors.*` 覆盖。

### 18.3 复用 `utils.ExecutorPool`

- `tableExecutor`、`userExecutor` 可包装 `ExecutorPool`，利用 `Task.groupId()` 保证同 key 串行。
- long key 使用 `Long.hashCode(id)` 仅决定分片；真正顺序由同一 ID 稳定映射保证。必须测试不同 long ID hash 冲突时正确性：冲突只降低并行度，不能破坏顺序。
- 当前 `ThreadPool` 使用有界队列和 `CallerRunsPolicy`。对 WebSocket I/O 提交线程，CallerRuns 可能把耗时桌任务拉回 I/O 线程；因此 weball wrapper 必须在提交前检测容量，或给 `utils.ThreadPool` 增加可配置 rejection policy。若修改 utils，先补旧模块回归测试。
- 关闭时调用 `ExecutorPool.shutdown()`，等待配置时间；超时后才 `shutdownNow()`，并记录未完成任务数量。当前 `GameThreadPoolManager.shutdown()` 直接全部 `shutdownNow()`，需改。

### 18.4 桌任务处理流程

```text
WS I/O thread
  -> 参数/身份校验
  -> GatewayCommandRouter
  -> GameApplicationService
  -> submitTable(tableId, command)
  -> 同桌串行队列
  -> 读取/修改 Table
  -> 生成 GameEvent
  -> 提交 DB/Replay 异步任务（必要结果有 future）
  -> GameEventPublisher
  -> SessionPushService
  -> WS send
```

规则：

- WS I/O 线程不直接改 Table、不执行 AI 搜索、不写 DB/文件。
- 所有能改变桌状态的方法只能从 `tableExecutor` 调用；增加线程断言或测试守卫。
- 建桌先经 `tableAdminExecutor` 分配 ID/登记，再向 `tableExecutor` 注册；失败逆序撤销。
- 销毁先标记 `CLOSING`，拒绝新外部操作，排队执行最终结算/回放，再移除索引和 serial key。
- 桌 tick 由 scheduler 触发，但实际逻辑投回 table key；同桌上一次 tick 未完成则合并/跳过，沿用当前 `tickBusy` 语义。
- 用户从“无桌”进入“有桌”时，先在 user key 完成身份/重复登录检查，再提交 table key；不得同时持有两种同步锁。

### 18.5 DB、回放和图片任务

- SQLite 写按明确事务提交到 `dbWriteExecutor`。同一 DB 默认单写线程；确认 WAL 后再评估 2 线程。
- 牌局结算分两类结果：业务结算先在桌线程完成；战绩/回放持久化状态返回 `pending/saved/failed`，不能因异步失败回滚已经广播的牌局状态。
- 需要“保存成功后才能销桌”的任务使用 future + 超时，超时进入 durable retry/告警方案；不能在桌线程同步等待无限期。
- 图片解码必须在 `photoIoExecutor`，同时保留像素、文件大小、任务数上限。
- 学习日报由 scheduler 触发，邮件/文件工作投 `fileIoExecutor`；禁止 Spring 默认 scheduler 执行耗时正文。

### 18.6 定时任务统一

新增 `ScheduledTaskRegistry`：

- 每个任务登记 `name`、owner、首次延迟、周期/cron、目标 executor、是否允许并发、停止方式。
- 牌桌 tick：`game.table.tick.<tableId>`，不允许同桌并发。
- 配置刷新：`game.runtime-config.reload`。
- 离线清理：`gateway.session.cleanup`。
- 学习使用统计：`learning.usage.flush`。
- 学习日报：`learning.daily-report`。
- 图片任务恢复/清理：`photo.upload.reconcile`。
- shutdown 第一步停止产生新 tick，再等待业务队列排空。

迁移期可以保留 `utils.Timer` 适配器，但所有注册必须经过 registry；最终不允许 Lobby/Game 自建 `new Timer()`。

## 19. 内部命令、处理器与事件设计

### 19.1 不复用旧 `HandleManager`

旧 `HandleManager` 以 Proto class 为 key 注册到静态全局 Map，负责 TCP 回包解析。weball 不再走 TCP，继续使用会带来：处理器串域、隐式全局状态、无法明确线程、难以单测。

新增实例级 `GatewayCommandRouter`：

```text
action auth         -> AuthCommandHandler
action enterTable   -> EnterTableCommandHandler
action refreshTable -> RefreshTableCommandHandler
action op           -> OperationCommandHandler
action leave        -> LeaveTableCommandHandler
action heartbeat    -> HeartbeatCommandHandler
```

每个 handler：

1. 校验 action data。
2. 解析 `GatewayRequestContext`。
3. 调用 Lobby/Game API。
4. 将 result 映射为现有 JSON response。
5. 不直接访问 repository、TableManager 或 WebSocket 全局 Map。

### 19.2 请求上下文

新增不可变 `GatewayRequestContext`：

```text
requestId: 服务端生成，用于日志
wsSessionId: Spring WS 连接 ID
sessionId: 浏览器现有会话 ID
userId: 认证后确定
tableId: action 可选字段或 session 绑定
clientSeq: 浏览器 seq，原样回包
receivedAt: 接收时间
remoteAddress: 安全审计需要时保存
```

上下文进入异步线程前显式传递；不依赖 ThreadLocal 自动跨线程。执行 task 时设置 TraceContext，finally 清理。

### 19.3 Game API 最小方法集

```java
CompletableFuture<CreateTableResult> createTable(CreateTableCommand command);
CompletableFuture<EnterTableResult> enterTable(EnterTableCommand command);
CompletableFuture<TableSnapshot> snapshot(TableSnapshotQuery query);
CompletableFuture<OperationResult> operate(OperationCommand command);
CompletableFuture<LeaveTableResult> leave(LeaveTableCommand command);
CompletableFuture<Void> heartbeat(TableHeartbeatCommand command);
CompletableFuture<List<TableSummary>> listTables(RoomTablesQuery query);
CompletableFuture<Void> disconnectPlayer(PlayerDisconnected command);
```

DTO 必须含原链路所需字段；字段清单从 Proto/WS 矩阵生成。内部错误统一为稳定 error enum，再映射旧 `ConstProto.Result`/HTTP code/WS msg。

### 19.4 Lobby API 最小方法集

```java
LoginResult login(LoginCommand command);
RegisterResult register(RegisterCommand command);
Optional<OnlineUserView> findOnlineUser(long userId);
RoomListResult listRooms(RoomListQuery query);
CompletableFuture<JoinRoomResult> joinRoom(JoinRoomCommand command);
CompletableFuture<CreateRoomResult> createCustomRoom(CreateRoomCommand command);
void onTableCreated(TableCreatedEvent event);
void onTableDestroyed(TableDestroyedEvent event);
void onTablePlayerLeft(TablePlayerLeftEvent event);
```

管理接口单独放 `LobbyAdminService`，避免普通 Lobby API 暴露启停用户、邀请码、Shell 等权限能力。

### 19.5 事件类型

Game 只发布领域事件，不持有 WebSocket/Gate/Lobby client：

- `PlayerEnteredTableEvent`
- `CardsDealtEvent`
- `OperationAvailableEvent`
- `OperationAppliedEvent`
- `TableStateChangedEvent`
- `RoundSettledEvent`
- `GameSettledEvent`
- `TableSnapshotChangedEvent`
- `PlayerLeftTableEvent`
- `TableDestroyedEvent`
- `PersistenceFailedEvent`

事件发布策略：

- 同桌事件保持生成顺序；event 带 `tableEventSeq`，不能只依赖线程到达顺序。
- Lobby 订阅桌生命周期事件，更新房间/桌索引。
- Gateway 订阅需要推送的事件，按 userIds 或 tableId 解析目标 session。
- 指标/审计订阅不得阻塞桌线程。关键订阅失败记录并进入补偿；普通指标失败不得影响牌局。
- 第一版使用进程内同步 publisher + 明确异步出口即可，不引入 Kafka/RabbitMQ。

## 20. 存储统一设计

### 20.1 数据源与目录

启动时 `DataPathResolver` 解析并冻结：

```text
dataRoot
accountDb / lobbyDb / arenaDb（确认后可指向同一 SQLite 文件）
learningDataDir
learningResourceDir
photoArchiveDir
photoThumbnailDir
photoCacheDir
photoStagingDir
replayDir
logDir
```

- 配置允许相对路径，但解析基准固定为显式 `weball.home`，不是 JVM 当前工作目录。
- 日志打印规范化绝对路径、可写性、剩余空间；不打印密码/token。
- `DataDirectoryLock` 对写入根目录加模式锁。旧模式持锁时 weball 拒绝 ready；weball 持锁时旧模式切换脚本应拒绝启动写服务。

### 20.2 SQLite 统一

- 先比较 `AccountDatabase` 与 Lobby `SqliteDatabase` 建表 SQL、字段、索引、密码算法。
- 确认同一 `data/lobby.db` 后，只保留一个 `SqliteConnectionProvider`；Repository 不自行拼 JDBC URL。
- `TransactionRunner` 提供 `read`、`write`、`inTransaction`，统一 busy timeout、WAL、foreign keys 和 rollback。
- Repository 返回 domain/entity DTO，不返回 ResultSet/Connection。
- 禁止启动时自动覆盖或重建表。`SchemaVerifier` 只核验；迁移另建显式版本脚本。
- 默认账号/邀请码 seed 必须幂等，且生产环境可关闭；当前硬编码 `admin/admin123` 必须纳入安全清单，不能在新部署静默创建弱口令。

### 20.3 持久化 owner

| 数据 | 唯一写 owner | 读者 |
|---|---|---|
| 账号、密码、enabled、admin | Lobby identity repository | Web auth、后台、学习 auth bridge |
| 在线 session | Gateway SessionRegistry（内存） | Lobby/Game/Gateway |
| 房间/自定义房 | Lobby repository | Web、Gateway |
| 活跃桌状态 | Game TableRegistry（内存） | Game、Lobby view、后台 |
| 分数/战绩 | Game score repository | Lobby/Web 后台 |
| 回放 | Game replay repository | Web ReplayController |
| 学习 | Learning service/repository | Learning Controller/Admin |
| 图片 | PhotoRepository/PhotoService | Photo Controller/Admin |
| 竞技场库存/进度 | Arena repository | Arena Controller/Admin |

## 21. 生命周期、健康状态与关闭顺序

### 21.1 启动顺序

```text
1. validateConfig
2. resolveAndLockDataPaths
3. verifySchemas
4. createRuntimeExecutors
5. initializeRepositories
6. initializeLobbyDomain
7. initializeGameDomain
8. initializeGatewayAndSessionRegistry
9. registerScheduledTasks
10. markInternalComponentsReady
11. accept HTTP/WS traffic
```

Spring Web 容器可能先绑定端口，因此 auth/game Controller 在内部未 ready 时必须返回明确 `503/degraded`，不能假装可用。

每步注册 rollback action。第 N 步失败，按 N→1 逆序释放；不得 `System.exit()`。

### 21.2 健康状态

组件状态：`NEW → STARTING → READY → DEGRADED → STOPPING → STOPPED/FAILED`。

`ComponentHealthRegistry` 至少维护：storage、lobby、game、gateway、learning、photo、arena、scheduler。Capabilities 对外字段可保持旧格式，但来源改为此 registry。

ready 条件：

- data lock 成功；Schema 可用。
- executor 创建成功且未 shutdown。
- Lobby/Game repository 初始化完成。
- Gateway 能解析所有要求 action。
- 必需定时任务已登记。
- 不要求探测 5400/5500/5600/5701。

### 21.3 关闭顺序

```text
1. readiness=false，拒绝新登录/建桌/操作
2. 停止 scheduler 新触发
3. 关闭新 WS 接入，通知/断开现有 session
4. 等待 table/user 队列到安全点
5. 保存必要战绩/回放
6. 停止 Lobby/Game domain
7. drain DB/file/photo queues
8. 关闭 executors
9. 关闭 DB connections
10. 释放 data lock
```

每步有独立超时和日志。超时进入下一步时记录未完成 task 名称/数量；仅最终阶段允许强制中断。

## 22. 详细实施批次与提交边界

为减少 230 个文件同时改动，按以下批次执行。每批必须可编译或明确记录临时失败原因；默认一批一个提交。

### 批次 A：恢复可编译骨架（2～3 人日）

1. 固定当前失败日志为基线。
2. 把 32 个 Lobby package 改到一致根包，修对应 import。
3. 分目录修 66 个文件的旧 import：先 Web core，再 learning/photo/arena，最后 Game/Lobby。
4. 补缺失 Spring annotation import，处理因机械包名替换暴露的真实错误。
5. 构建通过后只启动静态首页/API 冒烟；此时仍不得宣称一体化完成。

涉及：现有包声明/import、POM。暂不改规则逻辑。

### 批次 B：建立新骨架和边界（2～4 人日）

1. 新增 `bootstrap/config/runtime/storage` 基础类。
2. 新增 Game/Lobby API、command/result DTO，仅定义契约与测试。
3. 新增 legacy adapters，使现有代码能经接口调用；暂不删除 TCP 类。
4. 增加 ArchUnit 或简单依赖检查：domain 禁止 import `org.springframework.web`、`net.*`、`tools.Server*`。

### 批次 C：配置、DB、资源统一（4～7 人日）

1. `@ConfigurationProperties` + 路径解析。
2. 合并 SQLite provider、schema verification、事务 runner。
3. 接入 `RuntimeExecutors`、任务 registry 和健康状态。
4. 替换 Game/Lobby 自建线程与定时器的创建位置；保持任务执行语义。

### 批次 D：Lobby 去网络（5～8 人日）

1. handler 逻辑提取到 application service。
2. Controller 替换 `LobbyAdminClient`。
3. Lobby → Game 改 API；Game → Lobby 改事件。
4. 5700/5701、Center 注册类退出 weball 装配。
5. 登录、注册、房间、邀请、后台集成测试。

### 批次 E：Game 去网络（8～13 人日）

1. TableManager/ThreadRuntime/Repository/EventPublisher 注入。
2. role/server handler 逐个映射为 Game API。
3. Table/TableUser 广播改 GameEvent。
4. 四玩法按 TDD/完整局逐个验证。
5. 5500、Center、Gate/Lobby client 退出 weball 装配。

### 批次 F：Gateway 切流（8～12 人日）

按 action 分 6 个小提交：auth、enterTable、refreshTable、op、leave、heartbeat。每次：

1. 固定旧契约测试。
2. 新 command handler 调内部 API。
3. 新旧实现对比结果。
4. 删除该 action 对 GateClient 的依赖。
5. 补成功、业务失败、异常、超时/取消、重复请求测试。

全部 action 完成后删除 `GateClient`，再实现主动推送和断线恢复全链路。

### 批次 G：非牌桌 Web 功能（4～7 人日）

按 learning、photo、minigame、arena、admin/replay 分提交；每项同时验证静态响应、JS、API、日志和资源关闭。

### 批次 H：验收与部署（10～15 人日）

执行阶段 7～8；补 ops、数据锁、基线对比、故障注入和回退演练。

## 23. 禁止项与代码审查检查表

新增/修改代码不得出现：

- weball POM 依赖 center/gate/lobby/game/web。
- `com.cloud.weball` 业务代码新建 `ServerManager`、`ServerClientManager`、`GateClient` 或 TCP server/client。
- 新增 `System.exit()`。
- 新增业务 singleton `getInstance()`。
- runtime 包外新增 `Executors.new*`、`new Thread`、`new Timer`。
- Controller/WS handler 直接访问 Repository 或 TableManager。
- Table/domain 类直接发送 WebSocket/TCP。
- DB/文件/AI 耗时任务运行在 WS I/O 线程。
- 相对路径直接传给 JDBC/File API，未经过 `DataPathResolver`。
- 异步 callback 丢失 requestId/userId/tableId trace。
- 任务队列无上限、无拒绝策略、无关闭入口。

每次评审检查：

- 是否只改计划中的一个边界。
- 是否保持旧模块构建。
- 是否有失败测试再实现核心规则。
- 是否验证正常、错误、断线/取消和关闭路径。
- 是否新增线程、端口、DB 连接或定时任务；若有，owner 表是否更新。
- 是否有数据格式/Schema 变化；若有，迁移和回退是否齐全。
- 是否以实际 Java 进程、端口、当次请求和同期日志支撑运行结论。

计划规模：9 个阶段，约 100 个验收项。阶段 0～2 是基础；阶段 3～5 是主要重构；阶段 6～8 是完整功能、验证和交付。不得为了快速看到单 JAR 而跳过功能矩阵、数据保护和并发验证。

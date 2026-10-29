# 全项目消息处理重构与 Hub 优化全量同步执行计划

> **目标**：彻底删除 `MsgRouter` 与 `@Msg` 反射机制，全模块统一采用 `HandlerRegistry` 自动扫描 + `implements Handler` + `@ProcessType(values = {...})` 原生直接调用；并将 `hub` 内所有架构优化（领域分包、`TableHandlerHelper`、牛牛完整玩法、透视工具、回放治理等）全量同步至各拆分服务。
> **追踪规范**：做完一项即刻将状态由 `[ ]` 更新为 `[x]`，分步骤彻底做完，绝不留半拉子结果。

---

## 阶段一：底座瘦身与基础规范统一（`utils` 模块）
- [x] **Task 1.1**：增强 `HandlerRegistry` 的独立反序列化能力，在内部建立基于 `ConcurrentHashMap<Integer, Parser<?>>` 的自给自足解析器缓存，解除对 `MsgRouter` 的任何依赖。
- [x] **Task 1.2**：彻底删除 `utils/src/main/java/net/msg/` 目录下的所有文件：
  - `net.msg.MsgRouter.java`
  - `net.msg.Msg.java`
  - `net.msg.MsgContext.java`
  - 以及测试用例 `utils/src/test/java/net/msg/MsgRouterTest.java`。
- [x] **Task 1.3**：清理 `ClientHandler.java`、`WsClientHandler.java`、`ConnectHandler.java` 中的 `instanceof MsgRouter` 特判逻辑，统一走原生 `executeMessageHandler` 分发。

---

## 阶段二：中心服务（`center`）与大厅服务（`lobby`）重构
- [x] **Task 2.1【Center】**：重构 `center.client.handle` 下的 4 个处理器（`NotBreakHandle`、`NotClientLinkHandle`、`ReqRegisterHandle`、`ReqServerInfoHandle`），改为 `implements Handler` + `@ProcessType`。
- [x] **Task 2.2【Center】**：重构 `center.client.ClientProto`，废除 `MsgRouter`，改用 `HandlerRegistry.bind(HANDLER_MAP)` 配合 `TracedHandler` 自动装配。
- [x] **Task 2.3【Lobby】**：重构 `lobby.client.handle.role` 与 `server` 下的 7 个处理器（`ReqJoinTableHandle`、`ReqLoginHandler`、`ReqRegisterHandler`、`ReqRoomListHandle`、`CreateTableHandler` 等），改为 `implements Handler` + `@ProcessType`。
- [x] **Task 2.4【Lobby】**：重构 `lobby.client.ClientProto`，改用 `HandlerRegistry.bind(HANDLER_MAP)` 自动装配。
- [x] **Task 2.5【Lobby】**：同步 `hub` 中关于 `LobbyTableManager` 与 `UserManager` 的桌子分配、匹配与状态流转优化。

---

## 阶段三：网关服务（`gate`）透传重构与零反射改造
- [x] **Task 3.1【Gate】**：重构 `gate.connect.GateForwardController` 为标准的 `GateForwardHandler implements Handler`，使用 `@ProcessType(values = {GMsg.NOT_CARD, GMsg.NOT_OP, ...})` 批量绑定 9 个透传消息，彻底消除方法反射。
- [x] **Task 3.2【Gate】**：清理 `gate.connect.ConnectProcessor` 中持有的 `FORWARD_ROUTER` 及其关联引用，改为原生 `HANDLER_MAP` 直接调用。
- [x] **Task 3.3【Gate】**：重构 `gate.client.handle.ReqRegisterHandle` 等其它 Handler，确保全网关无任何 `@Msg` 残留。

---

## 阶段四：游戏服务（`game`）消息处理与 Hub 优化全量同步（核心主干）
- [x] **Task 4.1【消息处理】**：重构 `game.client.handle.role` 与 `server` 下全部 9 个 Handler 类，实现 `Handler` 接口并标注 `@ProcessType`；更新 `game.client.ClientProto` 使用 `HandlerRegistry.bind`。
- [x] **Task 4.2【调度模板】**：将 `hub` 的核心调度门面 `TableHandlerHelper.java` 同步到 `game.handle`，接入所有对局操作类（自动判空回包、切入桌单线程队列、统一异常处理与 sequence 闭环）。
- [x] **Task 4.3【Domain 领域分包同步】**：
  - 同步 `domain/cards/`（公共卡牌及工具类：`Card`、`CardConst`、`CardOps`、`CardSuit`、`CardFormatter`）；
  - 同步重构 `domain/poker/ddz/`（斗地主业务拆分与 AI 模块）；
  - 同步重构 `domain/poker/pdk/`（跑得快业务拆分与 AI 模块）；
  - 同步重构 `domain/poker/tractor/`（拖拉机/升级业务与 AI）；
  - 同步重构 `domain/mj/`（麻将按 `claim`、`jm`、`kw`、`rule`、`service`、`state` 领域深度拆分）；
  - 同步 `domain/replay/`（回放目录治理 `ReplayDirectories` 与清理 `ReplayRetention`）；
  - 同步 `domain/state/`（状态机流转与 `TableStateHandleManager` 自动扫描）；
  - 同步 `domain/table/`（统一桌子与用户模型）。
- [x] **Task 4.4【新玩法同步】**：完整同步 `domain/poker/niuniu/`（牛牛对局流转、牌型算法、规则计算、发牌池与智能 AI 全套系统）。
- [x] **Task 4.5【辅助与系统扩展同步】**：
  - 同步 `inspector/` 牌局透视与作弊调试工具（`TableInspectorService`、`MjTileCheatService` 等）；
  - 同步 `arena/` 竞技场与进阶系统（`crafting`、`journey`）。

---

## 阶段五：工程清理、编译验证与集成测试
- [x] **Task 5.1**：全工程搜索校验，确保无任何 `net.msg`、`MsgRouter`、`@Msg`、`MsgContext` 的残留引用。
- [x] **Task 5.2**：全模块 Maven 打包编译：`mvn clean package -DskipTests`，保证所有模块（hub、game、lobby、gate、center、utils、tool、proto、web、servertool）零报错编译通过。
- [x] **Task 5.3**：启动各服务核心测试，验证 HandlerRegistry 扫描条数、消息分发与协议解析的正确性（utils 测试与 game 测试全量通过）。


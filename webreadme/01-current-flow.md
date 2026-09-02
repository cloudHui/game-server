# Web 完整交互流程与代码导航

> 本文对应仓库当前 `main` 分支源码，重点回答“浏览器进来以后，请求到哪里、经过哪个 JS 哪一行、后端走哪个类、最终如何到 Lobby/Game，再怎样回到页面”。行号是阅读入口；代码增删后可能漂移，排查时应同时按本文给出的类名、方法名或 `action/msgId` 搜索。

本文的重点是 **Web 前端本身**：HTML 如何组成页面，CSS 如何控制外观，JS 如何找到和修改 HTML，按钮如何调用函数，页面如何用 HTTP/WebSocket 请求数据，以及刷新、重连和心跳分别怎样实现。Lobby/Game/Center 只作为 Web 请求的边界说明。

## 0. 先建立 Web 的基本概念

这个项目的页面不是 React 工程，也没有 npm 打包步骤。绝大多数页面是浏览器直接加载的传统三件套：

```text
HTML = 页面上“有什么”（标题、按钮、牌区、弹窗）
CSS  = 这些东西“长什么样、放在哪里”（颜色、尺寸、布局、动画）
JS   = 这些东西“怎样动、怎样请求服务器”（点击、渲染、fetch、WebSocket）
```

可以把一个页面理解成下面的启动顺序：

```text
浏览器 GET xxx.html
  → 从上到下解析 HTML
  → 遇到 <link> 下载 CSS
  → 遇到 <script> 下载并立即执行 JS（没有 defer 时）
  → 构造 DOM 树
  → 最后几个业务 JS 找到 DOM、绑定行为、发起请求
  → 响应回来后 JS 修改 DOM，浏览器自动重绘
```

### 0.1 用大厅页读懂一个完整 HTML

`web/src/main/resources/static/pages/lobby/index.html` 是最适合入门的页面：

- `:1` 的 `<!DOCTYPE html>` 告诉浏览器使用现代 HTML；
- `:2` 的 `<html lang="zh-CN">` 是整个文档根节点；
- `:3-9` 是 `<head>`，放编码、手机 viewport、标题、公共 JS 和 CSS；
- `:10-48` 是 `<body>`，放用户真正看见的内容；
- `:11-18` 的 `<header>` 是顶部栏；
- `:19-46` 的 `<main>` 是主体；
- `:28-45` 用 `<section>` 包两个 `<article>` 卡片；
- `:47` 在 DOM 已经生成之后加载 `session.js`。

这里有三种最常见的 HTML 属性：

```html
<span id="userDisplay"></span>
<a class="game-btn ent" href="./entertainment.html">进入娱乐</a>
<button onclick="logout()">退出</button>
```

- `id` 应在页面内唯一，JS 用 `document.getElementById('userDisplay')` 精确找到它；
- `class` 可以重复，主要给 CSS 选择器使用，也可以由 JS 批量查找/切换；
- `href` 是普通页面跳转；
- `onclick="logout()"` 表示点击时调用全局 JS 函数。

对应关系在 `pages/lobby/session.js` 中非常直观：`:9-12` 找到两个 id 后写入 `textContent`；`:17-26` 把 `logout` 挂到 `window`，所以 HTML 的 `onclick` 能调用它。

### 0.2 DOM 是 HTML 在 JS 中的对象形式

浏览器解析 HTML 后，每个标签都是一个 DOM 对象。项目里最常见的操作是：

```js
// 找一个节点
var el = document.getElementById('tableState');

// 只改文字，安全且不会解析 HTML
el.textContent = '游戏进行中';

// 填入一段标签；内容来自用户时必须防止 XSS
document.getElementById('actionBar').innerHTML = '<button>出牌</button>';

// 改内联样式
el.style.display = 'none';

// 切换 CSS class，让 CSS 决定最终样式
el.classList.toggle('is-waiting', true);

// 动态创建节点
var button = document.createElement('button');
button.textContent = '创建房间';
button.onclick = function () { /* ... */ };
parent.appendChild(button);
```

真实例子：`shared/room-page.js:93-147` 用 `createElement/appendChild` 从房间 JSON 动态创建整张房间卡；`shared/capabilities-poll.js:8-29` 用 `classList.toggle()` 和动态 `<span>` 把不可用入口变成“敬请期待”。

### 0.3 `<script>` 的顺序为什么很重要

斗地主 HTML 在 `pages/games/doudizhu/index.html:11-15,85-87` 依次加载：

```text
app-base.js       定义 appUrl、AppQuality
game-table.js     定义 GameTable
game-landscape.js 定义横屏布局工具
poker-card.js     定义牌值/牌面工具
poker-view.js     定义扑克公共渲染
doudizhu-view.js  定义斗地主渲染函数
doudizhu-op.js    定义操作和推送处理函数
doudizhu.js       最后组装状态、WS，并调用前面的函数
```

这些不是 ES module，而是共享同一个 `window` 全局作用域。后加载的文件可以直接使用前面定义的 `GameTable`、`renderMyCards()` 等。如果把 `doudizhu.js` 放到 `game-table.js` 前面，就会出现 `GameTable is not defined`。

麻将、跑得快、拖拉机也是同一模式，具体加载顺序见第 6.1 节。

### 0.4 页面跳转和“局部刷新”不是一回事

项目有三种看起来都像“刷新”的行为：

1. **页面跳转/整页加载**：给 `window.location.href` 赋值，浏览器重新 GET 一个 HTML，例如首页登录成功后 `index.html:634-636` 跳大厅。
2. **HTTP 拉数据后重画局部 DOM**：页面不换，`fetch('/api/rooms')` 后调用 `renderRooms()`，见 `room-page.js:62-91`。
3. **牌桌权威快照刷新**：仍不换页，通过 WebSocket 发送 `refreshTable`，拿完整桌状态后重新渲染，见第 7 节。

浏览器工具栏的刷新按钮属于第 1 类：所有 JS 内存变量会清空，然后从 localStorage 重新取 session/table 信息、重新连 WebSocket、重新入桌。

## 1. 一张图看懂整体架构

```text
外网浏览器
  │ HTTPS / WSS（外网可能带 /{随机路径} 前缀）
  ▼
Nginx / 反向代理
  │ HTTP / WS，转发到 127.0.0.1:8081
  ▼
Web.jar（Spring Boot）
  ├─ 静态页面：index.html、pages/**、shared/**
  ├─ HTTP API：登录、房间、管理、学习、回放
  ├─ /ws/game：JSON ⇄ Protobuf 的牌桌桥接器
  ├─ /ws/mini：五子棋/象棋，逻辑直接运行在 Web 进程
  ├─ SQLite：data/lobby.db（账号，与 Lobby 共用）
  ├─ 学习数据：data/learning/**
  ├─ HTTP → Lobby Admin：127.0.0.1:5701（管理/自定义房）
  └─ TCP → Gate：127.0.0.1:5600（每个 Web session 一条连接）
                      │
                      ├─ Lobby：登录态绑定、房间列表、加入/创建桌
                      │
                      └─ Game：入桌、准备、出牌、状态和结算推送
```

Web 的监听配置在 `web/src/main/resources/application.yml:1-5`：默认绑定 `127.0.0.1:8081`。Gate 地址在同文件 `:26-28`，Lobby 管理 HTTP 在 `:30-31`。这说明浏览器通常不直接访问 Gate、Lobby 或 Game；浏览器只面对 Web/Nginx。

## 2. URL 前缀与静态页面是怎样工作的

### 2.1 外网随机 context path

所有主页面先加载 `web/src/main/resources/static/shared/app-base.js`。它在 `:3-8` 从当前 URL 第一段推导 `APP_BASE`，在 `:11-19` 暴露 `appUrl(path)`。

例如外网页面是：

```text
https://example.com/Baa3SVlpDCyPh9T9v0/pages/lobby/index.html
```

则 `appUrl('/api/rooms')` 得到：

```text
/Baa3SVlpDCyPh9T9v0/api/rooms
```

本机直接访问 `/pages/...` 时，前缀为空。前端因此不应硬编码 `/api/...`，应统一调用 `appUrl(...)`。

`app-base.js:141-163` 还包装了 `fetch/XMLHttpRequest/WebSocket.send`，配合 `:165-187` 实现只对真实服务端交互生效的 3 秒防连点。它不会改变请求体或响应，只记录按钮是否触发了网络交互。

### 2.2 Spring 静态资源和登录拦截

Spring Boot 自动把 `web/src/main/resources/static/` 映射到站点根路径，因此：

| URL | 源文件 |
|---|---|
| `/` 或 `/index.html` | `static/index.html` |
| `/pages/lobby/index.html` | `static/pages/lobby/index.html` |
| `/pages/games/mahjong/rooms.html` | 对应的 `rooms.html` |
| `/shared/game-table.js` | `static/shared/game-table.js` |

`web.config.AppConfig.addInterceptors()` 在 `web/src/main/java/web/config/AppConfig.java:38-46` 对 `/**` 注册 `AuthInterceptor`，只排除登录页、登录 API、能力探测、公共脚本/样式和 WebSocket 等入口。

`web.config.AuthInterceptor.preHandle()` 在 `AuthInterceptor.java:25-41`：

1. 由 `SessionResolver` 从 cookie、参数或请求头解析 session；
2. 用 `UserService.getSession()` 检查 Web 进程内存会话；
3. 未登录访问 `/api/**` 返回 HTTP 401 JSON；
4. 未登录访问页面则 302 回 `/`。

注意：`localStorage` 只是前端保存信息；真正通过拦截器的是 HttpOnly `sessionId` cookie 或显式 session 参数。两者不可混为一谈。

## 3. 从打开首页到登录成功

### 3.0 先学会读项目里的 `fetch`

`fetch` 是浏览器发 HTTP 请求的标准 API。以登录为例（`index.html:446-450`）：

```js
fetch(appUrl('/api/auth/login'), {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({username: username, password: password})
})
```

逐项解释：

- `appUrl(...)` 补外网路径前缀；
- `method: 'POST'` 表示提交数据；不写时默认 GET；
- `Content-Type` 告诉 Java 请求体是 JSON；
- JS 对象不能直接当 HTTP body，先用 `JSON.stringify()` 转字符串；
- `fetch` 返回 Promise，不会立刻得到业务数据。

后面 `index.html:451-467` 的 Promise 链表示：

```js
.then(r => r.json())       // HTTP 响应体 JSON 字符串 → JS 对象
.then(data => { ... })     // 读取 data.code/data.msg，更新 DOM 或跳页
.catch(() => { ... })      // DNS、断网、连接失败或前面 then 抛异常
```

一个重要细节：`fetch` 遇到 HTTP 401/500 通常仍会进入 `.then`，只有网络级失败才自动进入 `.catch`。严谨写法应先判断 `r.ok`；学习模块的统一封装 `learning/js/parts/core.js:49-68` 就做了这个判断。

GET 请求参数直接放在 URL：

```js
fetch(appUrl('/api/rooms?sessionId=' + encodeURIComponent(sessionId)))
```

`encodeURIComponent()` 防止参数里的特殊字符破坏 URL。需要 cookie 时浏览器同源请求默认会带；学习代码还明确写了 `credentials:'include'`。

### 3.1 首页初始化

入口页的业务脚本目前直接写在 `static/index.html:359-644`：

1. `window.onload`（`:364-384`）读取 URL 的 `invite`，决定是否显示注册页；
2. 从 `localStorage` 读取 token 和昵称（`:378-383`），有旧 token 就显示“恢复登录”；
3. 点击恢复登录进入 `restoreToken()`（`:386-391`、`:413-433`）；
4. 普通账号密码登录进入 `doLogin()`（`:435-468`）；
5. 邀请注册进入 `doRegister()`（`:470-515`）。

三条路径最终都请求：

```text
POST appUrl('/api/auth/login')
POST appUrl('/api/auth/register')
Content-Type: application/json
```

账号密码登录的 `fetch` 在 `index.html:446-450`，token 恢复在 `:414-418`，注册在 `:488-496`。

### 3.2 Web 后端登录不是先走 Gate

请求先到 `web.controller.AuthController`：

- `POST /api/auth/login`：`AuthController.java:30-53`；
- `POST /api/auth/register`：`:58-84`；
- 成功响应与 cookie：`:86-108`。

登录调用 `UserService.login()`（`web/service/UserService.java:44-50`），它直接调用 `AccountService.authenticate()` 查询 Web 与 Lobby 共用的 `data/lobby.db`。注册同理，走 `UserService.register()`（`:52-64`）。账号库路径配置见 `application.yml:42-45`。

成功后 `UserService.storeSession()`（`:83-110`）建立三类内存索引：

```text
sessionId -> UserInfo
token     -> UserInfo
userId    -> 当前 sessionId
```

同一用户新登录会在 `UserService.java:97-102` 移除旧 Web 会话，并关闭旧的 Gate TCP 连接。

`AuthController.withSessionCookie()`（`:86-90`）同时：

- 设置 HttpOnly、SameSite=Lax 的 `sessionId` cookie；
- JSON 返回 `sessionId/userId/username/nickname/token/tables/tableInfos/isAdmin`。

### 3.3 浏览器保存结果并选择去向

`index.html:onAuthSuccess()` 在 `:517-534` 把响应写入 `localStorage`：

```text
sessionId, userId, username, nickname, token, isAdmin
```

随后：

- 有 `tableInfos`：`:536-558` 展示未结束牌桌；
- 只有旧格式 `tables`：`:560-578` 展示桌号；
- 无未结束牌桌：`:634-636` 跳到 `/pages/lobby/index.html`；
- 选择牌桌：`:585-595` 或 `:597-631` 写入 `tableId/roomId/gameType` 并跳到具体玩法页。

这里返回的初始 `tables/tableInfos` 通常来自账号在本 Web 会话中的信息；实际房间状态仍需后续 `/api/rooms` 或牌桌快照确认。

## 4. 大厅到房间列表

大厅页面只是导航层。进入具体玩法的 `rooms.html` 后，四种玩法共用 `static/shared/room-page.js`。HTML 用 `body[data-game-type]` 告诉公共脚本玩法：麻将=1、斗地主=2、跑得快=3、拖拉机=4；脚本装载点分别在四个 `rooms.html:25`。

### 4.1 浏览器请求

`room-page.js:6-14` 读取 `sessionId` 和玩法元数据，`:62-91` 执行：

```text
GET /api/rooms?sessionId={sessionId}
```

响应后 `:78-85` 按 `gameType` 和官方 roomId 过滤、排序；`:93-147` 渲染模板、现有桌、人数和“返回房间/创建房间”按钮。

### 4.2 Web → Gate → Lobby 的第一次完整链路

HTTP 请求到 `RoomController.getRooms()`（`web/controller/RoomController.java:40-101`）：

1. `:41-45` 校验 Web 内存 session；
2. `:48-49` 调 `UserService.getRoomList()` 并最多等 5 秒；
3. `:51-87` 把 `AckRoomList` Protobuf 转成浏览器 JSON；
4. `:89-93` 返回 `{code:0, rooms:[...]}`。

`UserService.getRoomList()` 在 `UserService.java:144-147` 创建空的 `ReqRoomList`，消息号是 `LMsg.REQ_ROOM_LIST_MSG`。

关键在 `UserService.sendAuthenticated()`（`:156-188`）：Web 登录和 Gate 登录是两层状态。若该 session 尚无已认证 Gate 连接，它会：

1. `:174-178` 用账号 token 构建 `LobbyProto.ReqLogin`；
2. `:179` 先发 `LMsg.REQ_LOGIN_MSG`；
3. `:181-185` 检查 `AckLogin`，标记此 TCP 已认证；
4. `:186` 再发原来的房间列表请求。

`GateClient.getConnection()`（`web/service/GateClient.java:69-82`）按 `sessionId` 复用连接，不存在时由 `createConnection()`（`:133-181`）连接 `127.0.0.1:5600`。`:153-155` 设置心跳，避免 Gate 的 90 秒空闲断开。

Gate 的 `GateTcpClient` 在 `gate/client/GateTcpClient.java:36-42` 只允许未登录连接发送登录、注册、心跳；认证后 `roleId != 0` 才允许房间/游戏消息。Lobby 登录由 `lobby/client/handle/role/ReqLoginHandler.java:28-99` 处理：token 在 `:49-54` 查库，成功在 `:69-91` 更新用户和 token 并回 `AckLogin`。

房间列表最终由 `ReqRoomListHandle`（`lobby/client/handle/role/ReqRoomListHandle.java:13-27`）调用 `TableManager.getAllRoomTable()`，带原 sequence 回包。Gate/TCP 的 completer 匹配 sequence，结果回到前述 `CompletableFuture`，最后由 RoomController 变成 JSON。

### 4.3 连接断开为什么不用重新网页登录

Gate TCP 断开时 `GateClient` 会清除 authenticated 标记（`GateClient.java:147-151`），但 Web 的 session 仍可能存在。下一次房间请求再次进入 `sendAuthenticated()`，自动新建 TCP、用 token 静默登录，再发送业务消息。因此：

```text
Web session 有效 ≠ Gate TCP 一定在线
```

排障时必须分别看 Web session、Web→Gate TCP、Gate roleId 和 Lobby/Game 链路。

## 5. 创建房间和得到 tableId

用户点击“创建房间”，`room-page.js:createAndEnter()`（`:149-181`）发：

```json
POST /api/rooms/create
{"sessionId":"...","mode":"fixed","roomId":9002,"gameType":2}
```

固定模板路径由 `RoomController.createRoom()`（`RoomController.java:157-224`）处理：

1. `:159-165` 校验 session/mode；
2. 固定房在 `:197-204` 读取 roomId；
3. 自定义房在 `:170-195` 先调用 `LobbyAdminClient.createCustomRoom()`，通过 `127.0.0.1:5701` 准备动态模板；
4. `:206-207` 调 `UserService.joinTable()` 并等待 10 秒；
5. `:208-219` 从 `AckJoinRoomTable` 取 tableId 返回浏览器。

`UserService.joinTable()`（`UserService.java:149-154`）发送 `LMsg.REQ_JOIN_ROOM_TABLE_MSG`。Lobby 的 `ReqJoinTableHandle`（`lobby/.../ReqJoinTableHandle.java:29-61`）：

- `:42-49` 查 room 模板；
- `:63-70` 有可加入桌就直接加入；
- 无桌时 `:51-59` 找 Game 服务并创建桌；
- `:73-83` 用 `SMsg.REQ_CREATE_TABLE_MSG` 发给 Game；
- 创建完成后 `:103-123` 用原 sequence 回 `AckJoinRoomTable`。

浏览器收到 tableId 后，`room-page.js:183-189` 保存 `tableId/roomId/gameType/seatNum` 并跳到玩法 `index.html`。如果第一次响应异常，`:163-175` 会重新拉房间列表，尝试找回已经创建成功但 HTTP 回包丢失的 `myTableId`。

## 6. 进入牌桌：WebSocket 建连、认证、入桌、快照

### 6.1 每种玩法加载哪些 JS

| 玩法 | HTML 装载顺序 | 主控制器 |
|---|---|---|
| 斗地主 | `doudizhu/index.html:11-15,85-87`：公共层→view→op→main | `doudizhu.js` |
| 麻将 | `mahjong/index.html:10-14,102-104`：公共层→exposed→view→op→main | `mahjong.js` |
| 跑得快 | `paodekuai/index.html:11-15,61-63` | `paodekuai.js` |
| 拖拉机 | `tractor/index.html:12-16,68-72`：另含 hand/settle | `tractor.js` |

公共网络层是 `static/shared/game-table.js`；各玩法 `*-view.js` 负责 DOM/画面，`*-op.js` 负责操作按钮与玩法状态，主 `*.js` 负责组装 WebSocket 与分发推送。

### 6.2 浏览器建立 `/ws/game`

以斗地主为例，`doudizhu.js:31-40` 调 `GameTable.createGameWs()`，认证成功后发送 `enterTable`。其他主文件入口：麻将 `mahjong.js:56-70`、跑得快 `paodekuai.js:31-40`、拖拉机 `tractor.js:42-51`。

`game-table.js:createGameWs()` 在 `:52-105`：

1. `:67-70` 根据当前页面选择 `ws:`/`wss:`，连接 `appUrl('/ws/game')`；
2. `:71-76` open 后立即发送 `{"action":"auth","data":{"sessionId":"..."}}`；
3. `:58-65` 给每个请求分配递增 seq，并保存回调；
4. `:78-88` 收消息时，有 seq 就完成请求回调，无 seq 就交给玩法 `onPush`；
5. `:89-93` 非主动关闭后每 3 秒重连。

Spring 在 `web/config/WebSocketConfig.java:27-31` 把 `/ws/game` 注册到 `GameWebSocketHandler`。

### 6.3 WebSocket JSON → Protobuf

`GameWebSocketHandler.handleTextMessage()`（`web/handler/GameWebSocketHandler.java:75-112`）解析统一格式：

```json
{"action":"xxx","seq":1,"data":{}}
```

action 分发点在 `:89-104`：

| action | Java 方法 | 下游消息 |
|---|---|---|
| `auth` | `handleAuth()` `:134-152` | 只绑定 WS 与 Web session，不发 Game |
| `enterTable` | `handleEnterTable()` `:154-207` | `GMsg.REQ_ENTER_TABLE_MSG` |
| `refreshTable` | `handleRefreshTable()` `:114-132` | `GMsg.REQ_TABLE_SNAPSHOT` |
| `op` | `handleOp()` `:209-274` | `GMsg.REQ_OP` |
| `leave` | `handleLeave()` `:287-312` | `GMsg.REQ_LEAVE` |

`auth` 成功后建立两张表（`:147-150`）：WS id→sessionId，以及 sessionId→WS。后者用于把 Gate 主动推送找到正确浏览器。

`enterTable` 在 `:170-176` 把 tableId、昵称构造成 `GameProto.ReqEnterTable`，经现有 session 对应的 Gate TCP 发出；`:188-197` 把 `AckEnterTable` 的玩家和桌信息转成 JSON 回给相同 seq。

当前四个玩法在 WebSocket 认证后会重新执行 `enterTable`，并不会自动紧接着发送 `refreshTable`。用户点击牌桌顶部“刷新牌桌”按钮时才发送快照请求；Java 在 `:121-130` 请求 `ReqTableSnapshot`，并用 `GameWsPushFormatter.formatSnapshot()`（`GameWsPushFormatter.java:101-162`）返回权威快照：回合、状态、座位、手牌、弃牌、副露、当前操作、倍数、底牌等。断线恢复问题应优先用该快照校准，而不是只依靠增量推送。

Game 端三个请求入口分别是：

- `game/client/handle/role/ReqEnterTableHandle.java:23`；
- `ReqTableSnapshotHandle.java:18`；
- `ReqOpHandle.java:26`；
- 离桌为 `ReqLeaveTableHandle.java:24`。

Gate 会根据连接上绑定的 roleId 和 table/mapId 把请求路由到 Game。

### 6.4 前端的状态、操作、渲染为什么拆成三个 JS

以斗地主为例，这是理解其他三个玩法的模板：

```text
doudizhu.js       总指挥：保存 gameState、连 WS、按 action 分发消息
doudizhu-op.js    业务变化：处理发牌/操作/状态/结算，发送用户操作
doudizhu-view.js  展示辅助：角色标记、按钮配置；并调用 poker-view 公共渲染
```

`doudizhu.js:6-24` 先创建页面内存状态 `gameState`。它不是服务器数据，也不会跨刷新保存；它只是当前页面对牌桌的本地映射。`:27-29` 校验 session 并把昵称、桌号写进 HTML。`:31-37` 创建 WS，`:98` 才真正 connect。

消息到达后 `doudizhu.js:43-70` 只做路由，例如 `notCard → handleNotCard()`、`notOp → handleNotOp()`。具体变化在 `doudizhu-op.js`：

- `:6-48` 解析发牌，改 `gameState.myCards/opponentCounts`，再调用 `renderMyCards/renderOpponentHands`；
- `:51-92` 解析已确认操作，删本地手牌并重画；
- `:94-120` 根据服务器给的 choices 决定显示哪些按钮；
- `:122-140` 根据桌状态改 `#tableState` 的文字并清理旧画面。

这形成统一规律：

```text
服务器消息
  → 主 JS 的 handleWsPush(action)
  → op JS 修改 gameState
  → view/公共 render 函数修改 DOM
  → CSS 根据 DOM 的 class/id 显示最终画面
```

用户点击则方向相反：

```text
HTML onclick / JS 动态 button.onclick
  → op JS 校验选择并组织 {choice,cards}
  → sendWsMessage()
  → game-table.js 给请求加 seq 并 ws.send()
```

麻将对应 `mahjong.js + mahjong-op.js + mahjong-view.js + mahjong-exposed.js`；跑得快对应 `paodekuai.js + op + view`；拖拉机额外拆出 `tractor-hand.js` 和 `tractor-settle.js`，避免主文件承担手牌动画和结算细节。

### 6.5 WebSocket 的 seq 到底解决什么问题

浏览器可能连续发送 auth、enterTable、refreshTable。响应返回顺序不一定和发送顺序完全相同，所以 `game-table.js:54-55` 保存计数器和 `pending` 表：

```text
seq=1 → auth 的回调
seq=2 → enterTable 的回调
seq=3 → refreshTable 的回调
```

发送时 `:58-64` 把回调存到 `pending[seq]`；收到消息时 `:81-86` 用响应 seq 找回正确回调并删除。Game 主动广播统一是 `seq=0`，所以会走 `:87` 的 `onPush`。这就是“请求/响应”和“服务器主动推送”共用一条 WebSocket 仍不会串消息的原因。

注意当前公共 WS 层没有为每个 pending 回调设置浏览器端超时清理；后端等待通常有 5 秒超时并会回 error。小游戏 `mini-base.js:62-78` 则显式实现了 8 秒 Promise 超时。

### 6.6 断线重连做了什么、没做什么

`game-table.js:89-93` 在 WS `close` 后每 3 秒调用 `connect()`。新连接 open 后，`:74-76` 再次发送 auth；玩法的 `onAuthed` 再调用 `enterTable()`。因此链路是：

```text
断开 → 等 3 秒 → 新 WebSocket → auth → enterTable → 恢复玩家/桌基本信息
```

它不会保存旧 WebSocket，也不会重发断线期间的用户操作。麻将在 `mahjong.js:63-64` 还会先 `resetMahjongViewForReconnect()` 清旧画面，减少新旧增量状态混合。

页面主动离开或桌子解散时应调用 `gameWs.stopReconnect()`，否则普通跳页前可能仍安排一次重连。桌解散处理示例在 `doudizhu-op.js:122-126`。

### 6.7 四种“刷新/定时”机制完整对照

| 机制 | 触发位置 | 周期/触发条件 | 实际动作 |
|---|---|---|---|
| 浏览器整页刷新 | 浏览器按钮或重新打开 URL | 人工 | 重新加载 HTML/CSS/JS，JS 内存清空 |
| 房间列表加载 | `room-page.js:62-91,202` | 页面启动一次，也可调用 `loadRooms()` | HTTP GET `/api/rooms` 后重画卡片 |
| 能力轮询 | `capabilities-poll.js:45-71` | 启动立即一次，之后每 60 秒；网络/页面恢复也触发 | HTTP GET `/api/capabilities`，切换入口 class |
| WS 断线重连 | `game-table.js:89-93` | close 后 3 秒 | 新建 WS，再 auth/enterTable |
| 牌桌快照刷新 | 各玩法顶部按钮 | 人工点击 | WS `refreshTable`，用权威快照重建 gameState 和 DOM |
| 学习心跳 | `learning/parts/core.js:95-98,123-138` | 登录后立即一次，每 30 秒 | POST `/api/learning/auth/heartbeat` |

牌桌快照以斗地主为例：HTML `doudizhu/index.html:25` 的按钮 `onclick="refreshTable()"` → `doudizhu-op.js:226-241` 禁用按钮并发 WS → 成功后 `applyDdzSnapshot()`（`:243` 起）整体替换玩家、手牌、底牌、最后出牌和操作区。麻将对应 `mahjong-op.js:101-145`，跑得快对应 `paodekuai-op.js:218` 起，拖拉机对应 `tractor-op.js:232` 起。

`capabilities-poll.js` 不是心跳。它只是页面对“功能入口是否可用”的一分钟探测，并在 `AppQuality.canRequest()` 判断页面可见且联网时才请求。牌桌本身也没有浏览器 JS 定时发送业务心跳；Web→Gate 的 TCP 心跳由 Java `GateClient` 负责。不要为了“保活”在玩法 JS 里随意新增重复定时器。

## 7. 一次“点击操作”的完整往返

以准备/出牌为例：

1. 页面按钮进入玩法 `*-op.js`；公共准备按钮调用 `game-table.js:143-150`，发送 `op` 且 `choice=7`；出牌则由玩法 op 脚本整理 `choice` 和 cards。
2. `game-table.js:58-63` 发 WS JSON，并用 seq 保存回调。
3. `GameWebSocketHandler.java:209-243` 校验 choice，把 cards 逐张构造成 `GameProto.OpInfo/ReqOp`。
4. `:245-247` 特意调用 `GateClient.sendAndWaitTcp()`，保留 TCP 包的 `result/messageId`；失败回包可能没有 Protobuf body。
5. Gate 转到 Game 的 `ReqOpHandle`；Game 根据当前桌状态把操作交给对应状态机/玩法服务。
6. `GameWebSocketHandler.java:258-268` 对错误立即返回可读提示；成功不以请求 ACK 驱动画面，避免 ACK 和广播成为两个数据源。
7. Game 修改权威桌状态后，向桌上玩家广播 `NOT_CARD/NOT_OP/ACK_OP/MJ_TILE_NOT/...`。
8. Gate `ConnectProcessor` 在 `gate/connect/ConnectProcessor.java:120-170` 根据消息号找到玩家 roleId 并转发；`:71-77` 区分带 sequence 的请求回复和 sequence=0 的主动推送。
9. Web `GateClient.handleIncoming()` 把不能完成 pending request 的包交给 push listener；listener 在 `GameWebSocketHandler.init()`（`:56-59`）注册。
10. `GameWebSocketHandler.onGatePush()`（`:315-336`）解析 Protobuf，调用 `GameWsPushFormatter` 转 JSON，以 `seq=0,msg="push"` 发到浏览器。
11. `game-table.js:78-88` 发现没有 pending seq，交给玩法主文件的 `handleWsPush`，再由 `*-view.js/*-op.js` 更新画面和按钮。

常用推送映射在 `GameWsPushFormatter.java:20-30`：

| GMsg | 浏览器 action | 内容转换入口 |
|---|---|---|
| `NOT_CARD` | `notCard` | `formatNotCard()` `:185-202` |
| `NOT_OP` | `notOp` | `formatNotOp()` `:204-210` |
| `ACK_OP` | `ackOp` | `formatAckOp()` `:164-183` |
| `NOT_STATE/NOT_TABLE_STATE` | `notState` | `:52-56` |
| `NOT_RESULT` | `notResult` | `formatNotResult()` `:212-235` |
| `MJ_TILE_NOT` | `notMjState` | `formatNotMjState()` `:237-248` |
| `NOT_ROUND_RESULT` | `notRoundResult` | `:271-311` |
| `NOT_GAME_RESULT` | `notGameResult` | `:325-348` |
| `ACK_ENTER_TABLE_MSG`（seq=0） | `seatUpdate` | `:34-42` |

## 8. 离桌、退出登录与状态清理

牌桌点击退出调用 `GameTable.exitRoom()`（`game-table.js:132-141`）：发 `leave`，成功后删 `localStorage.tableId` 并回房间列表。Java `handleLeave()`（`GameWebSocketHandler.java:287-312`）发 `GMsg.REQ_LEAVE`，等待 `AckLeaveTable`。

房间页退出登录由 `room-page.js:191-200` 调：

```text
POST /api/logout {sessionId}
```

`UserController.logout()`（`web/controller/UserController.java:81-93`）调用 `UserService.logout()`；后者在 `UserService.java:122-137` 删除 session/token/user 索引并关闭该 session 的 Gate TCP，同时响应清除 cookie。浏览器随后 `localStorage.clear()` 并回首页。

WebSocket 关闭本身只在 `GameWebSocketHandler.java:67-72` 删除 WS 映射，不等价于退出账号或离开 Game 桌。

## 8.1 CSS 与页面布局怎样对应

CSS 通过选择器匹配 HTML。斗地主 HTML 中：

```html
<div class="player-area player-left">
    <div class="player-info" id="playerLeft">...</div>
</div>
```

会同时匹配 `.player-area` 和 `.player-left`。`shared/game-table.css:42-61` 提供所有牌桌共用的玩家文字基础样式，`shared/poker-table.css:36-46` 再把左/右玩家绝对定位到桌面两侧。

常见选择器读法：

```css
.top-bar                 /* 所有 class 含 top-bar 的元素 */
.top-bar .back-btn       /* top-bar 内部的 back-btn */
#roomInfo                /* id 为 roomInfo 的唯一元素 */
.action-btn:hover        /* 鼠标悬停状态 */
.action-btn:disabled     /* button disabled 时 */
.center-message.show     /* 同时有两个 class；JS 加 show 后显示 */
@media (...)             /* 窄屏/横竖屏条件样式 */
```

一个典型的“JS 控制状态、CSS 控制动画”是中央提示：`game-table.css:102-119` 默认 `.center-message` 的 `opacity:0`，而 `.center-message.show` 是 `opacity:1`；`game-table.js:107-113` 只负责加 `show`，定时后再移除。JS 不需要知道透明度和过渡细节。

### CSS 文件的职责

| 文件 | 作用 |
|---|---|
| `shared/app-quality.css` | 全站可访问性、网络提示、移动端公共体验 |
| `shared/lobby-home.css` | 大厅/分类卡片布局 |
| `shared/room-page.css` | 四种玩法共用的房间列表 |
| `shared/game-table.css` | 牌桌顶栏、操作栏、提示、连接状态、结算壳 |
| `shared/game-landscape.css` | 强制横屏时的根布局变换 |
| `shared/poker-card.css` | 单张扑克牌牌面 |
| `shared/poker-table.css` | 斗地主/跑得快/拖拉机桌面位置 |
| `pages/games/mahjong/mahjong.css` | 麻将专属牌墙、弃牌、副露布局 |
| `pages/games/tractor/tractor.css` | 拖拉机专属覆盖 |

CSS 后加载的同等优先级规则通常覆盖先加载规则。例如公共 `game-table.css` 先给 `.player-area` 基础样式，后面的 `poker-table.css` 给具体位置。排查样式时在浏览器 DevTools 的 Elements → Styles/Computed 中看“最终哪条规则生效”，比只看源文件更可靠。

### 响应式和横屏

CSS 负责最终排版，但 JS 也需要知道牌区可用尺寸。`shared/game-landscape.js:8-30` 检测横竖屏、切换根节点 `force-landscape` class，并设置 CSS 变量 `--game-w/--game-h`；`:57-68` 监听 `resize/orientationchange`，40ms 防抖后重新布局。

玩法主 JS 在启动时绑定自己的布局函数，例如斗地主 `doudizhu.js:96-98` 绑定 `layoutMyCards`；麻将 `mahjong.js:118-123` 绑定 `layoutMyHand`。因此调整牌大小/位置时通常要同时检查：HTML 容器、CSS 定位、公共横屏尺寸、玩法的 layout/render 函数。

### 改页面时按什么顺序做

新增一个可交互区域，推荐顺序是：

1. HTML 加有语义的容器和稳定 id/class；
2. CSS 定义静态外观和状态 class；
3. JS 用 id 找节点、用事件调用函数；
4. 请求成功后先更新 `gameState`，再统一调用 render；
5. 不要把服务器返回的未转义文本直接拼进 `innerHTML`；优先 `textContent/createElement`；
6. 用 DevTools 分别验证 Elements、Network、WS Messages、Console。

## 9. 学习模块流程（纯 Web + 本地数据）

学习入口 `pages/learning/index.html` 加载 Vue 和拆分脚本，最终由 `pages/learning/js/app.js:1-44` mount。

`app.js:9-28` 启动时请求 `auth/me` 恢复当前游戏 session；失败直接回总站首页。统一请求封装在 `parts/core.js:45-68`：所有路径拼成 `/api/learning/**`，携带 cookie，HTTP 401 回首页。

主要流程：

- `core.js:185-192` 请求 `/api/learning/stats` 加载首页；
- `:209-228` 切换功能时请求 words、records、mistakes、content 等；
- `:123-138` 每 30 秒向 `/api/learning/auth/heartbeat` 报页面、功能和设备；
- `app.js:29-31` 捕获前端运行错误并报 `/frontend-error`。

后端认证接口在 `web/learning/controller/AuthController.java:27-83`。业务 API 在 `ApiController.java:44-123`：控制器先通过 `AuthService` 校验用户/权限，再调用 `WordService/MathService/RecordService/MistakeService/StatsService/ContentService`。数据目录由 `application.yml:47-59` 指向 `data/learning/**`。这条链路不经过 Gate/Lobby/Game。

学习资源与资料库分别由 `ResourceController` 的 `/api/learning/resources` 和 `LibraryController` 的 `/api/learning/library/**` 提供；管理接口在三个 `web.learning.controller.admin.*Controller` 中。

## 10. 休闲小游戏 `/ws/mini` 流程

五子棋和象棋联网模式共用 `static/shared/mini-base.js`：

1. `:3-14` 从 localStorage 要求已有游戏账号 session；
2. `:25-35` 连接 `appUrl('/ws/mini')` 并发送 `auth`；
3. `:44-58` 用 seq 完成 Promise，同时把事件交给页面；
4. `:62-78` 统一发送，8 秒超时。

`WebSocketConfig.java:30-31` 把 `/ws/mini` 映射到 `MiniGameWebSocketHandler`。其 action 分发在 `web/handler/MiniGameWebSocketHandler.java:65-104`：`auth/match/cancelMatch/move/resign/leave`。

认证在 `:107-130`，匹配队列和创建 `MiniRoom` 在 `:132-198`，落子合法性与胜负判断从 `:209` 开始调用 `GomokuBoard` 或 `ChessBoard`。房间、队列、棋盘都在 Web 进程内存里，因此这条链路也不经过 Gate/Lobby/Game；Web 进程重启会丢失正在进行的小游戏房间。

## 11. 管理、回放和能力探测

- 管理首页 `pages/admin/admin.html` 加载 `js/core.js` 及各功能脚本；邀请、玩家、桌子、回放等请求由 `AdminController.java:18-209` 提供。
- 整数分配页签由 `js/allocator.js` 请求 `POST /api/admin/integer-allocator/calculate`，经 `IntegerAllocatorAdminController` 校验管理员会话后调用 `web/arena/IntegerAllocator`；计算结果返回 6 个值、总和及局部窗口校验。
- ARPU 页签由 `js/arpu.js` 请求 `POST /api/admin/arpu/check`，经 `ArpuAdminController` 校验后由 `ArpuLookupService` 代理 `https://arpu.151365.cc/check?phone_no=...`；月明细中的不可用值跳过，`ArpuAverageCalculator` 计算近 3 / 6 个可用月份平均值，原始返回 JSON 由页面代码区展示。
- 牌局回放列表/兑换码：`pages/admin/replays.html:91-125` → `ReplayController.java:17-52` → `ReplayService` → `build/game/replay`（配置见 `application.yml:33-34`）。
- 终端页：`pages/admin/terminal.html:193` 拉取状态，`:272` 提交命令 → `AdminController` 的 `/api/admin/shell` → `ShellService`。这是高权限链路。
- `/api/capabilities` 由 `CapabilitiesController.java:35-81` 探测 Game/Center 地址是否可达，前端轮询在 `shared/capabilities-poll.js:50`。能力探测只能说明当次 TCP 探测结果，不等价于完整业务链路健康。

## 12. 接口总表

### 12.1 游戏主流程 HTTP

| 方法与路径 | 前端入口 | Java 入口 | 下游 |
|---|---|---|---|
| `POST /api/auth/login` | `index.html:413,446` | `AuthController:30` | SQLite；不直接走 Gate |
| `POST /api/auth/register` | `index.html:488` | `AuthController:58` | SQLite/邀请码 |
| `POST /api/logout` | `room-page.js:192` | `UserController:81` | 清 Web session/Gate TCP |
| `GET /api/rooms` | `room-page.js:65` | `RoomController:40` | Gate→Lobby |
| `POST /api/rooms/create` | `room-page.js:150` | `RoomController:157` | 可选 Lobby Admin；Gate→Lobby→Game |
| `POST /api/rooms/join` | 兼容入口 | `RoomController:107` | Gate→Lobby |
| `GET /api/capabilities` | `capabilities-poll.js:50` | `CapabilitiesController:35` | TCP 探测 |

### 12.2 WebSocket

| 路径 | 浏览器公共层 | Java handler | 是否跨服务 |
|---|---|---|---|
| `/ws/game` | `shared/game-table.js:52-105` | `GameWebSocketHandler` | 是，Web→Gate→Game |
| `/ws/mini` | `shared/mini-base.js:16-89` | `MiniGameWebSocketHandler` | 否，Web 内存逻辑 |

### 12.3 学习 API

| 前缀 | Controller | 用途 |
|---|---|---|
| `/api/learning/auth` | learning `AuthController` | 当前账号、密码、心跳 |
| `/api/learning` | `ApiController` | 题目、记录、错题、统计 |
| `/api/learning/library` | `LibraryController` | 字典、诗词、教材、英语 |
| `/api/learning/resources` | `ResourceController` | 文件资源 |
| `/api/learning/admin` | admin controllers | 用户、内容、进度管理 |

## 13. 排障时怎样定位“一行坏在哪里”

必须把同一时间段拆成四层，不要只凭一个页面报错推断后端全部停止：

1. **静态页面层**：当次 GET 是否 200、返回的是 HTML 还是代理错误页；script URL 是否 200。
2. **前端运行层**：浏览器 Console 是否有语法错误、未定义函数；Network 中 `fetch`/WS frame 的实际 URL、状态码、请求体和响应体。
3. **Web API/WS 层**：Web 日志是否出现对应 controller/handler、sessionId、seq；HTTP 401、业务 `code != 0` 和网络失败要区分。
4. **后端服务链路**：同一时间交叉核对 Java 进程、监听端口和 Web/Gate/Lobby/Game 日志；不能仅凭某个受限环境的 `ps` 或 `ops.sh status` 断言主机服务停止。

推荐使用关联键：

```text
HTTP：sessionId + userId + roomId/tableId + 时间
WS：sessionId + seq + action + GMsg 十六进制消息号 + 时间
跨服务：userId/clientId + tableId/mapId + sequence + 时间
```

典型判断：

- HTML 200 但按钮没反应：先看 JS 加载与 Console，不要先判 Web 后端挂了。
- `/api/rooms` 超时：查 Web 是否建 Gate TCP、是否静默登录成功、Lobby 是否收到 `REQ_ROOM_LIST_MSG`。
- 创房超时但刷新后出现桌：可能 Game 已创建成功，只是 sequence 回包链路或 HTTP 等待超时；前端已有二次列表恢复逻辑。
- WS 已连接但无法入桌：`/ws/game` 握手成功只代表静态 WebSocket 层正常，还要看 `auth`、`enterTable`、Gate roleId 和 Game handler。
- 操作“超时”：看 `GameWebSocketHandler:245-268` 的原始 TCP result；成功画面应由 sequence=0 的正式广播更新。
- 页面状态旧：主动发 `refreshTable`，用 Game 权威快照校准，不要只看前端缓存。

## 14. 最短代码阅读路线

如果要亲自跟一次完整请求，建议按这个顺序逐文件打断点或加临时日志：

```text
static/index.html
  → web.controller.AuthController
  → web.service.UserService
  → static/shared/room-page.js
  → web.controller.RoomController
  → web.service.GateClient
  → gate.client.GateTcpClient
  → lobby.client.handle.role.ReqLoginHandler / ReqRoomListHandle / ReqJoinTableHandle
  → static/shared/game-table.js
  → web.handler.GameWebSocketHandler
  → game.client.handle.role.ReqEnterTableHandle / ReqOpHandle / ReqTableSnapshotHandle
  → gate.connect.ConnectProcessor
  → web.handler.GameWsPushFormatter
  → 各玩法主 JS 的 handleWsPush
```

这条路线覆盖了“进站、登录、大厅、创建桌、进入牌桌、操作、广播、渲染、断线恢复”的完整闭环。

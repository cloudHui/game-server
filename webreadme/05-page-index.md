# Web 页面与代码索引

## 1. 页面组

| 页面组 | 入口目录 | 主要公共层 | 后端入口 |
|---|---|---|---|
| 登录/注册 | `static/index.html` | `shared/app-base.js` | `AuthController` |
| 大厅 | `static/pages/lobby/` | 大厅 CSS、session 脚本 | 用户/能力接口 |
| 房间 | `static/pages/games/*/rooms.html` | `shared/room-page.js` | `RoomController` |
| 牌桌 | `static/pages/games/*/index.html` | `shared/game-table.js` | `GameWebSocketHandler` |
| 学习 | `static/pages/learning/` | learning core/app | learning controllers |
| 小游戏 | 对应 mini 页面 | `shared/mini-base.js` | `MiniGameWebSocketHandler` |
| 后台 | `static/pages/admin/` | admin JS | `AdminController`、回放 controllers |

## 2. 牌桌脚本顺序

```text
app-base.js
→ game-table.js
→ game-landscape.js
→ poker-card/poker-view（扑克玩法）
→ 玩法 view/exposed
→ 玩法 op
→ 玩法主 JS
```

斗地主、麻将、跑得快、拖拉机分别检查各自 `index.html` 的真实 script 顺序。普通 script 共用 window 全局作用域，顺序错误会导致 `GameTable`、render 函数或玩法处理函数未定义。

## 3. 后端代码索引

| 功能 | Controller/Handler | Service/转换 |
|---|---|---|
| 登录注册 | `web/controller/AuthController.java` | `web/service/UserService.java` |
| 退出 | `web/controller/UserController.java` | `UserService`、GateClient |
| 房间 | `web/controller/RoomController.java` | `UserService`、`GateClient` |
| 牌桌 WS | `web/handler/GameWebSocketHandler.java` | `GateClient`、`GameWsPushFormatter` |
| 小游戏 WS | `web/handler/MiniGameWebSocketHandler.java` | Web 内存房间/棋盘 |
| 学习 | `web/learning/controller/` | 学习领域 Service |
| 后台/回放 | `web/controller/` 对应 Controller | Admin/Replay Service |

## 4. 入口到功能的检查顺序

```text
先看 HTML
→ 再看 script 顺序
→ 找 onclick/addEventListener
→ 找 fetch/WebSocket.send
→ 找 Controller/Handler
→ 找 Service/GateClient
→ 找响应处理
→ 找 render、class 和跳转
→ 最后看定时器和清理
```


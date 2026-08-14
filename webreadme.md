# Web 详细交互与代码导航

> 本文是 Web 文档总入口。详细内容拆分到 `webreadme/`，避免单个 Markdown 过大。本文描述当前静态 Web 的实际行为；Vue 迁移方案单独见 [vue3-migration-plan.md](vue3-migration-plan.md)。

## 目标

每个页面和操作都按照下面的链路说明：

```text
页面入口
→ HTML 按钮/链接/表单/动态控件
→ JS 事件与函数
→ HTTP 或 WebSocket 请求
→ Web Controller/Handler/Service
→ Gate 转发与 Protobuf（如有）
→ Gate/Lobby/Game 回复
→ Web 解析和 JSON 转换
→ 前端状态修改
→ DOM/CSS 渲染
→ 页面跳转、重试、重连或错误提示
```

## 文档索引

| 文档 | 说明 |
|---|---|
| [01-current-flow.md](webreadme/01-current-flow.md) | 现有完整代码导航：首页、登录、大厅、房间、牌桌、Gate、学习、小游戏、后台和排障 |
| [02-page-operation-trace.md](webreadme/02-page-operation-trace.md) | 按页面和用户操作追踪：入口、控件、函数、请求、响应、状态和渲染的记录标准与重点清单 |
| [03-timers-connections-rendering.md](webreadme/03-timers-connections-rendering.md) | 所有定时器、轮询、心跳、超时、重连、页面清理、DOM/CSS 和横屏行为 |
| [04-gate-message-boundary.md](webreadme/04-gate-message-boundary.md) | Web 与 Gate 的请求/回复边界、sequence、主动推送、Protobuf 和浏览器 JSON 转换 |
| [05-page-index.md](webreadme/05-page-index.md) | 页面入口、脚本加载顺序、Controller、Handler、Service 和玩法文件索引 |

## 详细说明要求

每个页面或功能至少要能回答：

- 页面入口 HTML 是什么；
- 页面加载了哪些 JS/CSS，顺序是什么；
- 页面初始状态如何建立；
- 每个按钮、链接、表单、弹窗和动态控件在哪里；
- 触发条件是什么，调用哪个函数；
- 请求 URL、方法、参数、session 和 sequence 从哪里来；
- Web 端经过哪些 Controller、Handler、Service；
- 是否转发 Gate，使用什么消息号和 Protobuf；
- Gate/Lobby/Game 返回什么，Web 如何判断成功或失败；
- 哪个前端函数接收结果，修改哪些状态；
- 哪些 DOM、class、CSS、动画和提示发生变化；
- 成功、失败、超时、刷新、退出、断线和重连时页面怎样表现。

## 统一边界

- 文档中的路径只使用项目内相对路径，例如 `web/src/main/java/...`、`web/src/main/resources/static/...`。
- Web 与 Gate 的请求和回复要写到消息类型/消息号及转换入口。
- Game 内部只追踪到 Gate 边界，不展开玩法算法和状态机实现。
- 浏览器定时器、Java Gate TCP 心跳、WebSocket 重连必须分开记录。
- 行号只作为辅助定位；代码变更后应同时按文件、类名、方法名、action 或消息号搜索。

## 最短完整阅读路线

```text
static/index.html
→ static/shared/app-base.js
→ web.controller.AuthController
→ web.service.UserService
→ static/shared/room-page.js
→ web.controller.RoomController
→ web.service.GateClient
→ static/shared/game-table.js
→ web.handler.GameWebSocketHandler
→ web.handler.GameWsPushFormatter
→ 对应玩法的主 JS、op JS、view JS
```


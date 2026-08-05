# 页面与用户操作详细追踪标准

本文规定 Web 详细说明的颗粒度。新增页面或补充旧页面时，按本模板记录，不能只写“调用某某接口”。

## 1. 页面级记录

每个页面先写：

| 项目 | 需要记录的内容 |
|---|---|
| 入口 | HTML 相对路径、访问 URL、是否需要 session |
| 页面组 | 登录、大厅、房间、牌桌、学习、小游戏或后台 |
| 脚本 | 所有 JS 文件及加载顺序 |
| 样式 | 所有 CSS 文件及覆盖关系 |
| 初始数据 | URL 参数、cookie、localStorage、sessionStorage、body data 属性 |
| 初始请求 | 页面打开后立即执行的 fetch、XHR 或 WebSocket |
| 跳转出口 | 返回、退出、进入房间、进入牌桌、登录失效 |
| 清理 | interval、timeout、WebSocket、事件监听和 pending 请求 |

## 2. 控件级记录

页面上的每个按钮、链接、表单、弹窗确认按钮、动态生成按钮都要单独记录：

```text
控件文字/selector
→ HTML onclick、addEventListener 或动态 onclick 位置
→ 调用的 JS 函数
→ 点击前的校验和禁用逻辑
→ 读取的状态、输入和存储值
→ 发出的请求
→ 成功分支
→ 业务失败分支
→ 网络失败/超时分支
→ 页面状态、DOM、CSS 和跳转结果
```

双击、重复点击、请求中禁用、请求完成后恢复、按钮在不同游戏状态下是否显示，也必须写清楚。

## 3. HTTP 操作模板

每个 HTTP 操作按以下顺序说明：

1. 页面文件和函数；
2. `appUrl()` 生成的实际路径；
3. HTTP 方法、query、JSON body、cookie/header；
4. Controller 方法；
5. Service 调用和 session 校验；
6. 是否访问本地数据、Lobby Admin 或 Gate；
7. 成功响应字段和业务 code；
8. 前端 `.then`/async 分支；
9. 文字提示、列表、卡片或表单的 DOM 更新；
10. 失败、401、超时、重试和页面恢复。

`fetch` 的 HTTP 200 不等于业务成功，必须同时记录 HTTP 状态和 JSON 业务码。401 要说明是回首页、弹登录框还是保留当前输入。

## 4. WebSocket 操作模板

每个 WebSocket action 按以下顺序说明：

1. 哪个页面创建连接；
2. `ws/wss` 和 context-path 如何生成；
3. `auth`、`enterTable` 或匹配初始化如何发送；
4. action、seq、data 的完整字段来源；
5. Web Handler 的分发方法；
6. Gate 请求消息和 Protobuf 构造位置；
7. 带 seq 的响应是什么；
8. `seq=0` 主动推送是什么；
9. 哪个 JS 函数接收并分发；
10. 哪些 gameState 字段变化；
11. 哪些节点重绘、按钮显示/禁用、提示或动画变化；
12. 超时、断线、重连、重复操作和离桌后的行为。

## 5. 页面跳转流转

每条跳转写清楚来源和目标：

```text
来源页面
→ 触发控件/函数
→ 写入或清理的 tableId、roomId、gameType、session 信息
→ 目标 HTML
→ 目标页面重新读取的数据
→ 目标页面首次请求/首次 WebSocket
```

浏览器整页刷新、局部 DOM 重画、牌桌快照重建必须分别记录，不能统称为“刷新”。

## 6. 牌桌操作示例的最低细节

准备、出牌、吃碰杠、刷新牌桌、离桌、结算继续等操作至少要追踪：

```text
顶部/操作区按钮
→ 玩法 *-op.js
→ choice/cards 等输入校验
→ shared/game-table.js 分配 seq
→ /ws/game
→ GameWebSocketHandler
→ GateClient
→ Game 请求/回复
→ GameWsPushFormatter
→ 主 JS handleWsPush
→ gameState
→ view/render
→ 按钮、牌面、提示、结算区
```


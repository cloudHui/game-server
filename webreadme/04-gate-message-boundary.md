# Web 与 Gate 的消息边界

## 1. 总体链路

```text
浏览器 JSON
→ GameWebSocketHandler
→ Java Protobuf 请求
→ GateClient TCP
→ Gate 根据 roleId/tableId 路由
→ Lobby 或 Game
→ Protobuf 回复/主动推送
→ GateClient
→ Web Handler/Formatter
→ 浏览器 JSON
```

登录、学习和部分后台接口可以不经过 Gate；房间和牌桌流程必须明确标注是否经过 Gate。

## 2. 牌桌 action 边界

| 浏览器 action | Web 入口 | Gate/Game 消息 | 返回或推送 |
|---|---|---|---|
| `auth` | `handleAuth()` | 绑定 Web session，不发 Game | auth 响应 |
| `enterTable` | `handleEnterTable()` | `REQ_ENTER_TABLE_MSG` | 入桌响应/座位推送 |
| `refreshTable` | `handleRefreshTable()` | `REQ_TABLE_SNAPSHOT` | 权威快照 |
| `op` | `handleOp()` | `REQ_OP` | 操作结果和正式广播 |
| `leave` | `handleLeave()` | `REQ_LEAVE` | 离桌响应 |

## 3. 请求回复和主动推送

```text
带 seq 的消息：浏览器请求 → Web 等待 → 同 seq 返回
seq=0 的消息：Gate/Game 主动广播 → Web 转 push → 玩法处理
```

`shared/game-table.js` 用 pending 表匹配 seq。成功的业务画面通常应由正式主动广播驱动，而不是由一个临时 HTTP/WS ACK 自己猜测最终状态。

## 4. Protobuf 转换要求

说明每个请求时必须记录：

- Java 从 JSON 读取的字段；
- 字段校验和默认值；
- 构造的 Protobuf request 类型；
- 使用的消息号；
- GateClient 的发送和等待方法；
- 原始 result/messageId 如何保留；
- 回复 Protobuf 如何转浏览器 JSON；
- Protobuf 缺失或错误时的浏览器错误表现。

出牌类操作尤其要记录 `choice` 和 cards 的逐张转换；不能只写“发送操作”。

## 5. 推送格式化

`GameWsPushFormatter` 将 Gate/Game 消息映射为浏览器 action，例如：

| 服务端消息 | 浏览器 action | 渲染重点 |
|---|---|---|
| `NOT_CARD` | `notCard` | 手牌、发牌和对手数量 |
| `NOT_OP` | `notOp` | 当前回合和可用按钮 |
| `ACK_OP` | `ackOp` | 操作确认/提示 |
| `NOT_STATE` | `notState` | 桌面状态和回合 |
| `NOT_RESULT` | `notResult` | 本局结果 |
| `NOT_ROUND_RESULT` | `notRoundResult` | 回合结算 |
| `NOT_GAME_RESULT` | `notGameResult` | 总结算 |
| 麻将状态消息 | `notMjState` | 牌墙、弃牌、副露和操作区 |

## 6. 排查 Gate 链路

按以下顺序核对：

1. 浏览器是否发出正确 action、seq 和 data；
2. Web Handler 是否收到并通过校验；
3. GateClient 是否找到当前 session 的 TCP；
4. Gate 连接是否已认证并绑定 roleId；
5. Gate 是否按 tableId/mapId 路由；
6. 下游是否返回原 sequence；
7. Web 是否把回复当成响应还是推送；
8. 浏览器是否进入正确的 handle/render 函数。


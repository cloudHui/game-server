# 定时器、连接、重连与渲染细节

## 1. 定时机制清单

| 机制 | 代码入口 | 启动时机 | 周期/延迟 | 结果 | 清理条件 |
|---|---|---|---|---|---|
| 房间列表加载 | `static/shared/room-page.js` | 进入房间页 | 启动一次或人工调用 | GET 房间并重画卡片 | 页面离开 |
| 能力轮询 | `static/shared/capabilities-poll.js` | 页面启动立即一次 | 通常每 60 秒 | GET capabilities、切换入口 class | 页面隐藏/卸载 |
| 学习心跳 | `static/pages/learning/js/parts/core.js` | 学习页登录后 | 立即一次、约每 30 秒 | POST learning heartbeat | 退出学习页 |
| WS 重连 | `static/shared/game-table.js` | close 且非主动停止 | 约 3 秒后 | 新连接、auth、enterTable | stopReconnect/页面离开 |
| 牌桌快照 | 各玩法 `*-op.js` | 用户点击刷新 | 人工触发 | `refreshTable`，整体校准 | 请求结束 |
| 横屏布局 | `static/shared/game-landscape.js` | resize/orientationchange | 防抖约 40ms | 重算 CSS 变量和牌区 | 页面卸载 |
| Gate TCP 心跳 | Java `GateClient` | Gate TCP 建立 | 由 Java 连接层控制 | 防止 Gate 空闲断开 | TCP 关闭 |

## 2. 不同“心跳”的边界

```text
学习心跳：浏览器 → Web 学习 API
能力轮询：浏览器 → Web 能力探测 API
WebSocket 重连：浏览器连接恢复机制
Gate TCP 心跳：Web Java → Gate TCP
```

能力轮询不等于登录保活；牌桌页面通常不需要用浏览器 JS 定时发送重复业务心跳。新增保活逻辑前必须先确认 Java `GateClient` 是否已有机制。

## 3. 重连完整流程

```text
WebSocket close
→ 判断是否主动关闭
→ 非主动关闭则安排延迟
→ 创建新 WebSocket
→ open 后发送 auth
→ 认证成功后 enterTable
→ 必要时 refreshTable
→ 重新渲染座位、状态、手牌和操作区
```

重连不会安全地重发断线期间的用户操作。离桌、跳页或桌子解散时必须停止重连和清理旧连接。

## 4. DOM/CSS 渲染

渲染说明至少要区分：

- `textContent`：更新文字；
- `createElement/appendChild`：创建列表、房间卡或按钮；
- `innerHTML`：替换片段，需检查未转义内容；
- `classList`：切换显示、禁用、连接、回合和动画状态；
- `style`/CSS 变量：尺寸、横屏和牌面位置；
- Canvas/Worker：不能只按普通 DOM 节点描述。

典型牌桌链路是“状态先变、render 后执行、CSS 决定最终样式”。例如服务器推送当前操作后，玩法脚本更新 choices，再生成或显示操作按钮；CSS 只负责按钮颜色、禁用态和布局。

## 5. 页面离开时的清理检查

每个带连接或定时器的页面都要检查：

1. 是否发送 leave 或 logout；
2. 是否停止 WebSocket 重连；
3. 是否清除 interval/timeout；
4. 是否释放 resize、online/offline、visibility 监听；
5. 是否清除 pending seq 回调；
6. 是否清理 tableId 和临时页面状态。


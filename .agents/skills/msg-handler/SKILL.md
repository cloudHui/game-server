---
name: msg-handler
description: >-
  为 game-server 新增或修改网络消息处理链路的全流程脚手架。
  当用户提出“加个接口”、“处理客户端请求”、“实现某个消息业务”时调用。
---

# Msg-Handler 全链路消息脚手架技能

## 何时使用
- 客户端有新需求，需要服务端暴露长连接消息接口；
- 新增棋牌操作（如理牌、摸牌、听牌、聊天、托管）；
- 扩展大厅功能（如查看个人中心、修改头像、好友互动）。

## 标准流水线（四步闭环）

### 第一步：检查并更新 Proto 协议
- 确认 `proto/src/main/java/` 下是否有对应的消息结构体；
- 若无，使用技能 `proto-gen` 编写 `ReqXxx` 与 `AckXxx`，并执行批处理重新生成 Java 类。

### 第二步：注册消息常量与 `@ClassField` 注解
在 `proto/src/main/java/msg/registor/message/` 对应的常量类（`GMsg` / `LMsg` / `SMsg`）中添加消息编号：
```java
@ClassField(value = GameProto.ReqPlayCard.class, des = "出牌请求")
public static final int REQ_PLAY_CARD = CMsg.GAME_TYPE | 25;

@ClassField(value = GameProto.AckPlayCard.class, des = "出牌响应")
public static final int ACK_PLAY_CARD = CMsg.GAME_TYPE | 26;
```
> **注意**：`HandleTypeRegister` 启动时会自动通过反射扫描并绑定 MsgId 与 Proto Class，无需任何手动路由表配置。

### 第三步：编写 Handler 处理类
- 命名：`XxxHandler.java`
- 接口：实现 `tools.manager.ConnectHandle`
- 注解：标注 `@ProcessClass(GameProto.ReqPlayCard.class)`
- 结构：
  ```java
  package com.cloud.hub.game.client.handle;

  import com.google.protobuf.Message;
  import msg.annotation.ProcessClass;
  import net.client.Sender;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  import proto.GameProto;
  import tools.manager.ConnectHandle;

  @ProcessClass(GameProto.ReqPlayCard.class)
  public class PlayCardHandler implements ConnectHandle {
      private static final Logger logger = LoggerFactory.getLogger(PlayCardHandler.class);

      @Override
      public void handle(Message message, Sender handler, int sequence, int transId) {
          GameProto.ReqPlayCard req = (GameProto.ReqPlayCard) message;
          int userId = transId;

          logger.info("收到玩家出牌请求, userId: {}, card: {}", userId, req.getCard());
          
          // 委托业务管理器执行核心流转
          TableManager.getInstance().handlePlayCard(userId, req, handler, sequence);
      }
  }
  ```

### 第四步：在 Manager 中编写具体业务与回包
- 在对应 Manager 中处理数据状态变更，并直接向 `handler`（`Sender`）回包：
  ```java
  GameProto.AckPlayCard.Builder ack = GameProto.AckPlayCard.newBuilder();
  ack.setSuccess(true);
  handler.sendMsg(ack.build(), sequence, userId);
  ```
- 若需向全桌广播，通过 `TableInfo.broadcast(ack.build())` 统一推送。

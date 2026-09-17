---
name: 300-message-guidelines
description: 消息协议全链路标准：Proto 定义、注解式常量绑定与 ConnectHandle 编写
alwaysApply: false
globs: "**/*.proto,**/proto/**,**/msg/**,**/handle/**"
---

# 消息与通信规范

`game-server` 采用基于 Protobuf 与注解扫描的轻量消息驱动模型。添加或修改消息接口必须遵循以下全链路标准。

## 1. 全链路流程速览

```text
1. 编写 .proto 消息结构体
       ↓ (执行 genProto.bat / genProto.sh)
2. 生成 Java Protobuf 类 (如 GameProto.java)
       ↓
3. 在 GMsg/LMsg/SMsg 常量类中添加消息 ID 与 @ClassField 绑定
       ↓
4. 创建 Handle 类，实现 ConnectHandle 并标注 @ProcessClass
       ↓
5. 在 Handle 中调用 Manager 完成逻辑并向 Sender 回包
```

## 2. 详细步骤与代码规范

### 步骤 1：Proto 文件编写与编译
- 目录：`proto/src/main/java/`
- 按所属领域放置：游戏逻辑放 `game.proto`，大厅逻辑放 `lobby.proto`，网关相关放 `gate.proto`，服务器内部交互放 `server.proto`。
- 修改完成后，在当前目录执行 `genProto.bat` (Windows) 或 `./genProto.sh` (Linux)，自动生成 Java 类。

### 步骤 2：消息常量与注解绑定
- 目录：`proto/src/main/java/msg/registor/message/`
- 在对应的消息常量类中新增常量定义（游戏服为 `GMsg`，大厅为 `LMsg`，系统为 `SMsg`）：
  ```java
  @ClassField(value = GameProto.ReqExampleAction.class, des = "示例操作请求")
  public static final int REQ_EXAMPLE_ACTION = CMsg.GAME_TYPE | 88;

  @ClassField(value = GameProto.AckExampleAction.class, des = "示例操作回复")
  public static final int ACK_EXAMPLE_ACTION = CMsg.GAME_TYPE | 89;
  ```
- **约束**：
  - 常量值必须使用对应的类型位掩码（如 `CMsg.GAME_TYPE | N`）；
  - 必须使用 `@ClassField` 注明绑定的 Proto 类与中文描述；
  - `HandleTypeRegister` 在服务启动时会自动扫描并建立双向映射，无需任何额外配置。

### 步骤 3：消息处理器（Handler）编写
- 继承规范：实现 `tools.manager.ConnectHandle` 接口。
- 绑定注解：必须标注 `@ProcessClass(绑定的Proto类.class)`。
- 处理方法：实现 `handle(Message message, Sender handler, int sequence, int transId)`。

示例代码模板：
```java
package com.cloud.hub.game.client.handle;

import com.google.protobuf.Message;
import msg.annotation.ProcessClass;
import net.client.Sender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.GameProto;
import tools.manager.ConnectHandle;

/**
 * 示例操作处理器
 */
@ProcessClass(GameProto.ReqExampleAction.class)
public class ExampleActionHandler implements ConnectHandle {

    private static final Logger logger = LoggerFactory.getLogger(ExampleActionHandler.class);

    @Override
    public void handle(Message message, Sender handler, int sequence, int transId) {
        GameProto.ReqExampleAction req = (GameProto.ReqExampleAction) message;
        int userId = transId;

        // 1. 参数防御性校验
        if (userId <= 0) {
            logger.warn("非法用户请求: {}", userId);
            return;
        }

        // 2. 委托业务管理器执行核心状态流转 (保持 Handler 简洁无状态)
        GameProto.AckExampleAction ack = ExampleManager.getInstance().doAction(userId, req);

        // 3. 回包
        if (ack != null && handler != null) {
            handler.sendMsg(ack, sequence, userId);
        }
    }
}
```

## 3. 通信与并发注意事项
- **Handler 无状态**：Handler 实例全局复用，**严禁**在 Handler 中声明与特定用户相关的成员变量。
- **状态流转下沉**：所有玩家数据读取与修改必须下沉到对应 `Manager`（如 `TableManager`、`UserManager`）中集中处理。
- **长连接生命周期**：断线后清理残留会话必须通过生命周期回调统一处理，严禁依赖 Handler 捕获的异常进行局部销毁。

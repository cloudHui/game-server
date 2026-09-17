---
name: proto-gen
description: >-
  为 game-server 编写、修改与编译 Protobuf 消息协议。
  当用户需要新增/修改 .proto 文件、重新生成 Java 协议源码或更新协议依赖时调用。
---

# Proto-Gen 协议生成与维护技能

## 何时使用
- 新增网络消息、修改接口协议字段；
- 需要将 `.proto` 重新编译生成 Java 类；
- 解决 Protobuf 字段解析或版本不一致问题。

## 协议文件分布
所有协议源文件与生成工具集中在 `proto/src/main/java/` 目录下：
- `const.proto`：通用枚举与基础常量
- `game.proto`：游戏核心桌子与对局协议
- `lobby.proto`：大厅、房间与用户信息协议
- `gate.proto`：网关握手与网络状态协议
- `server.proto`：服务间内部通信协议
- `model.proto`：公共数据结构定义

## 执行步骤

### 1. 规范编辑 proto 文件
- 保持 `proto3` 语法；
- 缩进 2 空格，大括号独立一行；
- 字段编号从 1 开始递增，已废弃字段使用 `reserved` 标记，禁止重用历史编号；
- 结构体遵循命名规则（请求 `Req*`，响应 `Ack*`，推送 `Not*`）。

### 2. 编译生成 Java 代码
根据当前运行环境进入对应目录执行：

- **Windows 环境**：
  ```cmd
  cd /d d:\code\st\game-server\proto\src\main\java
  genProto.bat
  ```
- **Linux 环境**：
  ```bash
  cd d:/code/st/game-server/proto/src/main/java
  chmod +x protoc-linux-x86_64 genProto.sh
  ./genProto.sh
  ```

### 3. 验证与排查
- 检查目标目录 `proto/src/main/java/proto/` 下对应的 `GameProto.java`、`LobbyProto.java` 是否已更新；
- 执行 `mvn compile -pl proto` 验证协议模块编译无误。

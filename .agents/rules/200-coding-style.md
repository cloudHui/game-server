---
name: 200-coding-style
description: 编码风格、注释标准、命名规范与 import 约束
alwaysApply: false
globs: "**/*.java,**/*.proto"
---

# 编码风格与注释规范

## 1. 通用规范
- **缩进**：Java 源码使用 4 空格缩进，禁止使用 Tab 字符；Proto 文件使用 2 空格缩进。
- **语句结束**：语句强制使用分号 `;` 结束。
- **编码与换行**：所有源文件字符编码必须使用 `UTF-8`，文件末尾保留一个空行。

## 2. Proto 协议规范
- 必须使用 `proto3` 语法。
- 大括号独立成行：
  ```proto3
  message ReqEnterTable
  {
    int32 tableId = 1;
    string password = 2;
  }
  ```
- 命名规范：
  - 客户端请求消息体使用 `Req*` 前缀（如 `ReqEnterTable`）；
  - 服务端回复消息体使用 `Ack*` 前缀（如 `AckEnterTable`）；
  - 服务端主动通知使用 `Not*` 前缀（如 `NotCard`, `NotTableState`）；
  - 枚举类首项必须为 0，且使用 `ALL_CAPS` 命名。

## 3. Import 排序规范
- 按以下顺序分组排列 import，组与组之间空一行：
  1. `java.*`
  2. `javax.*`
  3. `org.*`（如 `org.springframework.*`, `org.slf4j.*`）
  4. `com.*`（如 `com.cloud.*`）
  5. 本地包（如 `proto.*`, `msg.*`, `utils.*`）
- 同组内按字母顺序排序。
- **禁止使用通配符导入**（禁止 `import java.util.*;`），显式导入具体类。

## 4. 注释规范（三层强制要求）

> **核心原则**：生成或修改代码时，必须覆盖“字段、方法、关键代码块”三个层面。

### 4.1 字段注释
- 每一个成员变量必须有清晰注释，注明其**业务含义、单位、有效取值范围**。
- 集合/Map 字段必须标注键值对的业务含义：
  ```java
  /** 玩家已准备状态集合 <玩家UserId, 是否已准备> */
  private final Map<Integer, Boolean> readyMap = new ConcurrentHashMap<>();
  ```

### 4.2 函数注释
- 所有 `public` 和 `protected` 方法必须具备标准的 Javadoc 注释，清晰描述方法功能，并包含完整的 `@param` 与 `@return` 说明。
- 抛出非运行时异常的方法必须注明 `@throws` 及其发生场景。

### 4.3 逻辑代码块注释
- **解释“为什么”而非“做了什么”**：严禁流水账式注释（如 `// 返回结果`、`// i自增`）。
- 在复杂状态转换、分支判断、循环边界、异步回调之前，必须添加解释其业务意图的前置说明：
  ```java
  // 若房主掉线超过重连宽限期(60s)，需要优先将桌子控制权平移给座位号最小的在线玩家，防止死桌
  if (isOwnerTimeout(table)) {
      transferOwnershipToNextActiveUser(table);
  }
  ```

## 5. 命名约定
- **类名**：PascalCase（大驼峰，如 `TableManager`, `CreateTableHandler`）。
- **方法与变量名**：camelCase（小驼峰，如 `dealCreateSuccessTableJoin`, `userId`）。
- **常量**：ALL_CAPS（大写下划线，如 `REQ_ENTER_TABLE_MSG`, `DEFAULT_TIMEOUT_SECONDS`）。
- **类名后缀标准**：
  - 消息处理类：`*Handler.java` 或 `*Handle.java`
  - 业务单例管理类：`*Manager.java`
  - 配置与参数类：`*Config.java` / `*Properties.java`
  - 通用工具类：`*Util.java` / `*Utils.java`
  - 数据模型与实体类：`*Info.java` / `*Entity.java` / `*DTO.java`

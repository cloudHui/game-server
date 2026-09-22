# threadtutil 线程与亲和并发调度机制说明

`threadtutil` 是 `utils` 模块内的核心通用线程调度与定时工具包，广泛应用于 `hub`（一体化服务）及底层网关、大厅与游戏模块。

本文档详细拆解 Hub 服务中的**线程分流模型、单桌串行无锁架构、工作窃取（Work-Stealing）机制以及慢任务卡死告警体系**。

---

## 目录
- [一、Hub 全局线程分流架构](#一hub-全局线程分流架构)
- [二、单桌串行（Serial Execution）机制图解](#二单桌串行serial-execution机制图解)
- [三、工作窃取（Work-Stealing）机制图解](#三工作窃取work-stealing机制图解)
- [四、慢任务告警与卡死现场抓取](#四慢任务告警与卡死现场抓取)
- [五、外部统一调用 API 规范与矩阵](#五外部统一调用-api-规范与矩阵)
- [六、新旧接入模式对比（Before & After）](#六新旧接入模式对比before--after)
- [七、避坑准则与常见问题](#七避坑准则与常见问题)

---

## 一、Hub 全局线程分流架构

Hub 进程通过 `GameThreadPoolManager` 统筹**三大核心执行器**，实现**业务内存计算、阻塞数据库落盘与准时心跳脉冲的彻底物理隔离**：

### 1. 架构拓扑图

```mermaid
graph TD
    Client[客户端消息 Netty/WebSocket] --> NettyIO[网络 IO 线程<br/>只解码，不跑任何重业务]
    
    NettyIO -->|桌内对局操作 tableId| BusinessPool["【Game-Business 统一业务亲和池】<br/>32 物理线程, 基于哈希槽位无锁串行<br/>- 同桌任务按 tableId 串行<br/>- 个人属性按 userId 串行<br/>- 全局建桌按 groupId=0 串行"]
    NettyIO -->|玩家个人请求 userId| BusinessPool
    NettyIO -->|全局建桌/解散| BusinessPool
    
    Scheduler["【Game-TableScheduler】<br/>单线程准时脉冲源"] -->|周期心跳 Tick| BusinessPool
    
    BusinessPool -->|异步持久化| DBPool["【Game-Database 阻塞落盘池】<br/>战绩入库/SQLite 落盘 (阻塞 IO 专用)"]
```

### 2. 线程资源分配与职责说明

| 执行器名称 | 类型与配置 | 核心职责与并发保证 | 严禁行为 |
| :--- | :--- | :--- | :--- |
| **`Game-Business`**<br/>(统一业务亲和池) | `ExecutorPool`<br/>32 线程, 10万容量有界队列 | **纯内存状态机与业务逻辑**：整合原 Table、Player 与 TableManager，不同业务主体通过哈希槽位并行无锁调度，同 ID 绝对串行 | **严禁任何阻塞 IO**（如查数据库、网络 HTTP/RPC、Thread.sleep） |
| **`Game-Database`**<br/>(数据库阻塞池) | `ExecutorService`<br/>固定线程池（默认 4~8 线程） | **阻塞 IO 异步持久化**：SQLite 写入、战绩落盘、回放日志存储，彻底隔离慢 SQL | 严禁与桌内状态同步调用绑定 |
| **`Game-TableScheduler`**<br/>(定时心跳脉冲源) | `ScheduledExecutorService`<br/>**单物理线程** | **高精度时钟源**：以固定频率发出脉冲（带 tickBusy 防堆积），产生后立即投回 BusinessPool | 严禁在调度线程内直接执行任何业务逻辑 |

> **架构收敛优势**：
> 相比原先 Table、Player、Manager 分立导致的 64+ 系统物理线程浪费与剧烈的线程上下文切换，收敛为单个 32 线程亲和池后，CPU 利用率更高，槽位利用更均衡，且依靠槽位哈希保证串行无锁。

---

## 二、单桌串行（Serial Execution）机制图解

### 1. 为什么必须单桌串行？
牌桌（Table）是典型的有状态聚合根。若两个玩家同时出牌，或在心跳结算瞬间又有玩家离桌，传统加锁（`synchronized / Lock`）极易引发死锁与剧烈的线程上下文切换。

`ExecutorPool` 借鉴 **Actor / Mailbox 机制**：**“桌内状态绝对不加排他锁，所有同一桌的任务全部按哈希映射到同一槽位无锁队列，任意时刻仅由一个 Worker 物理线程排他执行。”**

### 2. 内存模型与槽位映射图

```text
【业务桌子】                       【任务队列槽位 (taskLists)】              【底层物理线程池】
 
 +--------------+
 | 桌子 1000 号 | --(floorMod 取模 0)--> [ 槽位 0 : TaskList ] <========= [ 线程 Thread-1 ]
 +--------------+                        | [任务A] -> [任务B]   |               (持有令牌，排他消费槽位 0)
                                         +---------------------+
 +--------------+
 | 桌子 1001 号 | --(floorMod 取模 1)--> [ 槽位 1 : TaskList ] <========= [ 线程 Thread-2 ]
 +--------------+                        | [任务C]              |               (持有令牌，排他消费槽位 1)
                                         +---------------------+
 +--------------+
 | 桌子 1002 号 | --(floorMod 取模 2)--> [ 槽位 2 : TaskList ]              [ 线程 Thread-3 ]
 +--------------+                        | [任务D] -> [任务E]   |               (空闲，正发起工作窃取)
                                         +---------------------+
                                         [ 槽位 3 : TaskList ]              [ 线程 Thread-4 ]
                                         | (空队列)            |               (空闲)
                                         +---------------------+
```

### 3. 单槽排他执行时序流程

```mermaid
sequenceDiagram
    autonumber
    participant Client as 客户端请求 (出牌)
    participant TaskList as 槽位队列 (TaskList)
    participant Worker1 as 线程 Thread-1
    participant Worker2 as 线程 Thread-2 (并发)
    participant Table as 牌桌 Table 实例

    Client->>TaskList: 1. offer(task) 任务入队
    Note over TaskList: scheduled.compareAndSet(false, true)<br/>首次入队触发拉起 Worker
    
    Worker1->>TaskList: 2. getProcessingAuthority(threadId)
    Note over TaskList: CAS: 0L -> 1 (成功抢占令牌钥匙！)
    
    par 并发尝试
        Worker2->>TaskList: 2.1 getProcessingAuthority(threadId)
        Note over TaskList: 检查已被 Thread-1 占用 -> 放弃退出！
    and 排他执行
        Worker1->>TaskList: 3. poll() 依次取出任务
        Worker1->>Table: 4. 执行业务逻辑 (桌内无锁修改)
        Worker1->>TaskList: 5. 消费排空，finally 释放令牌 (CAS: 1 -> 0L)
        Worker1->>TaskList: 6. finishAndRescheduleIfNeeded()<br/>边缘检查防止退出瞬间漏单
    end
```

---

## 三、工作窃取（Work-Stealing）机制图解

### 1. 核心定义：窃取的是“队列”，不是“单个任务”
> [!IMPORTANT]
> **绝对准则**：系统绝不会从“正被其他线程处理”的队列中偷取单个任务，否则会破坏单桌串行。
> **窃取的真正含义**：**闲置线程主动去接管【积压了任务、但当前完全无线程在处理的空闲队列】**。

### 2. 工作窃取运转场景图

```text
 假设此时：
  - 线程 Thread-1 刚跑完了【槽位 0】的所有任务，变成空闲；
  - 槽位 1 正在被 Thread-2 消费 (isBusy = true)；
  - 槽位 2 堆积了 2 个任务，但由于线程池调度延迟，暂无线程进入 (isBusy = false)；
  - 槽位 3 为空队列。

 [ 线程 Thread-1 ] 结束槽位 0 后，立即发起环形巡视：
 
   巡视第 1 站：【槽位 1】
   [ 槽位 1 ] ------------> 发现：Thread-2 正在忙碌 (isBusy=true)
                            处理：Thread-1 礼貌路过，绝不插手！
 
   巡视第 2 站：【槽位 2】
   [ 槽位 2 ] ------------> 发现：有堆积任务 (isNotEmpty) 且 无人处理 (!isBusy)
                            动作：Thread-1 抢下槽位 2 的处理权令牌！
                                  整槽接管并全部消费掉（这就是窃取）！
 
   巡视第 3 站：【槽位 3】
   [ 槽位 3 ] ------------> 发现：是空的队列 (isEmpty)
                            处理：跳过。
```

---

## 四、慢任务告警与卡死现场抓取

`ExecutorPool` 内置了两级执行时间雷达，杜绝任何因死循环、死锁或慢 SQL 拖死 Worker 线程的情况：

```text
 [任务开始执行]
       │
       ▼
 [耗时 > 5000ms]  ──► 触发一级告警：打印 ERROR 慢任务警告，输出 slot、持有线程 ID 及耗时
       │
       ▼
 [耗时 > 60000ms] ──► 触发二级快照：判定为严重死锁/卡死，直接抓取并 Dump 现场完整线程堆栈！
```

日志输出示例：
```log
2026-09-22 14:30:00 ERROR [ExecutorPool 慢任务警告] 槽位:3 处理时间过长! 持有线程:[15:Game-Table_3] 已持续耗时:5230ms
2026-09-22 14:31:00 ERROR 卡死线程堆栈 Dump [ID:15 Name:Game-Table_3]:
  at com.cloud.hub.game.domain.mj.MjPlayService.calculateFan(MjPlayService.java:188)
  at com.cloud.hub.game.domain.table.Table.tableLoop(Table.java:410)
  ...
```

---

## 五、外部统一调用 API 规范与矩阵

经演化重构后，外部业务无需再声明任何内部类，通过统一的重载方法与高阶辅助器进行调用：

### 1. 核心调度方法速查（API 矩阵）

| 调用场景 | 核心方法 | 返回类型 | 适用职责与特性 |
| :--- | :--- | :--- | :--- |
| **牌桌串行任务** | `table.execute(Runnable)` | `CompletableFuture<Void>` | 状态机流转、打牌、摸牌等无返回值写操作 |
| **牌桌计算/查询** | `table.execute(Supplier<T>)` | `CompletableFuture<T>` | 牌桌只读快照、结算数据获取等有返回值操作 |
| **带切面排错任务** | `table.execute(String, Runnable)` | `CompletableFuture<Void>` | 自动打印操作名与 tableId 的异常切面，无需手写 `.exceptionally` |
| **Handler 调度（静默）** | `TableHandlerHelper.dispatch(tableId, name, action)` | `boolean` (恒为 true) | 查桌、判空、桌串行投递、异常隔离四合一，桌不存在时静默忽略 |
| **Handler 调度（回错）** | `TableHandlerHelper.dispatchOrReplyNull(sender, tableId, name, action)` | `boolean` (恒为 true) | 查桌并调度；若桌不存在则自动向客户端发送 `TABLE_NULL_VALUE` 错误码 |
| **玩家独立会话串行** | `pool.serialExecute(userId, Runnable/Supplier)` | `CompletableFuture<Void/T>` | 个人属性变动、背包购买，同 userId 绝对串行防并发 |
| **周期心跳调度** | `pools.scheduleTable(tableId, tick, delay, interval)` | `ScheduledFuture<?>` | 牌桌周期 Tick，自带 `tickBusy` 防上一拍堆积 |

---

### 2. 牌桌与玩家端标准调用示例

```java
// 1. 同桌串行投递（写操作）
table.execute(() -> {
    table.processPlayCard(userId, card);
});

// 2. 同桌串行计算（读操作，直接异步链式流转）
table.execute(() -> table.buildRoomTableInfo())
     .thenAccept(info -> sender.sendMessage(info));

// 3. 自带语义化异常切面的桌投递（发生未捕获异常时自动打印 ERROR 日志）
table.execute("玩家出牌", () -> {
    table.processPlayCard(userId, card);
});

// 4. 玩家个人会话串行投递（传入 userId 与 Lambda）
executorPool.serialExecute(userId, () -> {
    player.updateScore(100);
});

// 5. 普通非串行异步任务（直接扔到底层物理线程池）
executorPool.execute(() -> {
    logger.info("异步统计上报");
});
```

---

### 3. Handler 层极简接入范式 (`TableHandlerHelper`)

针对所有接收客户端请求并需要操作牌桌的 Handler，优先使用 `TableHandlerHelper`：

#### 场景 A：只读快照或正常业务处理（查无此桌直接忽略或由上层处理）
```java
@ProcessType(GMsg.REQ_TABLE_SNAPSHOT)
public class ReqTableSnapshotHandle implements Handler {
    @Override
    public boolean handler(Sender sender, int clientId, Message message, long mapId, int sequence) {
        GameProto.ReqTableSnapshot req = (GameProto.ReqTableSnapshot) message;
        return TableHandlerHelper.dispatch(req.getTableId(), "生成牌桌快照", table -> {
            TableUser viewer = table.getUsers().get(clientId);
            if (viewer == null) return;
            sender.sendMessage(clientId, GMsg.ACK_TABLE_SNAPSHOT,
                    table.getTableId(), table.buildTableSnapshot(viewer), sequence);
        });
    }
}
```

#### 场景 B：严格桌内操作（若桌子不存在需明确向客户端回送错误码）
```java
@ProcessType(GMsg.REQ_OP)
public class ReqOpHandle implements Handler {
    @Override
    public boolean handler(Sender sender, int clientId, Message message, long mapId, int sequence) {
        GameProto.ReqOp request = (GameProto.ReqOp) message;
        // 若桌子不存在，dispatchOrReplyNull 会自动回发 TABLE_NULL_VALUE 并打日志
        return TableHandlerHelper.dispatchOrReplyNull(sender, mapId, "玩家操作", table -> {
            int result = table.processOp(clientId, request.getOp(), sender, mapId, sequence);
            replyOp(sender, clientId, mapId, sequence, request.getOp(), result);
        });
    }
}
```

---

### 4. 周期调度与防堆积标准调用

```java
// 每 200ms 一拍，心跳任务内部自带 tickBusy 防堆积
ScheduledFuture<?> loop = pools.scheduleTable(tableId, table::tableLoop, 1000, 200);

// 停桌时取消调度
pools.cancelTableSchedule(loop);
```

---

## 六、新旧接入模式对比（Before & After）

| 对比维度 | 改造前写法 | 改造后推荐写法 | 优化收益 |
| :--- | :--- | :--- | :--- |
| **异步计算与查询** | 外部声明变量并在 Lambda 内 `.complete(res)`，再手写 `.completeExceptionally` | `return execute(this::buildRoomTableInfo);` | 消除 6 行胶水代码，代码清晰度提升 100% |
| **Handler 业务接入** | 手动 `tableManager.getTable` + 判空回包 + 手写 `table.execute` + 手写 `.exceptionally` | 单行调用 `TableHandlerHelper.dispatch` 或 `dispatchOrReplyNull` | 消除 70% 重复模板代码，彻底杜绝漏写判空与异常未捕获 |
| **异常现场排错** | 各处手写 logger，日志格式不一且常缺少桌号或操作名 | `table.execute("操作名", task)` 统一拦截并记录上下文 | 统一格式，便于 ELK / 日志切片精准过滤定位 |

---

## 七、避坑准则与常见问题

1. **同资源必须同 Key**：
   凡是修改同一桌子状态的操作（出牌、入桌、离桌、超时判定），必须传同一个 `tableId`，确保进入同一个串行槽位。
2. **串行任务里绝不跑阻塞 IO**：
   写数据库、调 HTTP/RPC、`Thread.sleep()` 必须投递到 `databasePool` 或另外异步处理，严禁阻塞槽位 Worker。
3. **队列满时的反压行为（CallerRuns）**：
   当队列超过有界容量（10 万）时，底层采用 `CallerRunsPolicy`，由提交方线程（如网络 IO 线程）直接代为执行。这会自然放慢网络接收速率，形成正向背压，保护服务端不发生 OOM。
4. **负数 groupId 安全性**：
   内部使用 `Math.floorMod(groupId, slots)`，完全兼容 `hashCode` 为负数或 `Integer.MIN_VALUE` 的情况，永不数组越界。

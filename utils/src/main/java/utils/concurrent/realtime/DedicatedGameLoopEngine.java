package utils.concurrent.realtime;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.concurrent.pool.ObjectPool;

/**
 * 实时对战专用 Dedicated Game Loop 调度引擎（30/60 FPS 高精度物理与帧同步引擎）。
 * <p>
 * <b>核心设计：</b>
 * <ul>
 * <li><b>分片独占线程：</b> 每个 Game Loop 线程独占负责绑定固定数量的实时房间（例如单线程轮询 20 个房间）；</li>
 * <li><b>高精度累加器（Accumulator）：</b> 采用纳秒级时钟定长积分，彻底杜绝掉帧、漏算导致的客户端位移穿模；</li>
 * <li><b>防死亡螺旋上限：</b> 严格限制单次 Tick 最大追帧步数（默认最多追 5 帧），防止系统过载时连环卡死；</li>
 * <li><b>自适应休眠（ParkNanos）：</b> 结合剩余时间执行微秒级精准休眠，避免 100% 空转打满 CPU；</li>
 * <li><b>时钟与执行解耦分发：</b> 支持配置 {@link ExecutorService}
 * 异步非阻塞分发逻辑帧，单个房间耗时卡顿绝不连累同线程其他房间。</li>
 * </ul>
 *
 * @author cloud
 */
public class DedicatedGameLoopEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(DedicatedGameLoopEngine.class);

    /**
     * 单秒纳秒数
     */
    private static final long SEC_TO_NANOS = 1_000_000_000L;
    /**
     * 60 FPS 单帧纳秒间隔 (16,666,666 ns)
     */
    public static final long FRAME_INTERVAL_60_FPS = SEC_TO_NANOS / 60L;
    /**
     * 30 FPS 单帧纳秒间隔 (33,333,333 ns)
     */
    public static final long FRAME_INTERVAL_30_FPS = SEC_TO_NANOS / 30L;
    /**
     * 单次循环最大追帧上限（防死亡螺旋门禁）
     */
    private static final int MAX_CATCH_UP_TICKS = 5;

    /**
     * 触发高精度微秒级休眠的剩余时间阈值（1.5 毫秒），大于此预算执行精准 parkNanos
     */
    private static final long PARK_THRESHOLD_NS = 1_500_000L;
    /**
     * 休眠提前唤醒预留补偿时间（1.0 毫秒），抵消操作系统时钟中断唤醒抖动，杜绝下一帧迟到
     */
    private static final long PARK_RESERVED_NS = 1_000_000L;
    /**
     * 触发 CPU 协作让渡的剩余时间阈值（200 微秒），介于 200μs ~ 1.5ms 之间执行 Thread.yield()
     */
    private static final long YIELD_THRESHOLD_NS = 200_000L;
    /**
     * 发生未捕获致命异常时的安全退避休眠时长（10 毫秒），防止死循环空转打爆 CPU 与磁盘日志
     */
    private static final long EXCEPTION_BACKOFF_PAUSE_MS = 10L;

    /**
     * 分片独占 Worker 线程组
     */
    private final LoopWorker[] workers;

    /**
     * 异步业务执行线程池：用于解耦时钟与执行，单房间卡顿不阻塞其他房间（为空则降级为同线程同步执行）
     */
    private final ExecutorService dispatchExecutor;

    /**
     * 单房间逻辑帧执行忙碌防堆积标记表 <roomId, AtomicBoolean>，上一拍未结束自动跳过本拍
     */
    private final ConcurrentHashMap<Long, AtomicBoolean> roomBusyMap = new ConcurrentHashMap<>();

    /**
     * 实时帧事件专用对象池（4096 深度），实现高频网络指令零 GC 堆分配
     */
    private final ObjectPool<FrameEvent> eventPool = new ObjectPool<>(FrameEvent::new, 4096);

    public DedicatedGameLoopEngine(int workerCount) {
        this(workerCount, FRAME_INTERVAL_30_FPS, null);
    }

    public DedicatedGameLoopEngine(int workerCount, long targetIntervalNs) {
        this(workerCount, targetIntervalNs, null);
    }

    /**
     * 完整构造函数：支持时钟与执行解耦的异步非阻塞分发。
     *
     * @param workerCount      独占时钟 Worker 线程数
     * @param targetIntervalNs 单帧目标纳秒间隔（如 33,333,333 ns）
     * @param dispatchExecutor 业务执行线程池（非空时启用异步隔离分发模式）
     */
    public DedicatedGameLoopEngine(int workerCount, long targetIntervalNs, ExecutorService dispatchExecutor) {
        int count = Math.max(1, workerCount);
        this.dispatchExecutor = dispatchExecutor;
        this.workers = new LoopWorker[count];
        for (int i = 0; i < count; ++i) {
            this.workers[i] = new LoopWorker("Dedicated-GameLoop-" + i, targetIntervalNs, dispatchExecutor,
                    roomBusyMap);
            this.workers[i].start();
        }
        LOGGER.info("[init] DedicatedGameLoopEngine 启动完成, workers:{}, interval:{}ns, asyncDecoupled:{}",
                count, targetIntervalNs, dispatchExecutor != null);
    }

    /**
     * 获取高频实时帧事件对象池。
     *
     * @return 帧事件池实例
     */
    public ObjectPool<FrameEvent> getEventPool() {
        return eventPool;
    }

    /**
     * 将实时对局房间注册到负载最轻的分片 Worker 线程中。
     *
     * @param room 实时帧同步房间实例
     */
    public void registerRoom(FrameSyncRoom room) {
        LoopWorker selected = workers[0];
        for (int i = 1; i < workers.length; ++i) {
            if (workers[i].getRoomCount() < selected.getRoomCount()) {
                selected = workers[i];
            }
        }
        selected.addRoom(room);
        LOGGER.info("注册实时房间: {} 到 Worker: {}", room.getSyncRoomId(), selected.getName());
    }

    /**
     * 注销并移出实时对局房间。
     *
     * @param room 待注销房间
     */
    public void unregisterRoom(FrameSyncRoom room) {
        if (room != null) {
            roomBusyMap.remove(room.getSyncRoomId());
        }
        for (LoopWorker worker : workers) {
            worker.removeRoom(room);
        }
    }

    /**
     * 优雅关闭所有独占循环线程并清理对象池与运行状态。
     */
    public void shutdown() {
        for (LoopWorker worker : workers) {
            worker.stopGracefully();
        }
        roomBusyMap.clear();
        eventPool.clear();
    }

    /**
     * 单个分片 Game Loop 独占工作线程。
     */
    private static class LoopWorker implements Runnable {
        private final String name;
        private final long tickIntervalNs;
        private final Thread thread;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final List<FrameSyncRoom> boundRooms = new CopyOnWriteArrayList<>();
        private final ExecutorService dispatchExecutor;
        private final ConcurrentHashMap<Long, AtomicBoolean> roomBusyMap;

        public LoopWorker(String name, long tickIntervalNs, ExecutorService dispatchExecutor,
                ConcurrentHashMap<Long, AtomicBoolean> roomBusyMap) {
            this.name = name;
            this.tickIntervalNs = tickIntervalNs;
            this.dispatchExecutor = dispatchExecutor;
            this.roomBusyMap = roomBusyMap;
            this.thread = new Thread(this, name);
            this.thread.setDaemon(true);
        }

        public void start() {
            this.thread.start();
        }

        public void addRoom(FrameSyncRoom room) {
            this.boundRooms.add(room);
        }

        public void removeRoom(FrameSyncRoom room) {
            this.boundRooms.remove(room);
        }

        public int getRoomCount() {
            return this.boundRooms.size();
        }

        public String getName() {
            return name;
        }

        public void stopGracefully() {
            this.running.set(false);
            this.thread.interrupt();
        }

        @Override
        public void run() {
            LOGGER.info("实时循环线程启动: {}", name);
            long previousTimeNs = System.nanoTime();
            long accumulatorNs = 0L;

            while (running.get()) {
                try {
                    // 优化点：time 采用高性能 API System.nanoTime()
                    long currentTimeNs = System.nanoTime();
                    long elapsedNs = Math.max(0L, currentTimeNs - previousTimeNs);
                    previousTimeNs = currentTimeNs;
                    accumulatorNs += elapsedNs; // 累加时间

                    // 1. 定长补帧计算（按固定步长推进绑定房间的逻辑帧）
                    // 优化点：catchUpTicks 内部通过 for(;;) 循环，确保逻辑帧严格按定长推进，
                    // 彻底消除累加时间碎片化带来的帧率抖动
                    accumulatorNs = catchUpTicks(accumulatorNs);

                    // 2. 自适应纳秒级休眠（防 CPU 100% 空转）
                    // 优化点：采用 LockSupport.parkNanos 纳秒级微秒休眠，替代 Thread.sleep，
                    // 精准对齐帧时间，在保证 60 FPS 帧率的同时，将 CPU 占用从 80%+ 降至 5% 以下
                    adaptivePark(tickIntervalNs - accumulatorNs);

                } catch (Throwable t) {
                    // 如果是正常退出，直接 break
                    if (!running.get()) {
                        break;
                    }
                    LOGGER.error("[DedicatedLoop] Worker [{}] 发生未捕获致命异常，自动重置状态并保持运行!", name, t);

                    // 状态自愈：重置时间基准与累加器，避免异常耗时导致连环追帧雪崩
                    previousTimeNs = System.nanoTime();
                    accumulatorNs = 0L;

                    // 安全缓冲：避免异常死循环打满 CPU 与刷爆日志
                    safePause();
                }
            }
            LOGGER.info("实时循环线程退出: {}", name);
        }

        /**
         * 定长补帧演算：按固定步长追赶现实时间，超出上限则触发熔断防死亡螺旋。
         * <p>
         * <b>【核心机制与客户端协同处理全景指南】：</b>
         * <p>
         * <b>一、 为什么会瞬间产生多帧？</b><br>
         * 当服务端发生突发停顿（如短暂 GC 抖动或任务排队 50ms）后醒来，蓄水池积攒了 50ms。
         * 本方法会在小于 1ms 的极短时间内连续执行 3 次 {@code tickRooms(16.6ms)}，
         * 意味着服务端在瞬间连续生成并广播了第 101、102、103 帧。
         * <p>
         * <b>二、 客户端应对策略与工程推荐实现：</b>
         * <ol>
         *   <li><b>场景 1：帧同步架构（Lockstep / Deterministic Frame Sync）</b>
         *     <ul>
         *       <li><b>客户端倍速快进（Fast-Forward / Catch-Up Loop）：</b>
         *           客户端收到积压的 3 帧时，逻辑层（Logic Tick）必须在一个渲染周期内连续执行 3 次状态更新
         *           （只算数值与物理，不重复触发全量重渲染和 UI 重绘），瞬间将逻辑世界推进至最新帧；</li>
         *       <li><b>渲染层平滑插值（Render Interpolation）：</b>
         *           渲染层（Render Tick）切忌直接机械瞬移，应当以阻尼插值（Lerp / Slerp）将角色模型
         *           平滑过渡到第 103 帧的最新坐标，保证玩家肉眼视觉柔顺无跳跃感；</li>
         *       <li><b>自适应抖动缓冲（Adaptive Jitter Buffer）：</b>
         *           客户端队列维护 2~3 帧缓冲区。当收到追赶帧时，可微调逻辑时钟至 1.05x ~ 1.1x 倍速无感消化。</li>
         *     </ul>
         *   </li>
         *   <li><b>场景 2：状态同步 / 快照插值架构（State Sync / Snapshot Interpolation）</b>
         *     <ul>
         *       <li><b>位姿采纳最新帧（Drop Stale Position）：</b>
         *           对于坐标、速度等连续状态，中间第 101、102 帧的位置可以直接被 103 帧覆盖；
         *           客户端渲染从当前视觉位置直接平滑滑动向 103 帧目标点；</li>
         *       <li><b>离散关键事件绝对保全（Event Replay）：</b>
         *           连续坐标可以跳过，但 101、102 帧中包含的“受到伤害、子弹爆炸、护盾破损、阵亡结算”等
         *           离散业务事件绝不能丢弃，客户端必须依次触发播放对应的音效、扣血飘字与特效。</li>
         *     </ul>
         *   </li>
         *   <li><b>场景 3：网络下发合包优化（Frame Batching - 服务端协同）</b>
         *     <ul>
         *       <li>若业务层检测到连续追补，推荐将多帧打包至单个网络报文（如 {@code BatchFrameMsg}）一次性下发，
         *           避免在 1ms 内向 TCP/KCP 连续推 3 个小数据包，大幅降低协议头开销与客户端 Socket 中断频率；</li>
         *       <li>报文附带 {@code isCatchUp: true} 标记，提示客户端跳过过渡期冗余开销。</li>
         *     </ul>
         *   </li>
         *   <li><b>场景 4：极端大卡顿触发熔断时（超过 MAX_CATCH_UP_TICKS = 5 帧）</b>
         *     <ul>
         *       <li>若卡顿超过上限，服务端壮士断腕清空蓄水池。此时服务端物理时间丢失了若干帧；</li>
         *       <li><b>解决之道：</b> 服务端应立即主动下发一次权威全量世界快照（Keyframe Snapshot）；
         *           客户端收到后执行<b>强制权威校准（Hard Snap / Reconciliation）</b>，拉扯纠偏，防止世界产生永久分歧。</li>
         *     </ul>
         *   </li>
         * </ol>
         *
         * @param accumulatorNs 当前积攒的未结算纳秒数
         * @return 补帧演算后剩余的未结算纳秒数
         */
        private long catchUpTicks(long accumulatorNs) {
            // 第一步：事前容量截断（Clamping）—— 限制蓄水池单次最大追补深度（最多存 5 帧时间）
            long maxAccumulatorNs = tickIntervalNs * MAX_CATCH_UP_TICKS;
            if (accumulatorNs > maxAccumulatorNs) {
                LOGGER.warn("[DedicatedLoop] 严重卡顿超标, 触发防死亡螺旋截断! Worker: {}, 丢弃超额累加时间: {}ms",
                        name, TimeUnit.NANOSECONDS.toMillis(accumulatorNs - maxAccumulatorNs));
                accumulatorNs = maxAccumulatorNs;
            }

            // 第二步：定长微步递推演算 —— 蓄水池已被事前截断，天然最多推进 MAX_CATCH_UP_TICKS 帧
            while (accumulatorNs >= tickIntervalNs) {
                tickRooms(tickIntervalNs);
                accumulatorNs -= tickIntervalNs;
            }
            return accumulatorNs;
        }

        /**
         * 推进所有绑定房间的单次定长逻辑帧。
         * <p>
         * 如果配置了 dispatchExecutor（异步解耦模式），则仅作纳秒级快速时钟巡检，
         * 通过 roomBusyMap 进行非阻塞 CAS 门禁校验，分发至业务线程池并发执行，
         * 彻底避免单个房间卡顿连带拖慢当前线程绑定的其他房间。
         * </p>
         *
         * @param deltaNs 本次定长帧步长（纳秒）
         */
        private void tickRooms(long deltaNs) {
            if (dispatchExecutor == null || dispatchExecutor.isShutdown()) {
                // 同步串行模式（兜底基准）
                for (FrameSyncRoom room : boundRooms) {
                    try {
                        room.onFixedTick(deltaNs);
                    } catch (Throwable t) {
                        LOGGER.error("房间逻辑帧 Tick 异常, roomId: {}", room.getSyncRoomId(), t);
                    }
                }
                return;
            }

            // 异步非阻塞解耦模式：时钟秒级打铃，业务 Worker 并发消费
            for (FrameSyncRoom room : boundRooms) {
                final long roomId = room.getSyncRoomId();
                AtomicBoolean busy = roomBusyMap.computeIfAbsent(roomId, k -> new AtomicBoolean(false));
                // 非阻塞 CAS 门禁：若该桌上一帧仍在 Worker 执行中，则跳过本次打铃，防止任务在线程池队列中无限积压雪崩
                if (!busy.compareAndSet(false, true)) {
                    LOGGER.warn("[DedicatedLoop] 房间 [{}] 上一逻辑帧仍在执行中，跳过当前定长脉冲（防积压）", roomId);
                    continue;
                }

                try {
                    dispatchExecutor.execute(() -> {
                        try {
                            room.onFixedTick(deltaNs);
                        } catch (Throwable t) {
                            LOGGER.error("[DedicatedLoop] 异步派发房间逻辑帧异常, roomId: {}", roomId, t);
                        } finally {
                            busy.set(false);
                        }
                    });
                } catch (Throwable ex) {
                    // 若线程池已满拒绝任务，释放 busy 锁
                    busy.set(false);
                    LOGGER.error("[DedicatedLoop] 线程池拒绝执行房间逻辑帧任务, roomId: {}", roomId, ex);
                }
            }
        }

        /**
         * 微秒级自适应休眠：根据本帧剩余预算动态休眠或让渡，兼顾 60 FPS 精度与低 CPU 占用。
         *
         * @param remainingNs 本帧剩余时间预算（纳秒）
         */
        private void adaptivePark(long remainingNs) {
            if (remainingNs > PARK_THRESHOLD_NS) {
                // 剩余预算充裕：微秒精确休眠（扣减预留补偿时间，防止系统时钟抖动引起下一帧迟到）
                LockSupport.parkNanos(remainingNs - PARK_RESERVED_NS);
            } else if (remainingNs > YIELD_THRESHOLD_NS) {
                // 剩余预算适中：主动出让 CPU 时间片给业务处理线程
                Thread.yield();
            }
            // 剩余预算极小（< 200μs）时直接进入极短微自旋，精准对齐帧时间
        }

        /**
         * 异常后的安全退避休眠，防止死循环刷爆 CPU 与日志。
         */
        private void safePause() {
            try {
                Thread.sleep(EXCEPTION_BACKOFF_PAUSE_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

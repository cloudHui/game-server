package utils.concurrent.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * 实时对战专用 Dedicated Game Loop 调度引擎（30/60 FPS 高精度物理与帧同步引擎）。
 * <p>
 * <b>核心设计：</b>
 * <ul>
 * <li><b>分片独占线程：</b> 每个 Game Loop 线程独占负责绑定固定数量的实时房间（例如单线程轮询 20 个房间）；</li>
 * <li><b>高精度累加器（Accumulator）：</b> 采用纳秒级时钟定长积分，彻底杜绝掉帧、漏算导致的客户端位移穿模；</li>
 * <li><b>防死亡螺旋上限：</b> 严格限制单次 Tick 最大追帧步数（默认最多追 5 帧），防止系统过载时连环卡死；</li>
 * <li><b>自适应休眠（ParkNanos）：</b> 结合剩余时间执行微秒级精准休眠，避免 100% 空转打满 CPU。</li>
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

    public DedicatedGameLoopEngine(int workerCount) {
        this(workerCount, FRAME_INTERVAL_30_FPS);
    }

    public DedicatedGameLoopEngine(int workerCount, long targetIntervalNs) {
        int count = Math.max(1, workerCount);
        this.workers = new LoopWorker[count];
        for (int i = 0; i < count; ++i) {
            this.workers[i] = new LoopWorker("Dedicated-GameLoop-" + i, targetIntervalNs);
            this.workers[i].start();
        }
        LOGGER.info("[init] DedicatedGameLoopEngine 启动完成, workers:{}, interval:{}ns",
                count, targetIntervalNs);
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
        LOGGER.info("注册实时房间: {} 到 Worker: {}", room.getRoomId(), selected.getName());
    }

    /**
     * 注销并移出实时对局房间。
     *
     * @param room 待注销房间
     */
    public void unregisterRoom(FrameSyncRoom room) {
        for (LoopWorker worker : workers) {
            worker.removeRoom(room);
        }
    }

    /**
     * 优雅关闭所有独占循环线程。
     */
    public void shutdown() {
        for (LoopWorker worker : workers) {
            worker.stopGracefully();
        }
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

        public LoopWorker(String name, long tickIntervalNs) {
            this.name = name;
            this.tickIntervalNs = tickIntervalNs;
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
                    long currentTimeNs = System.nanoTime();
                    long elapsedNs = Math.max(0L, currentTimeNs - previousTimeNs);
                    previousTimeNs = currentTimeNs;
                    accumulatorNs += elapsedNs;

                    // 1. 定长补帧计算（按固定步长推进绑定房间的逻辑帧）
                    accumulatorNs = catchUpTicks(accumulatorNs);

                    // 2. 自适应纳秒级休眠（防 CPU 100% 空转）
                    adaptivePark(tickIntervalNs - accumulatorNs);

                } catch (Throwable t) {
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
         *
         * @param accumulatorNs 当前积攒的未结算纳秒数
         * @return 补帧演算后剩余的未结算纳秒数
         */
        private long catchUpTicks(long accumulatorNs) {
            int catchUpCount = 0;
            while (accumulatorNs >= tickIntervalNs) {
                if (catchUpCount++ >= MAX_CATCH_UP_TICKS) {
                    LOGGER.warn("[DedicatedLoop] 严重卡顿超标, 触发防死亡螺旋截断! Worker: {}, 丢弃累加时间: {}ms",
                            name, TimeUnit.NANOSECONDS.toMillis(accumulatorNs));
                    return 0L;
                }
                tickRooms(tickIntervalNs);
                accumulatorNs -= tickIntervalNs;
            }
            return accumulatorNs;
        }

        /**
         * 推进所有绑定房间的单次定长逻辑帧。
         *
         * @param deltaNs 本次定长帧步长（纳秒）
         */
        private void tickRooms(long deltaNs) {
            for (FrameSyncRoom room : boundRooms) {
                try {
                    room.onFixedTick(deltaNs);
                } catch (Throwable t) {
                    LOGGER.error("房间逻辑帧 Tick 异常, roomId: {}", room.getRoomId(), t);
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

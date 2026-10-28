package com.cloud.hub.game;

import com.cloud.hub.game.config.GameRuntimeConfig;
import com.cloud.hub.game.db.ScoreRepository;
import com.cloud.hub.game.manager.TableManager;
import com.cloud.hub.game.manager.thread.GameThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.concurrent.thread.ExecutorPool;
import utils.concurrent.thread.Task;

/**
 * 对局服务核心运行时门面单例。
 * <p>
 * 统筹管理三权分立全局线程调度器 {@link GameThreadPoolManager} 与游戏桌管理器 {@link TableManager}。
 * 在一体化架构中以嵌入式模式 (Embedded) 启动运行，与大厅同进程协同工作。
 * </p>
 *
 * @author cloud
 */
public class Game {

    private static final Logger logger = LoggerFactory.getLogger(Game.class);
    private static final Game instance = new Game();

    private ExecutorPool executorPool;
    private int serverId;
    private TableManager tableManager;
    private GameThreadPoolManager threadPoolManager;

    private Game() {
    }

    /**
     * 获取对局引擎单例实例。
     *
     * @return Game 单例对象
     */
    public static Game getInstance() {
        return instance;
    }

    public int getServerId() {
        return serverId;
    }

    public void setServerId(int serverId) {
        this.serverId = serverId;
    }

    public TableManager getTableManager() {
        return tableManager;
    }

    /**
     * 获取业务串行执行器。
     */
    public ExecutorPool getBusinessExecutor() {
        return threadPoolManager != null ? threadPoolManager.businessExecutor() : executorPool;
    }

    /**
     * 获取异步阻塞 I/O 隔离线程池。
     */
    public java.util.concurrent.ExecutorService getAsyncIoExecutor() {
        return threadPoolManager != null ? threadPoolManager.asyncIoExecutor() : null;
    }

    /**
     * 获取全局统一时钟调度器。
     */
    public java.util.concurrent.ScheduledExecutorService getScheduler() {
        return threadPoolManager != null ? threadPoolManager.scheduler() : null;
    }

    public GameThreadPoolManager getThreadPoolManager() {
        return threadPoolManager;
    }

    /**
     * 优雅停服：统一释放所有游戏桌逻辑，并由 GameThreadPoolManager 关闭所有三权线程池。
     */
    public void shutdown() {
        if (tableManager != null) {
            tableManager.shutdown();
        }
        if (threadPoolManager != null) {
            threadPoolManager.shutdown();
        }
        tableManager = null;
        threadPoolManager = null;
        executorPool = null;
    }

    /**
     * 启动嵌入式对局引擎运行时。
     *
     * @param scoreDatabasePath 战绩持久化 SQLite 文件路径
     * @param workers           业务对局工作线程数
     * @param queueCapacity     工作队列容量
     * @param databaseThreads   数据库异步写入线程数
     */
    public synchronized void startEmbedded(String scoreDatabasePath, int workers,
            int queueCapacity, int databaseThreads) {
        if (tableManager != null) {
            return;
        }
        if (workers <= 0 || queueCapacity <= 0 || databaseThreads <= 0) {
            throw new IllegalArgumentException("Game线程与队列配置必须大于0");
        }
        threadPoolManager = new GameThreadPoolManager(workers, queueCapacity, databaseThreads);
        executorPool = threadPoolManager.businessExecutor();
        ScoreRepository.initialize(scoreDatabasePath, threadPoolManager.asyncIoExecutor());
        tableManager = new TableManager();
        GameRuntimeConfig.initialize();

        // 统一使用全局 Scheduler 负责配置热更扫描，彻底替换自研 Timer
        threadPoolManager.scheduler().scheduleAtFixedRate(
                utils.concurrent.thread.DecoratedRunnable.of(GameRuntimeConfig::reloadIfDue, "GameRuntimeConfig-Reload"),
                1_000L, 1_000L, java.util.concurrent.TimeUnit.MILLISECONDS);

        logger.info("Game embedded runtime ready; 三权分立架构启动完成, 无 Center/TCP/metrics 端口");
    }

    /**
     * 提交异步任务至主业务线程池。
     */
    public void execute(Runnable task) {
        executorPool.execute(utils.concurrent.thread.DecoratedRunnable.of(task, "Game-AsyncExecute"));
        logger.debug("提交任务到线程池");
    }

    /**
     * 按顺序提交串行任务。
     */
    public void serialExecute(Task task) {
        executorPool.serialExecute(task);
        logger.debug("提交串行任务");
    }

    /**
     * 获取主线程池当前大小。
     */
    public int getPoolSize() {
        return executorPool.size();
    }
}

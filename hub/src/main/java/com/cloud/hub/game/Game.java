package com.cloud.hub.game;

import com.cloud.hub.game.config.GameRuntimeConfig;
import com.cloud.hub.game.db.DatabaseExecutorManager;
import com.cloud.hub.game.db.ScoreRepository;
import com.cloud.hub.game.manager.TableManager;
import com.cloud.hub.game.manager.thread.GameThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import threadtutil.thread.ExecutorPool;
import threadtutil.thread.Task;
import threadtutil.timer.Runner;
import threadtutil.timer.Timer;

/**
 * 对局服务核心运行时门面单例。
 * <p>
 * 管理游戏线程池 {@link GameThreadPoolManager}、定时器 {@link Timer}、
 * 游戏桌管理器 {@link TableManager} 以及异步落库执行器 {@link DatabaseExecutorManager}。
 * 在一体化架构中以嵌入式模式 (Embedded) 启动运行，与大厅同进程协同工作。
 * </p>
 *
 * @author cloud
 */
public class Game {

    private static final Logger logger = LoggerFactory.getLogger(Game.class);
    private static final Game instance = new Game();

    private ExecutorPool executorPool;
    private Timer timer;
    private int serverId;
    private TableManager tableManager;
    private DatabaseExecutorManager databaseExecutorManager;
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

    public DatabaseExecutorManager getDatabaseExecutorManager() {
        return databaseExecutorManager;
    }

    public GameThreadPoolManager getThreadPoolManager() {
        return threadPoolManager;
    }

    /**
     * 优雅停服：统一释放所有游戏桌逻辑、取消定时器并关闭数据库异步落库线程池。
     */
    public void shutdown() {
        if (tableManager != null) {
            tableManager.shutdown();
        }
        if (threadPoolManager != null) {
            threadPoolManager.shutdown();
        }
        if (timer != null) {
            timer.stop();
        }
        if (databaseExecutorManager != null) {
            databaseExecutorManager.shutdown();
        }
        tableManager = null;
        threadPoolManager = null;
        timer = null;
        executorPool = null;
        databaseExecutorManager = null;
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
        executorPool = threadPoolManager.playerPool();
        timer = new Timer().setRunners(executorPool);
        databaseExecutorManager = new DatabaseExecutorManager(threadPoolManager.databasePool());
        ScoreRepository.initialize(scoreDatabasePath, databaseExecutorManager);
        tableManager = new TableManager();
        GameRuntimeConfig.initialize();
        registerTimer(1_000L, 1_000L, -1, ignored -> {
            GameRuntimeConfig.reloadIfDue();
            return false;
        }, null);
        logger.info("Game embedded runtime ready; no Center/TCP/metrics ports opened");
    }

    /**
     * 注册全局定时器。
     */
    public <T> void registerTimer(long delay, long interval, int count, Runner<T> runner, T param) {
        timer.register(delay, interval, count, runner, param);
        logger.debug("注册定时器, delay: {}, interval: {}, count: {}", delay, interval, count);
    }

    /**
     * 注册串行定时器（同组内排队保序执行）。
     */
    public <T> void registerSerialTimer(int groupId, long delay, long interval, int count, Runner<T> runner, T param) {
        timer.registerSerial(groupId, delay, interval, count, runner, param);
        logger.debug("注册串行定时器, groupId: {}, delay: {}, interval: {}", groupId, delay, interval);
    }

    /**
     * 注册串行定时器并返回分配的计时器 ID。
     */
    public <T> int registerSerialTimerWithId(int groupId, long delay, long interval, int count, Runner<T> runner,
            T param) {
        int id = timer.registerSerialWithId(groupId, delay, interval, count, runner, param);
        logger.debug("注册串行定时器, id: {}, groupId: {}, delay: {}, interval: {}", id, groupId, delay, interval);
        return id;
    }

    /**
     * 注销指定计时器。
     */
    public void unregisterTimer(int nodeId) {
        timer.unregister(nodeId);
    }

    /**
     * 提交异步任务至主业务线程池。
     */
    public void execute(Runnable task) {
        executorPool.execute(task);
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

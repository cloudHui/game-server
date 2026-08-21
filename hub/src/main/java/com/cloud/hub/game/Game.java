package com.cloud.hub.game;

import com.cloud.hub.game.client.ClientProto;
import com.cloud.hub.game.client.GameClient;
import com.cloud.hub.game.config.GameRuntimeConfig;
import com.cloud.hub.game.db.DatabaseExecutorManager;
import com.cloud.hub.game.db.ScoreRepository;
import com.cloud.hub.game.manager.TableManager;
import com.cloud.hub.game.manager.thread.GameThreadPoolManager;
import msg.registor.HandleTypeRegister;
import msg.registor.enums.ServerType;
import net.service.ServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ModelProto;
import threadtutil.thread.ExecutorPool;
import threadtutil.thread.Task;
import threadtutil.timer.Runner;
import threadtutil.timer.Timer;
import threadtutil.utils.TimeUtils;
import tools.ServerClientManager;
import tools.ServerManager;
import utils.metrics.MetricsCollector;
import utils.metrics.MetricsHttpServer;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * @author cloud
 * @version 1.0
 * @date 2026-05-03
 * @className Game
 * @description 游戏服务器主类，负责游戏逻辑处理、桌子管理和玩家会话
 * @createDate 2026-05-03
 * @since 1.0
 */
public class Game {
    private static final Logger logger = LoggerFactory.getLogger(Game.class);
    private static final Game instance = new Game();
    private final ServerClientManager serverClientManager = new ServerClientManager();
    private ExecutorPool executorPool;
    private Timer timer;
    private int serverId;
    private String center;
    private ModelProto.ServerInfo serverInfo;
    private ServerManager serverManager;
    private TableManager tableManager;
    private DatabaseExecutorManager databaseExecutorManager;
    private MetricsHttpServer metricsHttpServer;
    private GameThreadPoolManager threadPoolManager;

    private Game() {
        // 私有构造函数,单例模式
    }

    public static Game getInstance() {
        return instance;
    }

    // Getter和Setter方法
    public int getServerId() {
        return serverId;
    }

    public void setServerId(int serverId) {
        this.serverId = serverId;
    }

    public void setCenter(String center) {
        this.center = center;
    }

    public ServerManager getServerManager() {
        return serverManager;
    }

    public ServerClientManager getServerClientManager() {
        return serverClientManager;
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
     * 统一释放桌子、定时器和数据库线程池，避免服务重启遗留非守护线程。
     */
    public void shutdown() {
        if (tableManager != null) tableManager.shutdown();
        if (threadPoolManager != null) threadPoolManager.shutdown();
        if (timer != null) timer.stop();
        if (databaseExecutorManager != null) databaseExecutorManager.shutdown();
        tableManager = null;
        threadPoolManager = null;
        timer = null;
        executorPool = null;
        databaseExecutorManager = null;
    }

    public synchronized void startEmbedded(String scoreDatabasePath, int workers,
                                            int queueCapacity, int databaseThreads) {
        if (tableManager != null) return;
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

    public ModelProto.ServerInfo getServerInfo() {
        return serverInfo;
    }

    /**
     * 注册定时器
     */
    public <T> void registerTimer(long delay, long interval, int count, Runner<T> runner, T param) {
        timer.register(delay, interval, count, runner, param);
        logger.debug("注册定时器, delay: {}, interval: {}, count: {}", delay, interval, count);
    }

    /**
     * 注册串行定时器
     */
    public <T> void registerSerialTimer(int groupId, long delay, long interval, int count, Runner<T> runner, T param) {
        timer.registerSerial(groupId, delay, interval, count, runner, param);
        logger.debug("注册串行定时器, groupId: {}, delay: {}, interval: {}", groupId, delay, interval);
    }

    /**
     * 注册串行定时器并返回ID（用于后续替换间隔）
     */
    public <T> int registerSerialTimerWithId(int groupId, long delay, long interval, int count, Runner<T> runner, T param) {
        int id = timer.registerSerialWithId(groupId, delay, interval, count, runner, param);
        logger.debug("注册串行定时器, id: {}, groupId: {}, delay: {}, interval: {}", id, groupId, delay, interval);
        return id;
    }


    /**
     * 注销定时器
     */
    public void unregisterTimer(int nodeId) {
        timer.unregister(nodeId);
    }

    /**
     * 直接提交任务到线程池
     */
    public void execute(Runnable task) {
        executorPool.execute(task);
        logger.debug("提交任务到线程池");
    }

    /**
     * 按顺序有序处理任务
     */
    public void serialExecute(Task task) {
        executorPool.serialExecute(task);
        logger.debug("提交串行任务");
    }

    /**
     * 获取线程池当前大小
     */
    public int getPoolSize() {
        return executorPool.size();
    }

}

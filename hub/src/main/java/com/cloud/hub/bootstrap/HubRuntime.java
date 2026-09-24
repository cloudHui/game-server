package com.cloud.hub.bootstrap;

import com.cloud.hub.game.Game;
import com.cloud.hub.game.config.GameRuntimeConfig;
import com.cloud.hub.game.domain.replay.ReplayDirectories;
import com.cloud.hub.storage.DataPathResolver;
import com.cloud.hub.lobby.manager.table.TableManager;
import com.cloud.hub.lobby.db.SqliteDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Hub 服务核心运行时引导器。
 * <p>
 * 在 Spring Boot 应用就绪事件 (ApplicationReadyEvent) 触发时，负责装配系统环境路径、
 * 初始化各子系统（存储、大厅、对局引擎、长连接网关）并将其注入生命周期管理器 {@link HubLifecycle}。
 * </p>
 *
 * @author cloud
 */
@Component
public class HubRuntime {

    /** 系统生命周期状态管理器 */
    private final HubLifecycle lifecycle;
    /** 基础数据目录解析器 */
    private final DataPathResolver paths;
    /** 账号数据库文件名或相对路径 */
    private final String accountDatabase;
    /** 桌子配置文件所在目录 */
    private final String tableConfigDir;
    /** 对局回放数据存储目录 */
    private final String replayDir;
    /** Web 端玩家离线判定超时时间（秒） */
    private final int webOfflineTimeoutSeconds;
    /** 机器人出牌最小拟人延迟（毫秒） */
    private final int robotDelayMinMs;
    /** 机器人出牌最大拟人延迟（毫秒） */
    private final int robotDelayMaxMs;
    /** 对局引擎业务工作线程数 */
    private final int gameWorkers;
    /** 对局引擎任务队列容量上限 */
    private final int gameQueueCapacity;
    /** 对局引擎数据库落库异步线程数 */
    private final int gameDatabaseThreads;

    /**
     * 构造运行时引导器并由 Spring 自动注入配置参数。
     *
     * @param lifecycle 状态生命周期总线
     * @param paths 路径解析器
     * @param accountDatabase 账号数据库相对路径
     * @param tableConfigDir 桌子配置目录
     * @param replayDir 回放保存目录
     * @param webOfflineTimeoutSeconds 网页离线超时阈值
     * @param robotDelayMinMs 机器人思考最小延迟
     * @param robotDelayMaxMs 机器人思考最大延迟
     * @param gameWorkers 对局工作线程数
     * @param gameQueueCapacity 对局工作队列长度
     * @param gameDatabaseThreads 对局落库线程数
     */
    public HubRuntime(HubLifecycle lifecycle, DataPathResolver paths,
                      @Value("${account.db-path:data/lobby.db}") String accountDatabase,
                      @Value("${hub.table-config-dir:config}") String tableConfigDir,
                      @Value("${game.replay-dir:data/replay}") String replayDir,
                      @Value("${game.web-offline-timeout-seconds:30}") int webOfflineTimeoutSeconds,
                      @Value("${game.robot-operation-delay-min-ms:3000}") int robotDelayMinMs,
                      @Value("${game.robot-operation-delay-max-ms:6000}") int robotDelayMaxMs,
                      @Value("${game.worker-threads:4}") int gameWorkers,
                      @Value("${game.queue-capacity:100000}") int gameQueueCapacity,
                      @Value("${game.database-threads:2}") int gameDatabaseThreads) {
        this.lifecycle = lifecycle;
        this.paths = paths;
        this.accountDatabase = accountDatabase;
        this.tableConfigDir = tableConfigDir;
        this.replayDir = replayDir;
        this.webOfflineTimeoutSeconds = webOfflineTimeoutSeconds;
        this.robotDelayMinMs = robotDelayMinMs;
        this.robotDelayMaxMs = robotDelayMaxMs;
        this.gameWorkers = gameWorkers;
        this.gameQueueCapacity = gameQueueCapacity;
        this.gameDatabaseThreads = gameDatabaseThreads;
    }

    /**
     * 监听 Spring Boot 启动就绪事件，依次加载各核心组件。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        // 1. 初始化路径配置与运行参数
        String databasePath = paths.resolve(accountDatabase).toString();
        ReplayDirectories.configure(paths.resolve(replayDir));
        System.setProperty("table.config.dir", paths.resolve(tableConfigDir).toString());
        System.setProperty("tableConfigDir", paths.resolve(tableConfigDir).toString());
        GameRuntimeConfig.configure(webOfflineTimeoutSeconds, robotDelayMinMs, robotDelayMaxMs);

        // 2. 注册并按序启动各业务组件
        lifecycle.start(Arrays.asList(
                managed(HubComponent.STORAGE, () -> SqliteDatabase.initialize(databasePath), () -> { }),
                managed(HubComponent.LOBBY, () -> TableManager.getInstance().init(),
                        () -> TableManager.getInstance().shutdown()),
                managed(HubComponent.GAME,
                        () -> Game.getInstance().startEmbedded(databasePath, gameWorkers,
                                gameQueueCapacity, gameDatabaseThreads),
                        () -> Game.getInstance().shutdown()),
                managed(HubComponent.GATEWAY, () -> { }, () -> { })));

        // 3. 将 Web 模块标记为就绪可用
        lifecycle.markReady(HubComponent.WEB);
    }

    /**
     * 构造被生命周期总线托管的适配组件。
     *
     * @param component 组件类型枚举
     * @param start 启动闭包动作
     * @param stop 停止闭包动作
     * @return 包装后的可管理组件实例
     */
    private static ManagedComponent managed(HubComponent component, CheckedAction start, CheckedAction stop) {
        return new ManagedComponent() {
            @Override
            public HubComponent component() {
                return component;
            }

            @Override
            public void start() throws Exception {
                start.run();
            }

            @Override
            public void stop() throws Exception {
                stop.run();
            }
        };
    }

    /**
     * 允许抛出受检异常的动作函数式接口。
     */
    @FunctionalInterface
    private interface CheckedAction {
        /**
         * 执行具体的启动/停止逻辑。
         *
         * @throws Exception 运行期发生的异常
         */
        void run() throws Exception;
    }
}


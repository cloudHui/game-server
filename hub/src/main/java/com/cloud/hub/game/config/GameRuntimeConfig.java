package com.cloud.hub.game.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 对局服务运行时动态参数快照。
 * <p>
 * 统一由 Spring Boot {@code application.yml} 注入（如机器人思考时延、Web 断线判定阈值），
 * 采用不可变快照对象 {@link Snapshot} 提供线程安全且无锁的高性能读取。
 * </p>
 *
 * @author cloud
 */
public final class GameRuntimeConfig {

    private static final Logger logger = LoggerFactory.getLogger(GameRuntimeConfig.class);

    /** 默认离线超时秒数 */
    private static final int DEFAULT_OFFLINE_TIMEOUT_SECONDS = 30;
    /** 机器人操作默认最小拟人延迟毫秒 */
    private static final int DEFAULT_ROBOT_DELAY_MIN_MS = 3000;
    /** 机器人操作默认最大拟人延迟毫秒 */
    private static final int DEFAULT_ROBOT_DELAY_MAX_MS = 6000;

    /** 当前全局生效的不可变配置快照 */
    private static volatile Snapshot current = Snapshot.defaults();

    private GameRuntimeConfig() {
    }

    /**
     * 首次加载运行时配置。
     */
    public static void initialize() {
        // Spring 启动时已由 HubRuntime.configure 统一注入
    }

    /**
     * 定时重载检测挂钩。
     */
    public static void reloadIfDue() {
        // Hub 使用 Spring 统一配置，无独立配置文件轮询
    }

    /**
     * 更新全局运行时参数快照。
     *
     * @param offlineSeconds 离线超时秒数
     * @param minDelayMs 机器人最小出牌延时
     * @param maxDelayMs 机器人最大出牌延时
     */
    public static synchronized void configure(int offlineSeconds, int minDelayMs, int maxDelayMs) {
        if (offlineSeconds <= 0) {
            throw new IllegalArgumentException("离线超时必须大于0");
        }
        if (minDelayMs < 0 || maxDelayMs < minDelayMs) {
            throw new IllegalArgumentException("机器人延迟范围非法");
        }
        current = new Snapshot(offlineSeconds, minDelayMs, maxDelayMs);
        logger.info("Game统一配置已装载: {}", current);
    }

    public static int robotDelayMinMs() {
        return current.robotDelayMinMs;
    }

    public static int robotDelayMaxMs() {
        return current.robotDelayMaxMs;
    }

    public static long webOfflineTimeoutMillis() {
        return current.webOfflineTimeoutSeconds * 1000L;
    }

    /**
     * 不可变配置快照，确保桌线程始终读取同一批次强一致的配置值。
     */
    private static final class Snapshot {
        private final int webOfflineTimeoutSeconds;
        private final int robotDelayMinMs;
        private final int robotDelayMaxMs;

        private Snapshot(int webOfflineTimeoutSeconds, int robotDelayMinMs, int robotDelayMaxMs) {
            this.webOfflineTimeoutSeconds = webOfflineTimeoutSeconds;
            this.robotDelayMinMs = robotDelayMinMs;
            this.robotDelayMaxMs = robotDelayMaxMs;
        }

        private static Snapshot defaults() {
            return new Snapshot(DEFAULT_OFFLINE_TIMEOUT_SECONDS,
                    DEFAULT_ROBOT_DELAY_MIN_MS, DEFAULT_ROBOT_DELAY_MAX_MS);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Snapshot)) {
                return false;
            }
            Snapshot that = (Snapshot) other;
            return webOfflineTimeoutSeconds == that.webOfflineTimeoutSeconds
                    && robotDelayMinMs == that.robotDelayMinMs
                    && robotDelayMaxMs == that.robotDelayMaxMs;
        }

        @Override
        public String toString() {
            return "webOfflineTimeoutSeconds=" + webOfflineTimeoutSeconds
                    + ", robotDelayMs=" + robotDelayMinMs + "-" + robotDelayMaxMs;
        }
    }
}


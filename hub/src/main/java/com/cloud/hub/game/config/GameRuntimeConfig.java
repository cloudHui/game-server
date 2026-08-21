package com.cloud.hub.game.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Game 运行参数快照。参数统一由 Hub application.yml 注入。
 */
public final class GameRuntimeConfig {
    private static final Logger logger = LoggerFactory.getLogger(GameRuntimeConfig.class);
    private static final int DEFAULT_OFFLINE_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_ROBOT_DELAY_MIN_MS = 3000;
    private static final int DEFAULT_ROBOT_DELAY_MAX_MS = 6000;
    private static volatile Snapshot current = Snapshot.defaults();

    private GameRuntimeConfig() {
    }

    /** 首次加载运行时配置；失败时保留安全默认值。 */
    public static void initialize() {
        // Spring 启动时已由 HubRuntime.configure 注入。
    }

    /** 到达当前配置指定的重读时间后重新加载，供轻量定时任务调用。 */
    public static void reloadIfDue() {
        // Hub 使用 Spring 统一配置，无独立配置文件轮询。
    }

    public static synchronized void configure(int offlineSeconds, int minDelayMs, int maxDelayMs) {
        if (offlineSeconds <= 0) throw new IllegalArgumentException("离线超时必须大于0");
        if (minDelayMs < 0 || maxDelayMs < minDelayMs) throw new IllegalArgumentException("机器人延迟范围非法");
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

    /** 不可变配置快照，确保桌线程始终读取同一批次的配置值。 */
    private static final class Snapshot {
        private final int webOfflineTimeoutSeconds;
        private final int robotDelayMinMs;
        private final int robotDelayMaxMs;

        private Snapshot(int webOfflineTimeoutSeconds,
                         int robotDelayMinMs, int robotDelayMaxMs) {
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
            if (!(other instanceof Snapshot)) return false;
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

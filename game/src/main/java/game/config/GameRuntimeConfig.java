package game.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

/**
 * Game 可热更新配置。每次完整读取 app.properties，校验成功后原子替换快照。
 * 端口、线程池等启动配置仍由 ConfigurationManager 管理，不在运行中重建。
 */
public final class GameRuntimeConfig {
    public static final String CONFIG_RELOAD_SECONDS = "game.config-reload-seconds";
    public static final String WEB_OFFLINE_TIMEOUT_SECONDS = "game.web-offline-timeout-seconds";
    public static final String ROBOT_DELAY_MIN_MS = "robot.operation-delay-min-ms";
    public static final String ROBOT_DELAY_MAX_MS = "robot.operation-delay-max-ms";

    private static final Logger logger = LoggerFactory.getLogger(GameRuntimeConfig.class);
    private static final int DEFAULT_RELOAD_SECONDS = 30;
    private static final int DEFAULT_OFFLINE_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_ROBOT_DELAY_MIN_MS = 3000;
    private static final int DEFAULT_ROBOT_DELAY_MAX_MS = 6000;
    private static volatile Snapshot current = Snapshot.defaults();
    private static volatile long nextReloadAt;

    private GameRuntimeConfig() {
    }

    /** 首次加载运行时配置；失败时保留安全默认值。 */
    public static void initialize() {
        reload();
    }

    /** 到达当前配置指定的重读时间后重新加载，供轻量定时任务调用。 */
    public static void reloadIfDue() {
        if (System.currentTimeMillis() >= nextReloadAt) reload();
    }

    /** 完整读取并校验配置；任何字段非法时整份配置均不生效。 */
    public static synchronized void reload() {
        File file = new File(System.getProperty("user.dir"), "app.properties");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
            Properties properties = new Properties();
            properties.load(reader);
            Snapshot updated = Snapshot.from(properties);
            if (!updated.equals(current)) {
                current = updated;
                logger.info("Game运行时配置已更新: {}", updated);
            }
        } catch (Exception e) {
            logger.warn("Game运行时配置加载失败，继续使用上一份配置, file: {}", file.getAbsolutePath(), e);
        } finally {
            nextReloadAt = System.currentTimeMillis() + current.reloadSeconds * 1000L;
        }
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
        private final int reloadSeconds;
        private final int webOfflineTimeoutSeconds;
        private final int robotDelayMinMs;
        private final int robotDelayMaxMs;

        private Snapshot(int reloadSeconds, int webOfflineTimeoutSeconds,
                         int robotDelayMinMs, int robotDelayMaxMs) {
            this.reloadSeconds = reloadSeconds;
            this.webOfflineTimeoutSeconds = webOfflineTimeoutSeconds;
            this.robotDelayMinMs = robotDelayMinMs;
            this.robotDelayMaxMs = robotDelayMaxMs;
        }

        private static Snapshot defaults() {
            return new Snapshot(DEFAULT_RELOAD_SECONDS, DEFAULT_OFFLINE_TIMEOUT_SECONDS,
                    DEFAULT_ROBOT_DELAY_MIN_MS, DEFAULT_ROBOT_DELAY_MAX_MS);
        }

        private static Snapshot from(Properties properties) {
            int reload = positive(properties, CONFIG_RELOAD_SECONDS, DEFAULT_RELOAD_SECONDS);
            int offline = positive(properties, WEB_OFFLINE_TIMEOUT_SECONDS, DEFAULT_OFFLINE_TIMEOUT_SECONDS);
            int min = nonNegative(properties, ROBOT_DELAY_MIN_MS, DEFAULT_ROBOT_DELAY_MIN_MS);
            int max = nonNegative(properties, ROBOT_DELAY_MAX_MS, DEFAULT_ROBOT_DELAY_MAX_MS);
            if (max < min) throw new IllegalArgumentException("机器人操作延迟上限不能小于下限");
            return new Snapshot(reload, offline, min, max);
        }

        private static int positive(Properties properties, String key, int fallback) {
            int value = integer(properties, key, fallback);
            if (value <= 0) throw new IllegalArgumentException(key + " 必须大于0");
            return value;
        }

        private static int nonNegative(Properties properties, String key, int fallback) {
            int value = integer(properties, key, fallback);
            if (value < 0) throw new IllegalArgumentException(key + " 不能小于0");
            return value;
        }

        private static int integer(Properties properties, String key, int fallback) {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(fallback)).trim());
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Snapshot)) return false;
            Snapshot that = (Snapshot) other;
            return reloadSeconds == that.reloadSeconds
                    && webOfflineTimeoutSeconds == that.webOfflineTimeoutSeconds
                    && robotDelayMinMs == that.robotDelayMinMs
                    && robotDelayMaxMs == that.robotDelayMaxMs;
        }

        @Override
        public String toString() {
            return "reloadSeconds=" + reloadSeconds
                    + ", webOfflineTimeoutSeconds=" + webOfflineTimeoutSeconds
                    + ", robotDelayMs=" + robotDelayMinMs + "-" + robotDelayMaxMs;
        }
    }
}

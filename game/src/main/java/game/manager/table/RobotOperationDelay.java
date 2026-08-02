package game.manager.table;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;

public final class RobotOperationDelay {
    private static final Logger logger = LoggerFactory.getLogger(RobotOperationDelay.class);
    private static final int DEFAULT_MIN_MS = 3000;
    private static final int DEFAULT_MAX_MS = 6000;
    private static volatile int minMs = DEFAULT_MIN_MS;
    private static volatile int maxMs = DEFAULT_MAX_MS;

    private RobotOperationDelay() {
    }

    public static void reload() {
        File file = new File(System.getProperty("user.dir"), "app.properties");
        Properties properties = new Properties();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
            properties.load(reader);
            int newMin = Integer.parseInt(properties.getProperty(
                    "robot.operation-delay-min-ms", String.valueOf(DEFAULT_MIN_MS)).trim());
            int newMax = Integer.parseInt(properties.getProperty(
                    "robot.operation-delay-max-ms", String.valueOf(DEFAULT_MAX_MS)).trim());
            if (newMin < 0 || newMax < newMin) {
                throw new IllegalArgumentException("下限必须大于等于0且上限不能小于下限");
            }
            minMs = newMin;
            maxMs = newMax;
            logger.info("机器人操作时间配置已加载, minMs: {}, maxMs: {}", minMs, maxMs);
        } catch (Exception e) {
            logger.warn("机器人操作时间配置加载失败，继续使用 minMs: {}, maxMs: {}, file: {}",
                    minMs, maxMs, file.getAbsolutePath(), e);
        }
    }

    public static long randomMillis() {
        int min = minMs;
        int max = maxMs;
        return min == max ? min : ThreadLocalRandom.current().nextLong(min, (long) max + 1);
    }
}

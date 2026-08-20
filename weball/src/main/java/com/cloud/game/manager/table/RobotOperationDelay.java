package com.cloud.game.manager.table;

import com.cloud.game.config.GameRuntimeConfig;

import java.util.concurrent.ThreadLocalRandom;

/** 从统一运行时配置提供机器人操作延迟。 */
public final class RobotOperationDelay {
    private RobotOperationDelay() {
    }

    /** 返回包含上下限的随机延迟，配置更新后无需重建牌桌。 */
    public static long randomMillis() {
        int min = GameRuntimeConfig.robotDelayMinMs();
        int max = GameRuntimeConfig.robotDelayMaxMs();
        return min == max ? min : ThreadLocalRandom.current().nextLong(min, (long) max + 1);
    }
}

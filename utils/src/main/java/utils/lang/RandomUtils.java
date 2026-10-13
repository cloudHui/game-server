package utils.lang;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 高性能并发伪随机数生成与概率判定工具类。
 * <p>
 * 采用 {@link ThreadLocalRandom} 消除多线程并发调用下的原子种子 CAS 锁竞争与 CPU 缓存行伪共享；
 * 修正了概率判断中的闭区间越界偏差，并提供详尽的方法级参数校验与追踪日志。
 * </p>
 *
 * @author cloud
 */
public class RandomUtils {

    /** 统一日志记录器 */
    private static final Logger LOGGER = LoggerFactory.getLogger(RandomUtils.class);

    public RandomUtils() {
    }

    /**
     * 生成 [min, max] 闭区间的伪随机整数。
     *
     * @param min 最小值（包含）
     * @param max 最大值（包含）
     * @return 随机整数
     */
    public static int randomRangeObtain(int min, int max) {
        if (min >= max) {
            LOGGER.debug("[randomRangeObtain] min >= max: min={}, max={}, 直接返回 min", min, max);
            return min;
        }
        int result = ThreadLocalRandom.current().nextInt(min, max + 1);
        LOGGER.trace("[randomRangeObtain] 生成闭区间随机数: [{}, {}] -> {}", min, max, result);
        return result;
    }

    /**
     * 生成 [0, bound) 半开区间的伪随机整数。
     *
     * @param bound 上界（不包含，必须 > 0）
     * @return 随机整数
     */
    public static int randomRange(int bound) {
        if (bound <= 0) {
            LOGGER.warn("[randomRange] bound 必须大于 0: bound={}, 兜底返回 0", bound);
            return 0;
        }
        int result = ThreadLocalRandom.current().nextInt(bound);
        LOGGER.trace("[randomRange] 生成半开区间随机数: [0, {}) -> {}", bound, result);
        return result;
    }

    /**
     * 使用指定外部 Random 实例在 [min, max] 闭区间内生成随机数（兼容单测或特定随机种子需求）。
     *
     * @param min 最小值（包含）
     * @param max 最大值（包含）
     * @param r   指定 Random 实例
     * @return 随机整数
     */
    public static int random(int min, int max, Random r) {
        if (r == null) {
            LOGGER.warn("[random] 指定的 Random 实例为 null，自动降级为 ThreadLocalRandom");
            return randomRangeObtain(min, max);
        }
        if (min >= max) {
            return min;
        }
        int result = (int) (r.nextDouble() * (max - min + 1)) + min;
        LOGGER.trace("[random] 基于外部 Random 生成随机数: [{}, {}] -> {}", min, max, result);
        return result;
    }

    /**
     * 符合随机概率判定（百分比 0-100）。
     * <p>
     * 修复原实现中 {@code nextInt(100) <= rate} 导致 0% 概率仍有 1% 命中、20% 概率实际为 21% 的 Off-by-one 缺陷。
     * </p>
     *
     * @param rate 期望命中的概率百分比（如 20 代表 20% 几率）
     * @return true 代表命中概率，false 代表未命中
     */
    public static boolean fitRandomRate(int rate) {
        if (rate <= 0) {
            LOGGER.trace("[fitRandomRate] rate <= 0: rate={}, 判定未命中", rate);
            return false;
        }
        if (rate >= 100) {
            LOGGER.trace("[fitRandomRate] rate >= 100: rate={}, 判定必中", rate);
            return true;
        }
        // nextInt(100) 返回 0..99（共 100 种均等可能）
        // 当 rate 为 20 时，0..19 共 20 个值满足 < 20，概率精确等于 20/100 = 20.0%
        int num = ThreadLocalRandom.current().nextInt(100);
        boolean hit = num < rate;
        LOGGER.trace("[fitRandomRate] 概率判定: rate={}%, roll={}, hit={}", rate, num, hit);
        return hit;
    }

    /**
     * 向目标字节数组填充加密无关的高速伪随机字节。
     *
     * @param bytes 目标字节数组
     */
    public static void randomByte(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            LOGGER.warn("[randomByte] 字节数组为空，忽略填充");
            return;
        }
        ThreadLocalRandom.current().nextBytes(bytes);
        LOGGER.trace("[randomByte] 随机填充字节数组完成: length={}", bytes.length);
    }
}

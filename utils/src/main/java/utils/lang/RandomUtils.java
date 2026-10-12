package utils.lang;

import java.util.Random;

/**
 * 随机数生成与概率判定工具类
 */
public class RandomUtils {

    private static final Random RANDOM = new Random(System.currentTimeMillis());

    public RandomUtils() {
    }

    /**
     * 生成 [min, max] 闭区间的伪随机整数
     *
     * @param min 最小值（包含）
     * @param max 最大值（包含）
     * @return 随机整数
     */
    public static int randomRangeObtain(int min, int max) {
        return random(min, max, RANDOM);
    }

    /**
     * 生成 [0, bound) 半开区间的伪随机整数
     *
     * @param bound 上界（不包含）
     * @return 随机整数
     */
    public static int randomRange(int bound) {
        return RANDOM.nextInt(bound);
    }

    /**
     * 使用指定 Random 实例在 [min, max] 闭区间内生成随机数
     */
    public static int random(int min, int max, Random r) {
        return (int) (r.nextDouble() * (max - min + 1)) + min;
    }

    /**
     * 符合随机概率（百分比 0-100）
     *
     * @param rate 概率的整数（如 20 代表 20% 几率）
     * @return true 符合，false 不符合
     */
    public static boolean fitRandomRate(int rate) {
        int num = RANDOM.nextInt(100);
        return num <= rate;
    }

    /**
     * 填充乱序随机字节
     *
     * @param bytes 目标字节数组
     */
    public static void randomByte(byte[] bytes) {
        RANDOM.nextBytes(bytes);
    }
}

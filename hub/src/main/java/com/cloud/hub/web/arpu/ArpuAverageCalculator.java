package com.cloud.hub.web.arpu;

import java.util.List;
import java.util.Map;

/**
 * ARPU 月均值计算工具类。
 * <p>
 * 根据接口返回的 ARPU 月度明细数据，分别计算近 3 个月和近 6 个月的有效均值。
 * 规避不可查询或无效月份，不计入除数分母。
 *
 * @author cloud
 */
public final class ArpuAverageCalculator {

    private ArpuAverageCalculator() {
    }

    /**
     * 计算月度明细的 3 个月与 6 个月均值结果。
     *
     * @param monthlyValues 月度明细列表
     * @return 统计均值结果
     */
    public static Result calculate(List<?> monthlyValues) {
        if (monthlyValues == null) {
            return new Result(null, 0, null, 0);
        }
        Average latest3 = average(monthlyValues, 3);
        Average latest6 = average(monthlyValues, 6);
        return new Result(latest3.value, latest3.count, latest6.value, latest6.count);
    }

    /**
     * 计算指定月份窗口的平均值。
     *
     * @param monthlyValues 月度明细
     * @param limit         最大统计月份窗口大小
     * @return 均值统计封装对象
     */
    private static Average average(List<?> monthlyValues, int limit) {
        double total = 0;
        int count = 0;
        for (Object item : monthlyValues) {
            Double value = numericArpu(item);
            if (value == null) {
                continue;
            }
            total += value;
            count++;
            if (count == limit) {
                break;
            }
        }
        return count == 0 ? new Average(null, 0) : new Average(round(total / count), count);
    }

    /**
     * 从单月数据结构中解析浮点 ARPU 值。
     *
     * @param item 月度数据项
     * @return 合法浮点值或 null
     */
    private static Double numericArpu(Object item) {
        if (!(item instanceof Map)) {
            return null;
        }
        Object raw = ((Map<?, ?>) item).get("arpu");
        if (raw instanceof Number) {
            double value = ((Number) raw).doubleValue();
            return valid(value) ? value : null;
        }
        if (raw == null) {
            return null;
        }
        try {
            double value = Double.parseDouble(String.valueOf(raw).trim());
            return valid(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 校验数值有效性（非 NaN、非无限大且非负）。
     */
    private static boolean valid(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0;
    }

    /**
     * 保留两位小数舍入。
     */
    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 均值计算内部中间结果。
     */
    private static final class Average {
        private final Double value;
        private final int count;

        private Average(Double value, int count) {
            this.value = value;
            this.count = count;
        }
    }

    /**
     * 对外暴露的 3 个月与 6 个月 ARPU 统计结果模型。
     */
    public static final class Result {
        private final Double average3;
        private final int available3;
        private final Double average6;
        private final int available6;

        private Result(Double average3, int available3, Double average6, int available6) {
            this.average3 = average3;
            this.available3 = available3;
            this.average6 = average6;
            this.available6 = available6;
        }

        public Double getAverage3() {
            return average3;
        }

        public int getAvailable3() {
            return available3;
        }

        public Double getAverage6() {
            return average6;
        }

        public int getAvailable6() {
            return available6;
        }
    }
}

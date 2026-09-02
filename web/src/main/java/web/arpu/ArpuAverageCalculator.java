package web.arpu;

import java.util.List;
import java.util.Map;

/**
 * 根据接口返回的 arpu 月明细计算近 3 / 6 个月均值。
 * 接口通常按新月份在前返回；不可查询的月份不计入分母。
 */
public final class ArpuAverageCalculator {
    private ArpuAverageCalculator() {
    }

    public static Result calculate(List<?> monthlyValues) {
        if (monthlyValues == null) {
            return new Result(null, 0, null, 0);
        }
        Average latest3 = average(monthlyValues, 3);
        Average latest6 = average(monthlyValues, 6);
        return new Result(latest3.value, latest3.count, latest6.value, latest6.count);
    }

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

    private static boolean valid(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class Average {
        private final Double value;
        private final int count;

        private Average(Double value, int count) {
            this.value = value;
            this.count = count;
        }
    }

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

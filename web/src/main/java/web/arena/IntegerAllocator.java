package web.arena;

import java.util.List;

/**
 * 六个整数的均值约束分配器。供后台 API 和命令行示例共用，避免两套算法逐渐产生差异。
 */
public final class IntegerAllocator {
    public static final int VALUE_COUNT = 6;
    public static final int MAX_KNOWN_VALUES = 5;

    private IntegerAllocator() {
    }

    /**
     * 按总均值分配整数；提供局部均值时，自动尝试第 1 至第 4 个位置。
     */
    public static Result calculate(List<Integer> knownValues, double totalAverage, Double subAverage) {
        validate(knownValues, totalAverage, subAverage);
        int totalTargetSum = targetSum(totalAverage, VALUE_COUNT);
        Integer subTargetSum = subAverage == null ? null : targetSum(subAverage, 3);
        WindowAllocation window = subTargetSum == null
                ? null : findWindow(knownValues, totalTargetSum, subTargetSum);
        if (subTargetSum != null && window == null) {
            return Result.failure("已知值和均值条件无法同时满足");
        }
        int[] values = window == null ? tryAllocate(knownValues, totalTargetSum, null, 1) : window.values;
        if (values == null) {
            return Result.failure("已知值和均值条件无法同时满足");
        }
        int startIndex = window == null ? 0 : window.startIndex;
        return Result.success(values, knownValues.size(), totalTargetSum, subTargetSum, startIndex);
    }

    private static WindowAllocation findWindow(List<Integer> knownValues, int totalTargetSum, int subTargetSum) {
        for (int startIndex = 1; startIndex <= 4; startIndex++) {
            int[] values = tryAllocate(knownValues, totalTargetSum, subTargetSum, startIndex);
            if (values != null) {
                return new WindowAllocation(values, startIndex);
            }
        }
        return null;
    }

    /**
     * 先填充局部窗口，再把全局剩余和均匀分给未确定位置，保持原测试程序行为。
     */
    private static int[] tryAllocate(List<Integer> knownValues, int totalTargetSum,
                                     Integer subTargetSum, int startIndex) {
        int[] result = new int[VALUE_COUNT];
        boolean[] fixed = fillKnownValues(result, knownValues);
        if (subTargetSum != null && !allocateWindow(result, fixed, subTargetSum, startIndex)) {
            return null;
        }
        long currentSum = 0;
        int unknownCount = 0;
        for (int i = 0; i < VALUE_COUNT; i++) {
            if (fixed[i]) {
                currentSum += result[i];
            } else {
                unknownCount++;
            }
        }
        long remaining = totalTargetSum - currentSum;
        if (remaining < 0 || (unknownCount == 0 && remaining != 0)) {
            return null;
        }
        if (unknownCount > 0) {
            int[] allocation = allocateIntegers((int) remaining, unknownCount);
            fillUnknown(result, fixed, allocation);
        }
        return result;
    }

    private static boolean[] fillKnownValues(int[] result, List<Integer> knownValues) {
        boolean[] fixed = new boolean[VALUE_COUNT];
        for (int i = 0; i < knownValues.size(); i++) {
            result[i] = knownValues.get(i);
            fixed[i] = true;
        }
        return fixed;
    }

    private static boolean allocateWindow(int[] result, boolean[] fixed, int target, int startIndex) {
        int start = startIndex - 1;
        long knownSum = 0;
        int unknownCount = 0;
        for (int i = start; i < start + 3; i++) {
            if (fixed[i]) {
                knownSum += result[i];
            } else {
                unknownCount++;
            }
        }
        long remaining = target - knownSum;
        if (remaining < 0 || (unknownCount == 0 && remaining != 0)) {
            return false;
        }
        if (unknownCount > 0) {
            int[] allocation = allocateIntegers((int) remaining, unknownCount);
            int next = 0;
            for (int i = start; i < start + 3; i++) {
                if (!fixed[i]) {
                    result[i] = allocation[next++];
                    fixed[i] = true;
                }
            }
        }
        return true;
    }

    private static void fillUnknown(int[] result, boolean[] fixed, int[] allocation) {
        int next = 0;
        for (int i = 0; i < VALUE_COUNT; i++) {
            if (!fixed[i]) {
                result[i] = allocation[next++];
            }
        }
    }

    /** 均分剩余和，余数从前往后分配，使结果尽可能平衡。 */
    private static int[] allocateIntegers(int total, int count) {
        int[] values = new int[count];
        int base = total / count;
        int remainder = total % count;
        for (int i = 0; i < count; i++) {
            values[i] = base + (i < remainder ? 1 : 0);
        }
        return values;
    }

    private static void validate(List<Integer> knownValues, double totalAverage, Double subAverage) {
        if (knownValues == null || knownValues.size() > MAX_KNOWN_VALUES) {
            throw new IllegalArgumentException("最多只能输入 5 个已知值");
        }
        validateAverage(totalAverage, "总期望均值");
        if (subAverage != null) {
            validateAverage(subAverage, "连续 3 个值的期望均值");
        }
        for (Integer value : knownValues) {
            if (value == null || value < 0) {
                throw new IllegalArgumentException("已知值必须是大于等于 0 的整数");
            }
        }
    }

    private static void validateAverage(double average, String label) {
        if (Double.isNaN(average) || Double.isInfinite(average) || average < 0) {
            throw new IllegalArgumentException(label + "必须是大于等于 0 的有效数字");
        }
        targetSum(average, 1);
    }

    private static int targetSum(double average, int count) {
        double raw = average * count;
        long rounded = Math.round(raw);
        if (Double.isInfinite(raw) || rounded > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("均值过大，目标总和超出整数范围");
        }
        return (int) Math.round(raw);
    }

    private static int sum(int[] values, int start, int length) {
        int total = 0;
        for (int i = start; i < start + length; i++) {
            total += values[i];
        }
        return total;
    }

    /** 计算结果是不可变快照，数组通过副本暴露给接口层。 */
    public static final class Result {
        private final boolean success;
        private final int[] values;
        private final int knownCount;
        private final int totalTargetSum;
        private final Integer subTargetSum;
        private final int subStartIndex;
        private final String errorMessage;

        private Result(boolean success, int[] values, int knownCount, int totalTargetSum,
                       Integer subTargetSum, int subStartIndex, String errorMessage) {
            this.success = success;
            this.values = values;
            this.knownCount = knownCount;
            this.totalTargetSum = totalTargetSum;
            this.subTargetSum = subTargetSum;
            this.subStartIndex = subStartIndex;
            this.errorMessage = errorMessage;
        }

        private static Result success(int[] values, int knownCount, int totalTargetSum,
                                      Integer subTargetSum, int subStartIndex) {
            return new Result(true, values.clone(), knownCount, totalTargetSum,
                    subTargetSum, subStartIndex, "");
        }

        private static Result failure(String message) {
            return new Result(false, null, 0, 0, null, 0, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public int[] getValues() {
            return values == null ? null : values.clone();
        }

        public int getKnownCount() {
            return knownCount;
        }

        public int getTotalTargetSum() {
            return totalTargetSum;
        }

        public int getTotalSum() {
            return values == null ? 0 : sum(values, 0, VALUE_COUNT);
        }

        public boolean hasSubConstraint() {
            return subTargetSum != null;
        }

        public Integer getSubTargetSum() {
            return subTargetSum;
        }

        public int getSubStartIndex() {
            return subStartIndex;
        }

        public int getSubSum() {
            return values == null || subTargetSum == null ? 0 : sum(values, subStartIndex - 1, 3);
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    private static final class WindowAllocation {
        private final int[] values;
        private final int startIndex;

        private WindowAllocation(int[] values, int startIndex) {
            this.values = values;
            this.startIndex = startIndex;
        }
    }
}

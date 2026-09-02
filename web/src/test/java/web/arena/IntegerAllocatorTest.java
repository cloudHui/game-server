package game.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class IntegerAllocatorTest {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // 1. 接收具体已知值
        System.out.println("请输入已知的具体数值（0 到 5 个，用空格隔开，直接回车代表没有已知值）：");
        String line = scanner.nextLine().trim();
        String[] parts = line.split("\\s+");
        List<Integer> knownValues = new ArrayList<>();

        for (String part : parts) {
            if (!part.isEmpty()) {
                knownValues.add(Integer.parseInt(part));
            }
        }

        if (knownValues.size() >= 6) {
            System.out.println("最多只能输入 5 个已知值。");
            return;
        }

        // 2. 接收总期望均值
        System.out.print("请输入 6 个值的【总期望均值】(可输入小数): ");
        double totalAvg = scanner.nextDouble();
        scanner.nextLine(); // 消耗掉输入数字后遗留的回车换行符

        // 3. 接收可选的局部连续均值（直接回车跳过）
        System.out.print("请输入【连续 3 个值的期望均值】(可输入小数，直接回车跳过此条件): ");
        String subAvgStr = scanner.nextLine().trim();
        scanner.close();

        boolean hasSubConstraint = !subAvgStr.isEmpty();
        double subAvg = hasSubConstraint ? Double.parseDouble(subAvgStr) : 0;

        int totalTargetSum = (int) Math.round(totalAvg * 6);
        int subTargetSum = hasSubConstraint ? (int) Math.round(subAvg * 3) : 0;

        int[] finalResult = null;
        int bestStartIndex = 1; // 默认记录起始位置

        // ================== 核心尝试与分配逻辑 ==================
        if (hasSubConstraint) {
            // 【全自动判断模式】直接从 1 到 4 挨个试，找最近的一个
            System.out.println("\n正在自动寻找最靠前且不冲突的位置...");
            for (int testIndex = 1; testIndex <= 4; testIndex++) {
                finalResult = tryAllocate(knownValues, totalTargetSum, true, subTargetSum, testIndex);
                if (finalResult != null) {
                    bestStartIndex = testIndex;
                    System.out.println("-> 匹配成功！程序为你自动锁定了最近的合法起始位置：第 " + bestStartIndex + " 个。");
                    break;
                } else {
                    System.out.println("-> 排除：第 " + testIndex + " 个位置（触发数学冲突或负数）");
                }
            }

            if (finalResult == null) {
                System.out.println("\n！！！无法计算：程序尝试了所有位置 (1-4)，发现你的已知值和均值要求无论如何都会产生冲突。");
                return;
            }
        } else {
            // 【没有局部条件，直接算整体】
            finalResult = tryAllocate(knownValues, totalTargetSum, false, 0, 1);
            if (finalResult == null) {
                System.out.println("\n！！！无法计算：当前的已知值总和已经超过了总目标，必定会出现负数。");
                return;
            }
        }

        // 4. 打印结果
        printResult(finalResult, knownValues.size(), totalAvg, totalTargetSum, hasSubConstraint, subAvg, bestStartIndex);
    }

    /**
     * 核心分配引擎：尝试以给定的 startIndex 进行分配。如果发生冲突或出现负数，返回 null。
     */
    private static int[] tryAllocate(List<Integer> knownValues, int totalTargetSum, boolean hasSubConstraint, int subTargetSum, int startIndex) {
        int[] result = new int[6];
        boolean[] isKnown = new boolean[6];

        // 填充已知值
        for (int i = 0; i < knownValues.size(); i++) {
            result[i] = knownValues.get(i);
            isKnown[i] = true;
        }

        // 尝试满足连续3个的局部条件
        if (hasSubConstraint) {
            int startIdx = startIndex - 1;
            int overlapSum = 0;
            int unknownCountInSub = 0;

            for (int i = startIdx; i <= startIdx + 2; i++) {
                if (isKnown[i]) overlapSum += result[i];
                else unknownCountInSub++;
            }

            int subRemainingSum = subTargetSum - overlapSum;
            if (subRemainingSum < 0) return null; // 出现负数冲突
            if (unknownCountInSub == 0 && subRemainingSum != 0) return null; // 被占满但不达标

            if (unknownCountInSub > 0) {
                int[] subAlloc = allocateIntegers(subRemainingSum, unknownCountInSub);
                int allocIdx = 0;
                for (int i = startIdx; i <= startIdx + 2; i++) {
                    if (!isKnown[i]) {
                        result[i] = subAlloc[allocIdx++];
                        isKnown[i] = true;
                    }
                }
            }
        }

        // 尝试满足总体的目标
        int currentTotalSum = 0;
        int remainingUnknownCount = 0;
        for (int i = 0; i < 6; i++) {
            if (isKnown[i]) currentTotalSum += result[i];
            else remainingUnknownCount++;
        }

        int globalRemainingSum = totalTargetSum - currentTotalSum;
        if (globalRemainingSum < 0) return null; // 全局负数冲突
        if (remainingUnknownCount == 0 && globalRemainingSum != 0) return null; // 全局被占满但不达标

        if (remainingUnknownCount > 0) {
            int[] globalAlloc = allocateIntegers(globalRemainingSum, remainingUnknownCount);
            int allocIdx = 0;
            for (int i = 0; i < 6; i++) {
                if (!isKnown[i]) {
                    result[i] = globalAlloc[allocIdx++];
                }
            }
        }

        return result;
    }

    /**
     * 将剩余总和尽量平分
     */
    private static int[] allocateIntegers(int total, int count) {
        if (count <= 0) return new int[0];
        int[] arr = new int[count];
        int base = total / count;
        int remainder = total % count;
        for (int i = 0; i < count; i++) arr[i] = base + (i < remainder ? 1 : 0);
        return arr;
    }

    /**
     * 打印结果
     */
    private static void printResult(int[] finalResult, int knownCount, double totalAvg, int totalTargetSum,
                                    boolean hasSubConstraint, double subAvg, int startIndex) {
        System.out.println("\n================ 最终数值分配结果 ================");
        for (int i = 0; i < 6; i++) {
            if (i < knownCount) System.out.printf("第 %d 个值: %d (已知输入)\n", (i + 1), finalResult[i]);
            else System.out.printf("第 %d 个值: %d (程序推算分配)\n", (i + 1), finalResult[i]);
        }
        System.out.println("----------------------------------------------");
        System.out.printf("【验证1】6个值最终总和: %d (要求总和: %d, 期望总均值: %.2f)\n", sum(finalResult, 0, 6), totalTargetSum, totalAvg);
        if (hasSubConstraint) {
            int subSumCheck = sum(finalResult, startIndex - 1, 3);
            int subTargetCheck = (int) Math.round(subAvg * 3);
            System.out.printf("【验证2】第%d-%d个值总和: %d (要求总和: %d, 期望局部均值: %.2f)\n", startIndex, startIndex + 2, subSumCheck, subTargetCheck, subAvg);
        }
        System.out.println("================================================");
    }

    private static int sum(int[] arr, int start, int length) {
        int s = 0;
        for (int i = start; i < start + length; i++) s += arr[i];
        return s;
    }
}
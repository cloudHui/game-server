package game.arena;

import web.arena.IntegerAllocator;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 整数分配器命令行示例。后台页面和此示例共用 web.arena.IntegerAllocator。
 */
public class IntegerAllocatorTest {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        List<Integer> knownValues = readKnownValues(scanner);
        if (knownValues.size() > IntegerAllocator.MAX_KNOWN_VALUES) {
            System.out.println("最多只能输入 5 个已知值。");
            return;
        }
        System.out.print("请输入 6 个值的【总期望均值】(可输入小数): ");
        double totalAverage = Double.parseDouble(scanner.nextLine().trim());
        System.out.print("请输入【连续 3 个值的期望均值】(可输入小数，直接回车跳过此条件): ");
        String subText = scanner.nextLine().trim();
        scanner.close();
        Double subAverage = subText.isEmpty() ? null : Double.parseDouble(subText);

        IntegerAllocator.Result result = IntegerAllocator.calculate(knownValues, totalAverage, subAverage);
        if (!result.isSuccess()) {
            System.out.println("\n！！！无法计算：" + result.getErrorMessage());
            return;
        }
        printResult(result, totalAverage, subAverage);
    }

    private static List<Integer> readKnownValues(Scanner scanner) {
        System.out.println("请输入已知的具体数值（0 到 5 个，用空格隔开，直接回车代表没有已知值）：");
        String line = scanner.nextLine().trim();
        List<Integer> values = new ArrayList<>();
        if (line.isEmpty()) {
            return values;
        }
        for (String part : line.split("\\s+")) {
            values.add(Integer.parseInt(part));
        }
        return values;
    }

    private static void printResult(IntegerAllocator.Result result,
                                    double totalAverage, Double subAverage) {
        int[] values = result.getValues();
        System.out.println("\n================ 最终数值分配结果 ================");
        for (int i = 0; i < values.length; i++) {
            String type = i < result.getKnownCount() ? "已知输入" : "程序推算分配";
            System.out.printf("第 %d 个值: %d (%s)%n", i + 1, values[i], type);
        }
        System.out.println("----------------------------------------------");
        System.out.printf("【验证1】6个值最终总和: %d (要求总和: %d, 期望总均值: %.2f)%n",
                result.getTotalSum(), result.getTotalTargetSum(), totalAverage);
        if (subAverage != null) {
            System.out.printf("【验证2】第%d-%d个值总和: %d (要求总和: %d, 期望局部均值: %.2f)%n",
                    result.getSubStartIndex(), result.getSubStartIndex() + 2,
                    result.getSubSum(), result.getSubTargetSum(), subAverage);
        }
        System.out.println("================================================");
    }
}

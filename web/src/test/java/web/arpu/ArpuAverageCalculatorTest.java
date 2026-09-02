package web.arpu;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ArpuAverageCalculatorTest {

    @Test
    public void averagesLatestNumericMonthsAndSkipsUnavailableCurrentMonth() {
        ArpuAverageCalculator.Result result = ArpuAverageCalculator.calculate(Arrays.asList(
                month("2026-08", "请4日以后查询"),
                month("2026-07", 83.47),
                month("2026-06", 83.91),
                month("2026-05", 68.81),
                month("2026-04", 50),
                month("2026-03", 49),
                month("2026-02", 50)));

        assertEquals(78.73, result.getAverage3(), 0.001);
        assertEquals(64.20, result.getAverage6(), 0.001);
        assertEquals(3, result.getAvailable3());
        assertEquals(6, result.getAvailable6());
    }

    private Map<String, Object> month(String name, Object value) {
        Map<String, Object> month = new LinkedHashMap<>();
        month.put("month", name);
        month.put("arpu", value);
        return month;
    }
}

package com.gamer.data.file.client.holiday;

import java.time.LocalDate;
import java.util.List;

import com.gamer.data.file.client.download.Log;
import com.gamer.data.file.client.workday.Decision;
import com.gamer.data.file.client.workday.Scheduler;
import com.gamer.data.file.config.HolidayConfig;

/**
 * 节假日判定：只读 {@link HolidayConfig}；当年无配置或日期未列出则周一～五。
 * <p>
 * 远端不自动拉。需要时自行 GET 后把日期写入 {@link HolidayConfig#DEFAULT_JSON_LINES}：
 * {@code GET https://timor.tech/api/holiday/year/{year}} ，
 * {@code HttpURLConnection.setRequestMethod("GET")}，超时 5000ms，HTTP 200 且 body 含 {@code "code":0}，
 * 再用 {@link JsonParser#parseApiHolidayBody} 解析（{@code holiday:true} 放假，{@code false} 补班）。
 * </p>
 */
public final class ConfigStore {

    /** 进程内缓存；null 表示尚未解析 HolidayConfig */
    private static List<YearData> cachedConfig;

    private ConfigStore() {}

    /**
     * 判定指定日期是否为工作日：HolidayConfig 的 off/work → 否则周一～五。
     *
     * @param date
     *            东八区日期
     * @return 判定结果
     */
    public static Decision resolveWorkday(LocalDate date) {
        YearData data = JsonParser.findYear(loadConfig(), date.getYear());
        if (data != null && !data.isEmpty()) {
            String dateStr = date.toString();
            if (data.offDays.contains(dateStr)) {
                return new Decision(false, Decision.Source.FILE, "off_days " + dateStr);
            }
            if (data.workDays.contains(dateStr)) {
                return new Decision(true, Decision.Source.FILE, "work_days " + dateStr);
            }
        }

        boolean weekday = isWeekdayFallback(date);
        int dayOfWeek = date.getDayOfWeek().getValue();
        return new Decision(weekday, Decision.Source.WEEKDAY,
            Scheduler.formatWeekday(dayOfWeek) + (weekday ? "，执行" : "，跳过"));
    }

    /**
     * 读取配置：进程内缓存，否则解析 {@link HolidayConfig#DEFAULT_JSON}。
     *
     * @return 缓存列表
     */
    private static synchronized List<YearData> loadConfig() {
        if (cachedConfig != null) {
            return cachedConfig;
        }
        List<YearData> parsed = JsonParser.parseConfigJson(HolidayConfig.DEFAULT_JSON);
        if (parsed.isEmpty()) {
            Log.logScheduler("HolidayConfig JSON 解析失败或为空，工作日按周一～五");
        }
        cachedConfig = parsed;
        return cachedConfig;
    }

    /**
     * 周一～五兜底。
     *
     * @param date
     *            日期
     * @return 是否周一到周五
     */
    private static boolean isWeekdayFallback(LocalDate date) {
        int dayOfWeek = date.getDayOfWeek().getValue();
        return dayOfWeek >= 1 && dayOfWeek <= 5;
    }
}

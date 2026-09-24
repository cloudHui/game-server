package utils.other;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * 时间日期与时区换算核心工具类
 * <p>提供跨天、跨周、跨月判定、时间段偏移、时区对齐与标准时间格式化功能。</p>
 *
 * @author Forrest
 * @author cloud
 */
public class TimeUtil {

    /** 每天的毫秒数 */
    public static final long DAY = TimeUnit.DAYS.toMillis(1);
    /** 每小时的毫秒数 */
    public static final long HOUR = TimeUnit.HOURS.toMillis(1);
    /** 每分钟的毫秒数 */
    public static final long MINUTE = TimeUnit.MINUTES.toMillis(1);
    /** 每秒钟的毫秒数 */
    public static final long SECOND = TimeUnit.SECONDS.toMillis(1);
    /** 每小时的分钟数 */
    public static final long MINUTES_OF_HOUR = HOUR / MINUTE;
    /** 每分钟的秒数 */
    public static final long SECONDS_OF_MINUTE = MINUTE / SECOND;
    /** 一周的天数 */
    public static final int DAY_OF_WEEK = 7;
    /** 一天的小时数 */
    public static final int HOUR_OF_DAY = 24;

    /** 标准日期格式 yyyy-MM-dd HH:mm:ss */
    public static final String DATE_FORMAT1 = "yyyy-MM-dd HH:mm:ss";

    private static final TimeZone UTC_TIME_ZONE = TimeZone.getTimeZone("GMT");
    private static final TimeZone UTC8_TIME_ZONE = TimeZone.getTimeZone("GMT+8");

    /** 指定逻辑时区（默认为 GMT） */
    public static String logicTimeZone = "GMT";

    private TimeUtil() {
    }

    /**
     * 根据当前配置获取对应的逻辑时区
     *
     * @return TimeZone 实例
     */
    private static TimeZone getTimeZone() {
        return "GMT+8".equalsIgnoreCase(logicTimeZone) ? UTC8_TIME_ZONE : UTC_TIME_ZONE;
    }

    /**
     * 根据时间戳毫秒数获取绑定逻辑时区的 Calendar 实例
     *
     * @param time 时间戳毫秒
     * @return Calendar 实例
     */
    public static Calendar getCalendarByTimeStamp(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(getTimeZone());
        calendar.setTimeInMillis(time);
        return calendar;
    }

    /**
     * 计算两个日历时刻之间相差的自然天数（以 0 点为界）
     *
     * @param start 起始日历
     * @param end   结束日历
     * @return 相差天数（start < end 返回正数，反之为负数）
     */
    private static int getSoFarWentDays(Calendar start, Calendar end) {
        int sign = start.before(end) ? 1 : -1;
        Calendar s = start.before(end) ? (Calendar) start.clone() : (Calendar) end.clone();
        Calendar e = start.before(end) ? (Calendar) end.clone() : (Calendar) start.clone();

        int days = e.get(Calendar.DAY_OF_YEAR) - s.get(Calendar.DAY_OF_YEAR);
        int startYear = s.get(Calendar.YEAR);
        int endYear = e.get(Calendar.YEAR);
        while (startYear < endYear) {
            days += s.getActualMaximum(Calendar.DAY_OF_YEAR);
            s.add(Calendar.YEAR, 1);
            startYear = s.get(Calendar.YEAR);
        }
        return days * sign;
    }

    /**
     * 计算两个时间戳相隔的自然天数
     */
    public static int getSoFarWentDays(Timestamp start, Timestamp end) {
        if (start == null || end == null) {
            return 0;
        }
        return getSoFarWentDays(getCalendarByTimeStamp(start.getTime()), getCalendarByTimeStamp(end.getTime()));
    }

    /**
     * 计算两个时间戳相差的整分钟数
     */
    public static long getSoFarWentMinutes(Timestamp start, Timestamp end) {
        if (start == null || end == null) {
            return 0;
        }
        return (end.getTime() - start.getTime()) / MINUTE;
    }

    /**
     * 计算两个时间戳相差的整秒数
     */
    public static long getSoFarWentSeconds(Timestamp start, Timestamp end) {
        if (start == null || end == null) {
            return 0;
        }
        return (end.getTime() - start.getTime()) / SECOND;
    }

    /**
     * 判断两个时间戳是否在同一个自然天
     */
    public static boolean isSameDay(Timestamp start, Timestamp end) {
        if (start == null || end == null) {
            return false;
        }
        Calendar st = getCalendarByTimeStamp(start.getTime());
        Calendar et = getCalendarByTimeStamp(end.getTime());
        return st.get(Calendar.YEAR) == et.get(Calendar.YEAR)
                && st.get(Calendar.MONTH) == et.get(Calendar.MONTH)
                && st.get(Calendar.DAY_OF_MONTH) == et.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 判断两个时间戳是否在同一个自然周内（周一为一周起始）
     */
    public static boolean isSameWeek(Timestamp start, Timestamp end) {
        if (start == null || end == null) {
            return false;
        }
        Calendar st = getCalendarByTimeStamp(start.getTime());
        Calendar et = getCalendarByTimeStamp(end.getTime());
        int days = Math.abs(getSoFarWentDays(st, et));
        if (days < DAY_OF_WEEK) {
            st.setFirstDayOfWeek(Calendar.MONDAY);
            et.setFirstDayOfWeek(Calendar.MONDAY);
            return st.get(Calendar.WEEK_OF_YEAR) == et.get(Calendar.WEEK_OF_YEAR);
        }
        return false;
    }

    /**
     * 判断两个时间戳是否在同一个自然月内
     */
    public static boolean isSameMonth(Timestamp start, Timestamp end) {
        if (start == null || end == null) {
            return false;
        }
        Calendar st = getCalendarByTimeStamp(start.getTime());
        Calendar et = getCalendarByTimeStamp(end.getTime());
        return st.get(Calendar.YEAR) == et.get(Calendar.YEAR)
                && st.get(Calendar.MONTH) == et.get(Calendar.MONTH);
    }

    /**
     * 判断当前时间戳是否落在指定闭区间之间
     */
    public static boolean betweenTimeStamp(Timestamp nowTimeStamp, Timestamp startTimeStamp, Timestamp endTimeStamp) {
        if (nowTimeStamp == null || startTimeStamp == null || endTimeStamp == null) {
            return false;
        }
        return nowTimeStamp.after(startTimeStamp) && nowTimeStamp.before(endTimeStamp);
    }

    /**
     * 获取下一个零点的时刻毫秒数（关联逻辑时区）
     */
    public static long nextZeroHourTime() {
        Calendar calendar = getCalendarByTimeStamp(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis() + DAY;
    }

    /**
     * 获取下一个整点起始时刻毫秒数
     */
    public static long nextHourStartTime(long currentTimeInMillis) {
        Calendar calendar = getCalendarByTimeStamp(currentTimeInMillis);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis() + HOUR;
    }

    /**
     * 获取给定时刻所在当天的零点毫秒数
     */
    public static long curZeroHourTime(long currentTimeInMillis) {
        Calendar calendar = getCalendarByTimeStamp(currentTimeInMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /**
     * 获取当天第 hourNum 个小时的整点毫秒数
     */
    public static long curHourTime(long currentTimeInMillis, int hourNum) {
        return curZeroHourTime(currentTimeInMillis) + Math.max(hourNum, 0) * HOUR;
    }

    public static long curDayStartTimeDefaultZone(long currentTimeInMillis) {
        return currentTimeInMillis / DAY * DAY;
    }

    public static long curWeekStartTimeDefaultZone(long currentTimeInMillis) {
        return currentTimeInMillis / DAY_OF_WEEK * DAY_OF_WEEK;
    }

    /**
     * 获取给定时刻所在周周日零点毫秒数
     */
    public static long curSundayStartTime(long currentTimeInMillis) {
        Calendar calendar = getCalendarByTimeStamp(currentTimeInMillis);
        calendar.set(Calendar.DAY_OF_WEEK, calendar.getActualMinimum(Calendar.DAY_OF_WEEK));
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /**
     * 获取下一个周一零点毫秒数
     */
    public static long nextMondayStartTime(long currentTimeInMillis) {
        Calendar calendar = getCalendarByTimeStamp(currentTimeInMillis);
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() > currentTimeInMillis) {
            return calendar.getTimeInMillis();
        }
        return calendar.getTimeInMillis() + DAY * DAY_OF_WEEK;
    }

    public static long nextSundayStartTime(long currentTimeInMillis) {
        return curSundayStartTime(currentTimeInMillis) + DAY * DAY_OF_WEEK;
    }

    public static long curMonthStartTime(long currentTimeInMillis) {
        return alignMonthStart(getCalendarByTimeStamp(currentTimeInMillis));
    }

    public static long nextMonthStartTime(long currentTimeInMillis) {
        Calendar calendar = getCalendarByTimeStamp(currentTimeInMillis);
        int month = calendar.get(Calendar.MONTH);
        if (month == calendar.getActualMaximum(Calendar.MONTH)) {
            calendar.set(Calendar.YEAR, calendar.get(Calendar.YEAR) + 1);
            calendar.set(Calendar.MONTH, calendar.getActualMinimum(Calendar.MONTH));
        } else {
            calendar.set(Calendar.MONTH, month + 1);
        }
        return alignMonthStart(calendar);
    }

    private static long alignMonthStart(Calendar calendar) {
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMinimum(Calendar.DAY_OF_MONTH));
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /**
     * 将时间戳转为整数格式日期（如 20260924）
     */
    public static int timestampToDateNumber(long time) {
        Calendar cal = getCalendarByTimeStamp(time);
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);
        return year * 10000 + month * 100 + day;
    }

    /**
     * 将整数日期（如 20260924）转为当天零点时间戳
     */
    public static long dateNumberToTimestamp(int utcDateNumber) {
        int year = utcDateNumber / 10000;
        int month = (utcDateNumber % 10000) / 100;
        int day = utcDateNumber % 100;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(getTimeZone());
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /**
     * 获取星期数字（1=周日，2=周一 ... 7=周六）
     */
    public static int getWeekNumber(long time) {
        return getCalendarByTimeStamp(time).get(Calendar.DAY_OF_WEEK);
    }

    /**
     * 时间戳转标准文本（本地时区）
     */
    public static String getTimeStr(long time) {
        return getTimeStr(time, DATE_FORMAT1);
    }

    /**
     * 时间戳按指定格式转文本（本地时区）
     */
    public static String getTimeStr(long time, String format) {
        return new SimpleDateFormat(format).format(new Date(time));
    }

    /**
     * 时间戳转标准文本（逻辑业务时区）
     */
    public static String getLogicalTimeStr(long time) {
        return getLogicalTimeStr(time, DATE_FORMAT1);
    }

    /**
     * 时间戳按指定格式转文本（逻辑业务时区）
     */
    public static String getLogicalTimeStr(long time, String format) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        sdf.setTimeZone(getTimeZone());
        return sdf.format(new Date(time));
    }

    public static int getTimeYear(long timeStamp) {
        return getCalendarByTimeStamp(timeStamp).get(Calendar.YEAR);
    }

    public static int getCalendarNum(int calendarNum, long time) {
        return getCalendarByTimeStamp(time).get(calendarNum);
    }

    /**
     * 给定时间戳偏移指定日历字段数值
     */
    public static long getFixTime(long time, boolean isBefore, int calendarNum, int afterBeforeNum) {
        Calendar calendar = getCalendarByTimeStamp(time);
        int offset = isBefore ? afterBeforeNum : -afterBeforeNum;
        calendar.add(calendarNum, offset);
        return calendar.getTimeInMillis();
    }

    /**
     * 计算当前循环周期的结束时刻
     */
    public static long getCurEndTime(long startTime, long minuteDuration) {
        long now = System.currentTimeMillis();
        long stageTime = minuteDuration * MINUTE;
        long elapsed = now - startTime;
        long currentOffset = elapsed % stageTime;
        return now - currentOffset + stageTime;
    }
}

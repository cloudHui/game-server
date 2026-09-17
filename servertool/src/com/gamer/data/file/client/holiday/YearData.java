package com.gamer.data.file.client.holiday;

import java.util.HashSet;
import java.util.Set;

/** 单年节假日：off_days 休、work_days 补班。 */
final class YearData {
    /** 年份 */
    int year;
    /** 休假日 yyyy-MM-dd */
    Set<String> offDays = new HashSet<>();
    /** 补班日 yyyy-MM-dd */
    Set<String> workDays = new HashSet<>();

    /**
     * @return 是否无任何条目
     */
    boolean isEmpty() {
        return offDays.isEmpty() && workDays.isEmpty();
    }
}

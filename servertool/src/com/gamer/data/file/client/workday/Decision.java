package com.gamer.data.file.client.workday;

/** 工作日判定结果。 */
public final class Decision {
    /** 判定来源 */
    public enum Source {
        /** HolidayConfig 代码配置 */
        FILE("本地配置"),
        /** 周一～五兜底 */
        WEEKDAY("周一～五");

        /** 日志展示名 */
        final String label;

        Source(String label) {
            this.label = label;
        }
    }

    /** 是否执行下载 */
    public final boolean workday;
    /** 判定来源 */
    public final Source source;
    /** 判定详情 */
    public final String detail;

    public Decision(boolean workday, Source source, String detail) {
        this.workday = workday;
        this.source = source;
        this.detail = detail;
    }
}

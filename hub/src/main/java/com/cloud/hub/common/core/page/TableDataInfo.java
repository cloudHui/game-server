package com.cloud.hub.common.core.page;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 表格分页数据对象（参照 RuoYi 设计）
 * 继承 LinkedHashMap 保障与现有前端直接读 total/rows/records/page 等字段无缝兼容。
 */
public class TableDataInfo extends LinkedHashMap<String, Object> {
    private static final long serialVersionUID = 1L;

    public TableDataInfo() {
    }

    public TableDataInfo(List<?> list, long total) {
        put("code", 0);
        put("msg", "success");
        put("rows", list != null ? list : Collections.emptyList());
        put("total", total);
    }

    public TableDataInfo(List<?> list, long total, int page, int size) {
        put("code", 0);
        put("msg", "success");
        put("rows", list != null ? list : Collections.emptyList());
        put("records", list != null ? list : Collections.emptyList());
        put("total", total);
        put("page", page);
        put("size", size);
    }

    public static TableDataInfo build(List<?> list, long total) {
        return new TableDataInfo(list, total);
    }

    public static TableDataInfo build(List<?> list, long total, int page, int size) {
        return new TableDataInfo(list, total, page, size);
    }
}

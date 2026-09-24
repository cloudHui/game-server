package com.cloud.hub.common.core.page;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 表格分页数据对象。
 * <p>
 * 继承自 {@link LinkedHashMap}，封装表格分页查询的统一数据格式（包含 rows、records、total、page、size）。
 * 保证与前端 UI 组件库的分页协议无缝衔接。
 * </p>
 *
 * @author cloud
 */
public class TableDataInfo extends LinkedHashMap<String, Object> {
    private static final long serialVersionUID = 1L;

    /**
     * 空构造器。
     */
    public TableDataInfo() {
    }

    /**
     * 基础列表数据分页构造。
     *
     * @param list 当前页数据列表
     * @param total 数据总记录数
     */
    public TableDataInfo(List<?> list, long total) {
        put("code", 0);
        put("msg", "success");
        put("rows", list != null ? list : Collections.emptyList());
        put("total", total);
    }

    /**
     * 完整列表数据分页构造。
     *
     * @param list 当前页数据列表
     * @param total 数据总记录数
     * @param page 当前页码
     * @param size 每页行数
     */
    public TableDataInfo(List<?> list, long total, int page, int size) {
        put("code", 0);
        put("msg", "success");
        put("rows", list != null ? list : Collections.emptyList());
        put("records", list != null ? list : Collections.emptyList());
        put("total", total);
        put("page", page);
        put("size", size);
    }

    /**
     * 静态快捷构建分页对象。
     *
     * @param list 当前页列表
     * @param total 总记录数
     * @return 分页对象
     */
    public static TableDataInfo build(List<?> list, long total) {
        return new TableDataInfo(list, total);
    }

    /**
     * 静态快捷构建完整分页对象。
     *
     * @param list 当前页列表
     * @param total 总记录数
     * @param page 当前页码
     * @param size 每页行数
     * @return 分页对象
     */
    public static TableDataInfo build(List<?> list, long total, int page, int size) {
        return new TableDataInfo(list, total, page, size);
    }
}


package com.gamer.data.excel.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 内存列表分页 Module：集中维护数据快照、页码钳制与分页截取逻辑。
 *
 * <p>界面只负责渲染 {@link #getPage()} 返回的数据，不再重复计算起止下标。</p>
 *
 * @param <T>
 *            列表元素类型
 */
public final class PagedListModel<T> {

    /** 当前分页使用的数据快照。 */
    private final List<T> items = new ArrayList<>();

    /** 当前页码，从 1 开始。 */
    private int currentPage = 1;

    /** 每页条数。 */
    private int pageSize;

    /**
     * @param pageSize
     *            每页条数，必须大于 0
     */
    public PagedListModel(int pageSize) {
        setPageSize(pageSize);
    }

    /**
     * 替换全部数据并回到第一页。
     *
     * @param values
     *            新数据；null 按空列表处理
     */
    public void setItems(List<T> values) {
        items.clear();
        if (values != null) {
            items.addAll(values);
        }
        currentPage = 1;
    }

    /**
     * 切换当前页，越界页码自动钳制。
     *
     * @param page
     *            目标页码
     */
    public void setCurrentPage(int page) {
        currentPage = clampPage(page);
    }

    /**
     * 修改每页条数并回到第一页。
     *
     * @param value
     *            每页条数，必须大于 0
     */
    public void setPageSize(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("pageSize must be greater than zero");
        }
        pageSize = value;
        currentPage = 1;
    }

    /**
     * 返回当前页的只读快照。
     *
     * @return 当前页数据
     */
    public List<T> getPage() {
        if (items.isEmpty()) {
            return Collections.emptyList();
        }

        // 统一在 Module 内计算切片，调用方不接触分页下标。
        int start = (currentPage - 1) * pageSize;
        int end = Math.min(start + pageSize, items.size());
        return Collections.unmodifiableList(new ArrayList<>(items.subList(start, end)));
    }

    /** @return 当前页码 */
    public int getCurrentPage() {
        return currentPage;
    }

    /** @return 每页条数 */
    public int getPageSize() {
        return pageSize;
    }

    /** @return 数据总条数 */
    public int getTotalItems() {
        return items.size();
    }

    /**
     * 计算总页数；空列表仍返回 1，便于分页控件保持稳定状态。
     *
     * @return 总页数
     */
    public int getTotalPages() {
        if (items.isEmpty()) {
            return 1;
        }
        return (items.size() + pageSize - 1) / pageSize;
    }

    /**
     * 将目标页码限制在有效区间。
     *
     * @param page
     *            原始页码
     * @return 有效页码
     */
    private int clampPage(int page) {
        return Math.max(1, Math.min(page, getTotalPages()));
    }
}

package com.gamer.data.file.module;

import java.util.List;

import javax.swing.JPanel;

import com.gamer.data.excel.ui.PagedListModel;
import com.gamer.data.excel.ui.PaginationController;

/**
 * 内存分页内容 Module：统一维护列表分页状态、分页控件和页码/页容量切换。
 *
 * <p>
 * 文本预览和 GD 预览只提供当前页渲染逻辑，不再各自重复维护分页回调。
 * </p>
 *
 * @param <T>
 *            分页数据元素类型
 */
final class PagedContentModule<T> {

    /** 分页渲染回调 Interface。 */
    interface Renderer<T> {
        /**
         * 渲染当前页数据。
         *
         * @param pageItems
         *            当前页数据
         */
        void renderPage(List<T> pageItems);
    }

    /** 列表分页状态。 */
    private final PagedListModel<T> model;

    /** 分页控件。 */
    private final PaginationController pagination;

    /** 当前页渲染回调。 */
    private final Renderer<T> renderer;

    /**
     * @param pageSize
     *            默认每页数量
     * @param renderer
     *            当前页渲染回调
     */
    PagedContentModule(int pageSize, Renderer<T> renderer) {
        if (renderer == null) {
            throw new IllegalArgumentException("renderer must not be null");
        }
        this.model = new PagedListModel<>(pageSize);
        this.renderer = renderer;
        this.pagination =
            new PaginationController(pageSize, page -> showPage(page == null ? 1 : page), this::changePageSize);
    }

    /**
     * @return 可直接加入界面的分页控件面板
     */
    JPanel createPaginationPanel() {
        JPanel panel = pagination.createPaginationPanel();
        pagination.updateControls(model.getCurrentPage(), model.getTotalPages());
        return panel;
    }

    /**
     * 替换数据并展示第一页。
     *
     * @param items
     *            新数据
     */
    void setItems(List<T> items) {
        model.setItems(items);
        showPage(1);
    }

    /**
     * 展示指定页。
     *
     * @param page
     *            目标页码
     */
    void showPage(int page) {
        model.setCurrentPage(page);
        renderer.renderPage(model.getPage());
        pagination.updateControls(model.getCurrentPage(), model.getTotalPages());
    }

    /** @return 当前页码 */
    int getCurrentPage() {
        return model.getCurrentPage();
    }

    /** @return 当前每页数量 */
    int getPageSize() {
        return model.getPageSize();
    }

    /** @return 总页数 */
    int getTotalPages() {
        return model.getTotalPages();
    }

    /** @return 总条数 */
    int getTotalItems() {
        return model.getTotalItems();
    }

    /**
     * 修改每页数量并回到第一页。
     *
     * @param pageSize
     *            新的每页数量
     */
    private void changePageSize(Integer pageSize) {
        if (pageSize == null) {
            return;
        }
        model.setPageSize(pageSize);
        pagination.setPageSize(pageSize);
        showPage(1);
    }
}

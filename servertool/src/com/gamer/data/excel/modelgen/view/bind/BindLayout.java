package com.gamer.data.excel.modelgen.view.bind;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;

/**
 * 列绑定面板纵向紧凑布局：高度随子组件累加，无子项时不占位。
 */
public class BindLayout implements LayoutManager {

    /** 行间距像素 */
    private static final int ROW_GAP = 2;

    /**
     * 禁止实例化。
     */
    private BindLayout() {}

    /**
     * 创建布局实例。
     *
     * @return LayoutManager
     */
    public static LayoutManager create() {
        return new BindLayout();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addLayoutComponent(String name, Component comp) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeLayoutComponent(Component comp) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public Dimension preferredLayoutSize(Container parent) {
        int height = 0;
        int width = 0;
        int count = parent.getComponentCount();
        for (int i = 0; i < count; i++) {
            Dimension size = parent.getComponent(i).getPreferredSize();
            height = height + size.height;
            if (i < count - 1) {
                height = height + ROW_GAP;
            }
            if (size.width > width) {
                width = size.width;
            }
        }
        return new Dimension(width, height);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void layoutContainer(Container parent) {
        int y = 0;
        Dimension preferred = preferredLayoutSize(parent);
        int width = parent.getWidth();
        if (width <= 0) {
            width = preferred.width;
        }
        int count = parent.getComponentCount();
        for (int i = 0; i < count; i++) {
            Component child = parent.getComponent(i);
            Dimension size = child.getPreferredSize();
            int childWidth = Math.max(width, size.width);
            child.setBounds(0, y, childWidth, size.height);
            y = y + size.height;
            if (i < count - 1) {
                y = y + ROW_GAP;
            }
        }
    }
}

package com.gamer.data.map.ui;

import java.awt.Component;
import java.awt.Dimension;

import javax.swing.JLayeredPane;
import javax.swing.JScrollPane;

/**
 * 主地图与小地图叠层：每次 layout 时为子组件设置 bounds，避免 JLayeredPane 子组件尺寸为 0。
 */
public final class MapLayeredPane extends JLayeredPane {

    private static final long serialVersionUID = 1L;

    /** 小地图相对主地图区右上角的边距（像素） */
    private static final int MINI_MAP_OVERLAY_MARGIN_PX = 8;
    private Component miniMapOverlay;

    MapLayeredPane() {
        setLayout(null);
    }

    /**
     * @param miniMapOverlay
     *            小地图宿主
     */
    void setMiniMapOverlay(Component miniMapOverlay) {
        this.miniMapOverlay = miniMapOverlay;
    }

    @Override
    public Dimension getPreferredSize() {
        Component scroll = findScrollPane();
        if (scroll != null) {
            return scroll.getPreferredSize();
        }
        return new Dimension(400, 300);
    }

    @Override
    public void doLayout() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        Component scroll = findScrollPane();
        if (scroll != null) {
            scroll.setBounds(0, 0, w, h);
        }
        if (miniMapOverlay != null) {
            Dimension miniSize = miniMapOverlay.getPreferredSize();
            int miniX = w - miniSize.width - MINI_MAP_OVERLAY_MARGIN_PX;
            if (miniX < 0) {
                miniX = 0;
            }
            miniMapOverlay.setBounds(miniX, MINI_MAP_OVERLAY_MARGIN_PX, miniSize.width, miniSize.height);
        }
    }

    /**
     * @return 主地图滚动面板，未找到时 null
     */
    private Component findScrollPane() {
        for (Component c : getComponents()) {
            if (c instanceof JScrollPane) {
                return c;
            }
        }
        return null;
    }
}

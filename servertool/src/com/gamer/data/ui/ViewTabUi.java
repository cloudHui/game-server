package com.gamer.data.ui;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;

import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

/**
 * 扁平页签：顶栏用状态条底，选中项白底 + 下划线。
 */
final class ViewTabUi extends BasicTabbedPaneUI {

    /** 是否铺顶栏底色与底部分隔线；卡片内的页签栏不铺，否则会把卡片切成两段 */
    private final boolean header;

    /**
     * @param header
     *            true 为窗口顶栏页签，false 为卡片内页签
     */
    ViewTabUi(boolean header) {
        this.header = header;
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        lightHighlight = ViewPalette.LINE;
        shadow = ViewPalette.LINE;
        darkShadow = ViewPalette.LINE;
        focus = ViewPalette.SELECT;
        tabAreaInsets = new Insets(4, 8, 0, 8);
        tabInsets = new Insets(8, 14, 8, 14);
        selectedTabPadInsets = new Insets(0, 0, 0, 0);
        contentBorderInsets = new Insets(0, 0, 0, 0);
        tabRunOverlay = 0;
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        if (header) {
            int bar = calculateTabAreaHeight(tabPane.getTabPlacement(), Math.max(runCount, 1), maxTabHeight);
            if (bar < 28) {
                bar = 32;
            }
            g.setColor(ViewPalette.STATUS);
            g.fillRect(0, 0, c.getWidth(), bar);
            g.setColor(ViewPalette.PAGE);
            g.fillRect(0, bar, c.getWidth(), c.getHeight() - bar);
            g.setColor(ViewPalette.LINE);
            g.fillRect(0, bar - 1, c.getWidth(), 1);
        }
        super.paint(g, c);
    }

    @Override
    protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h,
        boolean isSelected) {
        if (!isSelected) {
            return;
        }
        g.setColor(ViewPalette.CARD);
        g.fillRect(x, y, w, h - 1);
    }

    @Override
    protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h,
        boolean isSelected) {
        if (!isSelected) {
            return;
        }
        g.setColor(ViewPalette.LINK);
        int lineW = Math.max(16, w - 20);
        g.fillRect(x + (w - lineW) / 2, y + h - 3, lineW, 2);
    }

    @Override
    protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
    }

    @Override
    protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex,
        Rectangle iconRect, Rectangle textRect, boolean isSelected) {
    }

    @Override
    protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics, int tabIndex, String title,
        Rectangle textRect, boolean isSelected) {
        g.setFont(isSelected ? ViewPalette.FONT_B : ViewPalette.FONT);
        g.setColor(tabPane.isEnabledAt(tabIndex) ? (isSelected ? ViewPalette.TITLE : ViewPalette.HINT)
            : ViewPalette.LINE);
        int mnemonic = tabPane.getDisplayedMnemonicIndexAt(tabIndex);
        BasicGraphicsUtils.drawStringUnderlineCharAt(g, title, mnemonic, textRect.x,
            textRect.y + metrics.getAscent());
    }
}

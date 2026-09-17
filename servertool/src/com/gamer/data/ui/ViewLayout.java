package com.gamer.data.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.border.TitledBorder;

/**
 * 页底、卡片、网格、分栏、滚动。
 */
final class ViewLayout {

    private ViewLayout() {}

    /**
     * 页内边距与底色。
     *
     * @param panel
     *            根面板
     */
    static void page(JPanel panel) {
        page(panel, 8, 10, 8, 10);
    }

    /**
     * 页底色与指定内边距。
     *
     * @param panel
     *            根面板
     * @param top
     *            上
     * @param left
     *            左
     * @param bottom
     *            下
     * @param right
     *            右
     */
    static void page(JPanel panel, int top, int left, int bottom, int right) {
        panel.setOpaque(true);
        panel.setBackground(ViewPalette.PAGE);
        panel.setBorder(BorderFactory.createEmptyBorder(top, left, bottom, right));
    }

    /**
     * 带标题的卡片。
     *
     * @param title
     *            分组名
     * @param body
     *            内容
     * @return 卡片
     */
    static JPanel card(String title, JComponent body) {
        return makeCard(title, body, 4, 4, 6, 6, 6);
    }

    /**
     * 任务页窄卡片：上下内边距更小，避免默认窗口裁掉控件。
     *
     * @param title
     *            分组名
     * @param body
     *            内容
     * @return 卡片
     */
    static JPanel compactCard(String title, JComponent body) {
        return makeCard(title, body, 2, 1, 4, 2, 4);
    }

    /**
     * 给已有控件套分组边框。
     *
     * @param c
     *            控件
     * @param title
     *            标题
     */
    static void titled(JComponent c, String title) {
        titled(c, title, 4, 6, 6, 6);
    }

    /**
     * 分组边框，指定内边距。
     *
     * @param c
     *            控件
     * @param title
     *            标题
     * @param top
     *            上
     * @param left
     *            左
     * @param bottom
     *            下
     * @param right
     *            右
     */
    static void titled(JComponent c, String title, int top, int left, int bottom, int right) {
        TitledBorder border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(ViewPalette.LINE), title);
        border.setTitleFont(ViewPalette.FONT_B);
        border.setTitleColor(ViewPalette.TITLE);
        c.setBorder(BorderFactory.createCompoundBorder(border,
            BorderFactory.createEmptyBorder(top, left, bottom, right)));
    }

    /**
     * gap 为内容间距；其余为标题边框内边距。
     */
    private static JPanel makeCard(String title, JComponent body, int gap, int top, int left, int bottom, int right) {
        JPanel card = new JPanel(new BorderLayout(gap, gap));
        titled(card, title, top, left, bottom, right);
        card.setOpaque(true);
        card.setBackground(ViewPalette.CARD);
        body.setOpaque(false);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    /**
     * 顶对齐等宽卡片行，不按最高卡片把其它卡片纵向拉高。
     *
     * @param cards
     *            卡片
     * @return 一行
     */
    static JPanel topCards(JComponent... cards) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.weightx = 1;
        gbc.weighty = 0;
        gbc.gridy = 0;
        for (int i = 0; i < cards.length; i++) {
            gbc.gridx = i;
            gbc.insets = new Insets(0, i == 0 ? 0 : 6, 0, 0);
            row.add(cards[i], gbc);
        }
        return row;
    }

    /**
     * 竖排，按内容高度，横向拉满。
     *
     * @param rows
     *            从上到下
     * @return 面板
     */
    static JPanel stack(JComponent... rows) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) {
                panel.add(Box.createVerticalStrut(4));
            }
            JComponent row = rows[i];
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            panel.add(row);
        }
        return panel;
    }

    /**
     * 不折行的横排。要吃剩余宽度的控件先 {@link ViewControls#fillX}。
     *
     * @param items
     *            控件
     * @return 一行
     */
    static JPanel line(Component... items) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int i = 0; i < items.length; i++) {
            if (i > 0) {
                panel.add(Box.createHorizontalStrut(6));
            }
            Component item = items[i];
            if (item instanceof JComponent) {
                JComponent jc = (JComponent)item;
                jc.setAlignmentY(Component.CENTER_ALIGNMENT);
                ViewControls.capBox(jc);
            }
            panel.add(item);
        }
        return panel;
    }

    /**
     * 等分网格（按钮铺满格子）。
     *
     * @param rows
     *            行
     * @param cols
     *            列
     * @param items
     *            控件
     * @return 面板
     */
    static JPanel grid(int rows, int cols, Component... items) {
        JPanel panel = new JPanel(new GridLayout(rows, cols, 6, 6));
        panel.setOpaque(false);
        for (Component item : items) {
            panel.add(item);
        }
        return panel;
    }

    /**
     * 左对齐一行。
     *
     * @param items
     *            控件
     * @return 面板
     */
    static JPanel row(Component... items) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        panel.setOpaque(false);
        for (Component item : items) {
            panel.add(item);
        }
        return panel;
    }

    /**
     * 底栏：左提示、右按钮。
     *
     * @param hintText
     *            提示
     * @param buttons
     *            按钮
     * @return 底栏
     */
    static JPanel bar(String hintText, Component... buttons) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(ViewControls.hint(hintText), BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        for (Component button : buttons) {
            right.add(button);
        }
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    /**
     * 左右顶栏：西提示、东按钮。
     *
     * @param west
     *            左侧
     * @param east
     *            右侧
     * @return 面板
     */
    static JPanel westEast(Component west, Component east) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        if (west != null) {
            panel.add(west, BorderLayout.WEST);
        }
        if (east != null) {
            panel.add(east, BorderLayout.EAST);
        }
        return panel;
    }

    /**
     * 白底细边滚动区。
     *
     * @param view
     *            内容
     * @return 滚动面板
     */
    static JScrollPane scroll(Component view) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(BorderFactory.createLineBorder(ViewPalette.LINE));
        scroll.getViewport().setBackground(Color.WHITE);
        return scroll;
    }

    /**
     * 左右分栏。
     *
     * @param left
     *            左
     * @param right
     *            右
     * @param weight
     *            左侧权重
     * @return 分栏
     */
    static JSplitPane splitH(Component left, Component right, double weight) {
        return split(JSplitPane.HORIZONTAL_SPLIT, left, right, weight);
    }

    /**
     * 上下分栏。
     *
     * @param top
     *            上
     * @param bottom
     *            下
     * @param weight
     *            上侧权重
     * @return 分栏
     */
    static JSplitPane splitV(Component top, Component bottom, double weight) {
        return split(JSplitPane.VERTICAL_SPLIT, top, bottom, weight);
    }

    /**
     * 统一分割条：连续拖动、可一键折叠；第一次有尺寸时按 weight 设分割位置。
     */
    private static JSplitPane split(int orientation, Component first, Component second, double weight) {
        final JSplitPane split = new JSplitPane(orientation, first, second);
        split.setResizeWeight(weight);
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(true);
        split.setDividerSize(6);
        split.setBorder(null);
        split.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (split.getWidth() <= 0 || split.getHeight() <= 0) {
                    return;
                }
                split.setDividerLocation(weight);
                split.removeComponentListener(this);
            }
        });
        return split;
    }
}

package com.gamer.data.ui;

import java.awt.Font;
import java.util.Enumeration;

import javax.swing.BorderFactory;
import javax.swing.JMenuBar;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;

/**
 * 系统外观、菜单栏、页签。
 */
final class ViewLook {

    private ViewLook() {}

    /**
     * 把当前线程 TCCL 钉到工具 ClassLoader，供 POI/XMLBeans 从 fat-jar 读 schema。
     */
    static void pinToolClassLoader() {
        ClassLoader toolCl = ViewUi.class.getClassLoader();
        if (toolCl != null) {
            Thread.currentThread().setContextClassLoader(toolCl);
        }
    }

    /**
     * 系统外观 + 全局微软雅黑 12。
     */
    static void installLook() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            Enumeration<Object> keys = UIManager.getDefaults().keys();
            while (keys.hasMoreElements()) {
                Object key = keys.nextElement();
                Object value = UIManager.get(key);
                if (value instanceof Font) {
                    UIManager.put(key, ViewPalette.FONT);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        pinToolClassLoader();
    }

    /**
     * 菜单栏：状态条底、底部分割线。
     *
     * @param bar
     *            菜单栏
     * @return 原菜单栏
     */
    static JMenuBar menuBar(JMenuBar bar) {
        bar.setFont(ViewPalette.FONT);
        bar.setOpaque(true);
        bar.setBackground(ViewPalette.STATUS);
        bar.setForeground(ViewPalette.TITLE);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ViewPalette.LINE));
        return bar;
    }

    /**
     * 主页签栏：顶栏底色与卡片选中色一致，去掉系统立体边。
     * 用换行布局而非滚动布局：滚动布局把页签画在独立视口子组件里，首帧下划线坐标会偏到相邻页签上。
     *
     * @return 页签
     */
    static JTabbedPane tabs() {
        return tabs(true);
    }

    /**
     * CMD / 任务共用的日志页签栏；在卡片内，不铺顶栏底色。
     *
     * @return 页签
     */
    static JTabbedPane logTabs() {
        JTabbedPane tabs = tabs(false);
        ViewLayout.titled(tabs, "任务日志");
        return tabs;
    }

    /**
     * @param header
     *            true 为窗口顶栏页签，false 为卡片内页签
     * @return 页签
     */
    private static JTabbedPane tabs(boolean header) {
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.WRAP_TAB_LAYOUT);
        tabs.setFont(ViewPalette.FONT);
        tabs.setOpaque(true);
        tabs.setBackground(ViewPalette.PAGE);
        tabs.setForeground(ViewPalette.TITLE);
        tabs.setBorder(null);
        tabs.setFocusable(false);
        tabs.setUI(new ViewTabUi(header));
        return tabs;
    }
}

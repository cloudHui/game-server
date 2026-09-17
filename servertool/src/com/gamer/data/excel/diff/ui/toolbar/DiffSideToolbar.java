package com.gamer.data.excel.diff.ui.toolbar;

import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

import com.gamer.data.ui.ViewUi;

/**
 * 顶部横工具条：同步 / 生成 / 归档。
 * <p>
 * 只负责布局；动作与忙碌禁用由外部注入。
 * </p>
 */
public final class DiffSideToolbar {

    /** 按钮创建后回调（登记忙碌锁）。 */
    public interface ButtonHook {
        void onCreated(JButton button);
    }

    private DiffSideToolbar() {}

    /**
     * 构建顶栏。
     *
     * @param reload
     *            重加载
     * @param pullChanged
     *            复制变更
     * @param generateGd
     *            生成 GD
     * @param copyArchive
     *            复制归档
     * @param clearCurr
     *            清空当前目录
     * @param buttonHook
     *            按钮登记，可为 null
     * @return 顶栏
     */
    public static JPanel build(Runnable reload, Runnable pullChanged, Runnable generateGd, Runnable copyArchive,
        Runnable clearCurr, ButtonHook buttonHook) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        bar.setOpaque(true);
        bar.setBackground(ViewUi.STATUS);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ViewUi.LINE),
            BorderFactory.createEmptyBorder(2, 6, 2, 6)));

        bar.add(ViewUi.hint("同步"));
        bar.add(btn("重加载", reload, buttonHook));
        bar.add(btn("复制变更", pullChanged, buttonHook));
        sep(bar);
        bar.add(ViewUi.hint("生成"));
        bar.add(ViewUi.primary(btn("生成GD", generateGd, buttonHook)));
        sep(bar);
        bar.add(ViewUi.hint("归档"));
        bar.add(btn("复制", copyArchive, buttonHook));
        bar.add(ViewUi.danger(btn("清空", clearCurr, buttonHook)));
        return bar;
    }

    /**
     * 组间竖线。
     *
     * @param bar
     *            工具条
     */
    private static void sep(JPanel bar) {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setForeground(ViewUi.LINE);
        sep.setPreferredSize(new Dimension(6, 22));
        bar.add(sep);
    }

    /**
     * 普通按钮并登记忙碌锁。
     *
     * @param text
     *            文案
     * @param action
     *            点击
     * @param hook
     *            忙碌锁登记
     * @return 按钮
     */
    private static JButton btn(String text, Runnable action, ButtonHook hook) {
        JButton btn = ViewUi.click(text, action);
        if (hook != null) {
            hook.onCreated(btn);
        }
        return btn;
    }
}

package com.gamer.data.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JTree;

/**
 * WindowsTools / StrategyTool / CodeTool 共用外观入口。
 * <p>
 * 实现按职责拆在同包 {@code ViewLook} / {@code ViewControls} / {@code ViewLayout} / {@code ViewTables} /
 * {@code ViewLogUi}，调用方只依赖本类。
 * </p>
 */
public final class ViewUi {

    /** 正文。 */
    public static final Font FONT = ViewPalette.FONT;
    /** 分组标题。 */
    public static final Font FONT_B = ViewPalette.FONT_B;
    /** 边线。 */
    public static final Color LINE = ViewPalette.LINE;
    /** 卡片底。 */
    public static final Color CARD = ViewPalette.CARD;
    /** 页底。 */
    public static final Color PAGE = ViewPalette.PAGE;
    /** 标题色。 */
    public static final Color TITLE = ViewPalette.TITLE;
    /** 状态条底。 */
    public static final Color STATUS = ViewPalette.STATUS;
    /** 危险操作字色。 */
    public static final Color DANGER = ViewPalette.DANGER;
    /** 提示 / 占位字色。 */
    public static final Color HINT = ViewPalette.HINT;
    /** 链接 / 当前步骤。 */
    public static final Color LINK = ViewPalette.LINK;
    /** 表格选中行。 */
    public static final Color SELECT = ViewPalette.SELECT;

    private ViewUi() {}

    /**
     * 系统外观 + 全局微软雅黑 12。各工具启动时调一次。
     */
    public static void installLook() {
        ViewLook.installLook();
    }

    /**
     * 菜单栏：状态条底、底部分割线。
     *
     * @param bar
     *            菜单栏
     * @return 原菜单栏
     */
    public static JMenuBar menuBar(JMenuBar bar) {
        return ViewLook.menuBar(bar);
    }

    /**
     * 主操作按钮：蓝底白字。
     *
     * @param button
     *            已 style 的按钮
     * @return 原按钮
     */
    public static JButton primary(JButton button) {
        return ViewControls.primary(button);
    }

    /**
     * 次要实心按钮：琥珀底白字，给暂停/继续。
     *
     * @param button
     *            已 style 的按钮
     * @return 原按钮
     */
    public static JButton warn(JButton button) {
        return ViewControls.warn(button);
    }

    /**
     * 结束实心按钮：红底白字。
     *
     * @param button
     *            已 style 的按钮
     * @return 原按钮
     */
    public static JButton halt(JButton button) {
        return ViewControls.halt(button);
    }

    /**
     * 页内边距与底色。
     *
     * @param panel
     *            根面板
     */
    public static void page(JPanel panel) {
        ViewLayout.page(panel);
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
    public static void page(JPanel panel, int top, int left, int bottom, int right) {
        ViewLayout.page(panel, top, left, bottom, right);
    }

    /**
     * 统一按钮：不抢焦点、手型、留白一致。
     *
     * @param button
     *            按钮
     * @return 原按钮
     */
    public static JButton style(JButton button) {
        return ViewControls.style(button);
    }

    /**
     * 带点击动作的按钮。
     *
     * @param text
     *            文案
     * @param action
     *            点击
     * @return 按钮
     */
    public static JButton click(String text, Runnable action) {
        return ViewControls.click(text, action);
    }

    /**
     * 点击可编辑下拉框任意区域时展开列表。
     *
     * @param combo
     *            下拉框
     */
    public static void popupOnClick(JComboBox<?> combo) {
        ViewControls.popupOnClick(combo);
    }

    /**
     * 危险操作按钮。
     *
     * @param button
     *            已 style 的按钮
     * @return 原按钮
     */
    public static JButton danger(JButton button) {
        return ViewControls.danger(button);
    }

    /**
     * 勾选框。
     *
     * @param text
     *            文案
     * @param on
     *            初始
     * @return 勾选框
     */
    public static JCheckBox check(String text, boolean on) {
        return ViewControls.check(text, on);
    }

    /**
     * 勾选框，切换时回调。
     *
     * @param text
     *            文案
     * @param on
     *            初始
     * @param set
     *            切换
     * @return 勾选框
     */
    public static JCheckBox check(String text, boolean on, Consumer<Boolean> set) {
        return ViewControls.check(text, on, set);
    }

    /**
     * 主页签栏。
     *
     * @return 页签
     */
    public static JTabbedPane tabs() {
        return ViewLook.tabs();
    }

    /**
     * CMD / 任务共用的日志页签栏。
     *
     * @return 页签
     */
    public static JTabbedPane logTabs() {
        return ViewLook.logTabs();
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
    public static JPanel card(String title, JComponent body) {
        return ViewLayout.card(title, body);
    }

    /**
     * 任务页窄卡片。
     *
     * @param title
     *            分组名
     * @param body
     *            内容
     * @return 卡片
     */
    public static JPanel compactCard(String title, JComponent body) {
        return ViewLayout.compactCard(title, body);
    }

    /**
     * 顶对齐等宽卡片行。
     *
     * @param cards
     *            卡片
     * @return 一行
     */
    public static JPanel topCards(JComponent... cards) {
        return ViewLayout.topCards(cards);
    }

    /**
     * 竖排，按内容高度，横向拉满。
     *
     * @param rows
     *            从上到下
     * @return 面板
     */
    public static JPanel stack(JComponent... rows) {
        return ViewLayout.stack(rows);
    }

    /**
     * 不折行的横排。要吃剩余宽度的控件先 {@link #fillX}。
     *
     * @param items
     *            控件
     * @return 一行
     */
    public static JPanel line(Component... items) {
        return ViewLayout.line(items);
    }

    /**
     * BoxLayout 行里让控件吃掉剩余宽度。
     *
     * @param field
     *            输入框
     * @return 原控件
     */
    public static JTextField fillX(JTextField field) {
        return ViewControls.fillX(field);
    }

    /**
     * 给已有控件套分组边框。
     *
     * @param c
     *            控件
     * @param title
     *            标题
     */
    public static void titled(JComponent c, String title) {
        ViewLayout.titled(c, title);
    }

    /**
     * 等分网格。
     *
     * @param rows
     *            行
     * @param cols
     *            列
     * @param items
     *            控件
     * @return 面板
     */
    public static JPanel grid(int rows, int cols, Component... items) {
        return ViewLayout.grid(rows, cols, items);
    }

    /**
     * 左对齐一行。
     *
     * @param items
     *            控件
     * @return 面板
     */
    public static JPanel row(Component... items) {
        return ViewLayout.row(items);
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
    public static JPanel bar(String hintText, Component... buttons) {
        return ViewLayout.bar(hintText, buttons);
    }

    /**
     * 分组标签。
     *
     * @param text
     *            文案
     * @return 标签
     */
    public static JLabel label(String text) {
        return ViewControls.label(text);
    }

    /**
     * 已有标签：正文字体与标题色。
     *
     * @param label
     *            标签
     * @return 原标签
     */
    public static JLabel label(JLabel label) {
        return ViewControls.label(label);
    }

    /**
     * 底部提示。
     *
     * @param text
     *            文案
     * @return 标签
     */
    public static JLabel hint(String text) {
        return ViewControls.hint(text);
    }

    /**
     * 日志区上方状态条。
     *
     * @param label
     *            标签
     */
    public static void statusBar(JLabel label) {
        ViewLogUi.statusBar(label);
    }

    /**
     * 只读日志文本区。
     *
     * @param area
     *            文本区
     */
    public static void log(JTextArea area) {
        ViewLogUi.log(area);
    }

    /**
     * 只读富文本日志。
     *
     * @param pane
     *            文本窗格
     */
    public static void logPane(JTextPane pane) {
        ViewLogUi.logPane(pane);
    }

    /**
     * 表格字体、网格与选中色。
     *
     * @param table
     *            表格
     */
    public static void table(JTable table) {
        ViewTables.table(table);
    }

    /**
     * 只读表格：整行选中，Ctrl+C / 右键复制当前行。
     *
     * @param table
     *            表格
     */
    public static void enableRowCopy(JTable table) {
        ViewTables.enableRowCopy(table);
    }

    /**
     * 只读表格：按格选中，Ctrl+C / 右键复制。
     *
     * @param table
     *            表格
     */
    public static void enableCellCopy(JTable table) {
        ViewTables.enableCellCopy(table);
    }

    /**
     * 文本区右键复制。
     *
     * @param area
     *            文本区
     */
    public static void enableTextCopy(JTextArea area) {
        ViewTables.enableTextCopy(area);
    }

    /**
     * 白底细边滚动区。
     *
     * @param view
     *            内容
     * @return 滚动面板
     */
    public static JScrollPane scroll(Component view) {
        return ViewLayout.scroll(view);
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
    public static JSplitPane splitH(Component left, Component right, double weight) {
        return ViewLayout.splitH(left, right, weight);
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
    public static JSplitPane splitV(Component top, Component bottom, double weight) {
        return ViewLayout.splitV(top, bottom, weight);
    }

    /**
     * 树字体、行高、白底。
     *
     * @param tree
     *            树
     */
    public static void tree(JTree tree) {
        ViewTables.tree(tree);
    }

    /**
     * 列表字体、行高、白底与选中色。
     *
     * @param list
     *            列表
     */
    public static void list(JList<?> list) {
        ViewTables.list(list);
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
    public static JPanel westEast(Component west, Component east) {
        return ViewLayout.westEast(west, east);
    }

    /**
     * 下拉框与筛选行对齐。
     *
     * @param combo
     *            下拉框
     */
    public static void compactCombo(JComboBox<?> combo) {
        ViewControls.compactCombo(combo);
    }

    /**
     * 输入框统一高度。
     *
     * @param field
     *            输入框
     */
    public static void compactField(JTextField field) {
        ViewControls.compactField(field);
    }

    /**
     * 在 EDT 同步执行 Swing 控件读写。
     *
     * @param runnable
     *            动作
     */
    public static void edtWait(Runnable runnable) {
        ViewLogUi.edtWait(runnable);
    }
}

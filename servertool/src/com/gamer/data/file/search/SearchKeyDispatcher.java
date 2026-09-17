package com.gamer.data.file.search;

import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * 根目录页签全局检索键盘分发：已选中目录且焦点不在搜索框时，将按键转交 {@link SearchPanel}。
 */
public final class SearchKeyDispatcher implements KeyEventDispatcher {

    /** 主窗口，用于判断焦点是否落在模态对话框等外部窗口 */
    private final JFrame ownerFrame;

    /** 主功能页签容器 */
    private final JTabbedPane tabbedPane;

    /** 根目录页签标题 */
    private final String rootTabTitle;

    /** 文件检索面板 */
    private final SearchPanel searchPanel;

    /**
     * @param ownerFrame
     *            文件管理器主窗口
     * @param tabbedPane
     *            右侧主功能页签
     * @param rootTabTitle
     *            根目录页签标题
     * @param searchPanel
     *            文件检索面板
     */
    public SearchKeyDispatcher(JFrame ownerFrame, JTabbedPane tabbedPane, String rootTabTitle,
        SearchPanel searchPanel) {
        this.ownerFrame = ownerFrame;
        this.tabbedPane = tabbedPane;
        this.rootTabTitle = rootTabTitle;
        this.searchPanel = searchPanel;
    }

    /**
     * 在焦点到达具体控件前拦截按键，满足条件时转交检索面板。
     *
     * @param event
     *            系统键盘事件
     * @return 已消费并处理时 true
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!shouldIntercept(event)) {
            return false;
        }
        int eventId = event.getID();
        // 可打印字符：追加到检索关键词
        if (eventId == KeyEvent.KEY_TYPED) {
            char keyChar = event.getKeyChar();
            if (Character.isISOControl(keyChar)) {
                return false;
            }
            if (searchPanel.appendSearchChar(keyChar)) {
                event.consume();
                return true;
            }
            return false;
        }
        // 编辑键：回退或清空当前关键词
        if (eventId == KeyEvent.KEY_PRESSED) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.VK_BACK_SPACE) {
                if (searchPanel.backspaceSearchChar()) {
                    event.consume();
                    return true;
                }
            } else if (keyCode == KeyEvent.VK_ESCAPE) {
                if (searchPanel.clearSearchQuery()) {
                    event.consume();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断是否应由全局分发接管当前按键。
     *
     * @param event
     *            系统键盘事件
     * @return 需要拦截时 true
     */
    private boolean shouldIntercept(KeyEvent event) {
        if (event.isConsumed()) {
            return false;
        }
        int eventId = event.getID();
        if (eventId != KeyEvent.KEY_TYPED && eventId != KeyEvent.KEY_PRESSED) {
            return false;
        }
        // 仅在根目录页签且已设定检索范围时启用
        if (!isRootTabSelected() || searchPanel.hasNotActiveScope()) {
            return false;
        }
        // 搜索框已聚焦时由 JTextField 原生处理，避免重复追加
        if (searchPanel.isSearchFieldFocused()) {
            return false;
        }
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        // 模态对话框或其它顶层窗口持有焦点时不拦截
        if (isFocusOutsideOwner(focusOwner)) {
            return false;
        }
        // 根目录页签内其它可编辑控件（防御性判断）仍走原生输入
        return !isForeignTextInput(focusOwner);
    }

    /**
     * 当前是否选中根目录页签。
     *
     * @return 选中根目录页签时 true
     */
    private boolean isRootTabSelected() {
        int rootTabIndex = tabbedPane.indexOfTab(rootTabTitle);
        return rootTabIndex >= 0 && tabbedPane.getSelectedIndex() == rootTabIndex;
    }

    /**
     * 焦点是否落在主窗口之外的窗口（如文件选择框、确认框）。
     *
     * @param focusOwner
     *            当前焦点控件
     * @return 焦点在外部窗口时 true
     */
    private boolean isFocusOutsideOwner(Component focusOwner) {
        if (focusOwner == null) {
            return false;
        }
        Window focusWindow = SwingUtilities.getWindowAncestor(focusOwner);
        return focusWindow != null && focusWindow != ownerFrame;
    }

    /**
     * 焦点是否在非检索框的可编辑文本控件上。
     *
     * @param focusOwner
     *            当前焦点控件
     * @return 是其它文本输入控件时 true
     */
    private boolean isForeignTextInput(Component focusOwner) {
        if (focusOwner == null) {
            return false;
        }
        if (focusOwner instanceof JTextComponent) {
            return !searchPanel.isSearchField(focusOwner);
        }
        return false;
    }
}

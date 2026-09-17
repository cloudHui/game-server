package com.gamer.data.excel.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.text.JTextComponent;

import com.gamer.data.ui.ViewUi;

/**
 * 名称子串筛选：红字检索条、列表高亮、宿主页展示时整窗收键。
 * 只改展示模型，不回写全量名单。Esc 清空后选第一条由 {@link #applyFilter} 负责。
 */
public class ViewSubstringFilterList {

    private String filterText = "";// 当前筛选关键字
    private JPanel filterBar;// 检索条，有字才显示
    private JTextField filterField;// 只读展示，输入走窗口分发
    private JComponent host;// 收键宿主，isShowing 时才拦截
    private Runnable onFilterChanged;// 筛选词变更后刷新展示
    private KeyEventDispatcher keyDispatcher;// 窗口级键盘分发
    private AncestorListener hostAncestorListener;// 随宿主挂上/卸下分发器
    private boolean dispatcherRegistered;// 是否已注册到 KeyboardFocusManager

    /**
     * 构建检索条（有输入时由 {@link #syncFilterBar()} 显示）。
     *
     * @return 检索条面板
     */
    public JPanel buildFilterBar() {
        filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        filterBar.setOpaque(true);
        filterBar.setBackground(ViewUi.STATUS);
        filterBar.setVisible(false);
        filterField = new JTextField(18);
        filterField.setEditable(false);
        filterField.setFocusable(false);
        filterField.setFont(ViewUi.FONT_B);
        filterField.setForeground(Color.RED);
        filterField.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        filterBar.add(ViewUi.label("检索:"));
        filterBar.add(filterField);
        return filterBar;
    }

    /**
     * 宿主页展示期间整窗收键。可编辑文本与下拉框不拦。重复调用会先卸上一宿主。
     *
     * @param newHost
     *            页面根（该组件显示时生效）
     * @param filterChanged
     *            筛选变更后刷新列表/表
     */
    public void installOn(JComponent newHost, Runnable filterChanged) {
        uninstallKeyCapture();
        if (newHost == null) {
            return;
        }
        host = newHost;
        onFilterChanged = filterChanged;
        keyDispatcher = this::dispatchFilterKey;
        hostAncestorListener = new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                setDispatcherActive(true);
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
                setDispatcherActive(false);
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {}
        };
        host.addAncestorListener(hostAncestorListener);
        if (host.isShowing()) {
            setDispatcherActive(true);
        }
    }

    /**
     * 按当前筛选词过滤名称列表（忽略大小写子串包含）。
     *
     * @param allNames
     *            完整名单，不修改入参
     * @return 命中的新列表
     */
    public List<String> filterNames(List<String> allNames) {
        List<String> out = new ArrayList<>();
        if (allNames == null) {
            return out;
        }
        String needle = filterText.toLowerCase();
        for (String name : allNames) {
            if (needle.isEmpty() || name.toLowerCase().contains(needle)) {
                out.add(name);
            }
        }
        return out;
    }

    /**
     * 按当前筛选词刷新列表，默认选中第一条。
     *
     * @param allNames
     *            完整项列表
     * @param model
     *            展示用列表模型
     * @param list
     *            JList 组件
     */
    public void applyFilter(List<String> allNames, DefaultListModel<String> model, JList<String> list) {
        model.clear();
        for (String name : filterNames(allNames)) {
            model.addElement(name);
        }
        if (model.isEmpty()) {
            list.clearSelection();
        } else {
            list.setSelectedIndex(0);
        }
        syncFilterBar();
    }

    /**
     * 同步红字检索条显示。
     */
    public void syncFilterBar() {
        if (filterBar == null) {
            return;
        }
        boolean hasFilter = !filterText.isEmpty();
        filterBar.setVisible(hasFilter);
        filterField.setText(hasFilter ? filterText : "");
        filterBar.revalidate();
        filterBar.repaint();
    }

    /**
     * 清空筛选词（不改展示模型，由调用方再刷新）。
     */
    public void resetFilter() {
        filterText = "";
        syncFilterBar();
    }

    /**
     * 创建检索命中段红字高亮的列表渲染器。
     *
     * @return DefaultListCellRenderer
     */
    public DefaultListCellRenderer createHighlightCellRenderer() {
        return new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                boolean cellHasFocus) {
                JLabel row = (JLabel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                row.setFont(ViewUi.FONT);
                if (value == null) {
                    return row;
                }
                String text = value.toString();
                row.setText(filterText.isEmpty() ? text
                    : ViewHtmlHighlightUtil.highlightSubstringRedHtml(text, filterText));
                return row;
            }
        };
    }

    /**
     * 可打印字符追加，Backspace/Esc 改检索词。
     *
     * @param event
     *            系统键盘事件
     * @return 已消费时 true
     */
    private boolean dispatchFilterKey(KeyEvent event) {
        if (!shouldIntercept(event)) {
            return false;
        }
        int id = event.getID();
        if (id == KeyEvent.KEY_TYPED) {
            char ch = event.getKeyChar();
            if (Character.isISOControl(ch)) {
                return false;
            }
            filterText += ch;
        } else if (id == KeyEvent.KEY_PRESSED && event.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
            if (filterText.isEmpty()) {
                return false;
            }
            filterText = filterText.substring(0, filterText.length() - 1);
        } else if (id == KeyEvent.KEY_PRESSED && event.getKeyCode() == KeyEvent.VK_ESCAPE) {
            if (filterText.isEmpty()) {
                return false;
            }
            filterText = "";
        } else {
            return false;
        }
        if (onFilterChanged != null) {
            onFilterChanged.run();
        }
        event.consume();
        return true;
    }

    /**
     * 宿主可见、本窗聚焦、非编辑控件时才收键。
     *
     * @param event
     *            系统键盘事件
     * @return 需要拦截时 true
     */
    private boolean shouldIntercept(KeyEvent event) {
        if (event.isConsumed() || host == null || !host.isShowing()) {
            return false;
        }
        int id = event.getID();
        if (id != KeyEvent.KEY_TYPED && id != KeyEvent.KEY_PRESSED) {
            return false;
        }
        if (event.isControlDown() || event.isAltDown() || event.isMetaDown() || event.isAltGraphDown()) {
            return false;
        }
        KeyboardFocusManager fm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        Window hostWindow = SwingUtilities.getWindowAncestor(host);
        return hostWindow != null && fm.getFocusedWindow() == hostWindow && !isBlockedFocusOwner(fm.getFocusOwner());
    }

    /**
     * 可编辑文本、列绑定下拉正在用键盘时不抢键。
     *
     * @param focusOwner
     *            当前焦点控件
     * @return 应放行原生输入时 true
     */
    private boolean isBlockedFocusOwner(Component focusOwner) {
        for (Component c = focusOwner; c != null; c = c.getParent()) {
            if (c instanceof JComboBox) {
                return true;
            }
        }
        return focusOwner instanceof JTextComponent && ((JTextComponent)focusOwner).isEditable();
    }

    /**
     * 注册或卸下全局分发器。
     *
     * @param active
     *            true 注册，false 卸下
     */
    private void setDispatcherActive(boolean active) {
        if (keyDispatcher == null || dispatcherRegistered == active) {
            return;
        }
        KeyboardFocusManager fm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        if (active) {
            fm.addKeyEventDispatcher(keyDispatcher);
        } else {
            fm.removeKeyEventDispatcher(keyDispatcher);
        }
        dispatcherRegistered = active;
    }

    /**
     * 卸下上一宿主的监听与分发，避免菜单反复进入叠多个分发器。
     */
    private void uninstallKeyCapture() {
        setDispatcherActive(false);
        if (host != null && hostAncestorListener != null) {
            host.removeAncestorListener(hostAncestorListener);
        }
        host = null;
        onFilterChanged = null;
        keyDispatcher = null;
        hostAncestorListener = null;
    }
}


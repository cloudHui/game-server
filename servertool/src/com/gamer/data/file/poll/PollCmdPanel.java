package com.gamer.data.file.poll;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;

import com.gamer.data.ui.ViewUi;

/**
 * 间隔轮询命令：挂到任务页日志栏，命中关键字后通知并结束。
 */
public final class PollCmdPanel {

    private JTabbedPane taskTabs;
    /** 工作目录。 */
    private final JTextField pathField = new JTextField(14);
    /** cmd 命令。 */
    private final JTextField cmdField = new JTextField(10);
    /** 间隔秒。 */
    private final JTextField intervalField = new JTextField("60", 3);
    /** 期望关键字。 */
    private final JTextField keywordField = new JTextField(8);

    private PollCmdPanel() {}

    /**
     * 把新建轮询表单挂到共用日志页签栏（关程序即丢）。
     *
     * @param hostTabs
     *            任务页日志栏
     * @return 表单
     */
    public static JPanel form(JTabbedPane hostTabs) {
        PollCmdPanel panel = new PollCmdPanel();
        panel.taskTabs = hostTabs;
        return panel.buildForm();
    }

    private JPanel buildForm() {
        ViewUi.compactField(pathField);
        ViewUi.compactField(cmdField);
        ViewUi.compactField(intervalField);
        ViewUi.compactField(keywordField);
        JPanel body = ViewUi.line(ViewUi.label("目录"), ViewUi.fillX(pathField), ViewUi.click("选择", this::browse),
            ViewUi.label("命令"), ViewUi.fillX(cmdField), ViewUi.label("间隔"), intervalField, ViewUi.label("关键字"),
            keywordField, ViewUi.click("开始", this::onStart));
        return ViewUi.compactCard("轮询（关程序即丢）", body);
    }

    private void browse() {
        JFileChooser chooser = new JFileChooser(pathField.getText().trim());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(pathField) == JFileChooser.APPROVE_OPTION) {
            pathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void onStart() {
        PollCmdTask task = parseForm();
        if (task == null) {
            return;
        }
        JPanel view = buildTaskView(task);
        taskTabs.addTab(task.tabTitle(), view);
        int index = taskTabs.getTabCount() - 1;
        taskTabs.setTabComponentAt(index, buildClosableTab(task, view));
        taskTabs.setSelectedIndex(index);
        PollCmdEngine.start(task);
    }

    /** 带 × 的可关闭页签头：关闭时取消任务并移除页签。 */
    private JPanel buildClosableTab(PollCmdTask task, JPanel view) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        header.setOpaque(false);
        JLabel title = ViewUi.label(task.tabTitle());
        header.add(title);
        JButton close = ViewUi.style(new JButton("×"));
        close.setMargin(new Insets(0, 4, 0, 4));
        close.setPreferredSize(new Dimension(22, 18));
        close.setToolTipText("关闭任务");
        close.addActionListener(e -> closeTask(task, view));
        header.add(close);
        return header;
    }

    private void closeTask(PollCmdTask task, JPanel view) {
        task.cancelled.set(true);
        int index = taskTabs.indexOfComponent(view);
        if (index >= 0) {
            taskTabs.remove(index);
        }
    }

    /** 校验并构造任务；失败弹窗并返回 null。 */
    private PollCmdTask parseForm() {
        String path = pathField.getText().trim();
        String cmd = cmdField.getText().trim();
        String keyword = keywordField.getText().trim();
        int interval;
        try {
            interval = Integer.parseInt(intervalField.getText().trim());
        } catch (NumberFormatException e) {
            alert("间隔必须是正整数秒");
            return null;
        }
        File dir = new File(path);
        if (path.isEmpty() || !dir.isDirectory()) {
            alert("请填写有效工作目录");
            return null;
        }
        if (cmd.isEmpty()) {
            alert("请填写 cmd 命令");
            return null;
        }
        if (interval <= 0) {
            alert("间隔必须大于 0");
            return null;
        }
        if (keyword.isEmpty()) {
            alert("请填写期望关键字");
            return null;
        }
        return new PollCmdTask(dir, cmd, interval, keyword);
    }

    private JPanel buildTaskView(PollCmdTask task) {
        JPanel panel = new JPanel(new BorderLayout());
        final JButton cancel = ViewUi.style(new JButton("取消"));
        cancel.addActionListener(e -> {
            task.cancelled.set(true);
            PollCmdUi.setStatus(task, "取消中（当前轮跑完后停）");
            cancel.setEnabled(false);
        });
        ViewUi.statusBar(task.statusLabel);
        JPanel north = new JPanel(new BorderLayout());
        north.add(task.statusLabel, BorderLayout.CENTER);
        north.add(cancel, BorderLayout.EAST);
        panel.add(north, BorderLayout.NORTH);
        panel.add(ViewUi.scroll(task.logArea), BorderLayout.CENTER);
        return panel;
    }

    private void alert(String msg) {
        JOptionPane.showMessageDialog(pathField.getTopLevelAncestor(), msg, "提示", JOptionPane.WARNING_MESSAGE);
    }
}

package com.gamer.data.file.poll;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * 任务 UI 更新（统一切 EDT）。
 */
final class PollCmdUi {

    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss");

    private PollCmdUi() {}

    static void append(PollCmdTask task, String line) {
        String text = TIME.format(new Date()) + " " + line + "\n";
        SwingUtilities.invokeLater(() -> {
            task.logArea.append(text);
            task.logArea.setCaretPosition(task.logArea.getDocument().getLength());
        });
    }

    static void setStatus(PollCmdTask task, String status) {
        SwingUtilities.invokeLater(() -> task.statusLabel.setText(status));
    }

    /** 命中关键字后弹窗通知。 */
    static void notifyHit(PollCmdTask task) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
            task.logArea.getTopLevelAncestor(),
            "任务 #" + task.id + " 命中关键字：\n" + task.keyword + "\n命令：" + task.command,
            "定时命令完成",
            JOptionPane.INFORMATION_MESSAGE));
    }
}

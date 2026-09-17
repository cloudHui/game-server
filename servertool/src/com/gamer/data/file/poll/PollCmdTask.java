package com.gamer.data.file.poll;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.JLabel;
import javax.swing.JTextArea;

import com.gamer.data.ui.ViewUi;

/**
 * 一条定时命令任务（仅内存，不落盘）。
 */
final class PollCmdTask {

    private static final AtomicLong SEQ = new AtomicLong(1);

    /** 任务 id */
    final long id = SEQ.getAndIncrement();
    /** 工作目录 */
    final File workDir;
    /** cmd 命令 */
    final String command;
    /** 重复间隔（秒） */
    final int intervalSec;
    /** 成功关键字（子串、忽略大小写） */
    final String keyword;
    /** 用户取消标记：只停续期，不杀进程 */
    final AtomicBoolean cancelled = new AtomicBoolean(false);

    /** 日志区 */
    final JTextArea logArea = new JTextArea();
    /** 状态栏 */
    final JLabel statusLabel = new JLabel("待启动");

    PollCmdTask(File workDir, String command, int intervalSec, String keyword) {
        this.workDir = workDir;
        this.command = command;
        this.intervalSec = intervalSec;
        this.keyword = keyword;
        logArea.setEditable(false);
        ViewUi.log(logArea);
    }

    /** 页签标题 */
    String tabTitle() {
        return "#" + id + " " + command;
    }
}

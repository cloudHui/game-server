package com.gamer.data.file.task;

import java.awt.BorderLayout;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import com.gamer.data.file.client.deploy.DeployModule;
import com.gamer.data.file.client.download.ClientDownloader;
import com.gamer.data.file.client.download.Log;
import com.gamer.data.file.client.workday.Scheduler;
import com.gamer.data.file.cmd.Logs;
import com.gamer.data.file.mcp.CleanLog;
import com.gamer.data.file.mcp.LogCleaner;
import com.gamer.data.file.poll.PollCmdPanel;
import com.gamer.data.file.port.Port;
import com.gamer.data.file.report.Pull;
import com.gamer.data.file.report.Unpack;
import com.gamer.data.ui.ViewUi;

/**
 * 「任务」页：测服、客户端、端口、定时与轮询。
 */
public final class TaskPanel {

    /** 页签标题。 */
    public static final String TAB_TITLE = "任务";

    private TaskPanel() {}

    /**
     * 组装任务页。须在 CMD 页创建之后调用。
     *
     * @return 面板
     */
    public static JPanel create() {
        JTabbedPane logs = ViewUi.logTabs();
        Logs.bindTo(logs, () -> {
            Logs.bindExecutable(CleanLog.LOG_KEY, CleanLog.TAB_TITLE, LogCleaner::run);
            Logs.bindExecutable(Log.LOG_KEY, Log.TAB_TITLE, ClientDownloader::download);
            Logs.bindExecutable(DeployModule.LOG_KEY, DeployModule.TAB_TITLE, DeployModule::run);
            Logs.bindExecutable(Pull.LOG_KEY, Pull.TAB_TITLE, Pull::run);
            Logs.bindExecutable(Port.KEY, Port.TITLE, Port::queryInput);
        });
        Pull.setScheduled(true);
        ClientDownloader.setScheduled(true);
        LogCleaner.setScheduled(true);
        Scheduler.start();

        JPanel root = new JPanel(new BorderLayout(6, 4));
        ViewUi.page(root, 4, 8, 4, 8);
        root.add(ViewUi.stack(buildCards(), buildTools(logs)), BorderLayout.NORTH);
        root.add(logs, BorderLayout.CENTER);
        root.add(Logs.execBar(logs, "下载点按钮；可切页签再执行。", null), BorderLayout.SOUTH);
        return root;
    }

    /**
     * 测服 / 客户端 / 端口，顶对齐不等高拉伸。
     *
     * @return 卡片行
     */
    private static JPanel buildCards() {
        JPanel server = ViewUi.stack(
            TaskFields.sshPullRow(ViewUi.primary(Logs.boundButton("下载服务日志", Pull.LOG_KEY)), Unpack.pauseButton(),
                Unpack.haltButton()),
            TaskFields.sshHostRow(), TaskFields.sshKeyRow());
        JPanel client = ViewUi.stack(
            ViewUi.line(Logs.boundButton("客户端下载", Log.LOG_KEY), Logs.boundButton("解压并部署", DeployModule.LOG_KEY),
                Logs.boundButton("清理以前日志", CleanLog.LOG_KEY)),
            TaskFields.clientRow());
        return ViewUi.topCards(ViewUi.compactCard("服务", server), ViewUi.compactCard("客户端", client), Port.card());
    }

    /**
     * 工作日定时 + 轮询。
     *
     * @param logs
     *            任务日志栏，轮询页签挂这里
     * @return 一行
     */
    private static JPanel buildTools(JTabbedPane logs) {
        JPanel tools = new JPanel(new BorderLayout(6, 0));
        tools.setOpaque(false);
        tools.add(buildSchedule(), BorderLayout.WEST);
        tools.add(PollCmdPanel.form(logs), BorderLayout.CENTER);
        return tools;
    }

    /**
     * 工作日 10:00 勾选，默认开。
     *
     * @return 一行
     */
    private static JPanel buildSchedule() {
        JPanel row = ViewUi.line(ViewUi.check("测服下载", true, Pull::setScheduled),
            ViewUi.check("客户端下载", true, ClientDownloader::setScheduled),
            ViewUi.check("清理以前日志", true, LogCleaner::setScheduled));
        row.setToolTipText("默认开，可关；部署 / 端口只手动。关程序后下次仍默认开。");
        return ViewUi.compactCard("定时 工作日 10:00", row);
    }
}

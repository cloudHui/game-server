package com.gamer.data.file.mcp;

import java.awt.Component;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

import javax.swing.SwingUtilities;

import com.gamer.data.file.cmd.Logs;
import com.gamer.data.file.cmd.TaskLogView;
import com.gamer.data.file.config.PathConfig;
import com.gamer.data.file.job.Pipe;
import com.gamer.data.file.port.Port;
import com.gamer.data.file.port.ProcessInfo;
import com.gamer.data.task.BackgroundTasks;
import com.gamer.data.ui.ViewUi;

/**
 * CMD 页 MCP 按钮；状态、等待、结果写到底部 MCP 日志栏。
 */
public final class McpPanel {

    /** 与 {@code McpConfig.PORT} 一致。 */
    public static final int PORT = 18765;
    /** 底部日志页签键。 */
    public static final String LOG_KEY = "MCP_SERVER";
    /** 底部日志页签标题。 */
    public static final String TITLE = "MCP";

    /** 启动 / 重启 / 刷新互斥。 */
    private static boolean busy;
    /** 底部「执行」复跑上次动作，默认刷新。 */
    private static String lastBat;

    private McpPanel() {}

    /**
     * 启动 / 重启 / 刷新。
     *
     * @return 按钮
     */
    public static Component[] buttons() {
        return new Component[] {ViewUi.click("启动", () -> go("start")), ViewUi.click("重启", () -> go("restart")),
            ViewUi.click("刷新", () -> go(null))};
    }

    /**
     * 底部「执行」复跑上次启动 / 重启 / 刷新。
     */
    public static void replay() {
        go(lastBat);
    }

    /**
     * 跑 bat 或只刷新。过程进日志，状态进页签状态栏。
     *
     * @param bat
     *            start / restart，null 只查状态
     */
    private static void go(String bat) {
        TaskLogView view = Logs.bindLogView(LOG_KEY, TITLE, true);
        if (busy) {
            Logs.appendLog(view, "已在执行，跳过");
            return;
        }
        busy = true;
        lastBat = bat;
        view.runCount++;
        String wait = bat == null ? "查询中" : "restart".equals(bat) ? "重启中" : "启动中";
        Logs.setLogPhaseDetail(view, wait);
        Logs.appendLog(view, "");
        Logs.appendLog(view, "# " + wait + "（第" + view.runCount + "次）");
        BackgroundTasks.start("mcp", () -> {
            String text = wait;
            try {
                text = query(bat, view);
            } catch (Exception e) {
                Logs.appendThrowable(view, "失败: ", e);
                text = "失败  " + e.getMessage();
            }
            done(view, text);
            return 0;
        });
    }

    /**
     * 回 EDT：状态栏写进程信息，释放互斥。
     *
     * @param view
     *            日志页
     * @param text
     *            状态文案
     */
    private static void done(TaskLogView view, String text) {
        SwingUtilities.invokeLater(() -> {
            Logs.setLogPhaseDetail(view, text);
            busy = false;
        });
    }

    /**
     * 可选执行 bat（UTF-8，避免 Java 日志乱码），再按端口取进程并探健康。
     *
     * @param bat
     *            start / restart，null 只查
     * @param view
     *            MCP 日志页
     * @return 状态一行
     */
    private static String query(String bat, TaskLogView view) throws Exception {
        String note = "";
        if (bat != null) {
            int code = Pipe.runLines(Arrays.asList("cmd", "/c", "call McpServer.bat " + bat),
                new File(PathConfig.BIN_SERVER_DIR), null, true, StandardCharsets.UTF_8,
                line -> Logs.appendLog(view, line));
            note = code == 0 ? "" : "退出码 " + code;
            Logs.appendLog(view, "结果: " + (code == 0 ? "成功" : "失败") + (note.isEmpty() ? "" : "，" + note));
        }
        ProcessInfo process;
        try {
            process = Port.listeningProcess(PORT);
        } catch (Exception e) {
            Logs.appendLog(view, e.getMessage());
            return e.getMessage();
        }
        String text = format(process, healthOk(), note);
        Logs.appendLog(view, text);
        return text;
    }

    /**
     * 本机 /health，2 秒超时。
     *
     * @return 是否 200 且正文 OK
     */
    private static boolean healthOk() {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("http://127.0.0.1:" + PORT + "/health").openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            try (InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[16];
                int n = in.read(buf);
                return conn.getResponseCode() == 200 && n > 0
                    && "OK".equals(new String(buf, 0, n, StandardCharsets.UTF_8).trim());
            }
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 一行：进程、内存、监听、健康。
     *
     * @param process
     *            端口进程，可空
     * @param ok
     *            健康是否通过
     * @param note
     *            补充
     * @return 文案
     */
    private static String format(ProcessInfo process, boolean ok, String note) {
        String health = "127.0.0.1:" + PORT + "  " + (ok ? "健康 OK" : "健康失败");
        if (!note.isEmpty()) {
            health += "  " + note;
        }
        if (process == null || process.pid <= 0) {
            return (note.isEmpty() ? "未运行" : note) + "  " + health;
        }
        String name = process.name == null || process.name.isEmpty() ? "javaw.exe" : process.name;
        return name + "  (PID " + process.pid + ", 约 "
            + String.format(Locale.ROOT, "%.0fMB", process.workingSetKb / 1024.0) + ")  " + health;
    }
}

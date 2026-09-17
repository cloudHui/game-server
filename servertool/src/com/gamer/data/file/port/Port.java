package com.gamer.data.file.port;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.gamer.data.file.cmd.Logs;
import com.gamer.data.ui.ViewUi;
import com.gamer.data.file.cmd.TaskLogView;
import com.gamer.data.file.job.Pipe;
import com.gamer.data.file.job.Run;
import com.gamer.data.task.BackgroundTasks;

/**
 * 本机端口占用：填端口或 PID，查 LISTENING、确认后 taskkill。
 */
public final class Port {

    /** 页签键。 */
    public static final String KEY = "PORT_TOOL";
    /** 页签标题。 */
    public static final String TITLE = "端口";

    /** 一条占用。 */
    private static final class Hold {
        /** 端口，仅 PID 时为 0。 */
        public final int port;
        /** PID。 */
        public final int pid;
        /** 状态。 */
        public final String state;
        /** 本地地址。 */
        public final String local;
        /** 占用进程详情。 */
        public final ProcessInfo process;

        Hold(int port, int pid, String state, String local, ProcessInfo process) {
            this.port = port;
            this.pid = pid;
            this.state = state;
            this.local = local;
            this.process = process;
        }
    }

    /** 互斥。 */
    private static final Run RUN = new Run();
    /** 本进程 PID，结束时跳过；解析失败为 -1。 */
    private static final int SELF_PID = readSelfPid();

    /** 端口输入。 */
    private static JTextField portField;
    /** PID 输入。 */
    private static JTextField pidField;

    private Port() {}

    /**
     * 端口卡片：只填端口 / PID。
     *
     * @return 卡片
     */
    public static JPanel card() {
        if (portField == null) {
            portField = new JTextField("", 8);
            pidField = new JTextField("", 8);
            ViewUi.compactField(portField);
            ViewUi.compactField(pidField);
        }
        JPanel body = ViewUi.stack(
            ViewUi.line(ViewUi.label("端口"), portField, ViewUi.click("查询", Port::queryInput),
                ViewUi.danger(ViewUi.click("结束占用", Port::killPort))),
            ViewUi.line(ViewUi.label("PID"), pidField, ViewUi.danger(ViewUi.click("结束进程", Port::killPid))));
        return ViewUi.compactCard("端口", body);
    }

    /**
     * 底部「执行」= 查输入框端口。
     */
    public static void queryInput() {
        Integer port = readInt(portField, 65535, "端口 1–65535", "端口不是数字");
        if (port == null) {
            return;
        }
        final int p = port;
        run(() -> {
            log("---- 查端口 " + p + " ----");
            List<Hold> holds = listening(p);
            if (holds.isEmpty()) {
                log("无 LISTENING");
                return 0;
            }
            for (Hold h : holds) {
                logHold(h);
            }
            log("共 " + holds.size() + " 条");
            return 0;
        });
    }

    /**
     * 结束输入框端口上 LISTENING 进程（确认后）。
     */
    public static void killPort() {
        Integer port = readInt(portField, 65535, "端口 1–65535", "端口不是数字");
        if (port == null) {
            return;
        }
        final int p = port;
        run(() -> {
            List<Hold> holds = listening(p);
            if (holds.isEmpty()) {
                log("端口 " + p + " 无 LISTENING");
                return 0;
            }
            List<Hold> killable = removeProtectedServiceHosts(holds);
            if (killable.isEmpty()) {
                showProtectedWarning(holds);
                return 0;
            }
            if (notConfirm(killable, "结束占用端口 " + p + " 的进程？")) {
                log("已取消");
                return 0;
            }
            return killAll(killable);
        });
    }

    /**
     * 结束输入框 PID（确认后）。
     */
    public static void killPid() {
        Integer pidValue = readInt(pidField, Integer.MAX_VALUE, "填写 PID", "PID 不是数字");
        if (pidValue == null) {
            return;
        }
        final int pid = pidValue;
        run(() -> {
            ProcessInfo process = ProcessInfo.query(pid);
            List<Hold> list = new ArrayList<>();
            list.add(new Hold(0, pid, "", "", process));
            if (process.isProtectedServiceHost()) {
                showProtectedWarning(list);
                return 0;
            }
            if (notConfirm(list, "结束 PID " + pid + " " + process.name + " ？")) {
                log("已取消");
                return 0;
            }
            kill(pid);
            log("已结束 PID " + pid + " " + process.name);
            return 0;
        });
    }

    private static void run(BackgroundTasks.Work work) {
        RUN.start("跳过：端口任务仍在执行", "port-tool", view(true), work);
    }

    /**
     * 指定端口的 LISTENING（同端口+PID+地址去重；进程详情按 PID 缓存）。
     *
     * @param port
     *            端口
     * @return 占用列表
     * @throws IOException
     *             netstat 失败
     */
    private static List<Hold> listening(int port) throws IOException {
        List<Hold> all = parseNetstat();
        List<Hold> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Map<Integer, ProcessInfo> processInfoMap = new HashMap<>();
        for (Hold h : all) {
            if (!"LISTENING".equalsIgnoreCase(h.state) && !"UDP".equalsIgnoreCase(h.state)) {
                continue;
            }
            if (h.port != port || !seen.add(h.port + "-" + h.pid + "-" + h.local)) {
                continue;
            }
            Integer pidKey = h.pid;
            ProcessInfo process = processInfoMap.get(pidKey);
            if (process == null) {
                process = ProcessInfo.query(h.pid);
                processInfoMap.put(pidKey, process);
            }
            out.add(new Hold(h.port, h.pid, h.state, h.local, process));
        }
        return out;
    }

    /**
     * 指定端口第一条 LISTENING 进程。
     *
     * @param port
     *            端口
     * @return 进程，未占用时 null
     * @throws IOException
     *             netstat 失败
     */
    public static ProcessInfo listeningProcess(int port) throws IOException {
        List<Hold> holds = listening(port);
        return holds.isEmpty() ? null : holds.get(0).process;
    }

    /**
     * 强制结束 PID。
     *
     * @param pid
     *            PID
     * @throws IOException
     *             不允许或 taskkill 失败
     */
    private static void kill(int pid) throws IOException {
        if (pid <= 4) {
            throw new IOException("拒绝结束系统 PID " + pid);
        }
        if (SELF_PID > 0 && pid == SELF_PID) {
            throw new IOException("拒绝结束当前 WindowsTools 进程 " + pid);
        }
        String out = Pipe.capture("taskkill /PID " + pid + " /F");
        if (!out.contains("成功") && !out.toLowerCase().contains("success")) {
            throw new IOException(out.trim().isEmpty() ? "taskkill 无输出" : out.trim());
        }
    }

    /**
     * 移除共享服务宿主，避免强杀一个 PID 时连带停止多个 Windows 服务。
     *
     * @param holds
     *            原占用列表
     * @return 可安全进入确认流程的占用列表
     */
    private static List<Hold> removeProtectedServiceHosts(List<Hold> holds) {
        List<Hold> killable = new ArrayList<>();
        Set<Integer> protectedPids = new LinkedHashSet<>();
        for (Hold hold : holds) {
            if (hold.process.isProtectedServiceHost()) {
                if (protectedPids.add(hold.pid)) {
                    log("[保护] PID " + hold.pid + " " + hold.process.name + " 是共享服务宿主，禁止强制结束");
                }
            } else {
                killable.add(hold);
            }
        }
        return killable;
    }

    /**
     * 判断是否应禁止强杀。svchost 即使服务枚举受权限限制也按共享宿主保护。
     *
     * @param info
     *            进程详情
     * @return 是否保护
     */
    /**
     * 弹出共享服务宿主保护提示。
     *
     * @param holds
     *            被保护的占用
     */
    private static void showProtectedWarning(List<Hold> holds) {
        final StringBuilder message = new StringBuilder("检测到共享 Windows 服务宿主，已禁止强制结束。\n");
        Set<Integer> seen = new LinkedHashSet<>();
        for (Hold hold : holds) {
            if (!hold.process.isProtectedServiceHost() || !seen.add(hold.pid)) {
                continue;
            }
            message.append("PID ").append(hold.pid).append(' ').append(hold.process.name).append('\n');
            if (hold.process.services.isEmpty()) {
                message.append("  服务列表不可读取\n");
            } else {
                message.append(hold.process.serviceText("  "));
            }
        }
        message.append("请在 Windows 服务管理器中确认后单独停止服务。");
        showMessage(message.toString(), "共享服务宿主", JOptionPane.WARNING_MESSAGE);
    }

    /**
     * 输出一条端口占用及进程详情。
     *
     * @param hold
     *            端口占用
     */
    private static void logHold(Hold hold) {
        ProcessInfo process = hold.process;
        String name = process == null ? "?" : process.name;
        String program = process == null ? "?" : process.programName();
        String identity = name.equals(program) ? name : name + " / " + program;
        log(hold.port + "  " + hold.state + "  PID " + hold.pid + "  " + identity + "  " + hold.local);
    }

    /**
     * 在 EDT 显示消息。
     *
     * @param message
     *            内容
     * @param title
     *            标题
     * @param type
     *            JOptionPane 消息类型
     */
    private static void showMessage(final String message, final String title, final int type) {
        ViewUi.edtWait(() -> JOptionPane.showMessageDialog(null, message, title, type));
    }

    /**
     * 结束一组占用。
     *
     * @param holds
     *            占用
     * @return 0 全成功，1 有失败
     */
    private static int killAll(List<Hold> holds) {
        int fail = 0;
        for (Hold h : holds) {
            try {
                kill(h.pid);
                log("已结束 PID " + h.pid + " " + h.process.name + " 端口 " + h.port);
            } catch (Exception e) {
                log("[错误] PID " + h.pid + " " + e.getMessage());
                fail++;
            }
        }
        return fail == 0 ? 0 : 1;
    }

    /**
     * 在 EDT 弹出确认。
     *
     * @param holds
     *            将结束的进程
     * @param head
     *            标题句
     * @return 同意
     */
    private static boolean notConfirm(final List<Hold> holds, final String head) {
        final StringBuilder msg = new StringBuilder();
        msg.append(head).append('\n');
        for (Hold h : holds) {
            msg.append("PID ").append(h.pid).append(' ').append(h.process.name).append("  程序 ")
                .append(h.process.programName());
            if (h.port > 0) {
                msg.append("  端口 ").append(h.port);
            }
            msg.append('\n');
            msg.append(h.process.serviceText("  服务："));
        }
        final int[] ans = new int[] {JOptionPane.NO_OPTION};
        ViewUi.edtWait(() -> ans[0] = JOptionPane.showConfirmDialog(null, msg.toString(), "结束进程",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE));
        return ans[0] != JOptionPane.YES_OPTION;
    }

    /**
     * 读输入框整数。
     *
     * @param field    输入框
     * @param max      最大（含）
     * @param rangeMsg 超范围提示
     * @param nanMsg   非数字提示
     * @return 合法值，否则 null
     */
    private static Integer readInt(JTextField field, int max, String rangeMsg, String nanMsg) {
        if (field == null) {
            return null;
        }
        try {
            int p = Integer.parseInt(field.getText().trim());
            if (p < 1 || p > max) {
                JOptionPane.showMessageDialog(null, rangeMsg, "提示", JOptionPane.WARNING_MESSAGE);
                return null;
            }
            return p;
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, nanMsg, "提示", JOptionPane.WARNING_MESSAGE);
            return null;
        }
    }

    /**
     * 解析 netstat -ano。
     *
     * @return 全部行
     * @throws IOException
     *             失败
     */
    private static List<Hold> parseNetstat() throws IOException {
        String text = Pipe.capture("netstat -ano");
        List<Hold> list = new ArrayList<>();
        String[] lines = text.split("\r?\n");
        for (String line : lines) {
            Hold h = parseLine(line);
            if (h != null) {
                list.add(h);
            }
        }
        return list;
    }

    /**
     * 解析一行 netstat。
     *
     * @param line
     *            原始行
     * @return 占用或 null
     */
    private static Hold parseLine(String line) {
        if (line == null) {
            return null;
        }
        String[] p = line.trim().split("\\s+");
        if (p.length < 4) {
            return null;
        }
        String proto = p[0];
        if (!"TCP".equalsIgnoreCase(proto) && !"UDP".equalsIgnoreCase(proto)) {
            return null;
        }
        String local = p[1];
        int c = local.lastIndexOf(':');
        if (c < 0 || c == local.length() - 1) {
            return null;
        }
        int port;
        int pid;
        try {
            port = Integer.parseInt(local.substring(c + 1));
            pid = Integer.parseInt(p[p.length - 1]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (port <= 0) {
            return null;
        }
        String state = "UDP".equalsIgnoreCase(proto) ? "UDP" : p[p.length - 2];
        return new Hold(port, pid, state, local, null);
    }

    /**
     * 本进程 PID。
     *
     * @return PID，解析失败为 -1
     */
    private static int readSelfPid() {
        String n = ManagementFactory.getRuntimeMXBean().getName();
        int at = n.indexOf('@');
        if (at <= 0) {
            return -1;
        }
        try {
            return Integer.parseInt(n.substring(0, at));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 页签。
     *
     * @param select
     *            是否切过去
     * @return 视图
     */
    private static TaskLogView view(boolean select) {
        return Logs.bindLogView(KEY, TITLE, select);
    }

    /**
     * 写一行。
     *
     * @param line
     *            文本
     */
    private static void log(String line) {
        Logs.appendLog(view(false), line);
    }
}

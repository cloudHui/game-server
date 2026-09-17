package com.gamer.data.file.cmd;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import com.gamer.data.file.config.PathConfig;
import com.gamer.data.file.job.Pipe;
import com.gamer.data.file.mcp.McpPanel;
import com.gamer.data.task.BackgroundTasks;
import com.gamer.data.ui.ViewUi;

/**
 * 「目录执行 cmd」页签面板：底部「执行」按当前日志页签复跑；自定义页读路径/命令框。
 */
public final class CmdPanel {

    /** 最大并发执行数。 */
    private static final int MAX_PARALLEL = 6;

    public static final String SVN_UPDATE = "svn update";// 更新
    public static final String ANT = "ant";// 打包
    public static final String CALL_PROTOC_JAVA_BAT = "call protoc_java.bat";// 生成协议
    public static final String CALL_OPCODE_JAVA_BAT = "call opcode_java.bat";// 生成协议 id
    public static final String JAVA_JAR_CODE_TOOL = "java -jar CodeTool.jar";// 执行生成配置代码工具
    public static final String SVN_UPDATE_R = "svn update -r";// 更新指定版本
    public static final String SVN_LOG_L_V = "svn log -l 10 -v";// 查看最近10条更新信息并显示详细信息

    public static final String BIN = PathConfig.BIN_DIR;// 公共工具根目录
    public static final String SERVER = PathConfig.BIN_SERVER_DIR;// 服务器工具产物目录
    public static final String DOCUMENT = PathConfig.DOCUMENT_DIR;// 文档目录
    public static final String COMMON = PathConfig.COMMON_DIR;// 公共代码目录
    public static final String COMMON_PROTO = PathConfig.COMMON_PROTO_DIR;// 公共协议目录
    /** Server 工程根目录 */
    public static final String SERVER_ROOT = PathConfig.SERVER_DIR;

    /** 待执行队列。 */
    private static final List<CmdTask> pendingTasks = new ArrayList<>();
    /** 执行中任务映射。 */
    private static final Map<Long, CmdTask> runningTasks = new LinkedHashMap<>();
    /** 执行中的目录集合（同目录串行）。 */
    private static final Set<String> runningDirSet = new HashSet<>();
    /** 任务序号生成器。 */
    private static long taskSequence = 1L;

    /** 手动输入路径与命令时复用的固定日志键。 */
    private static final String CUSTOM_CMD_LOG_KEY = "CUSTOM_CMD";

    /** 协议生成与打包共用的日志键。 */
    static final String PROTOCOL_LOG_KEY = "PROTOCOL_BUILD";

    /** 协议生成与打包共用的日志页签标题。 */
    private static final String PROTOCOL_LOG_TITLE = "协议生成";

    /**
     * 一条固定快捷。多条 defaultSelected=true 时后写的生效。
     */
    private static final class CmdShortcut {
        final String label; // 按钮文案
        final String path; // 配置里的工作目录，入队时用原串
        final String command; // 执行命令
        final String shortName; // 页签短标题
        final boolean defaultSelected; // 启动后默认选中
        final String logKey; // 同目录同命令复用页签

        CmdShortcut(String label, String path, String command, String shortName) {
            this(label, path, command, shortName, false);
        }

        CmdShortcut(String label, String path, String command, String shortName, boolean defaultSelected) {
            this.label = label;
            this.path = path;
            this.command = command;
            this.shortName = shortName;
            this.defaultSelected = defaultSelected;
            this.logKey = buildLogKey(new File(path), command);
        }
    }

    /** 一键执行时的协议任务顺序。 */
    private static final CmdShortcut[] PROTOCOL = {
        new CmdShortcut("生成 opcode", BIN, CALL_OPCODE_JAVA_BAT, "Opcode"),
        new CmdShortcut("生成协议", BIN, CALL_PROTOC_JAVA_BAT, "协议"),
        new CmdShortcut("打包协议", COMMON_PROTO, ANT, "打协议"),
        new CmdShortcut("打包 common", COMMON, ANT, "打Common")};

    /** 常用快捷。默认页看 defaultSelected。 */
    private static final CmdShortcut[] QUICK = {
        new CmdShortcut("执行 CodeTool", SERVER, JAVA_JAR_CODE_TOOL, "CodeTool"),
        new CmdShortcut("更新 bin", BIN, SVN_UPDATE, "更新Bin", true),
        new CmdShortcut("更新 GD/文档", DOCUMENT, SVN_UPDATE, "更新文档")};

    /** 未知目录时的命令简写。 */
    private static final Map<String, String> CMD_BRIEF = new LinkedHashMap<>();

    /** 一键执行中的待执行步骤索引，-1 表示当前没有批量任务。 */
    private static int protocolBatchIndex = -1;

    /** 一键执行的开始时间。 */
    private static long protocolBatchStartMillis;

    /** 一键执行的总耗时。 */
    private static double protocolBatchDurationSec;

    /** 一键执行的显示状态。 */
    private static String protocolBatchStatus = "空闲";

    /** 协议子任务独立耗时：command → 统计。 */
    private static final Map<String, Timing> protocolTimingMap = new LinkedHashMap<>();

    /** 协议子任务耗时统计。 */
    private static final class Timing {
        int runCount;
        double lastSuccessSec;
        long lastSuccessMillis;
    }

    /** 手动输入路径与命令时使用的固定页签标题。 */
    private static final String CUSTOM_CMD_TAB_TITLE = "自定义CMD";

    /** 未知命令摘要最大长度。 */
    private static final int COMMAND_BRIEF_MAX_LEN = 12;

    static {
        // 同命令多目录时用通用简称，其余从快捷表补
        CMD_BRIEF.put(SVN_UPDATE, "SVN");
        CMD_BRIEF.put(SVN_UPDATE_R, "SVN -r");
        CMD_BRIEF.put(SVN_LOG_L_V, "SVN log");
        CMD_BRIEF.put(CALL_PROTOC_JAVA_BAT, "Proto");
        CMD_BRIEF.put(ANT, "ANT");
        putBriefFrom(QUICK);
        putBriefFrom(PROTOCOL);
    }

    /** 路径下拉框。 */
    private static JComboBox<String> pathComboBox;
    /** 命令下拉框。 */
    private static JComboBox<String> commandComboBox;
    /** 自定义页「选择目录」；固定页签禁用。 */
    private static JButton chooseDirButton;
    /** 任务日志页签。 */
    private static JTabbedPane taskLogTabs;
    /** 页签根面板。 */
    private static JPanel cmdPanel;

    /** 路径/命令框上次是否为自定义可编辑，用于跳过重复 setEnabled。 */
    private static boolean lastCustomInputsEnabled = true;

    private CmdPanel() {}

    /**
     * 获取（或创建）CMD 页签面板。
     *
     * @return CMD 面板
     */
    public static JPanel getOrCreateCmdPanel() {
        if (cmdPanel != null) {
            return cmdPanel;
        }
        pathComboBox = new JComboBox<>();
        commandComboBox = new JComboBox<>();
        taskLogTabs = ViewUi.logTabs();
        cmdPanel = new JPanel(new BorderLayout(8, 8));
        ViewUi.page(cmdPanel);
        cmdPanel.add(buildNorthPanel(), BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setOpaque(false);
        taskLogTabs.addChangeListener(e -> syncCustomInputs());
        center.add(taskLogTabs, BorderLayout.CENTER);
        cmdPanel.add(center, BorderLayout.CENTER);
        cmdPanel.add(Logs.execBar(taskLogTabs, "快捷按钮切到对应页签并执行；底部「执行」复跑当前页。", "[SYSTEM] 已手动清空当前任务日志"),
            BorderLayout.SOUTH);
        Logs.attachCmd(taskLogTabs, cmdPanel);
        initializeFixedTaskLogs();
        return cmdPanel;
    }

    /** 预建常用页签，默认选中 QUICK 里 defaultSelected 的那一行。 */
    private static void initializeFixedTaskLogs() {
        TaskLogView selected = null;
        for (CmdShortcut item : QUICK) {
            TaskLogView view = Logs.bindLogView(item.logKey, tabLabel(new File(item.path), item.command, item.shortName),
                false);
            view.executeAction = () -> enqueueValidatedCommand(item.path, item.command, null, null, false, false);
            if (item.defaultSelected) {
                selected = view;
            }
        }
        Logs.bindExecutable(McpPanel.LOG_KEY, McpPanel.TITLE, McpPanel::replay);
        Logs.get(McpPanel.LOG_KEY).trackLastSuccessDuration = false;
        Logs.bindExecutable(PROTOCOL_LOG_KEY, PROTOCOL_LOG_TITLE, CmdPanel::startProtocolBatch);
        Logs.bindExecutable(CUSTOM_CMD_LOG_KEY, CUSTOM_CMD_TAB_TITLE, CmdPanel::enqueueFromInput);
        Logs.select(selected);
    }

    /**
     * 上方：快捷卡片 + 自定义路径/命令条。
     *
     * @return 北区
     */
    private static JPanel buildNorthPanel() {
        setupCombo(pathComboBox);
        setupCombo(commandComboBox);
        refillCmdPathCombo(null);
        refillCommandCombo(null);

        JPanel north = new JPanel(new BorderLayout(0, 8));
        north.setOpaque(false);
        north.add(buildQuickCards(), BorderLayout.CENTER);
        north.add(buildCustomBar(), BorderLayout.SOUTH);
        return north;
    }

    private static void setupCombo(JComboBox<String> combo) {
        combo.setEditable(true);
        combo.setFont(ViewUi.FONT);
        ViewUi.popupOnClick(combo);
    }

    /** 一张通栏：常用 / 协议 / MCP 各一行。测服/下载/端口在「任务」页。 */
    private static JPanel buildQuickCards() {
        return ViewUi.card("快捷", ViewUi.stack(btnRow("常用", quickButtons()), btnRow("协议", protocolButtons()),
            btnRow("MCP", McpPanel.buttons())));
    }

    /**
     * 行名 + 按钮横排。
     *
     * @param title
     *            行名
     * @param buttons
     *            按钮
     * @return 行
     */
    private static JPanel btnRow(String title, Component[] buttons) {
        Component[] items = new Component[buttons.length + 1];
        items[0] = ViewUi.label(title);
        System.arraycopy(buttons, 0, items, 1, buttons.length);
        return ViewUi.row(items);
    }

    private static Component[] quickButtons() {
        Component[] list = new Component[QUICK.length];
        for (int i = 0; i < QUICK.length; i++) {
            list[i] = Logs.boundButton(QUICK[i].label, QUICK[i].logKey);
        }
        return list;
    }

    private static Component[] protocolButtons() {
        Component[] list = new Component[PROTOCOL.length + 1];
        for (int i = 0; i < PROTOCOL.length; i++) {
            final CmdShortcut item = PROTOCOL[i];
            list[i] = ViewUi.click(item.label, () -> {
                enqueueProtocol(item, false);
                Logs.bindLogView(PROTOCOL_LOG_KEY, PROTOCOL_LOG_TITLE, true);
            });
        }
        list[PROTOCOL.length] = Logs.boundButton("一键执行", PROTOCOL_LOG_KEY);
        return list;
    }

    /**
     * 自定义 CMD：仅自定义页可改路径和命令。
     *
     * @return 条
     */
    private static JPanel buildCustomBar() {
        chooseDirButton = ViewUi.style(new JButton("选择目录"));
        chooseDirButton.addActionListener(e -> onChooseCmdDirectory());

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 4, 0, 4);
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.weightx = 0;
        fields.add(ViewUi.label("路径"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        fields.add(pathComboBox, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        fields.add(chooseDirButton, gbc);
        gbc.gridx = 3;
        fields.add(ViewUi.label("命令"), gbc);
        gbc.gridx = 4;
        gbc.weightx = 1;
        fields.add(commandComboBox, gbc);

        return ViewUi.card("自定义 CMD（仅「自定义」页可改）", fields);
    }

    private static void enqueueProtocol(CmdShortcut item, boolean batch) {
        enqueueValidatedCommand(item.path, item.command, PROTOCOL_LOG_KEY, PROTOCOL_LOG_TITLE, false, batch);
    }

    /** 开始一键执行协议生成与打包任务。 */
    private static void startProtocolBatch() {
        synchronized (Logs.LOCK) {
            if (protocolBatchIndex >= 0) {
                return;
            }
            protocolBatchIndex = 0;
            protocolBatchStartMillis = System.currentTimeMillis();
            protocolBatchStatus = "执行中";
        }
        Logs.refreshLogStatus();
        enqueueNextProtocolTask();
    }

    /** 加入一键执行中的下一个协议任务。 */
    private static void enqueueNextProtocolTask() {
        int index;
        synchronized (Logs.LOCK) {
            index = protocolBatchIndex;
        }
        if (index < 0 || index >= PROTOCOL.length) {
            return;
        }
        enqueueProtocol(PROTOCOL[index], true);
    }

    /** 处理一键执行任务成功后的下一步。 */
    private static void continueProtocolBatch() {
        synchronized (Logs.LOCK) {
            protocolBatchIndex++;
            if (protocolBatchIndex >= PROTOCOL.length) {
                endProtocolBatch("成功");
                return;
            }
        }
        enqueueNextProtocolTask();
    }

    /** 任意步骤失败时终止一键执行。 */
    private static void stopProtocolBatch() {
        synchronized (Logs.LOCK) {
            endProtocolBatch("失败");
        }
        Logs.refreshLogStatus();
    }

    /** 结束一键执行（调用方已持 {@link Logs#LOCK}）。 */
    private static void endProtocolBatch(String status) {
        protocolBatchDurationSec = (System.currentTimeMillis() - protocolBatchStartMillis) / 1000.0;
        protocolBatchStatus = status;
        protocolBatchIndex = -1;
    }

    /**
     * 选择目录后加入 CMD 路径列表并持久化（不加入左侧目录树）。
     */
    private static void onChooseCmdDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        File defaultDir = new File(System.getProperty("user.dir"));
        Object selected = pathComboBox.getSelectedItem();
        if (selected != null) {
            defaultDir = new File(selected.toString());
        }
        chooser.setCurrentDirectory(defaultDir);
        int result = chooser.showOpenDialog(cmdPanel);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File selectedDir = chooser.getSelectedFile();
        if (selectedDir == null) {
            return;
        }
        String absPath = selectedDir.getAbsolutePath();
        PathConfig.ensureCmdPathListed(absPath);
        refillCmdPathCombo(absPath);
    }

    /**
     * 仅「自定义」页可改路径/命令；状态未变则跳过 setEnabled。
     */
    private static void syncCustomInputs() {
        TaskLogView view = Logs.selected(taskLogTabs);
        boolean custom = view != null && CUSTOM_CMD_LOG_KEY.equals(view.logKey);
        if (custom == lastCustomInputsEnabled) {
            return;
        }
        lastCustomInputsEnabled = custom;
        pathComboBox.setEnabled(custom);
        commandComboBox.setEnabled(custom);
        chooseDirButton.setEnabled(custom);
    }

    /**
     * 从输入框读取参数并入队。
     */
    private static void enqueueFromInput() {
        enqueueValidatedCommand(comboText(pathComboBox), comboText(commandComboBox), CUSTOM_CMD_LOG_KEY,
            CUSTOM_CMD_TAB_TITLE, true, false);
    }

    private static String comboText(JComboBox<String> combo) {
        Object item = combo.getEditor().getItem();
        return item == null ? "" : item.toString().trim();
    }

    /** 校验命令并创建任务，可标记为一键执行任务。 */
    private static void enqueueValidatedCommand(String path, String command, String fixedLogKey, String fixedTabTitle,
        boolean updateInputFields, boolean protocolBatchTask) {
        if (path.isEmpty()) {
            alert("请选择执行目录", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (command.isEmpty()) {
            alert("请输入 cmd 命令", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File workDir = new File(path);
        if (!workDir.exists() || !workDir.isDirectory()) {
            alert("目录不存在或不可用: " + path, JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (updateInputFields) {
            PathConfig.ensureCmdPathListed(workDir.getAbsolutePath());
            refillCmdPathCombo(workDir.getAbsolutePath());
            PathConfig.ensureCmdCommandListed(command);
            refillCommandCombo(command);
        }
        String logKey = fixedLogKey == null ? buildLogKey(workDir, command) : fixedLogKey;
        TaskTabLabel tabLabel =
            fixedTabTitle == null ? tabLabel(workDir, command, resolveTaskTabText(workDir, command))
                : Logs.fixedLabel(fixedTabTitle);
        synchronized (Logs.LOCK) {
            CmdTask task = new CmdTask(taskSequence++, workDir, command, logKey, protocolBatchTask);
            pendingTasks.add(task);
            task.logView = Logs.bindLogView(logKey, tabLabel, false);
        }
        Logs.refreshLogStatus();
        scheduleRunnableTasks();
    }

    private static void alert(String message, int type) {
        JOptionPane.showMessageDialog(cmdPanel, message, type == JOptionPane.ERROR_MESSAGE ? "错误" : "提示", type);
    }

    /**
     * 充下拉框并可选中指定路径。
     */
    private static void refillCmdPathCombo(String selectedPath) {
        pathComboBox.removeAllItems();
        for (String p : PathConfig.CMD_PATHS) {
            pathComboBox.addItem(p);
        }
        if (selectedPath != null && !selectedPath.trim().isEmpty()) {
            pathComboBox.setSelectedItem(selectedPath);
        }
    }

    /**
     * 用配置中的 CMD 命令候选填充下拉框。
     *
     * @param selectedCommand
     *            需要选中的命令
     */
    private static void refillCommandCombo(String selectedCommand) {
        commandComboBox.removeAllItems();
        for (String command : PathConfig.CMD_COMMANDS) {
            if (PathConfig.isQuickOnlyCmdCommand(command)) {
                continue;
            }
            commandComboBox.addItem(command);
        }
        if (selectedCommand != null && !selectedCommand.trim().isEmpty()) {
            commandComboBox.setSelectedItem(selectedCommand);
        } else if (commandComboBox.getItemCount() > 0) {
            commandComboBox.setSelectedIndex(0);
        }
    }

    /**
     * 调度可执行任务（并发 6 + 同目录串行）。
     */
    private static void scheduleRunnableTasks() {
        List<CmdTask> startList = new ArrayList<>();
        synchronized (Logs.LOCK) {
            while (runningTasks.size() < MAX_PARALLEL) {
                int index = findRunnableTaskIndex();
                if (index < 0) {
                    break;
                }
                CmdTask task = pendingTasks.remove(index);
                runningTasks.put(task.id, task);
                runningDirSet.add(task.workDirPath);
                startList.add(task);
            }
        }
        Logs.refreshLogStatus();
        for (CmdTask task : startList) {
            startTaskWorker(task);
        }
    }

    /**
     * 查找队列中可启动任务位置。
     */
    private static int findRunnableTaskIndex() {
        for (int i = 0; i < pendingTasks.size(); i++) {
            CmdTask task = pendingTasks.get(i);
            if (!runningDirSet.contains(task.workDirPath)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 启动单任务执行线程。
     */
    private static void startTaskWorker(CmdTask task) {
        BackgroundTasks.start("directory-cmd-worker-" + task.id, () -> {
            return Pipe.runLines(Arrays.asList("cmd", "/c", task.command), task.workDir, null, true, Pipe.cmdCs(),
                line -> appendTaskLog(task, line));
        }, new BackgroundTasks.Listener() {
            @Override
            public void onStarted(long startMillis) {
                task.startMillis = startMillis;
                Logs.markRunning(task.logView, startMillis);
                appendRunHeader(task, startMillis);
            }

            @Override
            public void onSucceeded(int exitCode, long endMillis) {
                finishTaskSuccess(task, exitCode, endMillis);
            }

            @Override
            public void onFailed(Exception error, long endMillis) {
                finishTaskError(task, error, endMillis);
            }

            @Override
            public void onFinished() {
                releaseTaskAndReschedule(task);
                if (!task.protocolBatchTask) {
                    return;
                }
                if (task.errorMessage.isEmpty() && task.exitCode == 0) {
                    continueProtocolBatch();
                } else {
                    stopProtocolBatch();
                }
            }
        });
    }

    /** 进程已结束：只写日志，释放槽位放在 {@code onFinished}。 */
    private static void finishTaskSuccess(CmdTask task, int exitCode, long endMillis) {
        task.exitCode = exitCode;
        task.endMillis = endMillis;
        Logs.finishTaskTiming(task.logView, endMillis, exitCode == 0);
        appendPost(task, "变更摘要失败: ", () -> {
            List<String> lines = DocChangeSummary.toLogLines(task);
            for (int i = 0; i < lines.size(); i++) {
                appendTaskLog(task, lines.get(i));
            }
        });
        appendPost(task, "结束日志失败: ", () -> appendEnd(task, endMillis, true));
        appendPost(task, "子任务统计失败: ", () -> appendProtocolTiming(task, endMillis, exitCode == 0));
    }

    /** 附属日志失败只记一行，不改变任务成败。 */
    private static void appendPost(CmdTask task, String failPrefix, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            appendTaskLog(task, failPrefix + t);
        }
    }

    /** 异常完成：只写日志，释放槽位放在 {@code onFinished}。 */
    private static void finishTaskError(CmdTask task, Exception ex, long endMillis) {
        task.errorMessage = ex.toString();
        task.endMillis = endMillis;
        Logs.finishTaskTiming(task.logView, endMillis, false);
        appendEnd(task, endMillis, false);
    }

    /**
     * 释放运行槽位并再次调度。
     */
    private static void releaseTaskAndReschedule(CmdTask task) {
        synchronized (Logs.LOCK) {
            runningTasks.remove(task.id);
            runningDirSet.remove(task.workDirPath);
        }
        Logs.refreshLogStatus();
        scheduleRunnableTasks();
    }

    /**
     * 排队计数（须在 {@link Logs#LOCK} 内调用）。
     *
     * @return 日志键 → 数量
     */
    static Map<String, Integer> waitingByKey() {
        return countByLogKey(pendingTasks);
    }

    /**
     * 执行中计数（须在 {@link Logs#LOCK} 内调用）。
     *
     * @return 日志键 → 数量
     */
    static Map<String, Integer> runningByKey() {
        return countByLogKey(new ArrayList<>(runningTasks.values()));
    }

    /**
     * 统计每个日志键对应的任务数量。
     *
     * @param tasks
     *            任务列表
     * @return 计数
     */
    private static Map<String, Integer> countByLogKey(List<CmdTask> tasks) {
        Map<String, Integer> countMap = new LinkedHashMap<>();
        for (CmdTask task : tasks) {
            countMap.compute(task.logKey, (k, count) -> count == null ? 1 : count + 1);
        }
        return countMap;
    }

    /** 输出协议子任务耗时。 */
    private static void appendProtocolTiming(CmdTask task, long endMillis, boolean success) {
        if (!PROTOCOL_LOG_KEY.equals(task.logKey) || task.startMillis <= 0) {
            return;
        }
        double durationSec = (endMillis - task.startMillis) / 1000.0;
        Timing timing;
        synchronized (Logs.LOCK) {
            timing = protocolTimingMap.get(task.command);
            if (timing == null) {
                timing = new Timing();
                protocolTimingMap.put(task.command, timing);
            }
        }
        timing.runCount++;
        if (success) {
            timing.lastSuccessSec = durationSec;
            timing.lastSuccessMillis = endMillis;
        }
        appendTaskLog(task,
            "子任务统计：" + task.command + " | " + (success ? "成功" : "失败") + " | 本次 "
                + Logs.formatDurationSec(durationSec) + " | 上次成功 "
                + Logs.formatDurationSec(timing.lastSuccessSec) + " | 最后成功 "
                + (timing.lastSuccessMillis <= 0 ? "无" : Logs.formatTime(timing.lastSuccessMillis)) + " | 第 "
                + timing.runCount + " 次");
    }

    /**
     * 构造协议日志的总任务状态栏。
     *
     * @param waiting
     *            排队数
     * @param running
     *            执行中数
     * @param timingPart
     *            耗时片段
     * @param runCount
     *            累计次数
     * @return 状态栏文本
     */
    static String protocolBar(int waiting, int running, String timingPart, int runCount) {
        String status;
        double totalSec;
        synchronized (Logs.LOCK) {
            status = protocolBatchStatus;
            totalSec = protocolBatchIndex >= 0 ? (System.currentTimeMillis() - protocolBatchStartMillis) / 1000.0
                : protocolBatchDurationSec;
        }
        String taskStatus = running > 0 ? "子任务执行中" : waiting > 0 ? "子任务等待中" : "子任务空闲";
        return String.format(Locale.ROOT, "总任务：%s，%s，总耗时 %s；%s，%s，累计执行 %d 次", status, taskStatus,
            Logs.formatDurationSec(totalSec), timingPart.isEmpty() ? "" : timingPart, "当前/上次成功耗时见日志", runCount);
    }

    /**
     * 追加单行任务日志。
     */
    private static void appendTaskLog(CmdTask task, String line) {
        task.appendOutput(line + "\n");
        SwingUtilities.invokeLater(() -> {
            if (task.logView != null) {
                Logs.appendLine(task.logView.logArea, line);
            }
        });
    }

    /**
     * 输出任务开始头。
     */
    private static void appendRunHeader(CmdTask task, long startMillis) {
        // 同目录同命令复用日志框，runCount 用于标识第几次执行。
        task.logView.runCount++;
        appendTaskLog(task, "");
        appendTaskLog(task,
            "# " + task.id + " " + task.logView.tabLabel.logTitle + "（第" + task.logView.runCount + "次）");
        appendTaskLog(task,
            isJarLaunchCommand(task.command) ? "时间: " + Logs.formatTime(startMillis)
                : "开始: " + Logs.formatTime(startMillis));
        appendTaskLog(task, "目录: " + task.workDirPath);
        appendTaskLog(task, "命令: " + task.command);
    }

    private static void appendEnd(CmdTask task, long endMillis, boolean processFinished) {
        if (!isJarLaunchCommand(task.command)) {
            appendTaskLog(task, "结束: " + Logs.formatTime(endMillis));
        }
        appendTaskLog(task, buildResultLine(task, processFinished, endMillis));
        appendTaskLog(task, "----------------------------------------");
    }

    /**
     * 构造任务结果行；java -jar 启动型任务不展示耗时。
     *
     * @param task
     *            任务
     * @param processFinished
     *            是否进入进程正常结束分支
     * @param endMillis
     *            结束时间
     * @return 结果行
     */
    private static String buildResultLine(CmdTask task, boolean processFinished, long endMillis) {
        boolean jar = isJarLaunchCommand(task.command);
        if (!processFinished) {
            return "结果: 失败，" + task.errorMessage + (jar ? "" : "，耗时 " + (endMillis - task.startMillis) + "ms");
        }
        String line = "结果: " + (task.exitCode == 0 ? "成功" : "失败") + "，退出码 " + task.exitCode;
        return jar ? line : line + "，耗时 " + (endMillis - task.startMillis) + "ms";
    }

    /** jar 会拉起长期驻留 GUI，不参与耗时展示。 */
    public static boolean isJarLaunchCommand(String command) {
        return normalizeCommand(command).toLowerCase(Locale.ROOT).startsWith("java -jar");
    }

    /**
     * 构造日志键：同目录同命令时用于复用日志框。
     */
    private static String buildLogKey(File workDir, String command) {
        String path = normalizePath(workDir);
        String cmd = normalizeCommand(command);
        return path + "||" + cmd;
    }

    /**
     * 规范化路径文本，避免路径大小写影响复用判断。
     */
    private static String normalizePath(File workDir) {
        return workDir.getAbsolutePath().trim().toLowerCase();
    }

    /**
     * 规范化命令文本：去首尾空白并合并多空格。
     */
    public static String normalizeCommand(String command) {
        if (command == null) {
            return "";
        }
        return command.trim().replaceAll("\\s+", " ");
    }

    private static TaskTabLabel tabLabel(File workDir, String command, String title) {
        return new TaskTabLabel(title, "", workDir.getAbsolutePath() + " | " + command, title);
    }

    /** 已知快捷用表里的短名，其余走命令简写。 */
    private static String resolveTaskTabText(File workDir, String command) {
        CmdShortcut item = findShortcut(buildLogKey(workDir, command));
        return item != null ? item.shortName : commandBrief(command);
    }

    private static CmdShortcut findShortcut(String logKey) {
        for (CmdShortcut item : QUICK) {
            if (item.logKey.equals(logKey)) {
                return item;
            }
        }
        for (CmdShortcut item : PROTOCOL) {
            if (item.logKey.equals(logKey)) {
                return item;
            }
        }
        return null;
    }

    /** 表里尚未出现的命令才写入，避免盖掉同命令的通用简称。 */
    private static void putBriefFrom(CmdShortcut[] items) {
        for (CmdShortcut item : items) {
            if (!CMD_BRIEF.containsKey(item.command)) {
                CMD_BRIEF.put(item.command, item.shortName);
            }
        }
    }

    /**
     * 解析命令简写：优先使用预设别名，未知命令截断显示。
     *
     * @param command
     *            执行命令
     * @return 命令简写
     */
    private static String commandBrief(String command) {
        String hit = CMD_BRIEF.get(command);
        if (hit != null) {
            return hit;
        }
        if (command.length() <= COMMAND_BRIEF_MAX_LEN) {
            return command;
        }
        return command.substring(0, COMMAND_BRIEF_MAX_LEN) + "...";
    }

}

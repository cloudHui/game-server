package com.gamer.data.file.task;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.MouseEvent;
import java.io.File;
import java.time.LocalDate;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import com.gamer.data.file.client.download.ClientDownloader;
import com.gamer.data.file.client.download.Log;
import com.gamer.data.file.cmd.Logs;
import com.gamer.data.file.cmd.TaskLogView;
import com.gamer.data.file.config.SshConfig;
import com.gamer.data.ui.ViewUi;

/**
 * 任务页输入：SSH / 客户端下载。进程内有效，密码不落盘。
 */
public final class TaskFields {

    /** 客户端下载五个输入框。 */
    private static JTextField[] clientFields;
    /** SSH 地址。 */
    private static JTextField sshHostField;
    /** SSH 端口。 */
    private static JTextField sshPortField;
    /** SSH 密码。 */
    private static JPasswordField sshPassField;
    /** SSH 私钥路径。 */
    private static JTextField sshKeyField;
    /** game 默认；all 覆盖三个服务。 */
    private static JComboBox<String> sshServerCombo;
    /** 结果打成 tar.gz。 */
    private static JCheckBox sshCompressCheck;
    private static JTextField logFromField; // 可空，默认昨天
    private static JTextField logToField; // 可空，默认现在
    private static JTextField logConditionField; // 可空，空则导出时间范围全部内容

    private TaskFields() {}

    /** 一次读取的日志导出参数。 */
    public static final class LogPull {
        /** game 或 all。 */
        public final String server;
        /** 是否 --compress。 */
        public final boolean compress;
        public final String from; // yyyy-MM-dd 或空
        public final String to; // yyyy-MM-dd 或空
        public final String condition; // 固定文本或空

        public LogPull(String server, boolean compress, String from, String to, String condition) {
            this.server = server;
            this.compress = compress;
            this.from = day(from);
            this.to = day(to);
            this.condition = condition;
            if (!this.from.isEmpty() && !this.to.isEmpty() && this.from.compareTo(this.to) > 0) {
                throw new IllegalArgumentException("起始日期不能晚于结束日期");
            }
        }

        private static String day(String text) {
            if (text.isEmpty()) {
                return text;
            }
            if (!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                throw new IllegalArgumentException("日期请填 yyyy-MM-dd，留空使用默认范围");
            }
            return LocalDate.parse(text).toString();
        }
    }

    /**
     * 测服上行：下载、服务、压缩、条件与日期。
     *
     * @param download
     *            下载
     * @param pause
     *            暂停解压
     * @param halt
     *            结束解压
     * @return 两行参数
     */
    public static JPanel sshPullRow(JButton download, JButton pause, JButton halt) {
        ensureInit();
        return ViewUi.stack(
            ViewUi.line(download, pause, halt, ViewUi.label("服务"), sshServerCombo, sshCompressCheck,
                ViewUi.label("条件"), ViewUi.fillX(logConditionField)),
            ViewUi.line(ViewUi.label("从"), logFromField, ViewUi.label("至"), logToField));
    }

    /**
     * 测服 IP / 端口。
     *
     * @return 一行
     */
    public static JPanel sshHostRow() {
        ensureInit();
        return ViewUi.line(ViewUi.label("IP"), sshHostField, ViewUi.label("端口"), sshPortField);
    }

    /**
     * 测服下行：密码 + 密钥路径 + 选文件。
     *
     * @return 一行
     */
    public static JPanel sshKeyRow() {
        ensureInit();
        return ViewUi.line(ViewUi.label("密码"), sshPassField, ViewUi.label("密钥"), ViewUi.fillX(sshKeyField),
            ViewUi.click("选择", TaskFields::browseKey));
    }

    /**
     * 客户端下载参数分两行，避免日期和计数框被端口卡片挤窄。
     *
     * @return 两行参数
     */
    public static JPanel clientRow() {
        ensureInit();
        JTextField[] f = clientFields;
        return ViewUi.stack(
            ViewUi.line(ViewUi.label("日期"), f[0], ViewUi.label("构建号"), f[1], ViewUi.label("序号"), f[2]),
            ViewUi.line(ViewUi.label("+构建"), f[3], ViewUi.label("+序号"), f[4]));
    }

    /**
     * 读 SSH 框。空项回落到 {@link SshConfig}；端口非法则警告并用默认。不含密码写日志。
     *
     * @param logView
     *            页签
     * @return 连接
     */
    public static SshConfig.Conn resolveSshConn(TaskLogView logView) {
        ensureInit();
        final String[] texts = {"", "", "", ""};
        ViewUi.edtWait(() -> {
            texts[0] = sshHostField.getText().trim();
            texts[1] = sshPortField.getText().trim();
            texts[2] = new String(sshPassField.getPassword());
            texts[3] = sshKeyField.getText().trim();
        });
        SshConfig.Conn def = SshConfig.Conn.defaults();
        String host = texts[0].isEmpty() ? def.host : texts[0];
        int port = def.port;
        if (!texts[1].isEmpty()) {
            try {
                port = Integer.parseInt(texts[1]);
                if (port <= 0 || port > 65535) {
                    throw new NumberFormatException("range");
                }
            } catch (NumberFormatException e) {
                if (logView != null) {
                    Logs.appendLog(logView, "[警告] 端口非法，已用 " + def.port);
                }
                port = def.port;
            }
        }
        String pass = texts[2].isEmpty() ? def.pass : texts[2];
        String key = texts[3].isEmpty() ? def.key : texts[3];
        return new SshConfig.Conn(host, port, def.user, pass, key);
    }

    /** @return 当前手动日志导出参数 */
    public static LogPull resolveLogPull() {
        return resolveLogPull(true);
    }

    /**
     * 读取定时日志导出参数；服务、压缩和条件沿用界面，日期交给远端脚本按执行当天计算。
     *
     * @return 定时日志导出参数
     */
    public static LogPull resolveScheduledLogPull() {
        return resolveLogPull(false);
    }

    /**
     * 读取日志导出参数，并控制是否采用界面日期。
     *
     * @param useDateFields
     *            是否读取手动日期输入框
     * @return 日志导出参数
     */
    private static LogPull resolveLogPull(final boolean useDateFields) {
        ensureInit();
        String[] texts = new String[4];
        boolean[] compress = new boolean[1];
        ViewUi.edtWait(new Runnable() {
            @Override
            public void run() {
                texts[0] = String.valueOf(sshServerCombo.getSelectedItem());
                // 定时任务保留日期框原值给手动下载，避免自动任务覆盖用户输入。
                texts[1] = useDateFields ? logFromField.getText().trim() : "";
                texts[2] = useDateFields ? logToField.getText().trim() : "";
                texts[3] = logConditionField.getText().trim();
                compress[0] = sshCompressCheck.isSelected();
            }
        });
        return new LogPull(texts[0], compress[0], texts[1], texts[2], texts[3]);
    }

    /**
     * 按当前输入框解析客户端下载参数，非法项回退默认值并一次写回输入框。
     *
     * @param logView
     *            下载日志页签
     * @return 解析后的下载参数
     */
    public static ClientDownloader.Params resolveClientDownloadParams(TaskLogView logView) {
        ensureInit();
        final String[] texts = new String[clientFields.length];
        ViewUi.edtWait(() -> {
            for (int i = 0; i < clientFields.length; i++) {
                texts[i] = clientFields[i].getText();
            }
        });
        ClientDownloader.ParseResult parsed = ClientDownloader.parseFields(texts);
        if (!parsed.warnings.isEmpty()) {
            ViewUi.edtWait(() -> applyClientTexts(parsed.fieldTexts));
            for (String warning : parsed.warnings) {
                Log.append(logView, warning);
            }
        }
        return parsed.params;
    }

    private static void ensureInit() {
        if (clientFields != null) {
            return;
        }
        clientFields = new JTextField[] {field(10, null), field(4, null), field(4, null), field(4, null), field(4, null)};
        sshHostField = field(12, "空则用默认测服地址");
        sshPortField = field(4, "空则用 22");
        sshPassField = new JPasswordField(8);
        ViewUi.compactField(sshPassField);
        sshPassField.setPreferredSize(new Dimension(96, 26));
        sshPassField.setMaximumSize(sshPassField.getPreferredSize());
        sshPassField.setToolTipText("密钥非空时忽略密码");
        sshKeyField = field(12, "私钥文件；非空走密钥，空则密码");
        sshServerCombo = new JComboBox<>(new String[] {"game", "all"});
        sshServerCombo.setFont(ViewUi.FONT);
        sshServerCombo.setPreferredSize(new Dimension(68, 26));
        sshServerCombo.setToolTipText("game：仅游戏服；all：game/world/common，目录存在才查");
        sshCompressCheck = ViewUi.check("压缩", true);
        sshCompressCheck.setToolTipText("--compress；解到 Desktop/log 下三服目录，成功后删压缩包");
        logFromField = dateField(true);
        logToField = dateField(false);
        logConditionField = field(10, "--condition 固定文本；空则保留时间范围全部日志行");
        applyClientTexts(ClientDownloader.getDefaults().toFieldTexts());
        SshConfig.Conn def = SshConfig.Conn.defaults();
        sshHostField.setText(def.host);
        sshPortField.setText(String.valueOf(def.port));
        sshPassField.setText(def.pass);
        sshKeyField.setText(def.key);
    }

    /**
     * 选私钥文件，路径写回输入框；仍可手改。
     */
    private static void browseKey() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        String current = sshKeyField.getText().trim();
        File start = current.isEmpty() ? null : new File(current);
        if (start != null && start.isFile()) {
            chooser.setSelectedFile(start);
        } else if (start != null && start.getParentFile() != null && start.getParentFile().isDirectory()) {
            chooser.setCurrentDirectory(start.getParentFile());
        } else {
            File sshDir = new File(System.getProperty("user.home"), ".ssh");
            chooser.setCurrentDirectory(sshDir.isDirectory() ? sshDir : new File(System.getProperty("user.home")));
        }
        if (chooser.showOpenDialog(sshKeyField) == JFileChooser.APPROVE_OPTION) {
            sshKeyField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private static void applyClientTexts(String[] texts) {
        for (int i = 0; i < clientFields.length; i++) {
            clientFields[i].setText(texts[i]);
        }
    }

    /**
     * 日期框：空时淡色显示昨天或今天，清空后仍显示，不写入文本。
     *
     * @param from
     *            true 为起始（昨天），false 为截止（今天）
     * @return 输入框
     */
    private static JTextField dateField(final boolean from) {
        String tip = from ? "yyyy-MM-dd，空则昨天" : "yyyy-MM-dd，空则今天（含当天）";
        JTextField f = new JTextField(10) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (!getText().isEmpty()) {
                    return;
                }
                Graphics g2 = g.create();
                try {
                    Insets in = getInsets();
                    g2.setColor(ViewUi.HINT);
                    g2.setFont(getFont());
                    String prompt = from ? LocalDate.now().minusDays(1).toString() : LocalDate.now().toString();
                    int y = (getHeight() + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2;
                    g2.drawString(prompt, in.left, y);
                } finally {
                    g2.dispose();
                }
            }

            @Override
            public String getToolTipText(MouseEvent event) {
                return getText().isEmpty() ? super.getToolTipText(event) : getText();
            }
        };
        ViewUi.compactField(f);
        Dimension size = new Dimension(Math.max(40, f.getFontMetrics(f.getFont()).charWidth('0') * 10 + 12), 26);
        f.setPreferredSize(size);
        f.setMinimumSize(size);
        f.setMaximumSize(size);
        f.setToolTipText(tip);
        return f;
    }

    /**
     * 统一高度的输入框。
     *
     * @param cols
     *            列宽
     * @param tip
     *            提示，可空
     * @return 输入框
     */
    private static JTextField field(int cols, String tip) {
        JTextField f = new JTextField(cols) {
            @Override
            public String getToolTipText(MouseEvent event) {
                return getText().isEmpty() ? super.getToolTipText(event) : getText();
            }
        };
        ViewUi.compactField(f);
        // 数字/日期按数字字宽分配，避免默认 m 字宽过宽挤掉相邻框；长文本仍可滚动和悬停查看。
        Dimension size = new Dimension(Math.max(40, f.getFontMetrics(f.getFont()).charWidth('0') * cols + 12), 26);
        f.setPreferredSize(size);
        f.setMinimumSize(size);
        f.setMaximumSize(size);
        f.setToolTipText(tip == null ? "" : tip);
        return f;
    }
}

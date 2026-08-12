package manager.ui;

import manager.config.RuntimeSettings;
import manager.model.*;
import manager.service.LogReader;
import manager.service.ManagerLog;
import manager.service.ServiceManager;
import manager.task.CommandRunner;
import manager.task.PackageCommands;
import manager.util.NetworkAddresses;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

public final class ManagerFrame extends JFrame {
    private final ServiceManager services;
    private final RuntimeSettings settings;
    private final ManagerLog managerLog;
    private final CommandRunner commands;
    private final PackageCommands packageCommands;
    private final LogReader logs = new LogReader();
    private final AsyncActions async = new AsyncActions();
    private final Map<String, ServiceRow> rows = new LinkedHashMap<>();
    private final List<JButton> operationButtons = new ArrayList<>();
    private final JTextArea output = new JTextArea();

    public ManagerFrame(Path root) {
        super("Family Server Manager");
        settings = new RuntimeSettings(root);
        services = new ServiceManager(root, settings);
        managerLog = new ManagerLog(root);
        managerLog.write("[启动] ServerManager，JDK=" + settings.jdkHome());
        commands = new CommandRunner(root);
        packageCommands = new PackageCommands(root, settings);
        configureWindow(root);
        Runtime.getRuntime().addShutdownHook(new Thread(services::stopAllOwned, "manager-shutdown"));
    }

    public void open() { setSize(1080, 700); setVisible(true); refresh(); }

    private void configureWindow(Path root) {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(960, 620));
        setLocationByPlatform(true);
        add(createToolbar(), BorderLayout.NORTH);
        add(createContent(), BorderLayout.CENTER);
        add(new JLabel(" 工程目录: " + root + "   Java: " + System.getProperty("java.version")), BorderLayout.SOUTH);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent event) { closeManager(); }
        });
    }

    private JComponent createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.add(operationButton("启动全部", services::startAll));
        toolbar.add(operationButton("停止全部", services::stopAllOwned));
        toolbar.add(operationButton("仅启动 Web", () -> services.start(ServiceCatalog.web())));
        toolbar.add(plainButton("刷新状态", this::refresh));
        toolbar.add(operationButton("打包业务+管理器", () -> commands.run(packageCommands.buildAll(), this::append)));
        toolbar.add(operationButton("生成应用镜像", () -> commands.run(packageCommands.applicationImage(), this::append)));
        toolbar.add(plainButton("管理器日志", this::showManagerLog));
        toolbar.add(plainButton("JDK 设置", this::editJdk));
        toolbar.add(plainButton("复制局域网地址", this::copyAddress));
        return toolbar;
    }

    private JComponent createContent() {
        JPanel serviceList = new JPanel(new GridLayout(0, 1, 0, 6));
        serviceList.setBorder(new EmptyBorder(8, 10, 8, 10));
        for (ServiceSpec spec : ServiceCatalog.ALL) {
            ServiceRow row = new ServiceRow(spec, this::handleServiceAction);
            rows.put(spec.id(), row);
            serviceList.add(row);
        }
        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(serviceList), new JScrollPane(output));
        split.setResizeWeight(.55);
        return split;
    }

    private void handleServiceAction(ServiceRow.Action action, ServiceSpec spec) {
        switch (action) {
            case START -> execute("启动 " + spec.name(), () -> services.start(spec));
            case STOP -> execute("停止 " + spec.name(), () -> services.stop(spec));
            case RESTART -> execute("重启 " + spec.name(), () -> services.restart(spec));
            case LOG -> execute("读取 " + spec.name() + " 日志", () -> showLog(spec));
        }
    }

    private JButton operationButton(String text, AsyncActions.Task task) {
        JButton button = plainButton(text, () -> execute(text, task));
        operationButtons.add(button);
        return button;
    }

    private JButton plainButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(event -> action.run());
        return button;
    }

    private void execute(String name, AsyncActions.Task task) {
        setBusy(true);
        append("[执行] " + name);
        async.run(task, () -> {
            append("[完成] " + name);
            setBusy(false);
            refresh();
        }, error -> {
            append("[失败] " + error.getMessage());
            setBusy(false);
            refresh();
            JOptionPane.showMessageDialog(this, error.getMessage(), name, JOptionPane.ERROR_MESSAGE);
        });
    }

    private void refresh() {
        for (ServiceSpec spec : ServiceCatalog.ALL) rows.get(spec.id()).update(services.state(spec));
    }

    private void showLog(ServiceSpec spec) throws Exception {
        showLogFile(services.logFile(spec));
    }

    private void showManagerLog() {
        try { showLogFile(managerLog.file()); }
        catch (Exception error) { JOptionPane.showMessageDialog(this, error.getMessage(), "管理器日志", JOptionPane.ERROR_MESSAGE); }
    }

    private void showLogFile(Path file) throws Exception {
        String text = "日志文件: " + file + System.lineSeparator() + System.lineSeparator() + logs.tail(file, 256 * 1024);
        SwingUtilities.invokeLater(() -> { output.setText(text); output.setCaretPosition(output.getDocument().getLength()); });
    }

    private void editJdk() {
        JTextField field = new JTextField(settings.jdkHome().toString(), 42);
        JButton choose = new JButton("选择目录");
        choose.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser(field.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) field.setText(chooser.getSelectedFile().getAbsolutePath());
        });
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(new JLabel("完整 JDK 17 目录（需要 bin/java 和 bin/jpackage）"), BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        panel.add(choose, BorderLayout.EAST);
        if (JOptionPane.showConfirmDialog(this, panel, "JDK 设置", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            settings.setJdkHome(Path.of(field.getText().trim()));
            append("JDK 目录已保存: " + settings.jdkHome());
        } catch (Exception error) { JOptionPane.showMessageDialog(this, error.getMessage(), "JDK 设置", JOptionPane.ERROR_MESSAGE); }
    }

    private void copyAddress() {
        try {
            String address = "http://" + NetworkAddresses.localIpv4() + ":8081/";
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(address), null);
            append("已复制: " + address);
        } catch (Exception error) {
            JOptionPane.showMessageDialog(this, error.getMessage(), "局域网地址", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void append(String line) {
        managerLog.write(line);
        SwingUtilities.invokeLater(() -> {
            output.append(line + System.lineSeparator());
            output.setCaretPosition(output.getDocument().getLength());
        });
    }

    private void setBusy(boolean busy) { operationButtons.forEach(button -> button.setEnabled(!busy)); }
    private void closeManager() {
        setEnabled(false);
        managerLog.write("[退出] 开始停止本管理器持有的临时服务");
        services.stopAllOwned();
        managerLog.write("[退出] 清理完成");
        dispose();
        System.exit(0);
    }
}

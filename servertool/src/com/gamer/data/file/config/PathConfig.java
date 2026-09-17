package com.gamer.data.file.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * WindowsTools 与 MCP 共用的本机路径。
 * <p>
 * 永久改：改下方路径常量 / DEFAULT_*；运行时 {@link #ensureCmdPathListed} 等只进内存列表，重启回默认。
 */
public final class PathConfig {

    /** 工作区根目录。 */
    public static final String WORKSPACE_DIR = "D:\\code\\WorkSpace";

    /** Server 工程根。 */
    public static final String SERVER_DIR = WORKSPACE_DIR + "\\Server";

    /** 设计文档与配置表根。 */
    public static final String DOCUMENT_DIR = WORKSPACE_DIR + "\\Document";

    /** 公共工具 Bin。 */
    public static final String BIN_DIR = WORKSPACE_DIR + "\\Common\\Tools\\Bin";

    /** 公共工具 server 产物目录。 */
    public static final String BIN_SERVER_DIR = BIN_DIR + "\\server";

    /** servertool 工程目录。 */
    public static final String SERVERTOOL_DIR = SERVER_DIR + "\\servertool";

    /** common 工程目录。 */
    public static final String COMMON_DIR = SERVER_DIR + "\\common";

    /** common-proto 工程目录。 */
    public static final String COMMON_PROTO_DIR = SERVER_DIR + "\\common-proto";

    /** MCP 按日日志：相对 Server 的 {@code .mcp/log}。 */
    public static File mcpLogDir(File serverDir) {
        return new File(serverDir, ".mcp" + File.separator + "log");
    }

    /** 配置 Excel 目录（客户端 GD 落地写死此路径）。 */
    public static final String EXCEL_DIR =
        DOCUMENT_DIR + "\\DevelopmentGD&ConfigurationExcel\\ConfigurationExcel";

    /** 服务器本地 GD 目录（gameserver data.path）。 */
    public static final String GD_DATA_DIR =
        DOCUMENT_DIR + "\\DevelopmentGD&ConfigurationExcel\\GD\\data";

    /** 客户端 StreamingAssets 中的 gddata.zip。 */
    public static final String CLIENT_GD_ZIP =
        "D:\\download\\fs\\KingdomWarships\\KingdomWarships_Data\\StreamingAssets\\gddata.zip";

    /** 列长 limit 目录（与 CodeTool/StrategyTool 的 Common/Tools/Bin/server/limit 一致）。 */
    public static final String LIMIT_DIR = BIN_SERVER_DIR + "\\limit";

    /**
     * 启动时挂到左侧目录树根下的默认目录（仅存在且为目录时才挂载）。
     * 运行时可再往 {@link #INITIAL_PATHS} 追加，重启不保留。
     */
    private static final String[] DEFAULT_INITIAL_PATHS = {
        "C:\\Users\\liuyunhui\\Desktop\\work\\tool",
        "D:\\BaiduNetdiskDownload",
        BIN_DIR,
        BIN_DIR + "\\map\\level",
        BIN_DIR + "\\opcode",
        BIN_DIR + "\\proto",
        DOCUMENT_DIR,
        EXCEL_DIR,
        GD_DATA_DIR,
        "D:\\download",
        "D:\\download\\fs\\KingdomWarships",
    };

    /**
     * 「目录执行 cmd」工作目录下拉候选（与左侧目录树列表独立，互不混用）。
     * 运行时 {@link #ensureCmdPathListed} 可追加，重启不保留。
     */
    private static final String[] DEFAULT_CMD_PATHS = {
        SERVERTOOL_DIR,
        BIN_DIR,
        BIN_SERVER_DIR,
        DOCUMENT_DIR,
        COMMON_DIR,
        COMMON_PROTO_DIR,
    };

    /**
     * 「目录执行 cmd」命令下拉候选（手动执行过的新命令会内存追加到 {@link #CMD_COMMANDS}）。
     * 快捷按钮专用命令见 {@link #DEFAULT_QUICK_ONLY_CMD_COMMANDS}，不下拉展示。
     */
    private static final String[] DEFAULT_CMD_COMMANDS = {
        "ant p-WindowsTools",
        "ant p-CodeTool",
        "ant p-McpServer",
        "ant p-NetOpcode",
        "ant p-StrategyTool",
        "svn update",
        "svn update -r",
        "svn log -l 10 -v",
        "svn cleanup",
    };

    /**
     * 仅快捷按钮使用、不出现在 CMD 命令下拉的命令（与 {@link #isQuickOnlyCmdCommand} 配套）。
     */
    private static final String[] DEFAULT_QUICK_ONLY_CMD_COMMANDS = {
        "ant",
        "call protoc_java.bat",
        "call opcode_java.bat",
        "java -jar CodeTool.jar",
        "call McpServer.bat start",
        "call McpServer.bat restart",
    };

    /**
     * 左侧目录树根节点路径（启动自 DEFAULT_INITIAL_PATHS 填充；运行时可追加）。
     */
    public static final List<String> INITIAL_PATHS = new ArrayList<>();

    /**
     * 「目录执行 cmd」下拉路径（启动自 DEFAULT_CMD_PATHS 填充）。
     */
    public static final List<String> CMD_PATHS = new ArrayList<>();

    /**
     * 「目录执行 cmd」命令下拉候选（启动自 DEFAULT_CMD_COMMANDS 填充）。
     */
    public static final List<String> CMD_COMMANDS = new ArrayList<>();

    /**
     * 仅快捷按钮使用的命令（启动自 DEFAULT_QUICK_ONLY_CMD_COMMANDS 填充）。
     */
    public static final List<String> QUICK_ONLY_CMD_COMMANDS = new ArrayList<>();

    /** 路径排序：按 Windows 路径段忽略大小写比较。 */
    private static final Comparator<String> PATH_COMPARATOR = (path1, path2) -> {
        String[] parts1 = path1.split("\\\\");
        String[] parts2 = path2.split("\\\\");
        int minLength = Math.min(parts1.length, parts2.length);
        for (int i = 0; i < minLength; i++) {
            int cmp = parts1[i].compareToIgnoreCase(parts2[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(parts1.length, parts2.length);
    };

    static {
        loadDefaults();
    }

    private PathConfig() {}

    /**
     * 用默认常量填充四个运行时列表（清空后重填）。
     */
    public static void loadDefaults() {
        INITIAL_PATHS.clear();
        INITIAL_PATHS.addAll(Arrays.asList(DEFAULT_INITIAL_PATHS));

        CMD_PATHS.clear();
        CMD_PATHS.addAll(Arrays.asList(DEFAULT_CMD_PATHS));

        QUICK_ONLY_CMD_COMMANDS.clear();
        for (String defaultQuickOnlyCmdCommand : DEFAULT_QUICK_ONLY_CMD_COMMANDS) {
            String normalized = defaultQuickOnlyCmdCommand.trim().replaceAll("\\s+", " ");
            if (!QUICK_ONLY_CMD_COMMANDS.contains(normalized)) {
                QUICK_ONLY_CMD_COMMANDS.add(normalized);
            }
        }

        CMD_COMMANDS.clear();
        for (String cmd : DEFAULT_CMD_COMMANDS) {
            if (!isQuickOnlyCmdCommand(cmd) && !CMD_COMMANDS.contains(cmd)) {
                CMD_COMMANDS.add(cmd);
            }
        }
    }

    /**
     * 是否为仅快捷按钮使用的命令（CMD 下拉框不展示）。
     *
     * @param command
     *            命令原文
     * @return true 表示快捷专用
     */
    public static boolean isQuickOnlyCmdCommand(String command) {
        if (command == null) {
            return false;
        }
        String normalized = command.trim().replaceAll("\\s+", " ");
        return QUICK_ONLY_CMD_COMMANDS.contains(normalized);
    }

    /**
     * 将路径加入 CMD 下拉列表（仅内存，不落盘）。
     *
     * @param path
     *            工作目录
     */
    public static void ensureCmdPathListed(String path) {
        if (path == null || path.trim().isEmpty()) {
            return;
        }
        String absPath = new File(path).getAbsolutePath();
        if (!CMD_PATHS.contains(absPath)) {
            CMD_PATHS.add(absPath);
            CMD_PATHS.sort(PATH_COMPARATOR);
        }
    }

    /**
     * 将命令加入 CMD 命令下拉列表（仅内存；快捷专用命令忽略）。
     *
     * @param command
     *            CMD 命令
     */
    public static void ensureCmdCommandListed(String command) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }
        String normalized = command.trim().replaceAll("\\s+", " ");
        if (isQuickOnlyCmdCommand(normalized)) {
            return;
        }
        if (!CMD_COMMANDS.contains(normalized)) {
            CMD_COMMANDS.add(normalized);
        }
    }

    /**
     * @return 固定配置 Excel 目录
     */
    public static File excelDir() {
        return new File(EXCEL_DIR);
    }

    /**
     * @return 服务器本地 GD 目录
     */
    public static File gdDataDir() {
        return new File(GD_DATA_DIR);
    }

    /**
     * @return 客户端 gddata.zip
     */
    public static File clientGdZip() {
        return new File(CLIENT_GD_ZIP);
    }

    /**
     * @return gddata.zip 所在目录（StreamingAssets），zip 不存在时仍返回父路径
     */
    public static File clientGdZipDir() {
        return clientGdZip().getParentFile();
    }

    /**
     * @return 列长 limit 目录（不依赖 WindowsTools 的 user.dir）
     */
    public static File limitDir() {
        return new File(LIMIT_DIR);
    }

    /**
     * 客户端运行缓存目录。用 user.home 拼路径，不写死本机用户名。
     *
     * @return LocalLow 下 KingdomWarships/data
     */
    public static File clientLocalLowDataDir() {
        return new File(System.getProperty("user.home"),
            "AppData" + File.separator + "LocalLow" + File.separator + "Fast" + File.separator + "KingdomWarships"
                + File.separator + "data");
    }
}
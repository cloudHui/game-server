package com.gamer.data.mpcserver;

import java.io.File;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamer.data.file.config.DbConfig;
import com.gamer.data.file.config.PathConfig;
import com.gamer.data.file.config.RedisConfig;
import com.gamer.data.file.db.ConnectionConfig;
import com.gamer.data.log.Log;
import com.gamer.data.mpcserver.core.CommandContext;
import com.gamer.data.mpcserver.core.DbDefaults;
import com.gamer.data.mpcserver.core.FileSandbox;
import com.gamer.data.mpcserver.core.GitDefaults;
import com.gamer.data.mpcserver.core.RedisDefaults;
import com.gamer.data.mpcserver.core.PlayerDatabases;

/**
 * MCP 运行参数。路径从运行位置定位 WorkSpace，不写盘符。
 */
final class McpConfig {
    static final int PORT = 18765;
    static final int TOOL_TIMEOUT_SECONDS = 120;
    static final int EXCEL_TIMEOUT_SECONDS = 30;

    final File workDir;
    final File logDir;
    final File serverDir;
    McpConfig() throws Exception {
        workDir = locateWorkspace();
        File possibleServer = new File(workDir, "Server");
        serverDir = (possibleServer.isDirectory()) ? possibleServer.getCanonicalFile() : workDir;
        logDir = PathConfig.mcpLogDir(serverDir).getCanonicalFile();
    }

    CommandContext context(ObjectMapper mapper, Log log) throws Exception {
        Map<String, DbDefaults> databases = new HashMap<>();
        databases.put("local", database(DbConfig.ALIAS_LOCAL_GAME));
        databases.put("test", database(DbConfig.ALIAS_TEST_GAME));
        List<File> dirs = new ArrayList<>();
        dirs.add(workDir);
        File dataDir = new File(workDir, "data");
        if (dataDir.isDirectory()) {
            dirs.add(dataDir);
        }
        dirs.add(new File(System.getProperty("user.home"), "Desktop/log"));
        File dataLog = dataLogDir(workDir);
        if (dataLog != null) {
            dirs.add(dataLog);
        }
        File config = new File(System.getProperty("mcp.db.config",
            new File(codeLocation(), "McpDatabaseConfig.json").getPath()));
        return new CommandContext(mapper, log, databases, new FileSandbox(workDir, dirs),
            new RedisDefaults(RedisConfig.HOST, RedisConfig.PORT, RedisConfig.USER, RedisConfig.PASSWORD),
            new GitDefaults(serverDir), new PlayerDatabases(config, mapper, log));
    }

    private static DbDefaults database(String alias) {
        ConnectionConfig conn = DbConfig.findConnection(alias);
        if (conn == null) {
            throw new IllegalStateException("DbConfig 缺少连接: " + alias);
        }
        return new DbDefaults(conn.getHost(), conn.getPort(), conn.getUser(), conn.getPassword(),
            conn.getDatabase());
    }

    private static File locateWorkspace() throws Exception {
        String override = System.getProperty("mcp.workspace");
        if (override != null && !override.trim().isEmpty()) {
            File dir = new File(override.trim()).getCanonicalFile();
            if (!isWorkspace(dir)) {
                throw new IllegalStateException("mcp.workspace 不是 WorkSpace: " + dir);
            }
            return dir;
        }
        File found = walkToWorkspace(codeLocation().getCanonicalFile());
        if (found == null) {
            found = walkToWorkspace(new File(System.getProperty("user.dir")).getCanonicalFile());
        }
        if (found == null) {
            throw new IllegalStateException("无法定位 WorkSpace（需含 Server 与 Document）。可用 -Dmcp.workspace");
        }
        return found.getCanonicalFile();
    }

    private static File codeLocation() throws Exception {
        CodeSource source = McpConfig.class.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            return new File(System.getProperty("user.dir"));
        }
        File file = new File(source.getLocation().toURI());
        return file.isFile() ? file.getParentFile() : file;
    }

    private static File walkToWorkspace(File start) {
        for (File dir = start; dir != null; dir = dir.getParentFile()) {
            if (isWorkspace(dir)) {
                return dir;
            }
        }
        return null;
    }

    private static boolean isWorkspace(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        // 优先匹配当前 game-server 多模块根工程
        if (new File(dir, "pom.xml").isFile() && (new File(dir, "hub").isDirectory() || new File(dir, "servertool").isDirectory())) {
            return true;
        }
        // 兼顾原 KingdomWarships 历史目录 (Server + Document)
        return new File(dir, "Server").isDirectory() && new File(dir, "Document").isDirectory();
    }

    private static File dataLogDir(File workspace) throws Exception {
        String override = System.getProperty("mcp.datalog");
        File dir;
        if (override != null && !override.trim().isEmpty()) {
            dir = new File(override.trim()).getCanonicalFile();
        } else {
            File parent = workspace.getParentFile();
            dir = parent == null ? null : new File(parent, "DataLog");
        }
        return dir != null && dir.isDirectory() ? dir.getCanonicalFile() : null;
    }
}

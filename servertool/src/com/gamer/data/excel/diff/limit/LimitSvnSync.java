package com.gamer.data.excel.diff.limit;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.gamer.data.process.ProcessOptions;
import com.gamer.data.process.ProcessRunner;

/**
 * StrategyTool 启动时后台 svn update CodeTool 目录以同步 limit 配置。
 */
public class LimitSvnSync {

    private static final String MSG_SUCCESS = "gd表格生成限制更新成功";

    private static final String MSG_FAIL = "gd表格生成限制更新失败";

    private LimitSvnSync() {}

    public static void updateAsync(final File serverDir, final LimitLog log) {
        if (serverDir == null || !serverDir.isDirectory()) {
            logFail(log, "目录不存在 " + (serverDir != null ? serverDir.getPath() : "null"));
            return;
        }
        Thread thread = new Thread(() -> runUpdate(serverDir, log));
        thread.setDaemon(true);
        thread.setName("limit-svn-update");
        thread.start();
    }

    private static void runUpdate(File serverDir, LimitLog log) {
        try {
            ProcessOptions options = new ProcessOptions()
                .workDirectory(serverDir)
                .charset(outputCharset());
            int exitCode = ProcessRunner.run(Arrays.asList("svn", "update"), options, line -> {
                if (log != null) {
                    log.log(line, false);
                }
            });
            handleExitCode(exitCode, log);
        } catch (Exception e) {
            logFail(log, e.getMessage());
        }
    }

    private static Charset outputCharset() {
        try {
            return Charset.forName("GBK");
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    private static void handleExitCode(int code, LimitLog log) {
        if (log == null) {
            return;
        }
        if (code == 0) {
            log.log(MSG_SUCCESS, false);
        } else {
            log.log(MSG_FAIL + "：exitCode=" + code, true);
        }
    }

    private static void logFail(LimitLog log, String detail) {
        if (log != null) {
            log.log(MSG_FAIL + "：" + detail, true);
        }
    }

    /**
     * limit SVN 更新日志回调。
     */
    public interface LimitLog {

        void log(String message, boolean isError);
    }
}

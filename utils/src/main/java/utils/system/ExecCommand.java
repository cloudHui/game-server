package utils.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 系统终端命令与本地脚本执行工具类。
 * <p>
 * 提供跨平台命令调用、脚本执行（.bat / .sh）、结果流式收集与安全超时控制。
 * 支持标准输出与错误流同步消费，防止底层管道缓冲区打满造成线程死锁挂起。
 * </p>
 *
 * @author cloud
 */
public class ExecCommand {

    /** 统一日志记录器 */
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecCommand.class);

    /** 默认命令执行超时时间（秒） */
    private static final int DEFAULT_TIMEOUT_SECONDS = 15;

    public ExecCommand() {
    }

    /**
     * 判断当前操作系统是否为 Windows。
     *
     * @return 若为 Windows 系统返回 true，否则返回 false
     */
    public static boolean isWindows() {
        String osName = System.getProperty("os.name");
        boolean windows = osName != null && osName.contains("Windows");
        LOGGER.trace("[isWindows] 检测当前操作系统: os.name={}, isWindows={}", osName, windows);
        return windows;
    }

    /**
     * 执行系统命令并按行获取标准输出与错误输出结果。
     *
     * @param command 待执行的终端命令文本
     * @return 命令执行输出的所有行文本列表
     */
    public static List<String> exeCommand(String command) {
        return exeCommand(command, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * 执行系统命令（支持指定超时时间）。
     *
     * @param command        待执行的终端命令文本
     * @param timeoutSeconds 最大超时时间（秒）
     * @return 命令执行输出的所有行文本列表
     */
    public static List<String> exeCommand(String command, int timeoutSeconds) {
        if (command == null || command.trim().isEmpty()) {
            LOGGER.warn("[exeCommand] 命令为空，直接返回空列表");
            return Collections.emptyList();
        }

        long startTime = System.currentTimeMillis();
        boolean windows = isWindows();
        List<String> cmdList = new ArrayList<>();
        if (windows) {
            cmdList.add("cmd.exe");
            cmdList.add("/c");
            cmdList.add(command);
        } else {
            cmdList.add("/bin/sh");
            cmdList.add("-c");
            cmdList.add(command);
        }

        LOGGER.info("[exeCommand] 开始执行外部命令: command='{}', timeout={}s, platform={}",
                command, timeoutSeconds, windows ? "Windows" : "Unix-like");

        List<String> results = new ArrayList<>();
        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(cmdList);
            // 合并标准输出与错误输出流，避免错误流单独堆积引发操作系统管道死锁
            processBuilder.redirectErrorStream(true);

            process = processBuilder.start();

            // 根据操作系统确定命令行读取字符集（Windows 中文终端多为 GBK，Unix 为 UTF-8）
            Charset charset = windows ? Charset.forName("GBK") : StandardCharsets.UTF_8;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), charset))) {
                String line;
                // 逐行消费输出流，修复原代码未重新赋值 line 的 Bug
                while ((line = reader.readLine()) != null) {
                    results.add(line);
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            long costMs = System.currentTimeMillis() - startTime;
            if (!finished) {
                LOGGER.warn("[exeCommand] 命令执行超时被强行终止: command='{}', cost={}ms, timeout={}s",
                        command, costMs, timeoutSeconds);
                process.destroyForcibly();
            } else {
                int exitCode = process.exitValue();
                LOGGER.info("[exeCommand] 命令执行完成: command='{}', exitCode={}, lines={}, cost={}ms",
                        command, exitCode, results.size(), costMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("[exeCommand] 命令执行被线程中断: command='{}'", command, e);
        } catch (IOException e) {
            LOGGER.error("[exeCommand] 命令启动或流读取发生 I/O 异常: command='{}'", command, e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }

        return results;
    }

    /**
     * 执行命令并在返回行中匹配目标前缀/子串，提取后续内容。
     *
     * @param command      待执行终端命令
     * @param targetResult 需要定位的标识子串
     * @return 匹配行提取后的结果字符串，未找到则返回空字符串
     */
    public static String exeCommand(String command, String targetResult) {
        LOGGER.debug("[exeCommand] 过滤执行: command='{}', targetResult='{}'", command, targetResult);
        List<String> lines = exeCommand(command);
        return getExeCommandResult(lines, targetResult);
    }

    /**
     * 从结果列表中检索包含指定目标子串的文本，并截取子串及其后内容。
     *
     * @param results      输出行列表
     * @param targetResult 检索目标子串
     * @return 截取后的结果字符串
     */
    public static String getExeCommandResult(List<String> results, String targetResult) {
        if (results == null || results.isEmpty() || targetResult == null || targetResult.isEmpty()) {
            LOGGER.debug("[getExeCommandResult] 结果列表或目标关键字为空");
            return "";
        }
        for (String temp : results) {
            if (temp != null && temp.contains(targetResult)) {
                String match = temp.substring(temp.indexOf(targetResult)).trim();
                LOGGER.debug("[getExeCommandResult] 命中目标关键字: target='{}', matched='{}'", targetResult, match);
                return match;
            }
        }
        LOGGER.debug("[getExeCommandResult] 未在输出结果中检索到关键字: target='{}'", targetResult);
        return "";
    }

    /**
     * 执行指定名称的批处理或 Shell 脚本文件（自动补齐跨平台调用包装）。
     *
     * @param batName 脚本路径（不含后缀，根据系统自动适配）
     */
    public static void exeBatSh(String batName) {
        if (batName == null || batName.trim().isEmpty()) {
            LOGGER.warn("[exeBatSh] 脚本路径为空，忽略执行");
            return;
        }

        String cmd = isWindows() ? "cmd.exe /c " + batName : "sh " + batName;
        long start = System.currentTimeMillis();
        LOGGER.info("[exeBatSh] 开始执行脚本: cmd='{}'", cmd);
        try {
            Process process = Runtime.getRuntime().exec(cmd);
            int exitCode = process.waitFor();
            LOGGER.info("[exeBatSh] 脚本执行完毕: cmd='{}', exitCode={}, cost={}ms",
                    cmd, exitCode, (System.currentTimeMillis() - start));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("[exeBatSh] 脚本执行被中断: cmd='{}'", cmd, e);
        } catch (Exception e) {
            LOGGER.error("[exeBatSh] 脚本执行异常: cmd='{}'", cmd, e);
        }
    }

    /**
     * 执行带自动后缀匹配的脚本。
     *
     * @param scriptBaseName 脚本前缀名（Windows 自动追加 .bat，Linux 追加 .sh）
     */
    public static void exeScript(String scriptBaseName) {
        if (scriptBaseName == null || scriptBaseName.trim().isEmpty()) {
            LOGGER.warn("[exeScript] 脚本基类名为空，忽略执行");
            return;
        }

        String actualScript = isWindows() ? (scriptBaseName + ".bat") : (scriptBaseName + ".sh");
        LOGGER.info("[exeScript] 调度脚本封装: scriptBaseName='{}' -> actual='{}'", scriptBaseName, actualScript);
        exeBatSh(actualScript);
    }
}

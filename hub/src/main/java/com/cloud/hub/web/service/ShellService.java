package com.cloud.hub.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 管理员 Web 终端 Shell 服务。
 * <p>
 * 按会话记录当前工作目录，内置 cd / pwd / clear 特殊指令维护，
 * 通过外部 {@code bash -lc} 安全执行系统管理运维脚本并捕获输出。
 *
 * @author cloud
 */
@Service
public class ShellService {

    private static final Logger logger = LoggerFactory.getLogger(ShellService.class);
    private static final String DEFAULT_CWD = "/home/ec2-user";
    private static final int TIMEOUT_SEC = 60;
    private static final int MAX_OUTPUT_BYTES = 512 * 1024;

    private final ConcurrentHashMap<String, String> sessionCwds = new ConcurrentHashMap<>();

    /**
     * 获取指定会话当前工作目录。
     */
    public String currentCwd(String sessionId) {
        return normalizeExisting(sessionCwds.getOrDefault(sessionId, DEFAULT_CWD));
    }

    /**
     * 执行命令行指令。
     *
     * @param sessionId  会话 Token
     * @param rawCommand 原始命令行文本
     * @return 执行结果字典
     */
    public Map<String, Object> execute(String sessionId, String rawCommand) {
        String command = rawCommand == null ? "" : rawCommand.trim();
        String cwd = currentCwd(sessionId);
        Map<String, Object> result = baseResult(cwd);
        if (command.isEmpty()) {
            result.put("code", 0);
            result.put("output", "");
            return result;
        }
        if (isClear(command)) {
            result.put("code", 0);
            result.put("clear", true);
            result.put("output", "");
            return result;
        }
        if (isPwd(command)) {
            result.put("code", 0);
            result.put("output", cwd + "\n");
            return result;
        }
        if (isCd(command)) {
            return handleCd(sessionId, command, cwd, result);
        }
        return runBash(sessionId, command, cwd, result);
    }

    private Map<String, Object> handleCd(String sessionId, String command, String cwd, Map<String, Object> result) {
        String target = extractCdTarget(command);
        Path next = resolvePath(cwd, target);
        File dir = next.toFile();
        if (!dir.isDirectory()) {
            result.put("code", 1);
            result.put("output", "bash: cd: " + target + ": No such file or directory\n");
            return result;
        }
        String normalized = normalizeExisting(dir.getAbsolutePath());
        sessionCwds.put(sessionId, normalized);
        result.put("cwd", normalized);
        result.put("code", 0);
        result.put("output", "");
        return result;
    }

    private Map<String, Object> runBash(String sessionId, String command, String cwd, Map<String, Object> result) {
        Process process = null;
        try {
            ProcessBuilder pb = buildProcess(command, cwd);
            process = pb.start();
            String output = readLimited(process.getInputStream());
            boolean finished = process.waitFor(TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                result.put("code", 124);
                result.put("output", output + "\n[命令超时，已终止，限制 " + TIMEOUT_SEC + " 秒]\n");
                return result;
            }
            result.put("code", process.exitValue());
            result.put("output", output);
            result.put("cwd", currentCwd(sessionId));
            return result;
        } catch (Exception e) {
            logger.warn("终端命令执行失败, sessionId: {}, cmd: {}, err: {}", sessionId, command, e.toString());
            result.put("code", 1);
            result.put("output", "执行失败: " + e.getMessage() + "\n");
            return result;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private ProcessBuilder buildProcess(String command, String cwd) {
        ProcessBuilder pb = new ProcessBuilder("bash", "-lc", command);
        pb.directory(new File(cwd));
        pb.redirectErrorStream(true);
        Map<String, String> env = pb.environment();
        env.put("HOME", DEFAULT_CWD);
        env.put("USER", "ec2-user");
        env.put("PWD", cwd);
        env.put("TERM", "xterm-256color");
        return pb;
    }

    private static String readLimited(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        boolean truncated = false;
        while ((n = in.read(chunk)) >= 0) {
            if (buf.size() >= MAX_OUTPUT_BYTES) {
                truncated = true;
                while (in.read(chunk) >= 0) {
                }
                break;
            }
            int allow = Math.min(n, MAX_OUTPUT_BYTES - buf.size());
            buf.write(chunk, 0, allow);
            if (allow < n) {
                truncated = true;
            }
        }
        String text = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        return truncated ? text + "\n[输出过长，已截断]\n" : text;
    }

    private static Map<String, Object> baseResult(String cwd) {
        Map<String, Object> result = new HashMap<>();
        result.put("cwd", cwd);
        result.put("clear", false);
        return result;
    }

    private static boolean isClear(String command) {
        return "clear".equals(command) || "cls".equals(command);
    }

    private static boolean isPwd(String command) {
        return "pwd".equals(command);
    }

    private static boolean isCd(String command) {
        return "cd".equals(command) || command.startsWith("cd ") || command.startsWith("cd\t");
    }

    private static String extractCdTarget(String command) {
        if ("cd".equals(command)) {
            return "~";
        }
        String rest = command.substring(2).trim();
        if (rest.isEmpty()) {
            return "~";
        }
        if ((rest.startsWith("\"") && rest.endsWith("\"")) || (rest.startsWith("'") && rest.endsWith("'"))) {
            return rest.substring(1, rest.length() - 1);
        }
        return rest;
    }

    private static Path resolvePath(String cwd, String target) {
        if (target == null || target.isEmpty() || "~".equals(target)) {
            return Paths.get(DEFAULT_CWD);
        }
        if (target.startsWith("~/")) {
            return Paths.get(DEFAULT_CWD, target.substring(2)).normalize();
        }
        Path path = Paths.get(target);
        return path.isAbsolute() ? path.normalize() : Paths.get(cwd).resolve(path).normalize();
    }

    private static String normalizeExisting(String path) {
        try {
            File file = new File(path);
            if (file.isDirectory()) {
                return file.getCanonicalPath();
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_CWD;
    }
}

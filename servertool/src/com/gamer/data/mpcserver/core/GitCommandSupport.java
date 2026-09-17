package com.gamer.data.mpcserver.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.gamer.data.process.ProcessOptions;
import com.gamer.data.process.ProcessRunner;

/**
 * 在指定仓库目录执行只读 git 命令，并截断输出。
 *
 * @author liuyunhui
 * @date 2026/05/20
 */
public final class GitCommandSupport {

    /** git_run 允许的子命令（只读） */
    public static final Set<String> READ_ONLY_SUBCOMMANDS = new HashSet<>(Arrays.asList(
            "log", "show", "diff", "diff-tree", "rev-parse", "status"));

    /** 明确禁止通过 git_run 触发的写入类命令 token。 */
    private static final Set<String> DANGEROUS_TOKENS = new HashSet<>(Arrays.asList(
            "push", "reset", "checkout", "merge", "rebase", "commit", "clean", "stash"));

    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private GitCommandSupport() {}

    /**
     * 解析并校验仓库目录。
     *
     * @param defaults
     *            Git 默认配置
     * @return 仓库 File
     */
    public static File requireRepo(GitDefaults defaults) {
        if (defaults == null || defaults.getRepoDir() == null) {
            throw new IllegalStateException("Git 仓库未配置（WorkSpace/Server）");
        }
        File repo = defaults.getRepoDir();
        if (!repo.isDirectory()) {
            throw new IllegalArgumentException("gitRepo 不是有效目录: " + repo.getPath());
        }
        File gitDir = new File(repo, ".git");
        if (!gitDir.exists()) {
            throw new IllegalArgumentException("目录不是 Git 仓库（缺少 .git）: " + repo.getPath());
        }
        return repo;
    }

    /**
     * 执行 git 参数列表（不含可执行文件名 git 本身）。
     *
     * @param repo
     *            仓库根目录
     * @param gitArgs
     *            git 子参数，如 log、show
     * @param maxOutputChars
     *            最大输出字符
     * @return 标准输出+标准错误合并文本
     */
    public static String runGit(File repo, List<String> gitArgs, int maxOutputChars) throws Exception {
        return runGit(repo, gitArgs, maxOutputChars, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * 执行 git 并限制超时。
     */
    public static String runGit(File repo, List<String> gitArgs, int maxOutputChars, int timeoutSeconds)
        throws Exception {
        if (gitArgs == null || gitArgs.isEmpty()) {
            throw new IllegalArgumentException("git 参数不能为空");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(gitArgs);

        StringBuilder out = new StringBuilder();
        ProcessOptions options = new ProcessOptions().workDirectory(repo).charset(StandardCharsets.UTF_8)
            .timeoutMillis(timeoutSeconds * 1000L);
        int exit;
        try {
            exit = ProcessRunner.run(cmd, options, line -> appendOutput(out, line, maxOutputChars));
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("超时")) {
                throw new IllegalStateException("git 命令超时（" + timeoutSeconds + "s）: " + joinArgs(gitArgs), e);
            }
            throw e;
        }
        String text = out.toString();
        if (exit != 0) {
            throw new IllegalStateException("git 退出码 " + exit + "，命令: git " + joinArgs(gitArgs) + "\n输出:\n" + text);
        }
        return text;
    }

    private static void appendOutput(StringBuilder out, String line, int maxChars) {
        if (out.length() >= maxChars) {
            return;
        }
        if (out.length() > 0) {
            out.append('\n');
        }
        int remaining = maxChars - out.length();
        if (line.length() <= remaining) {
            out.append(line);
            return;
        }
        out.append(line, 0, remaining).append("\n... [输出已截断]\n");
    }

    /**
     * 校验 git_run 的首个参数为只读子命令。
     */
    public static void assertReadOnlySubCommand(String subCommand) {
        if (subCommand == null || subCommand.trim().isEmpty()) {
            throw new IllegalArgumentException("git 子命令不能为空");
        }
        String sc = subCommand.trim().toLowerCase();
        if (!READ_ONLY_SUBCOMMANDS.contains(sc)) {
            throw new IllegalArgumentException("不允许的 git 子命令: " + subCommand + "，允许: " + READ_ONLY_SUBCOMMANDS);
        }
    }

    /**
     * 拒绝明显危险的命令 token（双保险），但允许 --no-merges、--no-commit-id 等只读选项。
     */
    public static void assertSafeArgs(List<String> gitArgs) {
        if (gitArgs == null) {
            return;
        }
        int i;
        for (i = 0; i < gitArgs.size(); i++) {
            String a = gitArgs.get(i);
            if (a == null) {
                continue;
            }
            String lower = a.trim().toLowerCase();
            if (isDangerousToken(lower)) {
                throw new IllegalArgumentException("参数含禁止命令: " + a);
            }
            if ("--output".equals(lower) || lower.startsWith("--output=")) {
                throw new IllegalArgumentException("禁止 git --output: " + a);
            }
        }
    }

    /**
     * 判断单个参数是否直接代表写入类 git 命令。
     *
     * @param lower
     *            已转小写的参数
     * @return true 表示禁止
     */
    private static boolean isDangerousToken(String lower) {
        if (lower == null || lower.isEmpty()) {
            return false;
        }
        if (DANGEROUS_TOKENS.contains(lower)) {
            return true;
        }
        int splitIndex = firstWhitespaceIndex(lower);
        if (splitIndex <= 0) {
            return false;
        }
        return DANGEROUS_TOKENS.contains(lower.substring(0, splitIndex));
    }

    /**
     * @param text
     *            参数文本
     * @return 首个空白字符下标，不存在返回 -1
     */
    private static int firstWhitespaceIndex(String text) {
        int i;
        for (i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String joinArgs(List<String> args) {
        StringBuilder sb = new StringBuilder();
        int i;
        for (i = 0; i < args.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(args.get(i));
        }
        return sb.toString();
    }
}

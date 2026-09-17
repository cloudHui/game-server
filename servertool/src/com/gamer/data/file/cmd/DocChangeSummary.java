package com.gamer.data.file.cmd;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import com.gamer.data.read.GdData;
import com.gamer.data.read.GdFileReader;
import com.gamer.data.read.GdIdRowDiff;

/**
 * Document 上 {@code svn update} 后的变更摘要：md/txt 打全文，gd 打列/行差。
 * <p>
 * 只认「状态字母 + 空白 + 路径」的条目行，忽略 {@code Updating} / {@code Updated} 等横幅。
 */
final class DocChangeSummary {

    /** 非 md/txt/gd。 */
    private static final int KIND_NONE = 0;
    /** .md / .txt。 */
    private static final int KIND_DOC = 1;
    /** .gd。 */
    private static final int KIND_GD = 2;

    /** Windows 丢弃子进程 stdin/stderr，避免管道堵死。 */
    private static final File NUL = new File("NUL");

    private DocChangeSummary() {}

    /**
     * 从任务输出生成已带小节标题的日志行。非 Document 的 svn update 返回 empty。
     *
     * @param task
     *            已结束的命令任务
     * @return 可直接追加到任务日志的行；无变更时 empty
     */
    static List<String> toLogLines(CmdTask task) {
        if (task == null || !isDocumentSvnUpdate(task)) {
            return Collections.emptyList();
        }
        List<String> docLines = new ArrayList<>();
        List<String> gdLines = new ArrayList<>();
        Set<String> handled = new HashSet<>();
        String[] outputLines = task.getOutput().split("\\r?\\n");
        for (String outputLine : outputLines) {
            Item item = parseItem(outputLine);
            if (item == null || !handled.add(item.action + item.path)) {
                continue;
            }
            int kind = kind(item.path);
            if (kind == KIND_NONE) {
                continue;
            }
            List<String> target = kind == KIND_DOC ? docLines : gdLines;
            try {
                target.addAll(build(item.action, task.workDir, item.path));
            } catch (Exception e) {
                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                target.add(item.path + "：摘要生成失败: " + reason);
            }
        }
        return mergeSections(docLines, gdLines);
    }

    /**
     * 是否为 Document 根上的 {@code svn update}（与快捷按钮命令字面量一致）。
     */
    private static boolean isDocumentSvnUpdate(CmdTask task) {
        String docPath = new File(CmdPanel.DOCUMENT).getAbsolutePath().trim().toLowerCase(Locale.ROOT);
        return docPath.equals(task.workDirPath.toLowerCase(Locale.ROOT))
            && CmdPanel.SVN_UPDATE.equals(task.command);
    }

    /**
     * svn update 条目行：首列 U/A/D，其后必须空白，从而排除 Updating/Updated。
     *
     * @param raw
     *            原始输出行
     * @return 非条目行返回 null
     */
    private static Item parseItem(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.length() < 3) {
            return null;
        }
        char action = text.charAt(0);
        if ("UAD".indexOf(action) < 0 || !Character.isWhitespace(text.charAt(1))) {
            return null;
        }
        String path = text.substring(1).trim();
        if (path.isEmpty()) {
            return null;
        }
        return new Item(action, path);
    }

    private static int kind(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md") || lower.endsWith(".txt")) {
            return KIND_DOC;
        }
        if (lower.endsWith(".gd")) {
            return KIND_GD;
        }
        return KIND_NONE;
    }

    private static List<String> build(char action, File workDir, String path) throws Exception {
        int kind = kind(path);
        if (kind == KIND_DOC) {
            return text(action, workDir, path);
        }
        if (kind == KIND_GD) {
            return gd(action, workDir, path);
        }
        return Collections.emptyList();
    }

    private static List<String> mergeSections(List<String> docLines, List<String> gdLines) {
        if (docLines.isEmpty() && gdLines.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>(docLines.size() + gdLines.size() + 2);
        appendSection(out, "【文档变更】", docLines);
        appendSection(out, "【GD变更】", gdLines);
        return out;
    }

    private static void appendSection(List<String> out, String title, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        out.add(title);
        out.addAll(lines);
    }

    private static List<String> text(char action, File workDir, String path) throws Exception {
        List<String> out = new ArrayList<>();
        File local = new File(workDir, path);
        if (action == 'A') {
            out.add("新增 " + path + "：");
            prefix(out, "+", Files.readAllLines(local.toPath(), StandardCharsets.UTF_8));
        } else if (action == 'D') {
            out.add("删除 " + path + "：");
            prefix(out, "-", svnLines(workDir, "cat", "-r", "PREV", path));
        } else {
            out.add("修改 " + path + "：");
            List<String> diff = svnLines(workDir, "diff", "-r", "PREV", path);
            if (diff.isEmpty()) {
                out.add("  （无差异）");
            } else {
                for (String s : diff) {
                    out.add("  " + s);
                }
            }
        }
        return out;
    }

    private static List<String> gd(char action, File workDir, String path) throws Exception {
        if (action == 'A') {
            return Collections.singletonList("新增 " + path);
        }
        if (action == 'D') {
            return Collections.singletonList("删除 " + path);
        }
        File prev = File.createTempFile("gd-prev-", ".gd");
        try {
            catPrev(workDir, path, prev);
            GdData oldData = GdFileReader.readGdFile(prev);
            GdData newData = GdFileReader.readGdFile(new File(workDir, path));
            if (oldData.header == null || newData.header == null || oldData.header.columnNames == null
                || newData.header.columnNames == null) {
                return Collections.singletonList(path + "：无法读表头");
            }
            Set<String> added = new TreeSet<>(newData.header.columnNames);
            oldData.header.columnNames.forEach(added::remove);
            Set<String> deleted = new TreeSet<>(oldData.header.columnNames);
            newData.header.columnNames.forEach(deleted::remove);
            return GdIdRowDiff.toLogLines(path, added, deleted, GdIdRowDiff.compare(oldData, newData));
        } finally {
            prev.delete();
        }
    }

    private static void prefix(List<String> out, String p, List<String> lines) {
        if (lines.isEmpty()) {
            out.add("  " + p + "（空文件）");
            return;
        }
        for (String line : lines) {
            out.add("  " + p + line);
        }
    }

    /**
     * svn cat PREV 写临时文件。stderr 丢到 NUL，避免与二进制 stdout 混流。
     */
    private static void catPrev(File workDir, String path, File out) throws Exception {
        Process process = svn(workDir, false, "cat", "-r", "PREV", path);
        try {
            try (InputStream in = process.getInputStream()) {
                Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            if (process.waitFor() != 0) {
                throw new IllegalStateException("svn cat PREV failed: " + path);
            }
        } finally {
            process.destroy();
        }
    }

    private static List<String> svnLines(File workDir, String... args) throws Exception {
        Process process = svn(workDir, true, args);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            if (process.waitFor() != 0) {
                throw new IllegalStateException("svn failed: " + joinArgs(args));
            }
            return lines;
        } finally {
            process.destroy();
        }
    }

    /**
     * @param mergeError
     *            true 则 stderr 并入 stdout（文本 diff/cat）；false 则 stderr 丢 NUL（二进制 cat）
     */
    private static Process svn(File workDir, boolean mergeError, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(args.length + 1);
        cmd.add("svn");
        Collections.addAll(cmd, args);
        ProcessBuilder builder = new ProcessBuilder(cmd);
        builder.directory(workDir);
        builder.redirectInput(ProcessBuilder.Redirect.from(NUL));
        if (mergeError) {
            builder.redirectErrorStream(true);
        } else {
            builder.redirectError(ProcessBuilder.Redirect.to(NUL));
        }
        return builder.start();
    }

    private static String joinArgs(String[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    /** svn update 单条文件动作。 */
    private static final class Item {
        /** U 更新 / A 新增 / D 删除 */
        final char action;
        /** 相对 Document 根的路径 */
        final String path;

        Item(char action, String path) {
            this.action = action;
            this.path = path;
        }
    }
}

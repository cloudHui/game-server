package com.gamer.data.excel.diff.util;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gamer.data.excel.shared.ExcelExtensions;

/**
 * 通过 {@code svn status} 扫描 ConfigurationExcel 下变更 xlsx，复制到 StrategyTool 当前目录。
 */
public final class SvnChangedXlsxPuller {

    /** svn status 输出中路径列起始位置 */
    private static final int SVN_PATH_COLUMN = 8;

    private SvnChangedXlsxPuller() {}

    /**
     * SVN 状态行回调。
     */
    private interface StatusLineConsumer {
        /**
         * @param status
         *            首字符状态
         * @param file
         *            xlsx 绝对文件
         * @return 是否继续
         */
        boolean accept(char status, File file);
    }

    /**
     * svn status 加载结果（区分失败与「无变更」）。
     */
    public static final class StatusLoadResult {
        /** svn status 是否执行成功 */
        public final boolean ok;
        /** 文件名 → 状态字符（仅非空状态） */
        public final Map<String, String> statusByName;

        private StatusLoadResult(boolean ok, Map<String, String> statusByName) {
            this.ok = ok;
            this.statusByName = statusByName;
        }
    }

    /**
     * 扫描 SVN 工作副本，返回 xlsx 文件名 → 状态字符（M/A/?/C 等）。
     *
     * @param svnRoot
     *            SVN 根（通常 XML_PATH）
     * @return 文件名到状态；失败时空 Map
     */
    public static Map<String, String> loadXlsxStatusByName(File svnRoot) {
        return loadXlsxStatusResult(svnRoot).statusByName;
    }

    /**
     * 扫描 SVN 工作副本（带成功标记）。
     *
     * @param svnRoot
     *            SVN 根
     * @return 结果；失败时 ok=false 且 map 为空
     */
    public static StatusLoadResult loadXlsxStatusResult(File svnRoot) {
        final Map<String, String> statusByName = new HashMap<>();
        boolean ok = forEachXlsxStatus(svnRoot, null, (status, file) -> {
            if (status != ' ') {
                statusByName.put(file.getName(), String.valueOf(status));
            }
            return true;
        });
        if (!ok) {
            return new StatusLoadResult(false, Collections.<String, String>emptyMap());
        }
        return new StatusLoadResult(true, statusByName);
    }

    /**
     * SVN 变更的 xlsx（含状态字符）。
     */
    public static final class ChangedXlsx {
        /** svn status 首字符：M/A/? 等 */
        public final char status;
        /** 工作副本中的 xlsx */
        public final File file;

        /**
         * @param status
         *            状态
         * @param file
         *            文件
         */
        public ChangedXlsx(char status, File file) {
            this.status = status;
            this.file = file;
        }
    }

    /**
     * 一次 svn status 的变更结果（区分失败与无变更）。
     */
    public static final class ChangedLoadResult {
        /** svn status 是否成功 */
        public final boolean ok;
        /** M/A/? 的 xlsx */
        public final List<ChangedXlsx> files;

        /**
         * @param ok
         *            svn 成功
         * @param files
         *            变更列表
         */
        public ChangedLoadResult(boolean ok, List<ChangedXlsx> files) {
            this.ok = ok;
            this.files = files;
        }
    }

    /**
     * 一次 svn status，收集 M/A/?（跳过冲突 C）。
     *
     * @param svnRoot
     *            SVN 根
     * @param logger
     *            日志，可为 null
     * @return ok=false 表示 svn 失败
     */
    public static ChangedLoadResult loadChangedXlsx(File svnRoot, ViewFileCopyUtil.CopyLogger logger) {
        final List<ChangedXlsx> changed = new ArrayList<>();
        boolean ok = forEachXlsxStatus(svnRoot, logger, (status, file) -> acceptChangedStatus(status, file, changed, logger));
        if (!ok) {
            return new ChangedLoadResult(false, Collections.emptyList());
        }
        return new ChangedLoadResult(true, changed);
    }

    /**
     * 收集 M/A/?，冲突只记日志。
     *
     * @param status
     *            svn 状态
     * @param file
     *            xlsx
     * @param changed
     *            输出列表
     * @param logger
     *            日志
     * @return 继续遍历
     */
    private static boolean acceptChangedStatus(char status, File file, List<ChangedXlsx> changed,
        ViewFileCopyUtil.CopyLogger logger) {
        if (status == 'C') {
            if (logger != null) {
                logger.log("跳过冲突文件: " + file.getName());
            }
            return true;
        }
        if (status == 'M' || status == 'A' || status == '?') {
            changed.add(new ChangedXlsx(status, file.getAbsoluteFile()));
        }
        return true;
    }

    /**
     * 将工作副本文件的 SVN BASE 导出到 destFile（二进制，供内容对比）。
     *
     * @param svnRoot
     *            SVN 根
     * @param workingFile
     *            工作副本文件
     * @param destFile
     *            导出目标
     * @param logger
     *            日志，可为 null
     * @return 导出成功
     */
    public static boolean exportSvnBase(File svnRoot, File workingFile, File destFile,
        ViewFileCopyUtil.CopyLogger logger) {
        String relative = toSvnRelativePath(svnRoot, workingFile, logger);
        if (relative == null) {
            return false;
        }
        if (!ensureParentDir(destFile, logger)) {
            return false;
        }
        return catBaseToFile(svnRoot, relative, workingFile.getName(), destFile, logger);
    }

    /**
     * 工作副本相对 SVN 根的路径（正斜杠）。
     *
     * @param svnRoot
     *            SVN 根
     * @param workingFile
     *            工作副本文件
     * @param logger
     *            日志
     * @return 相对路径；失败 null
     */
    private static String toSvnRelativePath(File svnRoot, File workingFile, ViewFileCopyUtil.CopyLogger logger) {
        if (svnRoot == null || workingFile == null) {
            return null;
        }
        try {
            return svnRoot.getCanonicalFile().toPath().relativize(workingFile.getCanonicalFile().toPath()).toString()
                .replace('\\', '/');
        } catch (Exception e) {
            if (logger != null) {
                logger.log("无法计算相对路径: " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * 确保导出文件的父目录存在。
     *
     * @param destFile
     *            目标文件
     * @param logger
     *            日志
     * @return 父目录可用
     */
    private static boolean ensureParentDir(File destFile, ViewFileCopyUtil.CopyLogger logger) {
        if (destFile == null) {
            return false;
        }
        File parent = destFile.getParentFile();
        if (parent == null || parent.isDirectory()) {
            return true;
        }
        if (parent.mkdirs()) {
            return true;
        }
        if (logger != null) {
            logger.log("无法创建 BASE 导出目录: " + parent.getAbsolutePath());
        }
        return false;
    }

    /**
     * svn cat -r BASE 写到 destFile。
     *
     * @param svnRoot
     *            SVN 根
     * @param relative
     *            相对路径
     * @param fileName
     *            日志用文件名
     * @param destFile
     *            目标
     * @param logger
     *            日志
     * @return 导出成功
     */
    private static boolean catBaseToFile(File svnRoot, String relative, String fileName, File destFile,
        ViewFileCopyUtil.CopyLogger logger) {
        Process process = null;
        InputStream stdout = null;
        FileOutputStream fos = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("svn", "cat", "-r", "BASE", relative);
            builder.directory(svnRoot);
            process = builder.start();
            stdout = process.getInputStream();
            fos = new FileOutputStream(destFile);
            copyStream(stdout, fos);
            int code = process.waitFor();
            if (code != 0) {
                logCatFail(logger, fileName, "exitCode=" + code);
                destFile.delete();
                return false;
            }
            return destFile.isFile() && destFile.length() > 0;
        } catch (Exception e) {
            logCatFail(logger, fileName, e.getMessage());
            destFile.delete();
            return false;
        } finally {
            closeQuietlyStream(stdout);
            closeQuietlyStream(fos);
            destroyQuietly(process);
        }
    }

    /**
     * 复制输入流到输出流。
     *
     * @param in
     *            输入
     * @param out
     *            输出
     */
    private static void copyStream(InputStream in, FileOutputStream out) throws Exception {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
    }

    /**
     * 写 svn cat 失败日志。
     *
     * @param logger
     *            日志
     * @param fileName
     *            文件名
     * @param detail
     *            详情
     */
    private static void logCatFail(ViewFileCopyUtil.CopyLogger logger, String fileName, String detail) {
        if (logger != null) {
            logger.log("svn cat BASE 失败: " + fileName + " " + detail);
        }
    }

    /**
     * 安静关闭流。
     *
     * @param closeable
     *            可为 null
     */
    private static void closeQuietlyStream(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    /**
     * 拉变更执行结果统计。
     */
    public static final class PullResult {

        /** 成功复制数 */
        public int copied;
        /** mtime 跳过数 */
        public int skippedByMtime;
        /** 复制失败数 */
        public int failed;
        /** 冲突态跳过数 */
        public int conflictSkipped;
    }

    /**
     * 扫描 SVN 变更并复制 xlsx 到目标目录（不执行 svn update）。
     *
     * @param svnRoot   SVN 工作副本根（XML_PATH）
     * @param targetDir 工具当前目录（CURR_DIR）
     * @param logger    日志
     */
    public static void pull(File svnRoot, File targetDir, ViewFileCopyUtil.CopyLogger logger) {
        PullResult result = new PullResult();
        if (isNotValidDirectory(svnRoot, "SVN 根目录", logger)) {
            return;
        }
        if (isNotValidDirectory(targetDir, "目标目录", logger)) {
            return;
        }
        List<File> changed = listChangedXlsx(svnRoot, logger, result);
        if (changed.isEmpty()) {
            logger.log("无待复制的 SVN 变更 xlsx");
            return;
        }
        List<File> toCopy = filterByMtime(changed, targetDir, logger, result);
        if (toCopy.isEmpty()) {
            logger.log("变更文件均已是最新，无需复制");
            return;
        }
        logCopyPlan(toCopy, targetDir, logger);
        copyAll(toCopy, targetDir, logger, result);
        logger.log("拉变更完成：成功 " + result.copied + "，mtime跳过 " + result.skippedByMtime + "，失败 "
            + result.failed + "，冲突跳过 " + result.conflictSkipped);
    }

    private static boolean isNotValidDirectory(File dir, String label, ViewFileCopyUtil.CopyLogger logger) {
        if (dir != null && dir.isDirectory()) {
            return false;
        }
        logger.log(label + "不存在: " + (dir != null ? dir.getPath() : "null"));
        return true;
    }

    /**
     * 执行 svn status 并收集 M/A/? 的 xlsx。
     */
    private static List<File> listChangedXlsx(final File svnRoot, final ViewFileCopyUtil.CopyLogger logger,
        final PullResult result) {
        final Set<File> ordered = new LinkedHashSet<>();
        boolean ok = forEachXlsxStatus(svnRoot, logger, (status, file) -> {
            if (status == 'C') {
                result.conflictSkipped++;
                logger.log("跳过冲突文件: " + file.getName());
                return true;
            }
            if (status == 'M' || status == 'A' || status == '?') {
                ordered.add(file.getAbsoluteFile());
            }
            return true;
        });
        if (!ok) {
            ordered.clear();
        }
        return new ArrayList<>(ordered);
    }

    /**
     * 遍历 svn status 中的 xlsx 行（单次进程，供状态表与拉变更复用）。
     *
     * @param svnRoot
     *            工作副本根
     * @param logger
     *            可为 null（静默）
     * @param consumer
     *            行回调
     * @return 进程成功返回 true
     */
    private static boolean forEachXlsxStatus(File svnRoot, ViewFileCopyUtil.CopyLogger logger,
        StatusLineConsumer consumer) {
        if (svnRoot == null || !svnRoot.isDirectory() || consumer == null) {
            return false;
        }
        Process process = null;
        BufferedReader reader = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("svn", "status", "--depth", "infinity");
            builder.directory(svnRoot);
            builder.redirectErrorStream(true);
            process = builder.start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() < SVN_PATH_COLUMN) {
                    continue;
                }
                char status = line.charAt(0);
                String relativePath = line.substring(SVN_PATH_COLUMN).trim();
                if (relativePath.isEmpty()) {
                    continue;
                }
                File file = new File(svnRoot, relativePath);
                if (!isXlsx(file)) {
                    continue;
                }
                if (!consumer.accept(status, file)) {
                    break;
                }
            }
            int code = process.waitFor();
            if (code != 0) {
                if (logger != null) {
                    logger.log("svn status 失败：exitCode=" + code);
                }
                return false;
            }
            return true;
        } catch (Exception e) {
            if (logger != null) {
                logger.log("svn status 异常: " + e.getMessage());
            }
            return false;
        } finally {
            closeQuietly(reader);
            destroyQuietly(process);
        }
    }

    private static boolean isXlsx(File file) {
        String name = file.getName().trim().toLowerCase();
        return name.endsWith(ExcelExtensions.FILE_EXT_XLSX);
    }

    private static List<File> filterByMtime(List<File> sources, File targetDir, ViewFileCopyUtil.CopyLogger logger,
        PullResult result) {
        List<File> toCopy = new ArrayList<>();
        for (File source : sources) {
            File dest = new File(targetDir, source.getName());
            if (dest.exists() && dest.lastModified() >= source.lastModified()) {
                result.skippedByMtime++;
                logger.log("已是最新，跳过: " + source.getName());
                continue;
            }
            toCopy.add(source);
        }
        return toCopy;
    }

    private static void logCopyPlan(List<File> toCopy, File targetDir, ViewFileCopyUtil.CopyLogger logger) {
        logger.log("待复制 " + toCopy.size() + " 个文件到: " + targetDir.getAbsolutePath());
        int index = 1;
        for (File source : toCopy) {
            logger.log(index + ". " + source.getAbsolutePath() + " -> " + source.getName());
            index++;
        }
    }

    private static void copyAll(List<File> toCopy, File targetDir, ViewFileCopyUtil.CopyLogger logger,
        PullResult result) {
        for (File source : toCopy) {
            String name = source.getName();
            try {
                ViewFileCopyUtil.copyIntoDirectory(source, name, targetDir, logger);
                result.copied++;
            } catch (Exception e) {
                result.failed++;
                logger.log("复制失败: " + name + " - " + e.getMessage());
            }
        }
    }

    private static void closeQuietly(BufferedReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (Exception ignored) {
        }
    }

    private static void destroyQuietly(Process process) {
        if (process != null) {
            process.destroy();
        }
    }
}

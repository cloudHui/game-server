package com.gamer.data.excel.diff.util;

import java.io.File;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import com.gamer.data.excel.shared.ExcelExtensions;

/**
 * 当前目录 xlsx / gd 复制到归档目录（带重试），供 Excel GD 视图「复制」按钮使用。
 */
public class ViewFileCopyUtil {

    /** 默认最大重试次数 */
    public static final int DEFAULT_MAX_RETRIES = 5;
    /** 默认首次重试间隔（毫秒） */
    public static final int DEFAULT_RETRY_DELAY_MS = 200;

    /**
     * 复制过程日志。
     */
    public interface CopyLogger {
        /**
         * @param message
         *            日志文案
         */
        void log(String message);
    }

    private ViewFileCopyUtil() {}

    /**
     * 列出目录下的 xlsx 与 .gd 文件。
     *
     * @param dir
     *            目录
     * @param gdSuffix
     *            gd 扩展名（如 {@code .gd}）
     * @return 文件数组，无匹配时可能为 null
     */
    public static File[] listExcelAndGdFiles(final File dir, final String gdSuffix) {
        if (dir == null) {
            return null;
        }
        return dir.listFiles(pathname -> {
            String name = pathname.getName();
            if (name.trim().isEmpty()) {
                return false;
            }
            String lower = name.trim().toLowerCase();
            return lower.endsWith(ExcelExtensions.FILE_EXT_XLSX) || lower.endsWith(gdSuffix);
        });
    }

    /**
     * 将当前目录内 xlsx 复制到 xml 归档目录、gd 复制到 gd 归档目录，成功后删除源文件。
     *
     * @param currDir
     *            当前工作目录
     * @param xmlPathDir
     *            XML_PATH
     * @param gdPathDir
     *            GD_PATH
     * @param gdSuffix
     *            gd 扩展名
     * @param logger
     *            日志
     * @return 处理的文件个数（含复制失败仍尝试删除的项）
     */
    public static int copyExcelGdFromCurrAndDelete(File currDir, File xmlPathDir, File gdPathDir, String gdSuffix,
        CopyLogger logger) {
        File[] files = listExcelAndGdFiles(currDir, gdSuffix);
        if (files == null) {
            return 0;
        }
        for (File file : files) {
            copyOneFileFromCurr(file, xmlPathDir, gdPathDir, gdSuffix, logger);
        }
        return files.length;
    }

    /**
     * 删除当前目录内全部 xlsx 与 .gd 文件（不递归子目录）。
     *
     * @param currDir
     *            当前工作目录
     * @param gdSuffix
     *            gd 扩展名（如 {@code .gd}）
     * @param logger
     *            日志
     * @return 成功删除的文件个数
     */
    public static int deleteAllExcelAndGdInDir(File currDir, String gdSuffix, CopyLogger logger) {
        File[] files = listExcelAndGdFiles(currDir, gdSuffix);
        if (files == null || files.length == 0) {
            return 0;
        }
        int deleted = 0;
        for (File file : files) {
            String name = file.getName();
            if (file.delete()) {
                logger.log("已删除: " + name);
                deleted++;
            } else {
                logger.log("删除失败: " + name);
            }
        }
        return deleted;
    }

    /**
     * 复制单个文件到对应归档目录并删除源文件。
     */
    private static void copyOneFileFromCurr(File source, File xmlPathDir, File gdPathDir, String gdSuffix,
        CopyLogger logger) {
        String name = source.getName();
        try {
            if (name.endsWith(ExcelExtensions.FILE_EXT_XLSX)) {
                copyIntoDirectory(source, name, xmlPathDir, logger);
            } else if (name.endsWith(gdSuffix)) {
                copyIntoDirectory(source, name, gdPathDir, logger);
            } else {
                return;
            }
        } catch (Exception e) {
            logger.log("复制文件失败: " + e.getMessage());
            return;
        }
        logger.log("删除文件: " + name + " 成功" + source.delete());
    }

    /**
     * 复制到目标目录下同名文件。
     *
     * @param source
     *            源文件
     * @param fileName
     *            目标文件名
     * @param targetDir
     *            目标目录
     * @param logger
     *            日志
     */
    public static void copyIntoDirectory(File source, String fileName, File targetDir, CopyLogger logger)
        throws Exception {
        if (targetDir == null || !targetDir.exists() || !targetDir.isDirectory()) {
            logger.log("目录不存在: " + (targetDir != null ? targetDir.getPath() : "null"));
            return;
        }
        File targetFile = new File(targetDir, fileName);
        copyWithRetry(source, targetFile, fileName, logger);
        logger.log("已复制文件到: " + targetFile.getPath());
    }

    /**
     * 带指数退避的文件复制（缓解 Windows 文件占用）。
     *
     * @param sourceFile
     *            源
     * @param targetFile
     *            目标
     * @param fileType
     *            日志用类型名
     * @param logger
     *            日志
     */
    public static void copyWithRetry(File sourceFile, File targetFile, String fileType, CopyLogger logger)
        throws Exception {
        int retryDelay = DEFAULT_RETRY_DELAY_MS;
        for (int attempt = 0; attempt < DEFAULT_MAX_RETRIES; attempt++) {
            try {
                tryDeleteTargetForReplace(targetFile, logger);
                Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (FileSystemException e) {
                if (attempt < DEFAULT_MAX_RETRIES - 1) {
                    logger.log(fileType + "复制失败（可能被占用），" + retryDelay + "ms后重试 (" + (attempt + 1) + "/"
                        + DEFAULT_MAX_RETRIES + ")...");
                    Thread.sleep(retryDelay);
                    retryDelay = retryDelay * 2;
                } else {
                    throw new Exception(
                        fileType + "复制失败: 文件可能被其他程序占用: " + targetFile.getAbsolutePath() + ", 错误: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 覆盖复制前尝试删除已存在的目标文件。
     */
    private static void tryDeleteTargetForReplace(File targetFile, CopyLogger logger) {
        if (targetFile.exists() && !targetFile.delete()) {
            logger.log("警告: 无法删除目标文件，可能被占用: " + targetFile.getPath());
        }
    }
}

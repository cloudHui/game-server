package utils.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

/**
 * 本地文件与目录递归扫描工具类。
 * <p>
 * 提供目录递归遍历、文件列表聚合与最后修改时间戳提取。
 * </p>
 *
 * @author cloud
 */
public class DirectoryScanner {

    /** 统一日志记录器 */
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryScanner.class);

    public DirectoryScanner() {
    }

    /**
     * 递归扫描指定目录下的所有常规文件，并将文件绝对路径存入结果 Map。
     *
     * @param directoryPath 待扫描的根目录绝对路径
     * @param path          结果输出字典，Key 为文件绝对路径，Value 默认存入 1
     */
    public static void listFilesInDirectory(String directoryPath, Map<String, Integer> path) {
        if (directoryPath == null || path == null) {
            LOGGER.warn("[listFilesInDirectory] 路径或输出容器为 null: directoryPath={}", directoryPath);
            return;
        }

        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            LOGGER.warn("[listFilesInDirectory] 目标路径不存在或不是目录: path='{}'", directoryPath);
            return;
        }

        long start = System.currentTimeMillis();
        int beforeSize = path.size();
        scanFilesInternal(directory, path);
        LOGGER.debug("[listFilesInDirectory] 扫描目录完成: path='{}', 找到文件数={}, 耗时={}ms",
                directoryPath, (path.size() - beforeSize), (System.currentTimeMillis() - start));
    }

    /**
     * 内部递归遍历文件树辅助方法。
     */
    private static void scanFilesInternal(File directory, Map<String, Integer> path) {
        File[] files = directory.listFiles();
        if (files == null) {
            LOGGER.trace("[scanFilesInternal] 目录为空或无权限访问: dir='{}'", directory.getAbsolutePath());
            return;
        }
        for (File file : files) {
            if (file.isFile()) {
                path.put(file.getAbsolutePath(), 1);
            } else if (file.isDirectory()) {
                scanFilesInternal(file, path);
            }
        }
    }

    /**
     * 递归扫描目录，收集文件名及最后修改时间戳（毫秒）。
     *
     * @param directoryPath 根目录路径
     * @param fileMap       输出字典，Key 为文件名，Value 为最后修改时间戳
     */
    public static void listFiles(String directoryPath, Map<String, Long> fileMap) {
        if (directoryPath == null || fileMap == null) {
            LOGGER.warn("[listFiles] 入参为空: directoryPath={}", directoryPath);
            return;
        }

        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            LOGGER.error("[listFiles] 目录不存在: path='{}'", directoryPath);
            throw new IllegalArgumentException("目录不存在或不是合法文件夹: " + directoryPath);
        }

        long start = System.currentTimeMillis();
        int beforeSize = fileMap.size();
        listFilesWithTimestampInternal(directory, fileMap);
        LOGGER.info("[listFiles] 扫描文件修改时间完成: path='{}', 文件数={}, 耗时={}ms",
                directoryPath, (fileMap.size() - beforeSize), (System.currentTimeMillis() - start));
    }

    /**
     * 内部递归收集文件及修改时间戳。
     */
    private static void listFilesWithTimestampInternal(File directory, Map<String, Long> fileMap) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile()) {
                fileMap.put(file.getName(), file.lastModified());
            } else if (file.isDirectory()) {
                listFilesWithTimestampInternal(file, fileMap);
            }
        }
    }
}

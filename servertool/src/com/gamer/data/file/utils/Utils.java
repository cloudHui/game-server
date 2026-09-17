package com.gamer.data.file.utils;

/**
 * 文件工具通用格式化与扩展名解析方法。
 */
public class Utils {

    /**
     * 格式化文件大小
     * 
     * @param size
     *            文件大小
     * @return 格式化后的文件大小
     */
    public static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 获取文件扩展名
     * 
     * @param filename
     *            文件名
     * @return 文件扩展名
     */
    public static String getFileExtension(String filename) {
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

}

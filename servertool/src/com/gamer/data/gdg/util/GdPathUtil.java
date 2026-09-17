package com.gamer.data.gdg.util;

import java.io.File;

import com.gamer.data.excel.framework.BaseCheck;

/**
 * 当前目录与上层 GD 目录下的 .gd 文件路径解析。
 */
public final class GdPathUtil {

    private GdPathUtil() {
    }

    /**
     * 当前目录下某 Sheet 的 .gd 路径。
     *
     * @param currDir
     *            当前工作目录
     * @param sheetName
     *            Sheet 名
     * @return gd 文件
     */
    public static File currGdFile(File currDir, String sheetName) {
        return new File(currDir, sheetName + BaseCheck.GD);
    }

    /**
     * 上层 GD 归档目录下某 Sheet 的 .gd 路径（平铺）。
     *
     * @param gdPathDir
     *            上层 GD 目录（GD_PATH）
     * @param sheetName
     *            Sheet 名
     * @return gd 文件
     */
    public static File archiveGdFile(File gdPathDir, String sheetName) {
        return new File(gdPathDir, sheetName + BaseCheck.GD);
    }

    /**
     * 当前目录下某 Sheet 的 .gd.tmp 路径。
     *
     * @param currDir
     *            工作目录
     * @param sheetName
     *            Sheet 名
     * @return 临时 gd 文件
     */
    public static File currGdTempFile(File currDir, String sheetName) {
        return new File(currDir, sheetName + BaseCheck.GD + ".tmp");
    }
}

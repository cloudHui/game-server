package com.gamer.data.gdg.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * xlsx GD 生成结果（是否成功及已写入的 Sheet 名列表）。
 */
public final class GdProcessResult {

    /** 是否成功 */
    private final boolean success;
    /** Excel 文件名 */
    private final String fileName;
    /** 阶段 2 成功写入的 Sheet 名（顺序与写入一致） */
    private final List<String> writtenSheetNames;

    /**
     * 构造。
     *
     * @param success
     *            是否成功
     * @param fileName
     *            文件名
     * @param writtenSheetNames
     *            已写入 Sheet 名
     */
    private GdProcessResult(boolean success, String fileName, List<String> writtenSheetNames) {
        this.success = success;
        this.fileName = fileName;
        this.writtenSheetNames = writtenSheetNames;
    }

    /**
     * 失败结果。
     *
     * @param fileName
     *            文件名
     * @return 失败结果
     */
    public static GdProcessResult fail(String fileName) {
        return new GdProcessResult(false, fileName, Collections.emptyList());
    }

    /**
     * 成功结果。
     *
     * @param fileName
     *            文件名
     * @param writtenSheetNames
     *            已写入 Sheet 名
     * @return 成功结果
     */
    public static GdProcessResult success(String fileName, List<String> writtenSheetNames) {
        List<String> names = writtenSheetNames != null ? writtenSheetNames : Collections.emptyList();
        return new GdProcessResult(true, fileName, Collections.unmodifiableList(new ArrayList<>(names)));
    }

    /**
     * 是否成功。
     *
     * @return 是否成功
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 文件名。
     *
     * @return 文件名
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * 已写入 Sheet 名列表。
     *
     * @return Sheet 名列表
     */
    public List<String> getWrittenSheetNames() {
        return writtenSheetNames;
    }
}

package com.gamer.data.gdg.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 并行 GD 批量处理结果（校验 + 写入 + 回滚范围）。
 */
public class GdBatchResult {

    private final boolean success;
    private final String fileName;
    private final List<String> writtenSheetNames;

    /** 本次任务涉及的合法 Sheet（用于 R-窄 回滚删除） */

    private GdBatchResult(boolean success, String fileName, List<String> writtenSheetNames) {
        this.success = success;
        this.fileName = fileName;
        this.writtenSheetNames = writtenSheetNames;
    }

    public static GdBatchResult fail(String fileName) {
        return new GdBatchResult(false, fileName, Collections.emptyList());
    }

    public static GdBatchResult success(String fileName, List<String> writtenSheetNames) {
        return new GdBatchResult(true, fileName, copyNames(writtenSheetNames));
    }

    private static List<String> copyNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(names));
    }

    public boolean isSuccess() {
        return success;
    }

    public String getFileName() {
        return fileName;
    }

    public List<String> getWrittenSheetNames() {
        return writtenSheetNames;
    }
}

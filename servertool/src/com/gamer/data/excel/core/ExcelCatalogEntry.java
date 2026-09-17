package com.gamer.data.excel.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 目录扫描结果：单个 Excel 文件及其合法 Sheet 名。
 */
public class ExcelCatalogEntry {

    /** 文件名 */
    public final String fileName;

    /** 源文件 */
    public final File sourceFile;

    /** 合法 Sheet 名（顺序与 Excel 一致） */
    public final List<String> sheetNames;

    /**
     * @param fileName
     *            文件名
     * @param sourceFile
     *            源文件
     * @param sheetNames
     *            Sheet 名列表
     */
    public ExcelCatalogEntry(String fileName, File sourceFile, List<String> sheetNames) {
        this.fileName = fileName;
        this.sourceFile = sourceFile;
        if (sheetNames == null) {
            this.sheetNames = Collections.emptyList();
        } else {
            this.sheetNames = Collections.unmodifiableList(new ArrayList<>(sheetNames));
        }
    }
}

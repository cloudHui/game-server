package com.gamer.data.excel.shared;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文件与Sheet 列表的数据类
 */
public class FileWithSheets {
    public String excelName;

    /** 磁盘上的源文件（模型生成懒加载用） */
    public File sourceFile;

    // sheets是map 存储sheet名和sheet内容的列表
    public Map<String, List<Title>> sheets = new HashMap<>();

    /** 已展开并完成 string 数组推断的 Sheet 名 */
    private final Set<String> structureRefinedSheets = new HashSet<>();

    /**
     * 指定 Sheet 是否已在展开时完成列加载与 string 类型推断。
     *
     * @param sheetName
     *            Sheet 名
     * @return 是否已就绪
     */
    public boolean isStructureRefined(String sheetName) {
        return sheetName != null && structureRefinedSheets.contains(sheetName);
    }

    /**
     * 标记 Sheet 列定义与 string 推断已完成。
     *
     * @param sheetName
     *            Sheet 名
     */
    public void markStructureRefined(String sheetName) {
        if (sheetName != null) {
            structureRefinedSheets.add(sheetName);
        }
    }

    /** 清掉已展开的列结构，下次展开重新读 Excel。 */
    public void clearStructure() {
        structureRefinedSheets.clear();
        for (List<Title> titles : sheets.values()) {
            if (titles != null) {
                titles.clear();
            }
        }
    }

    public FileWithSheets(String excelName) {
        this.excelName = excelName;
    }

    /**
     * 获取sheet 内容
     */
    public List<Title> getSheet(String sheetName) {
        return sheets.computeIfAbsent(sheetName, k -> new ArrayList<>());
    }

    /**
     * 添加sheet 内容
     */
    public void addSheet(String sheetName, Title title) {
        getSheet(sheetName).add(title);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<Title>> entry : sheets.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return "\nFileWithSheets{" + "fileName='" + excelName + '\n' + ", sheets=" + sb + '}';
    }
}

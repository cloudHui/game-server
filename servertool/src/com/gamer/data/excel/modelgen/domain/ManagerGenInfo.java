package com.gamer.data.excel.modelgen.domain;

import java.util.List;

import com.gamer.data.excel.shared.Title;

/**
 * 待批量生成的 Manager 信息（一个子 sheet 一条）
 */
public class ManagerGenInfo {
    private final String configClassName;
    private final String packagePath;
    private final String sheetName;
    private final List<Title> titleList;

    public ManagerGenInfo(String configClassName, String packagePath, String sheetName, List<Title> titleList) {
        this.configClassName = configClassName;
        this.packagePath = packagePath;
        this.sheetName = sheetName;
        this.titleList = titleList;
    }

    public String getConfigClassName() {
        return configClassName;
    }

    public String getPackagePath() {
        return packagePath;
    }

    public String getSheetName() {
        return sheetName;
    }

    public List<Title> getTitleList() {
        return titleList;
    }
}

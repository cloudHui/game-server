package com.gamer.data.excel.modelgen.domain;

/**
 * Manager 选择项（每个 manager 的选择状态）
 */
public class ManagerSelectionItem {
    private final ManagerGenInfo info;
    private boolean generateManager;
    private boolean addToConfig;

    public ManagerSelectionItem(ManagerGenInfo info) {
        this.info = info;
        this.generateManager = true;
        this.addToConfig = true;
    }

    public ManagerGenInfo getInfo() {
        return info;
    }

    public boolean isGenerateManager() {
        return generateManager;
    }

    public void setGenerateManager(boolean generateManager) {
        this.generateManager = generateManager;
    }

    public boolean isAddToConfig() {
        return addToConfig;
    }

    public void setAddToConfig(boolean addToConfig) {
        this.addToConfig = addToConfig;
    }
}
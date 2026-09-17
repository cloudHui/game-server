package com.gamer.data.excel.entry;

import java.io.File;

import javax.swing.JFrame;

import com.gamer.data.excel.framework.BaseCheck;
import com.gamer.data.excel.browse.ExcelViewer;
/**
 * 程序侧 CodeTool 入口（默认打开模型代码生成）。
 *
 * @author liuyunhui
 * @date 2025/12/05
 */
public class DataBuilderCheck extends BaseCheck {
    public DataBuilderCheck() {}

    /**
     * 初始化配置
     *
     * @param gdPath
     *            游戏数据目录
     * @param xmlPath
     *            配置Excel 目录
     * @param moreDeep
     *            是否更深层次的检查
     */
    @Override
    public void initConfig(String gdPath, String xmlPath, boolean moreDeep) {
        CURR_DIR = new File(System.getProperty("user.dir"));
        baseDir = CURR_DIR.getParentFile();
        SERVER_PATH = baseDir.getPath();
        if (moreDeep) {
            // 回退3层
            baseDir = baseDir.getParentFile().getParentFile().getParentFile();
            // 退3层进一层server目录
            SERVER_PATH = new File(baseDir, "server").getPath();
        } else{
            baseDir = baseDir.getParentFile();
        }

        GD_PATH = new File(baseDir, gdPath);
        XML_PATH = new File(baseDir, xmlPath);
    }

    @Override
    protected JFrame createViewer() {
        ExcelViewer viewer = new ExcelViewer(this);
        viewer.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        return viewer;
    }

    /**
     * @return CodeTool
     */
    @Override
    protected String toolName() {
        return "CodeTool";
    }
}
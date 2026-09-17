package com.gamer.data.excel.framework;

import java.io.File;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.gamer.data.ui.ViewUi;

/**
 * 基础检查类，提供通用的文件加载和界面显示功能
 *
 * @author liuyunhui
 * @date 2025/12/05
 */
public abstract class BaseCheck {

    public static final String GD = ".gd";

    public File GD_PATH;
    public File XML_PATH;
    public File CURR_DIR;
    public String SERVER_PATH;
    public boolean MORE_DEEP;

    /**
     * 基础目录
     */
    public File baseDir;

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
    public void initConfig(String gdPath, String xmlPath, boolean moreDeep) {
        CURR_DIR = new File(System.getProperty("user.dir"));
        baseDir = CURR_DIR.getParentFile();
        SERVER_PATH = baseDir.getPath();
        baseDir = baseDir.getParentFile();
        MORE_DEEP = moreDeep;
        if (moreDeep) {
            SERVER_PATH = baseDir.getPath();
        }
        if (moreDeep) {
            baseDir = baseDir.getParentFile();
        }

        GD_PATH = new File(baseDir, gdPath);
        XML_PATH = new File(baseDir, xmlPath);
    }

    /**
     * 创建查看器实例（由子类实现）
     * 
     * @return 查看器实例
     */
    protected abstract JFrame createViewer();

    /**
     * 工具名，用于启动失败对话框。
     *
     * @return 工具名
     */
    protected String toolName() {
        return "StrategyTool";
    }

    /**
     * 查看数据构建检查
     * 
     * @param gdPath
     *            游戏数据目录
     * @param xmlPath
     *            配置Excel 目录
     */
    public void view(String gdPath, String xmlPath, boolean moreDeep) {
        initConfig(gdPath, xmlPath, moreDeep);
        showAsync();
    }

    /**
     * 异步显示查看器
     */
    public void showAsync() {
        // 先创建查看器并显示
        SwingUtilities.invokeLater(() -> {
            try {
                ViewUi.installLook();
                JFrame viewer = createViewer();
                viewer.setVisible(true);
            } catch (Throwable t) {
                t.printStackTrace();
                JOptionPane.showMessageDialog(null,
                    "界面初始化失败:\n" + t.getMessage(),
                    toolName() + " 启动错误",
                    JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}

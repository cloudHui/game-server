package com.gamer.data.limit;

import java.io.File;

import com.gamer.data.message.Util;

/**
 * limit 目录与 CodeTool 目录路径解析。
 */
public class LimitPathUtil {

    /** CodeTool server 相对项目根路径 */
    private static final String SERVER_REL = "Common/Tools/Bin/server";

    /** limit 相对项目根路径 */
    private static final String LIMIT_REL = SERVER_REL + "/limit";

    /**
     * 禁止实例化。
     */
    private LimitPathUtil() {}

    /**
     * 从 baseDir 向上查找项目根（存在 Common/Tools/Bin/server 的目录）。
     *
     * @param baseDir
     *            BaseCheck 初始化后的 baseDir
     * @return 项目根目录；找不到返回 null
     */
    public static File resolveProjectRoot(File baseDir) {
        // baseDir 为空直接失败
        if (baseDir == null) {
            return null;
        }
        // 从 baseDir 向上逐级探测
        File current = baseDir;
        while (current != null) {
            // 拼接 server 标记目录
            File serverDir = new File(current, SERVER_REL);
            // 找到则 current 即项目根
            if (serverDir.isDirectory()) {
                return current;
            }
            // 向上一级
            current = current.getParentFile();
        }
        // 未找到
        return null;
    }

    /**
     * 解析 limit 读取目录。
     *
     * @param baseDir
     *            BaseCheck.baseDir
     * @return limit 目录；解析失败返回 null
     */
    public static File resolveLimitDir(File baseDir) {
        // 先找项目根
        File root = resolveProjectRoot(baseDir);
        // 失败返回 null
        if (root == null) {
            return null;
        }
        // 拼接 limit 路径
        return new File(root, LIMIT_REL);
    }

    /**
     * 解析 CodeTool server 目录（SVN update 目标）。
     *
     * @param baseDir
     *            BaseCheck.baseDir
     * @return server 目录；解析失败返回 null
     */
    public static File resolveCodeToolServerDir(File baseDir) {
        // 先找项目根
        File root = resolveProjectRoot(baseDir);
        // 失败返回 null
        if (root == null) {
            return null;
        }
        // 返回 server 目录
        return new File(root, SERVER_REL);
    }

    /**
     * 解析 limit 写入目录（CodeTool 打包运行时写 jar 旁 limit/）。
     *
     * @param currDir
     *            当前 user.dir
     * @param baseDir
     *            BaseCheck.baseDir
     * @return limit 写入目录
     */
    public static File resolveLimitWriteDir(File currDir, File baseDir) {
        // jar 运行时直接写 user.dir/limit
        if (Util.inVM() && currDir != null) {
            return new File(currDir, "limit");
        }
        // IDE 调试时走项目根下 limit
        return resolveLimitDir(baseDir);
    }

    /**
     * 按 StrategyTool 规则从 user.dir 推算 baseDir（parent.parent）。
     *
     * @return StrategyTool 对应的 baseDir
     */
    public static File resolveStrategyToolBaseDirFromCurrDir() {
        // 当前工作目录
        File currDir = new File(System.getProperty("user.dir"));
        // 无父目录则返回自身
        if (currDir.getParentFile() == null) {
            return currDir;
        }
        // 仅一层父目录
        if (currDir.getParentFile().getParentFile() == null) {
            return currDir.getParentFile();
        }
        // 标准 StrategyTool：上退两层
        return currDir.getParentFile().getParentFile();
    }

    /**
     * DataBuilder 等无 BaseCheck 场景的 limit 目录。
     *
     * @return limit 目录
     */
    public static File resolveLimitDirFromCurrDir() {
        // 先算 StrategyTool baseDir 再解析 limit
        return resolveLimitDir(resolveStrategyToolBaseDirFromCurrDir());
    }

    /**
     * Excel 文件名去后缀，用于 limit 文件名。
     *
     * @param excelFileName
     *            Excel 文件名（可含后缀）
     * @return 不含后缀的基础名
     */
    public static String toExcelBaseName(String excelFileName) {
        // 空名返回空串
        if (excelFileName == null) {
            return "";
        }
        // 找最后一个点
        int dot = excelFileName.lastIndexOf('.');
        // 有有效后缀则截断
        if (dot > 0) {
            return excelFileName.substring(0, dot);
        }
        // 无后缀原样返回
        return excelFileName;
    }
}

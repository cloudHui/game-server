package com.gamer.data.file.tree;

import java.io.File;
import java.util.Map;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/**
 * 「根目录」视图重置：全收目录树，定位到当前工作目录所属的已添加根。
 */
public final class Reset {

    /** 搜索范围读写，由 WindowsTools 注入。 */
    public interface Scope {
        /**
         * @return 当前搜索范围目录，无则 null
         */
        File getScope();

        /**
         * @param directory
         *            新的搜索根目录
         */
        void setScope(File directory);

        /** 清空搜索范围与结果。 */
        void clearScope();
    }

    private Reset() {}

    /**
     * 执行重置：全收 → 展开虚拟根 → 选中已添加根或仅展示根列表。
     *
     * @param tree
     *            目录树
     * @param rootNode
     *            虚拟根
     * @param pathNodes
     *            已添加根节点映射（绝对路径 → 节点）
     * @param scope
     *            搜索范围操作
     * @param treeSelected
     *            树当前选中项（文件或目录），无则 null
     */
    public static void apply(JTree tree, DefaultMutableTreeNode rootNode, Map<String, DefaultMutableTreeNode> pathNodes,
        Scope scope, File treeSelected) {
        File contextDir = resolveDir(scope.getScope(), treeSelected);
        File addedRoot = findAddedRoot(contextDir, pathNodes);

        Collapse.collapseAll(tree, rootNode);
        Collapse.expandVirtualRoot(tree, rootNode);

        if (addedRoot != null) {
            DefaultMutableTreeNode node = pathNodes.get(addedRoot.getAbsolutePath());
            if (node != null) {
                TreePath path = new TreePath(node.getPath());
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
                // 显式更新 scope，选中路径未变时 listener 不会清搜索
                scope.setScope(addedRoot);
                return;
            }
        }
        tree.clearSelection();
        scope.clearScope();
    }

    /**
     * 解析当前工作目录：搜索范围优先，否则取树选中（文件用父目录）。
     *
     * @param scopeDir
     *            搜索范围
     * @param treeSelected
     *            树选中项
     * @return 目录，无法解析时 null
     */
    static File resolveDir(File scopeDir, File treeSelected) {
        if (scopeDir != null && scopeDir.isDirectory()) {
            return scopeDir;
        }
        return toDir(treeSelected);
    }

    /**
     * 从目录向上查找已添加根（pathNodes 中的顶层挂载点）。
     *
     * @param dir
     *            起始目录
     * @param pathNodes
     *            已添加根映射
     * @return 所属已添加根，找不到 null
     */
    public static File findAddedRoot(File dir, Map<String, DefaultMutableTreeNode> pathNodes) {
        File current = dir;
        while (current != null) {
            if (pathNodes.containsKey(current.getAbsolutePath())) {
                return current;
            }
            current = current.getParentFile();
        }
        return null;
    }

    /**
     * 将文件或目录转为目录上下文。
     *
     * @param file
     *            树选中项
     * @return 目录，无法转换时 null
     */
    private static File toDir(File file) {
        if (file == null) {
            return null;
        }
        if (file.isDirectory()) {
            return file;
        }
        return file.getParentFile();
    }
}

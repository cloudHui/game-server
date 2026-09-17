package com.gamer.data.file.tree;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/**
 * 目录树展开/收起工具：统一收集展开路径与 collapse 逻辑，供主窗口与重置复用。
 */
public final class Collapse {

    private Collapse() {}

    /**
     * 复制当前已展开路径快照。
     *
     * @param tree
     *            目录树
     * @param rootNode
     *            虚拟根节点
     * @return 展开路径列表
     */
    static List<TreePath> collectExpanded(JTree tree, DefaultMutableTreeNode rootNode) {
        List<TreePath> expandedPaths = new ArrayList<>();
        Enumeration<TreePath> expanded = tree.getExpandedDescendants(new TreePath(rootNode.getPath()));
        while (expanded != null && expanded.hasMoreElements()) {
            expandedPaths.add(expanded.nextElement());
        }
        return expandedPaths;
    }

    /**
     * 收起除虚拟根以外的全部展开节点（深路径优先 collapse）。
     *
     * @param tree
     *            目录树
     * @param rootNode
     *            虚拟根节点
     */
    public static void collapseAll(JTree tree, DefaultMutableTreeNode rootNode) {
        List<TreePath> expandedPaths = collectExpanded(tree, rootNode);
        // 深路径先收，避免父节点先收导致遗漏
        expandedPaths.sort((left, right) -> right.getPathCount() - left.getPathCount());
        for (TreePath path : expandedPaths) {
            if (path.getPathCount() > 1) {
                tree.collapsePath(path);
            }
        }
    }

    /**
     * 收起与 retainedPath 无关的分支，保留当前路径及其祖先、子孙展开态。
     *
     * @param tree
     *            目录树
     * @param rootNode
     *            虚拟根节点
     * @param retainedPath
     *            需要保留的分支路径
     */
    public static void collapseOthers(JTree tree, DefaultMutableTreeNode rootNode, TreePath retainedPath) {
        if (retainedPath == null || rootNode == null) {
            return;
        }
        List<TreePath> expandedPaths = collectExpanded(tree, rootNode);
        for (TreePath path : expandedPaths) {
            if (path == null || path.getPathCount() == 1) {
                continue;
            }
            if (retainedPath.equals(path) || retainedPath.isDescendant(path) || path.isDescendant(retainedPath)) {
                continue;
            }
            tree.collapsePath(path);
        }
    }

    /**
     * 展开虚拟根，使已添加根目录可见。
     *
     * @param tree
     *            目录树
     * @param rootNode
     *            虚拟根节点
     */
    public static void expandVirtualRoot(JTree tree, DefaultMutableTreeNode rootNode) {
        tree.expandPath(new TreePath(rootNode.getPath()));
    }
}

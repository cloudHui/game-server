package com.gamer.data.excel.modelgen.view.tree;

import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/**
 * 复选框树节点选择监听器，支持勾选变更回调。
 */
public class TreeCheckListener extends MouseAdapter {

    /** 树组件 */
    private final JTree tree;

    /** 勾选变更回调（可为 null） */
    private final Runnable onCheckChanged;

    /**
     * @param tree 树
     * @param onCheckChanged 勾选变更后回调
     */
    public TreeCheckListener(JTree tree, Runnable onCheckChanged) {
        this.tree = tree;
        this.onCheckChanged = onCheckChanged;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        int x = e.getX();
        int y = e.getY();
        int row = tree.getRowForLocation(x, y);
        if (row < 0) {
            return;
        }
        TreePath path = tree.getPathForRow(row);
        if (path == null) {
            return;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObject = node.getUserObject();
        Rectangle rect = tree.getRowBounds(row);
        if (rect == null || x > rect.x + 20) {
            return;
        }
        if (toggleSheetLineNode(node, userObject)) {
            notifyCheckChanged();
            return;
        }
        if (toggleSheetNode(node, userObject, path)) {
            notifyCheckChanged();
        }
    }

    /**
     * 切换 Title 行节点勾选。
     */
    private boolean toggleSheetLineNode(DefaultMutableTreeNode node, Object userObject) {
        if (!(userObject instanceof SheetLineNode)) {
            return false;
        }
        SheetLineNode data = (SheetLineNode) userObject;
        data.selected = !data.selected;
        if (data.selected) {
            setParentSelected(node, true);
        } else {
            uncheckParentIfAllSiblingsUnchecked(node);
        }
        tree.repaint();
        return true;
    }

    /**
     * 切换 Sheet/文件节点勾选并联动子节点。
     */
    private boolean toggleSheetNode(DefaultMutableTreeNode node, Object userObject, TreePath path) {
        if (!(userObject instanceof SheetNode)) {
            return false;
        }
        SheetNode data = (SheetNode) userObject;
        data.selected = !data.selected;
        syncChildSelection(node, data.selected);
        if (data.selected) {
            setParentSelected(node, true);
        } else {
            uncheckParentIfAllSiblingsUnchecked(node);
        }
        tree.expandPath(path);
        tree.repaint();
        return true;
    }

    /**
     * 联动子节点勾选状态。
     */
    private void syncChildSelection(DefaultMutableTreeNode node, boolean selected) {
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            if (child.getUserObject() instanceof SheetNode) {
                ((SheetNode) child.getUserObject()).selected = selected;
            }
            for (int j = 0; j < child.getChildCount(); j++) {
                DefaultMutableTreeNode lineNode = (DefaultMutableTreeNode) child.getChildAt(j);
                if (lineNode.getUserObject() instanceof SheetLineNode) {
                    ((SheetLineNode) lineNode.getUserObject()).selected = selected;
                }
            }
        }
    }

    /**
     * 通知勾选变更。
     */
    private void notifyCheckChanged() {
        if (onCheckChanged != null) {
            onCheckChanged.run();
        }
    }

    private void setParentSelected(DefaultMutableTreeNode node, boolean selected) {
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
        if (parent == null || !(parent.getUserObject() instanceof SheetNode)) {
            return;
        }
        SheetNode parentData = (SheetNode) parent.getUserObject();
        parentData.selected = selected;
        setParentSelected(parent, selected);
    }

    private void uncheckParentIfAllSiblingsUnchecked(DefaultMutableTreeNode node) {
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
        if (parent == null || !(parent.getUserObject() instanceof SheetNode)) {
            return;
        }
        boolean allSiblingsUnselected = true;
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode sibling = (DefaultMutableTreeNode) parent.getChildAt(i);
            Object uo = sibling.getUserObject();
            if (uo instanceof SheetNode && ((SheetNode) uo).selected) {
                allSiblingsUnselected = false;
                break;
            }
        }
        if (allSiblingsUnselected) {
            ((SheetNode) parent.getUserObject()).selected = false;
            uncheckParentIfAllSiblingsUnchecked(parent);
        }
    }
}

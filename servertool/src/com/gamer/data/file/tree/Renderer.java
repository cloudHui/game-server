package com.gamer.data.file.tree;

import java.awt.Color;
import java.awt.Component;
import java.io.File;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

import com.gamer.data.ui.ViewUi;

/**
 * 目录树节点渲染：文件/文件夹使用系统图标与显示名。
 */
public class Renderer extends DefaultTreeCellRenderer {

    /** 系统文件视图，用于图标与名称 */
    private final FileSystemView fsv = FileSystemView.getFileSystemView();

    /** 根节点「我的目录」图标，避免每次绘制 new File(user.home) */
    private final Icon homeIcon = fsv.getSystemIcon(new File(System.getProperty("user.home")));

    /**
     * 与其它页签统一选中色。
     */
    public Renderer() {
        setBackgroundNonSelectionColor(Color.WHITE);
        setBackgroundSelectionColor(ViewUi.SELECT);
        setTextNonSelectionColor(ViewUi.TITLE);
        setTextSelectionColor(ViewUi.TITLE);
        setBorderSelectionColor(ViewUi.SELECT);
    }

    /**
     * 渲染树节点图标与文本。
     *
     * @return 渲染后的单元格组件
     */
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
        boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        if (!(value instanceof DefaultMutableTreeNode)) {
            return this;
        }
        Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
        if (userObject instanceof File) {
            File file = (File) userObject;
            setIcon(fsv.getSystemIcon(file));
            setText(fsv.getSystemDisplayName(file));
        } else if (userObject instanceof String) {
            setIcon(homeIcon);
        }
        return this;
    }
}

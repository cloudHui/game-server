package com.gamer.data.excel.modelgen.view.tree;

import java.awt.BorderLayout;
import java.awt.Component;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;

import com.gamer.data.ui.ViewUi;

/**
 * 复选框树单元格渲染器
 */
public class TreeCheckRenderer extends JPanel implements TreeCellRenderer {
    private final JCheckBox checkBox;
    private final JLabel label;

    public TreeCheckRenderer() {
        setLayout(new BorderLayout());
        checkBox = new JCheckBox();
        checkBox.setOpaque(false);
        label = new JLabel();
        label.setFont(ViewUi.FONT);
        add(checkBox, BorderLayout.WEST);
        add(label, BorderLayout.CENTER);
        setOpaque(false);
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
        boolean leaf, int row, boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObject = node.getUserObject();

            if (userObject instanceof SheetPlaceholder) {
                checkBox.setSelected(false);
                checkBox.setVisible(false);
                label.setText("...");
                label.setIcon(UIManager.getIcon("Tree.closedIcon"));
            } else if (userObject instanceof SheetLineNode) {
                checkBox.setVisible(true);
                SheetLineNode line = (SheetLineNode) userObject;
                checkBox.setSelected(line.selected);
                label.setText(line.toString());
                label.setIcon(UIManager.getIcon("Tree.leafIcon"));
            } else if (userObject instanceof SheetNode) {
                checkBox.setVisible(true);
                SheetNode data = (SheetNode) userObject;
                checkBox.setSelected(data.selected);
                label.setText(data.name);
                if (data.fileWithSheets != null) {
                    label.setIcon(UIManager.getIcon("FileView.fileIcon"));
                } else {
                    label.setIcon(UIManager.getIcon("Tree.closedIcon"));
                }
            } else {
                checkBox.setSelected(false);
                label.setText(userObject.toString());
                label.setIcon(UIManager.getIcon("Tree.openIcon"));
            }
        }
        if (selected) {
            setOpaque(true);
            setBackground(ViewUi.SELECT);
        } else {
            setOpaque(false);
        }

        return this;
    }
}

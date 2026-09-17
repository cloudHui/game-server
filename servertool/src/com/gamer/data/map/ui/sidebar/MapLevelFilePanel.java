package com.gamer.data.map.ui.sidebar;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;

import com.gamer.data.ui.ViewUi;

/**
 * 关卡文件列表（Map_Chapter*_Level.txt）。
 */
public final class MapLevelFilePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> fileList = new JList<>(listModel);
    private final JLabel countLabel = new JLabel("总计: 0个文件");
    private boolean suppressSelectionEvent;

    /**
     * @param listener
     *            选中回调
     */
    public MapLevelFilePanel(final MapLevelFileListListener listener) {
        setLayout(new BorderLayout(5, 5));
        ViewUi.titled(this, "关卡文件 (Map_Chapter*_Level.txt)");
        setOpaque(true);
        setBackground(ViewUi.CARD);

        JPanel countPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        countPanel.setOpaque(false);
        countPanel.add(ViewUi.label(countLabel));
        add(countPanel, BorderLayout.NORTH);

        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ViewUi.list(fileList);
        fileList.addListSelectionListener(e -> {
            if (suppressSelectionEvent || e.getValueIsAdjusting()) {
                return;
            }
            int idx = fileList.getSelectedIndex();
            if (idx >= 0 && idx < listModel.size()) {
                listener.onLevelFileSelected(listModel.getElementAt(idx));
            }
        });
        add(ViewUi.scroll(fileList), BorderLayout.CENTER);
    }

    /**
     * 填充列表并更新计数。
     */
    public void setFiles(java.util.List<String> fileNames) {
        listModel.clear();
        for (String name : fileNames) {
            listModel.addElement(name);
        }
        countLabel.setText("总计: " + listModel.getSize() + "个文件");
    }

    /**
     * 同步选中关卡文件（不触发回调）。
     *
     * @param fileName 关卡文件名
     */
    public void selectFile(String fileName) {
        if (fileName == null) {
            return;
        }
        for (int i = 0; i < listModel.size(); i++) {
            if (fileName.equals(listModel.getElementAt(i))) {
                suppressSelectionEvent = true;
                try {
                    fileList.setSelectedIndex(i);
                } finally {
                    suppressSelectionEvent = false;
                }
                return;
            }
        }
    }

    /**
     * 关卡文件选中回调。
     */
    public interface MapLevelFileListListener {

        void onLevelFileSelected(String fileName);
    }
}

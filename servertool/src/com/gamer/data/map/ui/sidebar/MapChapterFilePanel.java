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
 * 地图章节文件列表（Map_Chapter*.txt）。
 */
public final class MapChapterFilePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> fileList = new JList<>(listModel);
    private final JLabel countLabel = new JLabel("总计: 0个文件");

    /**
     * @param toolControlPanel
     *            工具栏，挂在列表下方
     * @param listener
     *            选中回调
     */
    public MapChapterFilePanel(MapToolControlPanel toolControlPanel, final MapFileListListener listener) {
        setLayout(new BorderLayout(5, 5));
        ViewUi.titled(this, "地图文件 (Map_Chapter*.txt)");
        setOpaque(true);
        setBackground(ViewUi.CARD);

        JPanel countPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        countPanel.setOpaque(false);
        countPanel.add(ViewUi.label(countLabel));
        add(countPanel, BorderLayout.NORTH);

        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ViewUi.list(fileList);
        fileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = fileList.getSelectedIndex();
                if (idx >= 0 && idx < listModel.size()) {
                    listener.onMapFileSelected(listModel.getElementAt(idx));
                }
            }
        });
        add(ViewUi.scroll(fileList), BorderLayout.CENTER);
        add(toolControlPanel, BorderLayout.SOUTH);
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
        if (!listModel.isEmpty() && fileList.getSelectedIndex() < 0) {
            fileList.setSelectedIndex(0);
        }
    }

    /**
     * 地图文件选中回调。
     */
    public interface MapFileListListener {

        void onMapFileSelected(String fileName);
    }
}

package com.gamer.data.excel.diff.ui.filetable;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import com.gamer.data.excel.ui.ViewFileDnDUtil;
import com.gamer.data.excel.ui.ViewSubstringFilterList;
import com.gamer.data.ui.ViewUi;
import com.gamer.data.gdg.excel.ExcelOperate;

/**
 * 上层 / 当前目录 Excel 双表格台账：筛选、拖放、双击打开。
 */
public final class DiffFileTablesModule {

    private static final String CARD_FILE_LIST = "FILE_LIST";
    private static final String CARD_NO_FILE = "NO_FILE";

    private JPanel parentPanel;
    private JPanel currPanel;
    private JTable parentTable;
    private JTable currTable;
    private DiffFileTableModel parentModel;
    private DiffFileTableModel currModel;

    private final ViewSubstringFilterList parentFilter = new ViewSubstringFilterList();
    private final Map<String, File> parentFileMap = new HashMap<>();
    private final List<String> parentAllNames = new ArrayList<>();
    private final Map<String, String> parentSvnMap = new HashMap<>();
    private final DefaultListModel<String> currNameModel;
    private final Map<String, ExcelOperate> fileMap;
    private final File currDir;

    public DiffFileTablesModule(DefaultListModel<String> currNameModel, Map<String, ExcelOperate> fileMap,
        File currDir) {
        this.currNameModel = currNameModel;
        this.fileMap = fileMap;
        this.currDir = currDir;
    }

    /**
     * @param openParent
     *            双击上层打开
     * @param openCurr
     *            双击当前打开
     * @param onDrop
     *            拖入当前目录
     * @param dropErrorLog
     *            拖放错误日志
     * @param currMenuFactory
     *            当前表右键菜单，可为 null
     */
    public JComponent build(Consumer<String> openParent, Consumer<String> openCurr, Consumer<File> onDrop,
        ViewFileDnDUtil.DropErrorCallback dropErrorLog, BiConsumer<JPopupMenu, String> currMenuFactory) {
        currPanel = buildCurrPanel(openCurr, onDrop, dropErrorLog, currMenuFactory);
        return ViewUi.splitH(buildParentPanel(openParent), currPanel, 0.55);
    }

    public Map<String, File> getParentFileMap() {
        return parentFileMap;
    }

    public void applyParentScan(Map<String, File> files, Map<String, String> svnMap) {
        parentFileMap.clear();
        parentAllNames.clear();
        parentSvnMap.clear();
        if (files != null) {
            parentFileMap.putAll(files);
            parentAllNames.addAll(files.keySet());
            Collections.sort(parentAllNames);
        }
        if (svnMap != null) {
            parentSvnMap.putAll(svnMap);
        }
        parentFilter.resetFilter();
        refreshParentTable();
        showParentCard();
    }

    public void refreshParentTable() {
        if (parentModel == null) {
            return;
        }
        List<String> filtered = parentFilter.filterNames(parentAllNames);
        List<DiffFileTableModel.Row> rows = new ArrayList<>(filtered.size());
        for (String name : filtered) {
            rows.add(DiffFileTableModel.createRow(name, parentFileMap.get(name), parentSvnMap.get(name)));
        }
        parentModel.setRows(rows);
        parentFilter.syncFilterBar();
    }

    public void rebuildCurrTable() {
        if (currModel == null || currNameModel == null) {
            return;
        }
        List<DiffFileTableModel.Row> rows = new ArrayList<>(currNameModel.size());
        for (int i = 0; i < currNameModel.size(); i++) {
            String name = currNameModel.getElementAt(i);
            ExcelOperate eo = fileMap.get(name);
            File file = eo != null ? eo.file : new File(currDir, name);
            rows.add(DiffFileTableModel.createRow(name, file, null));
        }
        currModel.setRows(rows);
    }

    public void clearParent() {
        parentFileMap.clear();
        parentAllNames.clear();
        parentSvnMap.clear();
        parentFilter.resetFilter();
        if (parentModel != null) {
            parentModel.clear();
        }
        if (parentTable != null) {
            parentTable.clearSelection();
        }
        showParentCard();
    }

    public void clearCurrSelection() {
        if (currTable != null) {
            currTable.clearSelection();
        }
        if (currModel != null) {
            currModel.clear();
        }
    }

    public void showCurrCard() {
        showCard(currPanel, currNameModel != null && !currNameModel.isEmpty() ? CARD_FILE_LIST : CARD_NO_FILE);
    }

    public String getSelectedCurrFileName() {
        if (currTable == null || currModel == null) {
            return null;
        }
        int row = currTable.getSelectedRow();
        return row < 0 ? null : currModel.getFileName(row);
    }

    private JPanel buildParentPanel(Consumer<String> openParent) {
        parentPanel = newCardPanel("上层目录");
        parentModel = new DiffFileTableModel(true);
        parentTable = new JTable(parentModel);
        DiffFileTableUi.configure(parentTable, parentModel);
        parentFilter.installOn(parentPanel, this::refreshParentTable);
        DiffFileTableUi.installDoubleClick(parentTable, parentModel, openParent);
        parentTable.setDragEnabled(true);
        parentTable.setTransferHandler(
            ViewFileDnDUtil.createJTableFileExportHandler(v -> parentFileMap.get(String.valueOf(v))));
        JPanel listCard = new JPanel(new BorderLayout(0, 2));
        listCard.add(parentFilter.buildFilterBar(), BorderLayout.NORTH);
        listCard.add(ViewUi.scroll(parentTable), BorderLayout.CENTER);
        parentPanel.add(listCard, CARD_FILE_LIST);
        parentPanel.add(DiffFileTableUi.createEmptyHintLabel("没有 Excel 文件"), CARD_NO_FILE);
        showParentCard();
        return parentPanel;
    }

    private JPanel buildCurrPanel(Consumer<String> openCurr, Consumer<File> onDrop,
        ViewFileDnDUtil.DropErrorCallback dropErrorLog, BiConsumer<JPopupMenu, String> currMenuFactory) {
        JPanel panel = newCardPanel("当前目录");
        currModel = new DiffFileTableModel(false);
        currTable = new JTable(currModel);
        DiffFileTableUi.configure(currTable, currModel);
        DiffFileTableUi.installDoubleClick(currTable, currModel, openCurr);
        if (currMenuFactory != null) {
            installCurrContextMenu(currMenuFactory);
        }
        JScrollPane scroll = ViewUi.scroll(currTable);
        JLabel empty = DiffFileTableUi.createEmptyHintLabel("没有 Excel 文件");
        ViewFileDnDUtil.installCopyDropTarget(onDrop::accept, dropErrorLog, panel, scroll, currTable, empty);
        panel.add(scroll, CARD_FILE_LIST);
        panel.add(empty, CARD_NO_FILE);
        showCard(panel, CARD_NO_FILE);
        return panel;
    }

    private void installCurrContextMenu(BiConsumer<JPopupMenu, String> factory) {
        currTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showPopup(e);
            }

            private void showPopup(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int row = currTable.rowAtPoint(e.getPoint());
                if (row < 0) {
                    return;
                }
                currTable.setRowSelectionInterval(row, row);
                JPopupMenu popup = new JPopupMenu();
                factory.accept(popup, currModel.getFileName(row));
                if (popup.getComponentCount() > 0) {
                    popup.show(currTable, e.getX(), e.getY());
                }
            }
        });
    }

    private void showParentCard() {
        showCard(parentPanel, parentAllNames.isEmpty() ? CARD_NO_FILE : CARD_FILE_LIST);
    }

    private static JPanel newCardPanel(String title) {
        JPanel panel = new JPanel(new CardLayout(5, 5));
        ViewUi.titled(panel, title);
        panel.setOpaque(true);
        panel.setBackground(ViewUi.CARD);
        return panel;
    }

    private static void showCard(JPanel panel, String card) {
        if (panel != null) {
            ((CardLayout)panel.getLayout()).show(panel, card);
        }
    }
}

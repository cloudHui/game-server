package com.gamer.data.excel.modelgen.view;

import com.gamer.data.excel.modelgen.view.tree.SheetLineNode;
import com.gamer.data.excel.modelgen.view.tree.SheetNode;
import com.gamer.data.excel.modelgen.view.tree.SheetPlaceholder;
import com.gamer.data.excel.modelgen.view.tree.TreeCheckListener;
import com.gamer.data.excel.modelgen.view.tree.TreeCheckRenderer;
import com.gamer.data.excel.modelgen.view.bind.BindPanel;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import com.gamer.data.excel.core.ExcelCatalogEntry;
import com.gamer.data.excel.core.ExcelCatalogLoader;
import com.gamer.data.excel.framework.BaseCheck;
import com.gamer.data.log.Log;
import com.gamer.data.excel.modelgen.ModelGen;
import com.gamer.data.excel.modelgen.ModelGenContext;
import com.gamer.data.excel.modelgen.binding.ModelGenBindingLoader;
import com.gamer.data.excel.modelgen.binding.ModelGenLimitTypeWriter;
import com.gamer.data.excel.modelgen.loader.ConfigModelReader;
import com.gamer.data.excel.modelgen.loader.ExcelLoader;
import com.gamer.data.excel.shared.FileWithSheets;
import com.gamer.data.excel.shared.Title;
import com.gamer.data.excel.ui.ViewSubstringFilterList;
import com.gamer.data.ui.ViewUi;

/**
 * 模型生成选择面板：左侧 Excel 检索列表，中间 Sheet/列树，右侧列绑定面板。
 */
public class ModelGenSelectionPanel extends JPanel {

    /** 绑定面板刷新防抖（毫秒） */
    private static final int BIND_PANEL_DEBOUNCE_MS = 250;

    /** 路径配置 */
    private final BaseCheck baseCheck;

    /** 日志输出 */
    private final Log log;

    /** Excel 文件名子串筛选 */
    private final ViewSubstringFilterList nameFilter = new ViewSubstringFilterList();

    /** 目录内全部 Excel 文件名 */
    private final List<String> allExcelNames = new ArrayList<>();

    /** 列表展示模型 */
    private final DefaultListModel<String> displayListModel = new DefaultListModel<>();

    /** Excel 文件列表 */
    private JList<String> excelFileList;

    /** Sheet/列树 */
    private JTree sheetTree;

    /** Sheet/列树模型 */
    private DefaultTreeModel sheetTreeModel;

    /** Excel 文件名 -> 文件根节点 */
    private final Map<String, DefaultMutableTreeNode> fileRootCache = new HashMap<>();

    /** Excel 文件名 -> stub */
    private final Map<String, FileWithSheets> fileWithSheetsMap = new HashMap<>();

    /** 中间树当前展示的 Excel 名，过滤后第一条未变则不拆树 */
    private String shownExcelName;

    /** 列长度绑定面板 */
    private BindPanel bindPanel;

    /** 绑定面板刷新防抖定时器 */
    private Timer bindPanelRefreshTimer;

    /** limit 写入用的完整 Excel 映射 */
    private Map<String, FileWithSheets> pendingLimitFullExcelByName;

    /**
     * @param baseCheck
     *            路径配置
     * @param log
     *            日志接口
     */
    public ModelGenSelectionPanel(BaseCheck baseCheck, Log log) {
        super(new BorderLayout(8, 8));
        this.baseCheck = baseCheck;
        this.log = log;
        setOpaque(true);
        setBackground(ViewUi.PAGE);
        add(buildMainSplit(), BorderLayout.CENTER);
        // 本页在展示时整窗收键，切到看表/地图后宿主隐藏即卸下
        nameFilter.installOn(this, this::applyExcelNameFilter);
    }

    /**
     * 渐进写入单条 catalog（与看表页共用扫描进度）。
     *
     * @param entry
     *            扫描条目
     */
    public void ingestCatalogEntry(ExcelCatalogEntry entry) {
        onCatalogEntryLoaded(entry);
    }

    /**
     * 用已扫描好的 catalog 一次性填充列表（与看表页共用扫描结果）。
     *
     * @param entries
     *            目录 catalog 条目
     */
    public void initFromCatalog(List<ExcelCatalogEntry> entries) {
        if (entries == null) {
            return;
        }
        for (ExcelCatalogEntry entry : entries) {
            onCatalogEntryLoaded(entry);
        }
    }

    /**
     * 构建左右主分割（左：列表+树，右：绑定面板）。
     *
     * @return 水平分割面板
     */
    private JSplitPane buildMainSplit() {
        bindPanel = new BindPanel();
        bindPanel.setDedupLog(message -> log.logMessage(message, true));
        return ViewUi.splitH(buildLeftWrap(), bindPanel, 0.45);
    }

    /**
     * 构建左侧：检索条、列表+树、按钮。
     *
     * @return 左侧面板
     */
    private JPanel buildLeftWrap() {
        JPanel wrap = new JPanel(new BorderLayout(4, 4));
        wrap.setOpaque(false);
        wrap.add(nameFilter.buildFilterBar(), BorderLayout.NORTH);
        wrap.add(buildListAndTreeSplit(), BorderLayout.CENTER);
        wrap.add(buildControlPanel(), BorderLayout.SOUTH);
        return ViewUi.card("Excel 文件与 Sheet", wrap);
    }

    /**
     * 构建 Excel 列表与 Sheet 树的水平分割。
     *
     * @return 内部分割面板
     */
    private JSplitPane buildListAndTreeSplit() {
        excelFileList = new JList<>(displayListModel);
        excelFileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ViewUi.list(excelFileList);
        excelFileList.setCellRenderer(nameFilter.createHighlightCellRenderer());
        excelFileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onExcelFileListSelected();
            }
        });
        DefaultMutableTreeNode emptyRoot = new DefaultMutableTreeNode("请选择 Excel");
        sheetTreeModel = new DefaultTreeModel(emptyRoot);
        sheetTree = new JTree(sheetTreeModel);
        ViewUi.tree(sheetTree);
        sheetTree.setCellRenderer(new TreeCheckRenderer());
        sheetTree.addMouseListener(new TreeCheckListener(sheetTree, this::scheduleRefreshBindPanel));
        sheetTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowTypePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowTypePopup(e);
            }
        });
        sheetTree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                onSheetTreeExpanded(event.getPath());
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {}
        });
        JSplitPane inner = ViewUi.splitH(ViewUi.scroll(excelFileList), ViewUi.scroll(sheetTree), 0.35);
        inner.setPreferredSize(new Dimension(400, 320));
        return inner;
    }

    /**
     * 构建取消、生成按钮（单行三按钮）。
     *
     * @return 按钮面板
     */
    private JPanel buildControlPanel() {
        JButton selectNoneBtn = ViewUi.click("取消全选", () -> {
            deselectAllInCurrentSheetTree();
            refreshBindPanelForCurrentTree();
        });
        JButton generateBtn = ViewUi.danger(ViewUi.click("生成模型代码", () -> onGenerateClicked(false)));
        JButton generateAllBtn = ViewUi.primary(ViewUi.click("生成代码和配置", () -> onGenerateClicked(true)));
        return ViewUi.grid(1, 3, selectNoneBtn, generateBtn, generateAllBtn);
    }

    /**
     * 写入单条 catalog 到列表与 stub 缓存。
     *
     * @param entry
     *            扫描条目
     */
    private void onCatalogEntryLoaded(ExcelCatalogEntry entry) {
        if (entry == null || entry.fileName == null) {
            return;
        }
        allExcelNames.add(entry.fileName);
        fileWithSheetsMap.put(entry.fileName, ExcelCatalogLoader.toFileWithSheetsStub(entry));
        applyExcelNameFilter();
        log.logMessage("已加载: " + entry.fileName + " (" + entry.sheetNames.size() + " Sheet)");
    }

    /**
     * 按检索词刷新 Excel 文件列表。
     */
    private void applyExcelNameFilter() {
        nameFilter.applyFilter(allExcelNames, displayListModel, excelFileList);
    }

    /**
     * 列表选中后切换 Sheet 树，并刷新右侧绑定（仅当前树根）。
     */
    private void onExcelFileListSelected() {
        int idx = excelFileList.getSelectedIndex();
        if (idx < 0) {
            refreshBindPanelForCurrentTree();
            return;
        }
        String fileName = displayListModel.getElementAt(idx);
        if (fileName.equals(shownExcelName)) {
            return;
        }
        shownExcelName = fileName;
        showSheetTreeForFile(fileName);
        refreshBindPanelForCurrentTree();
    }

    /**
     * 显示指定 Excel 的 Sheet/列树。
     *
     * @param excelName
     *            Excel 文件名
     */
    private void showSheetTreeForFile(String excelName) {
        // 切 Excel：丢掉该文件树/列结构/绑定缓存，展开时重新读格式
        fileRootCache.remove(excelName);
        FileWithSheets stub = fileWithSheetsMap.get(excelName);
        if (stub != null) {
            stub.clearStructure();
        }
        ModelGenBindingLoader.invalidate(excelName);

        DefaultMutableTreeNode fileRoot = ensureFileRootBuilt(excelName);
        if (fileRoot == null) {
            sheetTreeModel.setRoot(new DefaultMutableTreeNode("无 Sheet"));
        } else {
            sheetTreeModel.setRoot(fileRoot);
        }
        sheetTreeModel.reload();
        if (fileRoot != null) {
            sheetTree.expandRow(0);
        }
    }

    /**
     * 获取或构建 Excel 文件根节点。
     *
     * @param excelName
     *            Excel 文件名
     * @return 文件根节点；无数据 null
     */
    private DefaultMutableTreeNode ensureFileRootBuilt(String excelName) {
        DefaultMutableTreeNode cached = fileRootCache.get(excelName);
        if (cached != null) {
            return cached;
        }
        FileWithSheets stub = fileWithSheetsMap.get(excelName);
        if (stub == null || stub.sheets.isEmpty()) {
            return null;
        }
        DefaultMutableTreeNode fileRoot = buildFileRootNode(stub);
        fileRootCache.put(excelName, fileRoot);
        return fileRoot;
    }

    /**
     * 由 stub 构建 Sheet 子节点（列占位懒加载）。
     *
     * @param stub
     *            FileWithSheets stub
     * @return 文件根节点
     */
    private DefaultMutableTreeNode buildFileRootNode(FileWithSheets stub) {
        SheetNode fileNode = new SheetNode(stub.excelName, false);
        fileNode.fileWithSheets = stub;
        DefaultMutableTreeNode fileRoot = new DefaultMutableTreeNode(fileNode);
        for (String sheetName : stub.sheets.keySet()) {
            SheetNode sheetData = new SheetNode(sheetName, false);
            DefaultMutableTreeNode sheetTreeNode = new DefaultMutableTreeNode(sheetData);
            sheetTreeNode.add(new DefaultMutableTreeNode(SheetPlaceholder.INSTANCE));
            fileRoot.add(sheetTreeNode);
        }
        return fileRoot;
    }

    /**
     * Sheet 展开时触发列懒加载。
     *
     * @param path
     *            树展开路径
     */
    private void onSheetTreeExpanded(TreePath path) {
        if (path == null || path.getPathCount() < 2) {
            return;
        }
        DefaultMutableTreeNode sheetTreeNode = (DefaultMutableTreeNode)path.getLastPathComponent();
        loadSheetColumnsForNode(sheetTreeNode);
    }

    /**
     * 后台加载 Sheet 列并更新树。
     *
     * @param sheetTreeNode
     *            Sheet 树节点
     */
    private void loadSheetColumnsForNode(final DefaultMutableTreeNode sheetTreeNode) {
        if (!(sheetTreeNode.getUserObject() instanceof SheetNode)) {
            return;
        }
        final SheetNode sheetData = (SheetNode)sheetTreeNode.getUserObject();
        if (sheetData.fileWithSheets != null || sheetData.columnsLoaded || sheetData.columnsLoading) {
            return;
        }
        if (hasColumnLineChildren(sheetTreeNode)) {
            return;
        }
        DefaultMutableTreeNode fileTreeNode = (DefaultMutableTreeNode)sheetTreeNode.getParent();
        if (fileTreeNode == null || !(fileTreeNode.getUserObject() instanceof SheetNode)) {
            return;
        }
        final FileWithSheets excel = ((SheetNode)fileTreeNode.getUserObject()).fileWithSheets;
        if (excel == null || excel.sourceFile == null) {
            return;
        }
        sheetData.columnsLoading = true;
        final String sheetName = sheetData.name;
        log.logMessage("加载列: " + excel.excelName + " / " + sheetName);
        SwingWorker<List<Title>, Void> worker = new SwingWorker<List<Title>, Void>() {
            @Override
            protected List<Title> doInBackground() throws Exception {
                ExcelLoader.loadSheetColumnsOnExpand(excel, sheetName, log);
                return excel.sheets.get(sheetName);
            }

            @Override
            protected void done() {
                try {
                    List<Title> titles = get();
                    applyLoadedColumns(sheetTreeNode, sheetData, excel, sheetName, titles);
                } catch (Exception ex) {
                    restorePlaceholderChild(sheetTreeNode);
                    log.logMessage("加载列失败: " + sheetName + " - " + ex.getMessage(), true);
                    sheetData.columnsLoading = false;
                }
            }
        };
        worker.execute();
    }

    /**
     * 列加载结果写入树节点。
     *
     * @param sheetTreeNode
     *            Sheet 树节点
     * @param sheetData
     *            Sheet 数据
     * @param excel
     *            所属 Excel
     * @param sheetName
     *            Sheet 名
     * @param titles
     *            列定义列表
     */
    private void applyLoadedColumns(DefaultMutableTreeNode sheetTreeNode, SheetNode sheetData, FileWithSheets excel,
        String sheetName, List<Title> titles) {
        try {
            removePlaceholderChildren(sheetTreeNode);
            if (titles == null || titles.isEmpty()) {
                log.logMessage("Sheet 无列: " + excel.excelName + " / " + sheetName, true);
                return;
            }

            // 展开 Sheet 时才读取对应 Config，避免目录扫描阶段批量读取全部源码。
            Set<String> dataCellNames = loadExistingDataCellNames(excel.excelName, sheetName);
            Set<String> excelColumnNames = new LinkedHashSet<>();
            boolean hasSelectedColumn = false;
            for (Title title : titles) {
                if (title != null) {
                    String columnName = title.getOldName();
                    boolean existingDataCell = dataCellNames.contains(columnName);
                    boolean selected = sheetData.selected || existingDataCell;
                    excelColumnNames.add(columnName);
                    sheetTreeNode.add(
                        new DefaultMutableTreeNode(new SheetLineNode(title, selected, existingDataCell)));
                    hasSelectedColumn |= selected;
                }
            }

            // 父节点状态跟随实际勾选列，保证自动勾选结果可以直接参与生成。
            sheetData.selected = hasSelectedColumn;
            refreshFileNodeSelection(sheetTreeNode);
            sheetData.columnsLoaded = true;
            sheetTreeModel.nodeStructureChanged(sheetTreeNode);
            logMissingExcelColumns(excel.excelName, sheetName, dataCellNames, excelColumnNames);
            log.logMessage("已加载列: " + excel.excelName + " / " + sheetName + " (" + titles.size() + ")");
            refreshBindPanelForCurrentTree();
        } finally {
            sheetData.columnsLoading = false;
        }
    }

    /**
     * 读取当前 Sheet 对应 Config 的 DataCell 列名；缺失或失败时按空集合处理。
     *
     * @param excelName
     *            Excel 文件名
     * @param sheetName
     *            Sheet 名
     * @return 已有 DataCell 列名
     */
    private Set<String> loadExistingDataCellNames(String excelName, String sheetName) {
        File configSource = ConfigModelReader.resolveConfigSourceFile(baseCheck.SERVER_PATH, excelName,
            sheetName);
        if (!configSource.isFile()) {
            log.logMessage("[模型对照] 未找到 Config，整表默认不勾: " + configSource.getPath(), true);
            return Collections.emptySet();
        }
        try {
            return ConfigModelReader.readDataCellNames(configSource);
        } catch (IOException e) {
            log.logMessage("[模型对照] 读取 Config 失败，整表默认不勾: " + configSource.getPath() + " - "
                + e.getMessage(), true);
            return Collections.emptySet();
        }
    }

    /**
     * 输出代码中存在、Excel 中已不存在的列，便于清理旧模型字段。
     *
     * @param excelName
     *            Excel 文件名
     * @param sheetName
     *            Sheet 名
     * @param dataCellNames
     *            Config 已有列
     * @param excelColumnNames
     *            Excel 当前列
     */
    private void logMissingExcelColumns(String excelName, String sheetName, Set<String> dataCellNames,
        Set<String> excelColumnNames) {
        Set<String> missingColumns = new LinkedHashSet<>(dataCellNames);
        missingColumns.removeAll(excelColumnNames);
        if (!missingColumns.isEmpty()) {
            log.logMessage("[模型对照] " + excelName + " / " + sheetName + "：代码存在但 Excel 缺少列 "
                + String.join(", ", missingColumns), true);
        }
    }

    /**
     * 根据所有 Sheet 的勾选状态刷新文件根节点。
     *
     * @param sheetTreeNode
     *            本次完成加载的 Sheet 节点
     */
    private void refreshFileNodeSelection(DefaultMutableTreeNode sheetTreeNode) {
        DefaultMutableTreeNode fileRoot = (DefaultMutableTreeNode)sheetTreeNode.getParent();
        if (fileRoot == null || !(fileRoot.getUserObject() instanceof SheetNode)) {
            return;
        }
        boolean selected = false;
        for (int i = 0; i < fileRoot.getChildCount(); i++) {
            Object childData = ((DefaultMutableTreeNode)fileRoot.getChildAt(i)).getUserObject();
            if (childData instanceof SheetNode && ((SheetNode)childData).selected) {
                selected = true;
                break;
            }
        }
        ((SheetNode)fileRoot.getUserObject()).selected = selected;
    }

    /**
     * Sheet 是否已有列子节点。
     *
     * @param sheetTreeNode
     *            Sheet 节点
     * @return 有列节点 true
     */
    private boolean hasColumnLineChildren(DefaultMutableTreeNode sheetTreeNode) {
        for (int i = 0; i < sheetTreeNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode)sheetTreeNode.getChildAt(i);
            if (child.getUserObject() instanceof SheetLineNode) {
                return true;
            }
        }
        return false;
    }

    /**
     * 移除 Sheet 下占位子节点。
     *
     * @param sheetTreeNode
     *            Sheet 节点
     */
    private void removePlaceholderChildren(DefaultMutableTreeNode sheetTreeNode) {
        for (int i = sheetTreeNode.getChildCount() - 1; i >= 0; i--) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode)sheetTreeNode.getChildAt(i);
            if (child.getUserObject() instanceof SheetPlaceholder) {
                sheetTreeNode.remove(i);
            }
        }
    }

    /**
     * 加载失败时恢复占位节点。
     *
     * @param sheetTreeNode
     *            Sheet 节点
     */
    private void restorePlaceholderChild(DefaultMutableTreeNode sheetTreeNode) {
        removePlaceholderChildren(sheetTreeNode);
        if (!hasColumnLineChildren(sheetTreeNode)) {
            sheetTreeNode.add(new DefaultMutableTreeNode(SheetPlaceholder.INSTANCE));
        }
    }

    /**
     * 防抖刷新绑定面板（仅当前 Sheet 树根）。
     */
    private void scheduleRefreshBindPanel() {
        if (bindPanelRefreshTimer == null) {
            bindPanelRefreshTimer = new Timer(BIND_PANEL_DEBOUNCE_MS, e -> refreshBindPanelForCurrentTree());
            bindPanelRefreshTimer.setRepeats(false);
        }
        bindPanelRefreshTimer.restart();
    }

    /**
     * 列节点右键：换类型菜单。
     */
    private void maybeShowTypePopup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        TreePath path = sheetTree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
            return;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        if (!(node.getUserObject() instanceof SheetLineNode)) {
            return;
        }
        final SheetLineNode line = (SheetLineNode)node.getUserObject();
        if (line.title == null) {
            return;
        }
        sheetTree.setSelectionPath(path);
        JPopupMenu menu = getJPopupMenu(line, node);
        menu.show(sheetTree, e.getX(), e.getY());
    }

    private JPopupMenu getJPopupMenu(SheetLineNode line, DefaultMutableTreeNode node) {
        JPopupMenu menu = new JPopupMenu();
        String[] types = new String[] {"int", "string", "int[]", "int[][]"};
        for (final String type : types) {
            JMenuItem item = new JMenuItem(type);
            item.addActionListener(ev -> {
                line.title.setType(type);
                if (type.indexOf('[') >= 0) {
                    line.title.setNewCode("value = \"" + line.title.getOldName() + "\", isArray = true");
                } else {
                    line.title.setNewCode(null);
                }
                line.name = line.title.toString();
                sheetTreeModel.nodeChanged(node);
                forceRefreshBindPanel();
            });
            menu.add(item);
        }
        return menu;
    }

    /**
     * 类型变更后强制重建右侧绑定列下拉。
     */
    private void forceRefreshBindPanel() {
        List<CheckedSheetItem> checked = collectCheckedSheetsForCurrentTree();
        bindPanel.forceLoadCheckedSheets(checked,
            (excelName, excel) -> ModelGenBindingLoader.resolveBindingState(excel, baseCheck.CURR_DIR, baseCheck.baseDir));
    }

    /**
     * 按当前 Sheet 树根的勾选刷新右侧绑定面板（不累计其它 Excel）。
     */
    private void refreshBindPanelForCurrentTree() {
        List<CheckedSheetItem> checked = collectCheckedSheetsForCurrentTree();
        bindPanel.loadCheckedSheets(checked,
            (excelName, excel) -> ModelGenBindingLoader.resolveBindingState(excel, baseCheck.CURR_DIR, baseCheck.baseDir));
    }

    /**
     * 解析当前 Sheet 树文件根（与左侧高亮对应的 Excel 节点）。
     *
     * @return 文件根节点；占位根或未选时 null
     */
    private DefaultMutableTreeNode resolveCurrentFileRoot() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) sheetTreeModel.getRoot();
        if (root == null || !(root.getUserObject() instanceof SheetNode)) {
            return null;
        }
        SheetNode fileData = (SheetNode) root.getUserObject();
        if (fileData.fileWithSheets == null) {
            return null;
        }
        return root;
    }

    /**
     * 收集当前 Sheet 树根下已勾选 Sheet。
     *
     * @return 已勾选 Sheet 列表
     */
    private List<CheckedSheetItem> collectCheckedSheetsForCurrentTree() {
        List<CheckedSheetItem> result = new ArrayList<>();
        DefaultMutableTreeNode fileRoot = resolveCurrentFileRoot();
        if (fileRoot == null) {
            return result;
        }
        SheetNode fileData = (SheetNode) fileRoot.getUserObject();
        collectSheetsUnderFileRoot(fileRoot, fileData.name, fileData.fileWithSheets, result);
        return result;
    }

    /**
     * 收集单个文件根下已勾选 Sheet。
     *
     * @param fileRoot
     *            文件根节点
     * @param excelName
     *            Excel 文件名
     * @param excel
     *            FileWithSheets
     * @param result
     *            输出列表
     */
    private void collectSheetsUnderFileRoot(DefaultMutableTreeNode fileRoot, String excelName, FileWithSheets excel,
        List<CheckedSheetItem> result) {
        for (int i = 0; i < fileRoot.getChildCount(); i++) {
            DefaultMutableTreeNode sheetNode = (DefaultMutableTreeNode)fileRoot.getChildAt(i);
            if (!(sheetNode.getUserObject() instanceof SheetNode)) {
                continue;
            }
            SheetNode sheetData = (SheetNode)sheetNode.getUserObject();
            if (!sheetData.selected) {
                continue;
            }
            result.add(new CheckedSheetItem(excelName, sheetData.name, excel));
        }
    }

    /**
     * 当前 Sheet 树全部取消勾选。
     */
    private void deselectAllInCurrentSheetTree() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode)sheetTreeModel.getRoot();
        if (root == null) {
            return;
        }
        deselectAllRecursive(root);
        sheetTree.repaint();
    }

    /**
     * 递归取消文件、Sheet、列节点勾选。
     *
     * @param node
     *            树节点
     */
    private void deselectAllRecursive(DefaultMutableTreeNode node) {
        Object nodeData = node.getUserObject();
        if (nodeData instanceof SheetLineNode) {
            ((SheetLineNode)nodeData).selected = false;
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            deselectAllRecursive((DefaultMutableTreeNode)node.getChildAt(i));
        }
        if (nodeData instanceof SheetNode) {
            ((SheetNode)nodeData).selected = false;
        }
    }

    /**
     * 点击生成：仅处理当前 Sheet 树（左侧高亮 Excel）。
     */
    private void onGenerateClicked(boolean withManagerConfig) {
        DefaultMutableTreeNode fileRoot = resolveCurrentFileRoot();
        if (fileRoot == null) {
            JOptionPane.showMessageDialog(this, "请先在左侧选择 Excel 文件", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String expandError = validateFileRootExpanded(fileRoot);
        if (expandError != null) {
            JOptionPane.showMessageDialog(this, expandError, "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Map<String, FileWithSheets> selectedData = collectSelectedDataForCurrentTree();
        if (selectedData.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请选择至少一个已展开 Sheet 下的列", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        bindPanel.flushAll();
        List<FileWithSheets> toGenerate = new ArrayList<>(selectedData.values());
        pendingLimitFullExcelByName = collectFullExcelFromCache(selectedData.keySet());
        SheetNode fileData = (SheetNode) fileRoot.getUserObject();
        if (withManagerConfig) {
            log.logMessage("开始生成模型+Manager+配置: " + fileData.name);
            ModelGen.genCodeAndConfig(toGenerate, buildModelGenContext());
        } else {
            log.logMessage("开始生成模型与 limit: " + fileData.name);
            ModelGen.genCode(toGenerate, buildModelGenContext());
        }
    }

    /**
     * 构建模型生成运行时上下文。
     *
     * @return ModelGenContext
     */
    private ModelGenContext buildModelGenContext() {
        return new ModelGenContext() {
            @Override
            public void logMessage(String message) {
                log.logMessage(message);
            }

            @Override
            public String getServerPath() {
                return baseCheck.SERVER_PATH;
            }

            @Override
            public String getXmlPath() {
                return baseCheck.XML_PATH.getAbsolutePath();
            }

            @Override
            public void afterConfigFilesWritten(List<FileWithSheets> files) {
                ModelGenLimitTypeWriter.writeGeneratedSheets(baseCheck.CURR_DIR, baseCheck.baseDir, files,
                    pendingLimitFullExcelByName, log::logMessage);
            }
        };
    }

    /**
     * 汇总当前 Sheet 树的生成子集（单个 Excel）。
     *
     * @return Excel 名 -> 勾选列子集
     */
    private Map<String, FileWithSheets> collectSelectedDataForCurrentTree() {
        Map<String, FileWithSheets> selectedData = new HashMap<>();
        DefaultMutableTreeNode fileRoot = resolveCurrentFileRoot();
        if (fileRoot != null) {
            appendSelectedFromFileRoot(fileRoot, selectedData);
        }
        removeEmptySelectedFiles(selectedData);
        return selectedData;
    }

    /**
     * 校验单个文件根下已勾选 Sheet 是否已加载列。
     *
     * @param fileRoot
     *            文件根节点
     * @return 错误提示；通过 null
     */
    private String validateFileRootExpanded(DefaultMutableTreeNode fileRoot) {
        if (!(fileRoot.getUserObject() instanceof SheetNode)) {
            return null;
        }
        SheetNode fileData = (SheetNode)fileRoot.getUserObject();
        for (int i = 0; i < fileRoot.getChildCount(); i++) {
            DefaultMutableTreeNode sheetNode = (DefaultMutableTreeNode)fileRoot.getChildAt(i);
            if (!(sheetNode.getUserObject() instanceof SheetNode)) {
                continue;
            }
            SheetNode sheetData = (SheetNode)sheetNode.getUserObject();
            if (!sheetData.selected) {
                continue;
            }
            if (!sheetData.columnsLoaded && !hasColumnLineChildren(sheetNode)) {
                return "请先展开并加载列: " + fileData.name + " / " + sheetData.name;
            }
        }
        return null;
    }

    /**
     * 从文件根追加勾选列到结果 map。
     *
     * @param fileRoot
     *            文件根
     * @param selectedData
     *            输出 map
     */
    private void appendSelectedFromFileRoot(DefaultMutableTreeNode fileRoot, Map<String, FileWithSheets> selectedData) {
        if (!(fileRoot.getUserObject() instanceof SheetNode)) {
            return;
        }
        SheetNode fileData = (SheetNode)fileRoot.getUserObject();
        if (!fileData.selected || fileData.fileWithSheets == null) {
            return;
        }
        FileWithSheets subset = selectedData.get(fileData.name);
        if (subset == null) {
            subset = new FileWithSheets(fileData.name);
            selectedData.put(fileData.name, subset);
        }
        appendSelectedSheetsFromRoot(fileRoot, subset);
    }

    /**
     * 追加文件根下已勾选 Sheet 的列。
     *
     * @param fileRoot
     *            文件根
     * @param subset
     *            输出子集
     */
    private void appendSelectedSheetsFromRoot(DefaultMutableTreeNode fileRoot, FileWithSheets subset) {
        for (int i = 0; i < fileRoot.getChildCount(); i++) {
            DefaultMutableTreeNode sheetNode = (DefaultMutableTreeNode)fileRoot.getChildAt(i);
            if (!(sheetNode.getUserObject() instanceof SheetNode)) {
                continue;
            }
            SheetNode sheetData = (SheetNode)sheetNode.getUserObject();
            if (!sheetData.selected) {
                continue;
            }
            appendSelectedColumnsFromSheet(sheetNode, sheetData.name, subset);
        }
    }

    /**
     * 追加 Sheet 下已勾选列。
     *
     * @param sheetNode
     *            Sheet 节点
     * @param sheetName
     *            Sheet 名
     * @param subset
     *            输出子集
     */
    private void appendSelectedColumnsFromSheet(DefaultMutableTreeNode sheetNode, String sheetName,
        FileWithSheets subset) {
        for (int j = 0; j < sheetNode.getChildCount(); j++) {
            DefaultMutableTreeNode lineNode = (DefaultMutableTreeNode)sheetNode.getChildAt(j);
            if (!(lineNode.getUserObject() instanceof SheetLineNode)) {
                continue;
            }
            SheetLineNode lineData = (SheetLineNode)lineNode.getUserObject();
            if (lineData.selected && lineData.title != null) {
                subset.addSheet(sheetName, lineData.title);
            }
        }
    }

    /**
     * 移除无勾选列的 Excel 条目。
     *
     * @param selectedData
     *            生成数据 map
     */
    private void removeEmptySelectedFiles(Map<String, FileWithSheets> selectedData) {
        List<String> empty = new ArrayList<>();
        for (Map.Entry<String, FileWithSheets> entry : selectedData.entrySet()) {
            if (!hasAnySelectedColumn(entry.getValue())) {
                empty.add(entry.getKey());
            }
        }
        for (String s : empty) {
            selectedData.remove(s);
        }
    }

    /**
     * 子集是否含至少一列。
     *
     * @param subset
     *            FileWithSheets 子集
     * @return 有列 true
     */
    private boolean hasAnySelectedColumn(FileWithSheets subset) {
        if (subset == null || subset.sheets == null) {
            return false;
        }
        for (List<Title> titles : subset.sheets.values()) {
            if (titles != null && !titles.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 收集 limit 写入所需的完整 Excel 映射。
     *
     * @param excelNames
     *            勾选的 Excel 名集合
     * @return 完整映射
     */
    private Map<String, FileWithSheets> collectFullExcelFromCache(Set<String> excelNames) {
        Map<String, FileWithSheets> result = new HashMap<>();
        for (String excelName : excelNames) {
            FileWithSheets full = fileWithSheetsMap.get(excelName);
            if (full != null) {
                result.put(excelName, full);
            }
        }
        return result;
    }
}

package com.gamer.data.excel.browse;

import static com.gamer.data.excel.framework.BaseCheck.GD;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.gamer.data.excel.core.ExcelCatalogEntry;
import com.gamer.data.excel.core.ExcelCatalogLoader;
import com.gamer.data.excel.core.ExcelSaxHeaderReader;
import com.gamer.data.excel.core.GdPagedTableSource;
import com.gamer.data.excel.core.SaxPagedExcelSource;
import com.gamer.data.excel.framework.AbstractViewFrameCore;
import com.gamer.data.excel.framework.BaseCheck;
import com.gamer.data.excel.modelgen.view.ModelGenSelectionPanel;
import com.gamer.data.excel.ui.PagedPreviewPanel;
import com.gamer.data.excel.ui.PagedPreviewPanelMode;
import com.gamer.data.excel.ui.ViewSubstringFilterList;
import com.gamer.data.ui.ViewUi;
import com.gamer.data.gdg.excel.ExcelOperate;
import com.gamer.data.map.grid.MapRingUtil;
import com.gamer.data.map.ui.MapViewerPanel;

/**
 * CodeTool 主窗口：模型代码生成、Excel/GD 流式预览、地图查看。 使用方：{@link com.gamer.data.excel.entry.DataBuilderCheck} 程序侧入口。
 *
 * @author liuyunhui
 * @date 2025/12/05
 */
public class ExcelViewer extends AbstractViewFrameCore {

    /** Sheet 列表最小高度（px），避免空列表被纵向分割条压成一条缝 */
    private static final int SHEET_LIST_MIN_HEIGHT_PX = 96;

    /** 目录预扫描结果（Excel/GD 看表文件列表） */
    private List<ExcelCatalogEntry> catalog;

    /** Sheet 列表（看表页右侧） */
    private JList<String> sheetList;

    /** Sheet 列表模型 */
    private DefaultListModel<String> sheetListModel;

    /** Excel 流式预览面板 */
    private PagedPreviewPanel excelPreviewPanel;

    /** GD 流式预览面板 */
    private PagedPreviewPanel gdPreviewPanel;

    /** 主面板 */
    private JPanel mainPanel;

    /** 当前显示的中心面板 */
    private JPanel currentCenterPanel;

    /** 底部状态栏（非地图页面显示） */
    private JPanel statusBarPanel;

    /** 看表页 Excel 文件名子串筛选 */
    private final ViewSubstringFilterList browseFileFilter = new ViewSubstringFilterList();

    /** 看表页全部 Excel 文件名（检索数据源） */
    private final List<String> browseAllFileNames = new ArrayList<>();

    /** 模型生成选择面板 */
    private ModelGenSelectionPanel modelGenSelectionPanel;

    /** catalog 是否正在后台扫描 */
    private boolean catalogScanRunning;

    /**
     * @param baseCheck
     *            路径配置
     */
    public ExcelViewer(BaseCheck baseCheck) {
        super(baseCheck);
        initUI();
        setupDragAndDrop();
    }

    /**
     * 看表页启动 catalog 扫描（与模型生成共用，仅读 Sheet 名）。
     */
    private void initSwingWorker() {
        scanCatalogAsync(baseCheck.XML_PATH);
    }

    /**
     * 后台扫描配置目录，渐进写入 catalog 并刷新看表/模型生成列表。
     *
     * @param dir
     *            配置表目录
     */
    private void scanCatalogAsync(final File dir) {
        if (catalog != null && !catalog.isEmpty()) {
            applyBrowseFileFilter();
            return;
        }
        if (catalogScanRunning) {
            return;
        }
        catalogScanRunning = true;
        if (catalog == null) {
            catalog = new ArrayList<>();
        }
        SwingWorker<Void, ExcelCatalogEntry> worker = new SwingWorker<Void, ExcelCatalogEntry>() {
            @Override
            protected Void doInBackground() {
                File[] files = listExcelFiles(dir);
                int total = files == null ? 0 : files.length;
                logMessage("开始预扫描 Excel 目录，共 " + total + " 个文件");
                if (files == null) {
                    return null;
                }
                baseCheck.XML_PATH = dir;
                for (File file : files) {
                    try {
                        ExcelCatalogEntry entry = ExcelCatalogLoader.scanEntry(file, log);
                        if (entry != null) {
                            publish(entry);
                        }
                    } catch (Exception e) {
                        logMessage("预扫描失败: " + file.getName() + " - " + e.getMessage(), true);
                    }
                }
                return null;
            }

            @Override
            protected void process(List<ExcelCatalogEntry> chunks) {
                for (ExcelCatalogEntry entry : chunks) {
                    if (entry != null) {
                        onCatalogEntryScanned(entry);
                    }
                }
            }

            @Override
            protected void done() {
                catalogScanRunning = false;
                try {
                    get();
                    if (catalog.isEmpty()) {
                        logMessage("没有可用的 Excel 文件");
                    } else {
                        logMessage("Excel 预扫描完成，可用 " + catalog.size() + " 个");
                    }
                } catch (Exception e) {
                    logMessage("预扫描失败: " + e.getMessage(), true);
                }
            }
        };
        worker.execute();
    }

    /**
     * 单条 catalog 扫描结果写入共享缓存并刷新 UI。
     *
     * @param entry
     *            目录条目
     */
    private void onCatalogEntryScanned(ExcelCatalogEntry entry) {
        catalog.add(entry);
        browseAllFileNames.add(entry.fileName);
        ExcelOperate op = new ExcelOperate(entry.sourceFile);
        op.bindProbedSheetNames(entry.sheetNames);
        fileMap.put(entry.fileName, op);
        if (fileList != null) {
            applyBrowseFileFilter();
        }
        if (modelGenSelectionPanel != null) {
            modelGenSelectionPanel.ingestCatalogEntry(entry);
        }
    }

    /**
     * 按检索词刷新看表页 Excel 文件列表。
     */
    private void applyBrowseFileFilter() {
        if (fileList == null || listModel == null) {
            return;
        }
        browseFileFilter.applyFilter(browseAllFileNames, listModel, fileList);
    }

    /**
     * 从预扫描目录取 Sheet 名。
     *
     * @param fileName
     *            Excel 文件名
     * @return Sheet 名列表
     */
    private List<String> sheetNamesFromCatalog(String fileName) {
        if (catalog == null || fileName == null) {
            return new ArrayList<>();
        }
        for (ExcelCatalogEntry entry : catalog) {
            if (fileName.equals(entry.fileName)) {
                return new ArrayList<>(entry.sheetNames);
            }
        }
        return new ArrayList<>();
    }

    /**
     * 更新 Sheet 列表（看表模式，不打开 Workbook）。
     */
    private void updateSheetListFromNames(List<String> sheetNames) {
        if (sheetListModel == null) {
            return;
        }
        sheetListModel.clear();
        if (sheetNames == null) {
            return;
        }
        int index = 1;
        for (String sheetName : sheetNames) {
            sheetListModel.addElement(index + ". " + sheetName);
            index++;
        }
    }

    /**
     * 预览面板是否已创建（看表页底部 Excel/GD 双栏）。
     *
     * @return 两侧预览面板均已 createView
     */
    private boolean isPreviewReady() {
        return excelPreviewPanel != null && excelPreviewPanel.isViewCreated() && gdPreviewPanel != null
            && gdPreviewPanel.isViewCreated();
    }

    @Override
    protected void onExcelFileSelected(ExcelOperate excelOperate) {
        currentExcelOperate = excelOperate;
        releaseOtherWorkbooks(excelOperate.fileName);
        List<String> sheetNames = sheetNamesFromCatalog(excelOperate.fileName);
        if (sheetNames.isEmpty()) {
            excelOperate.probeNotSheetNames(log);
            sheetNames = excelOperate.getConfigSheetNames();
        }
        updateSheetListFromNames(sheetNames);
        logMessage("已选: " + excelOperate.fileName + "，Sheet " + sheetNames.size() + " 个");
        if (!sheetNames.isEmpty() && sheetList != null) {
            sheetList.setSelectedIndex(0);
        }
    }

    /**
     * 查找对应的GD文件（根据Sheet名称）
     */
    private File findGdFile(String excelFileName, String sheetName) {
        if (excelFileName == null || !baseCheck.GD_PATH.exists()) {
            return null;
        }

        // 尝试多种可能的GD文件名格式

        // 1. Excel文件名_Sheet名.gd (最可能)
        String baseName = excelFileName;
        int dotIndex = excelFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = excelFileName.substring(0, dotIndex);
        }

        // 尝试不带Sheet名
        String[] possibleNames = {
            // Excel文件名_Sheet名.gd
            baseName + GD, // Excel文件名.gd
            sheetName + GD, // Sheet名.gd
        };

        // 在GD目录中查找
        for (String gdFileName : possibleNames) {

            String gdFile = gdFileName.substring(0, gdFileName.lastIndexOf("."));
            if (!sheetName.equals(gdFile)) {
                continue;
            }
            File file = new File(baseCheck.GD_PATH, gdFileName);
            if (file.exists() && file.isFile()) {
                return file;
            }
        }

        // 在子目录中查找
        File[] subDirs = baseCheck.GD_PATH.listFiles(File::isDirectory);
        if (subDirs != null) {
            for (File subDir : subDirs) {
                for (String gdFileName : possibleNames) {
                    File gdFile = new File(subDir, gdFileName);
                    if (gdFile.exists() && gdFile.isFile()) {
                        return gdFile;
                    }
                }
            }
        }

        logMessage("未找到GD文件，尝试了: " + String.join(", ", possibleNames));
        return null;
    }

    /**
     * 创建看表页左侧 Excel 列表（含字符检索）。
     *
     * @return 左侧面板
     */
    private JPanel createLeftPanel() {
        JPanel body = new JPanel(new BorderLayout(5, 5));
        body.setOpaque(false);
        JScrollPane fileScrollPane = initBrowseFileList();
        body.add(browseFileFilter.buildFilterBar(), BorderLayout.NORTH);
        body.add(fileScrollPane, BorderLayout.CENTER);
        return ViewUi.card("Excel文件列表", body);
    }

    /**
     * 初始化看表页文件列表并安装检索与高亮。
     *
     * @return 文件列表滚动面板
     */
    private JScrollPane initBrowseFileList() {
        listModel = new DefaultListModel<>();
        fileList = new JList<>(listModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ViewUi.list(fileList);
        fileList.setCellRenderer(browseFileFilter.createHighlightCellRenderer());
        fileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onBrowseFileListSelected();
            }
        });
        installFileListContextMenu();
        return ViewUi.scroll(fileList);
    }

    /**
     * 看表页文件列表选中：打开对应 Excel 并刷新 Sheet 列表。
     */
    private void onBrowseFileListSelected() {
        int selectedIndex = fileList.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= listModel.size()) {
            return;
        }
        String displayName = listModel.getElementAt(selectedIndex);
        if (currentExcelOperate != null && displayName.equals(currentExcelOperate.fileName)) {
            return;
        }
        ExcelOperate selectedOperate = fileMap.get(displayName);
        if (selectedOperate != null) {
            onExcelFileSelected(selectedOperate);
        } else {
            logMessage("无法找到对应的 Excel 文件: " + displayName);
        }
    }

    /**
     * 异步流式加载 GD 预览（按 Sheet 名查找 .gd 文件）。
     *
     * @param excelFileName
     *            Excel 文件名
     * @param sheetName
     *            Sheet 名
     */
    private void loadGdPreviewAsync(String excelFileName, String sheetName) {
        SwingWorker<GdPagedTableSource, String> gdLoader = new SwingWorker<GdPagedTableSource, String>() {
            @Override
            protected GdPagedTableSource doInBackground() throws Exception {
                publish("正在查找Sheet对应的GD文件...");
                File gdFile = findGdFile(excelFileName, sheetName);
                if (gdFile == null) {
                    publish("未找到对应的GD文件");
                    return null;
                }
                publish("找到GD文件: " + gdFile.getAbsolutePath());
                GdPagedTableSource src = new GdPagedTableSource(gdFile);
                publish("GD 就绪: " + src.getTotalRows() + " 行");
                return src;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    logMessage(message);
                }
            }

            @Override
            protected void done() {
                try {
                    GdPagedTableSource src = get();
                    if (src == null) {
                        if (gdPreviewPanel != null) {
                            gdPreviewPanel.clear();
                        }
                        return;
                    }
                    if (gdPreviewPanel == null) {
                        logMessage("警告: GD预览面板未初始化", true);
                        return;
                    }
                    gdPreviewPanel.bindSource(src.getColumnHeaders(), src);
                } catch (Exception e) {
                    logMessage("加载GD预览失败: " + e, true);
                }
            }
        };
        gdLoader.execute();
    }

    /**
     * 设置整窗拖放，接受 .gd 文件并显示内容（与 WindowsTools 一致）
     */
    private void setupDragAndDrop() {
        new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
                    Transferable transferable = dtde.getTransferable();

                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        List<File> files = (List<File>)transferable.getTransferData(DataFlavor.javaFileListFlavor);

                        if (!files.isEmpty()) {
                            File file = files.get(0);
                            String name = file.getName();
                            boolean isGd = name.toLowerCase().endsWith(".gd");

                            if (isGd) {
                                SwingUtilities.invokeLater(() -> {
                                    try {
                                        Thread.sleep(100);
                                        openGdFileFromDrop(file);
                                    } catch (Exception e) {
                                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(ExcelViewer.this,
                                            "打开GD文件失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE));
                                    }
                                });

                                dtde.dropComplete(true);
                                return;
                            }
                        }
                    }

                    dtde.dropComplete(true);
                } catch (Exception e) {
                    dtde.dropComplete(false);
                    JOptionPane.showMessageDialog(ExcelViewer.this, "拖放失败: " + e, "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    /**
     * 从拖放或选择打开 GD 文件：新开独立窗口（与 WindowsTools 一致的全面版），不拆分本面板。
     *
     * @param file
     *            GD 文件
     */
    private void openGdFileFromDrop(File file) {
        logMessage("打开GD文件: " + file.getName());
        final GdViewerFrame frame = new GdViewerFrame(file);
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
    }

    /**
     * 选择 GD 文件并打开显示（与 WindowsTools 一致，点击「打开 GD」时调用）
     */
    private void selectGdFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择GD文件");
        chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                }
                String name = f.getName();
                return name.toLowerCase().endsWith(".gd");
            }

            @Override
            public String getDescription() {
                return "GD文件 (*.gd)";
            }
        });

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            openGdFileFromDrop(file);
        }
    }

    /**
     * 初始化 UI
     */
    private void initUI() {
        setTitle("CodeTool - 生成模型代码");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        createMenuBar();
        createInitPanel();
    }

    /**
     * 创建菜单栏
     */
    private void createMenuBar() {
        // 创建菜单栏
        JMenuBar menuBar = ViewUi.menuBar(new JMenuBar());
        JMenu fileMenu = new JMenu("文件");

        JMenuItem genCodeItem = new JMenuItem("生成模型代码");
        genCodeItem.addActionListener(e -> showExcelSelectionPanel(baseCheck.XML_PATH));
        fileMenu.add(genCodeItem);

        JMenuItem tableItem = new JMenuItem("Excel/GD 看表");
        tableItem.addActionListener(e -> showExcelPanel());
        fileMenu.add(tableItem);

        // 地图范围查看器菜单项
        JMenuItem mapViewerItem = new JMenuItem("地图范围查看器");
        mapViewerItem.addActionListener(e -> showMapViewerPanel());
        fileMenu.add(mapViewerItem);

        // 打开GD文件菜单项
        JMenuItem openGdItem = new JMenuItem("打开GD文件");
        openGdItem.addActionListener(e -> selectGdFile());
        fileMenu.add(openGdItem);

        menuBar.add(fileMenu);
        setJMenuBar(menuBar);
    }

    /**
     * 创建初始面板
     */
    private void createInitPanel() {
        // 全屏显示
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);

        mainPanel = new JPanel(new BorderLayout(8, 8));
        ViewUi.page(mainPanel);
        statusBarPanel = createStatusBar();
        mainPanel.add(statusBarPanel, BorderLayout.SOUTH);
        setContentPane(mainPanel);
        setVisible(true);

        SwingUtilities.invokeLater(() -> showExcelSelectionPanel(baseCheck.XML_PATH));
    }

    /**
     * 显示模型生成选择面板（检索列表 + Sheet 树 + 列绑定）。
     *
     * @param dir
     *            配置表目录
     */
    private void showExcelSelectionPanel(File dir) {
        toggleStatusBar(true);
        if (currentCenterPanel != null) {
            mainPanel.remove(currentCenterPanel);
        }
        JPanel selectionPanel = new JPanel(new BorderLayout(8, 8));
        selectionPanel.setOpaque(false);
        JLabel titleLabel = new JLabel("选择要生成模型代码的Excel文件和Sheet", JLabel.CENTER);
        ViewUi.label(titleLabel);
        titleLabel.setFont(ViewUi.FONT_B);
        titleLabel.setForeground(ViewUi.LINK);
        selectionPanel.add(titleLabel, BorderLayout.NORTH);
        modelGenSelectionPanel = new ModelGenSelectionPanel(baseCheck, log);
        selectionPanel.add(modelGenSelectionPanel, BorderLayout.CENTER);
        currentCenterPanel = selectionPanel;
        mainPanel.add(currentCenterPanel, BorderLayout.CENTER);
        mainPanel.revalidate();
        mainPanel.repaint();
        if (catalog != null && !catalog.isEmpty()) {
            modelGenSelectionPanel.initFromCatalog(catalog);
        } else {
            scanCatalogAsync(dir);
        }
    }

    /**
     * 显示Excel面板
     */
    private void showExcelPanel() {
        toggleStatusBar(true);
        logMessage("初始化 Excel/GD 看表...");
        if (currentCenterPanel != null) {
            mainPanel.remove(currentCenterPanel);
        }
        JPanel excelPanel = new JPanel(new BorderLayout());
        excelPanel.setOpaque(false);
        excelPanel.add(ViewUi.splitH(createLeftPanel(), createRightPanel(), 0.125), BorderLayout.CENTER);
        currentCenterPanel = excelPanel;
        mainPanel.add(currentCenterPanel, BorderLayout.CENTER);
        browseFileFilter.installOn(excelPanel, this::applyBrowseFileFilter);
        mainPanel.revalidate();
        mainPanel.repaint();
        initSwingWorker();
        logMessage("等待加载Excel文件...");
    }

    /**
     * 显示地图范围查看器面板
     */
    private void showMapViewerPanel() {
        // 地图查看器隐藏底部状态栏，让地图向下铺满
        toggleStatusBar(false);
        logMessage("初始化地图范围查看器...");
        if (currentCenterPanel != null) {
            mainPanel.remove(currentCenterPanel);
        }
        File mapDir = new File(baseCheck.baseDir, MapRingUtil.MAP);
        if (!mapDir.isDirectory()) {
            mapDir = new File(baseCheck.CURR_DIR.getParentFile().getParentFile(), MapRingUtil.MAP);
        }
        currentCenterPanel = new MapViewerPanel(mapDir, true, baseCheck.GD_PATH);
        mainPanel.add(currentCenterPanel, BorderLayout.CENTER);
        mainPanel.revalidate();
        mainPanel.repaint();
        logMessage("地图范围查看器已打开，目录: " + mapDir.getAbsolutePath());
    }

    /**
     * 切换状态栏的显示与隐藏
     * 
     * @param visible
     *            是否显示
     */
    private void toggleStatusBar(boolean visible) {
        if (mainPanel == null || statusBarPanel == null) {
            return;
        }
        if (visible) {
            if (statusBarPanel.getParent() != mainPanel) {
                mainPanel.add(statusBarPanel, BorderLayout.SOUTH);
            }
        } else if (statusBarPanel.getParent() == mainPanel) {
            mainPanel.remove(statusBarPanel);
        }
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    /**
     * 创建右侧面板
     */
    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);
        panel.add(ViewUi.splitV(createSheetListPanel(), createBottomPreviewPanel(), 0.15), BorderLayout.CENTER);
        return panel;
    }

    /**
     * 创建Sheet列表面板
     */
    private JPanel createSheetListPanel() {
        sheetListModel = new DefaultListModel<>();
        sheetList = new JList<>(sheetListModel);
        sheetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ViewUi.list(sheetList);
        sheetList.addListSelectionListener(listSelectionListener);
        JPanel card = ViewUi.card("Sheet列表 (点击切换)", ViewUi.scroll(sheetList));
        card.setMinimumSize(new Dimension(0, SHEET_LIST_MIN_HEIGHT_PX));
        return card;
    }

    /**
     * Sheet 列表选择监听器
     */
    private final ListSelectionListener listSelectionListener = new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (!e.getValueIsAdjusting()) {
                int selectedIndex = sheetList.getSelectedIndex();
                if (selectedIndex >= 0 && currentExcelOperate != null) {
                    // 获取Sheet 显示名称（格式：1. SheetName）
                    String selectedItem = sheetListModel.getElementAt(selectedIndex);
                    // 提取实际的Sheet名称（去掉序号前缀）
                    String sheetName = extractSheetName(selectedItem);

                    logMessage("选择Sheet: " + sheetName + " (索引: " + selectedIndex + ")");
                    // 确保面板已创建
                    if (!isPreviewReady()) {
                        logMessage("警告: 预览面板未初始化，等待创建...");
                        SwingUtilities.invokeLater(() -> {
                            if (isPreviewReady()) {
                                loadSheetData(sheetName);
                            } else {
                                logMessage("错误: 预览面板仍未初始化", true);
                            }
                        });
                    } else {
                        loadSheetData(sheetName);
                    }
                }
            }
        }
    };

    /**
     * 从显示名称中提取实际的Sheet名称（去掉"序号. "前缀）
     */
    private String extractSheetName(String displayName) {
        if (displayName == null) {
            return "";
        }
        // 格式：1. SheetName 或 10. SheetName
        int dotIndex = displayName.indexOf(". ");
        if (dotIndex > 0) {
            return displayName.substring(dotIndex + 2);
        }
        return displayName;
    }

    /**
     * 创建底部 Excel + GD 双预览区。
     *
     * @return 左右分栏预览面板
     */
    private JPanel createBottomPreviewPanel() {
        excelPreviewPanel =
            new PagedPreviewPanel("Excel数据预览", msg -> logMessage(msg, true), PagedPreviewPanelMode.PROBE_ONLY);
        gdPreviewPanel =
            new PagedPreviewPanel("GD文件数据预览", msg -> logMessage(msg, true), PagedPreviewPanelMode.WITH_TOTAL);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);
        panel.add(ViewUi.splitH(excelPreviewPanel.createView(), gdPreviewPanel.createView(), 0.5), BorderLayout.CENTER);
        return panel;
    }

    /**
     * 异步 SAX 流式加载 Excel 预览。
     *
     * @param sheetName
     *            Sheet 名
     */
    private void loadExcelPreviewAsync(String sheetName) {
        if (currentExcelOperate == null || currentExcelOperate.file == null) {
            return;
        }
        final File xlsx = currentExcelOperate.file;
        SwingWorker<SaxPagedExcelSource, String> worker = new SwingWorker<SaxPagedExcelSource, String>() {
            @Override
            protected SaxPagedExcelSource doInBackground() throws Exception {
                publish("SAX 加载 Excel: " + sheetName);
                String[] cols = ExcelSaxHeaderReader.readColumnNames(xlsx, sheetName);
                SaxPagedExcelSource src = new SaxPagedExcelSource(xlsx, sheetName, cols, ExcelOperate.warnFromLog(log));
                publish("Excel 流式就绪（不扫全表计行数）");
                return src;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    logMessage(message);
                }
            }

            @Override
            protected void done() {
                try {
                    SaxPagedExcelSource src = get();
                    if (excelPreviewPanel == null) {
                        logMessage("错误: Excel预览面板未初始化", true);
                        return;
                    }
                    excelPreviewPanel.bindSource(src.getColumnHeaders(), src);
                } catch (Exception e) {
                    logMessage("SAX 加载 Excel 失败: " + e, true);
                }
            }
        };
        worker.execute();
    }

    /**
     * 创建状态栏（共享的日志区域）
     */
    private JPanel createStatusBar() {
        logArea = new JTextPane();
        ViewUi.logPane(logArea);
        JPanel bar = ViewUi.card("日志", ViewUi.scroll(logArea));
        bar.setPreferredSize(new Dimension(0, 180));
        return bar;
    }

    /**
     * 切换 Sheet：清空预览并重新流式加载 Excel 与 GD。
     *
     * @param sheetName
     *            Sheet 名
     */
    private void loadSheetData(String sheetName) {
        if (currentExcelOperate == null) {
            logMessage("当前Excel文件为空，无法加载Sheet数据");
            return;
        }
        if (!isPreviewReady()) {
            logMessage("警告: 预览面板未初始化，延迟加载...");
            SwingUtilities.invokeLater(() -> loadSheetData(sheetName));
            return;
        }

        excelPreviewPanel.clear();
        gdPreviewPanel.clear();
        logMessage("加载Sheet: " + sheetName);

        if (!ExcelOperate.isNotXlsxFile(currentExcelOperate.fileName)) {
            loadExcelPreviewAsync(sheetName);
        } else {
            logMessage("当前文件非 xlsx，跳过 Excel 预览: " + currentExcelOperate.fileName);
        }
        if (currentExcelOperate.file != null) {
            loadGdPreviewAsync(currentExcelOperate.file.getName(), sheetName);
        }
    }
}
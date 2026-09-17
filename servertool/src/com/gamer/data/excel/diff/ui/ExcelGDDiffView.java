package com.gamer.data.excel.diff.ui;

import static com.gamer.data.excel.framework.BaseCheck.GD;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.io.File;
import java.util.Map;

import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import com.gamer.data.excel.diff.gd.ViewFixedLineLog;
import com.gamer.data.excel.diff.gd.ProgressLogFactory;
import com.gamer.data.excel.diff.limit.LimitSvnSync;
import com.gamer.data.excel.diff.ui.busy.DiffBusyUi;
import com.gamer.data.excel.diff.ui.filetable.DiffFileTablesModule;
import com.gamer.data.excel.diff.ui.generate.DiffGdGenerateSupport;
import com.gamer.data.excel.diff.ui.log.DiffLogHighlighter;
import com.gamer.data.excel.diff.ui.scan.DiffFileScanSupport;
import com.gamer.data.excel.diff.ui.sheet.DiffCandidateScanner;
import com.gamer.data.excel.diff.ui.toolbar.DiffSideToolbar;
import com.gamer.data.excel.diff.util.SvnChangedXlsxPuller;
import com.gamer.data.excel.diff.util.ViewFileCopyUtil;
import com.gamer.data.excel.framework.AbstractViewFrameCore;
import com.gamer.data.excel.framework.BaseCheck;
import com.gamer.data.log.Log;
import com.gamer.data.excel.ui.ViewTasks;
import com.gamer.data.excel.ui.ViewDesktopUtil;
import com.gamer.data.excel.ui.ViewEdtUtil;
import com.gamer.data.ui.ViewUi;
import com.gamer.data.gdg.excel.ExcelOperate;
import com.gamer.data.gdg.progress.GdProgressContext;
import com.gamer.data.limit.ColumnLengthLimitStore;
import com.gamer.data.limit.LimitPathUtil;
import com.gamer.data.map.grid.MapRingUtil;
import com.gamer.data.map.ui.MapViewerPanel;
import com.gamer.data.message.Util;

/**
 * Excel / GD 对比主窗口（编排层）。
 * <p>
 * 差异展示：SVN 变更 ∩ 当前目录 → 内容对比全部有差异的 Sheet；生成后只比本次写出的 GD。<br>
 * 不默认选中、不点选触发对比。
 * </p>
 *
 * @author liuyunhui
 * @date 2025/12/30
 */
public class ExcelGDDiffView extends AbstractViewFrameCore {

    private static final String BASE_WINDOW_TITLE = "StrategyTool";
    private static final String CARD_EXCEL_GD_DIFF = "EXCEL_GD_DIFF";
    private static final String CARD_MAP_VIEWER = "MAP_VIEWER";

    private final DiffBusyUi busyUi;
    private final ViewTasks<GdProgressContext> backgroundTasks;
    private final DiffFileTablesModule fileTables;
    private final DiffCandidateScanner candidateScanner;
    private final DiffGdGenerateSupport gdGenerate;
    private final DiffFileScanSupport fileScan;

    private final JPanel mainPanel;
    private JPanel contentPanel;
    private CardLayout contentCardLayout;

    private final Object logDocumentLock = new Object();
    private ViewFixedLineLog fixedLineLog;

    /**
     * @param baseCheck
     *            路径与配置
     */
    public ExcelGDDiffView(BaseCheck baseCheck) {
        super(baseCheck);
        listModel = new DefaultListModel<>();
        busyUi = new DiffBusyUi(this, BASE_WINDOW_TITLE, log);
        fileTables = new DiffFileTablesModule(listModel, fileMap, baseCheck.CURR_DIR);
        candidateScanner = new DiffCandidateScanner(baseCheck);
        gdGenerate = new DiffGdGenerateSupport(fileMap);
        fileScan = new DiffFileScanSupport(this::listExcelFiles);
        backgroundTasks = createTasks();

        createMenuBar();
        setTitle(BASE_WINDOW_TITLE);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);

        mainPanel = new JPanel(new BorderLayout(8, 8));
        ViewUi.page(mainPanel);
        setContentPane(mainPanel);
        initContentPanel();
        initExcelGdUI();
        startLimitSvnUpdate();
        loadCurrentDirExcelFilesAsync();
    }

    // ======================== 布局 ========================

    private void initContentPanel() {
        contentCardLayout = new CardLayout(0, 0);
        contentPanel = new JPanel(contentCardLayout);
        mainPanel.add(contentPanel, BorderLayout.CENTER);
    }

    private void initExcelGdUI() {
        if (contentPanel == null) {
            initContentPanel();
        }
        contentPanel.add(buildExcelGdRootPanel(), CARD_EXCEL_GD_DIFF);
        showCard(CARD_EXCEL_GD_DIFF);
    }

    private JComponent buildExcelGdRootPanel() {
        return ViewUi.splitV(createWorkArea(), createStatusBar(), 0.62);
    }

    private JPanel createWorkArea() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setOpaque(false);
        root.add(DiffSideToolbar.build(this::reloadCurrentDirFiles, this::pullChangedXlsxFiles, this::generateGdFiles,
            this::copyFilesToTargetDirsNew, this::confirmAndDeleteAllCurrExcelGd, busyUi::registerLockButton),
            BorderLayout.NORTH);
        root.add(createExcelFileDisplayPanel(), BorderLayout.CENTER);
        return root;
    }

    private JComponent createExcelFileDisplayPanel() {
        // 点选不触发对比；右键仅删除限制
        return fileTables.build(this::openParentExcelFile, this::openCurrExcelFile, this::handleParentToCurrDrop,
            message -> logMessage(message, true), this::fillCurrContextMenu);
    }

    private JPanel createStatusBar() {
        logArea = new JTextPane();
        ViewUi.logPane(logArea);
        fixedLineLog = new ViewFixedLineLog(logArea, log);
        JPanel body = new JPanel(new BorderLayout(0, 4));
        body.setOpaque(false);
        body.add(busyUi.createStatusTopBar(), BorderLayout.NORTH);
        body.add(ViewUi.scroll(logArea), BorderLayout.CENTER);
        body.setMinimumSize(new Dimension(0, 100));
        return ViewUi.card("日志", body);
    }

    private void showCard(String cardName) {
        if (contentCardLayout != null && contentPanel != null) {
            contentCardLayout.show(contentPanel, cardName);
            contentPanel.revalidate();
            contentPanel.repaint();
        } else {
            logMessage("showCard失败: " + cardName);
        }
    }

    private void createMenuBar() {
        JMenuBar menuBar = ViewUi.menuBar(new JMenuBar());
        JMenu fileMenu = new JMenu("文件");
        JMenuItem item = new JMenuItem("StrategyTool");
        item.addActionListener(e -> initExcelGdUI());
        fileMenu.add(item);
        if (!baseCheck.MORE_DEEP) {
            item = new JMenuItem("地图范围查看器");
            item.addActionListener(e -> initMapViewerPanel());
            fileMenu.add(item);
        }
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);
    }

    private void initMapViewerPanel() {
        if (contentPanel == null) {
            initContentPanel();
        }
        File mapDir = new File(baseCheck.baseDir, MapRingUtil.MAP);
        if (Util.inVM()) {
            mapDir = new File(baseCheck.baseDir.getParentFile().getParentFile(), MapRingUtil.MAP);
        }
        JPanel mapViewerPanel;
        if (!mapDir.isDirectory()) {
            mapViewerPanel = new JPanel(new BorderLayout());
            JLabel label = new JLabel("未找到地图目录: " + mapDir.getAbsolutePath(), JLabel.CENTER);
            ViewUi.label(label);
            label.setFont(ViewUi.FONT_B);
            label.setForeground(ViewUi.DANGER);
            mapViewerPanel.add(label, BorderLayout.CENTER);
        } else {
            mapViewerPanel = new MapViewerPanel(mapDir, true, baseCheck.GD_PATH);
        }
        contentPanel.add(mapViewerPanel, CARD_MAP_VIEWER);
        showCard(CARD_MAP_VIEWER);
    }

    private void fillCurrContextMenu(JPopupMenu menu, String fileName) {
        JMenuItem deleteLimit = new JMenuItem("删除限制");
        deleteLimit.addActionListener(e -> confirmAndDeleteExcelLimit());
        menu.add(deleteLimit);
    }

    // ======================== 文件打开 / 拖放 ========================

    private void openParentExcelFile(String fileName) {
        File file = fileTables.getParentFileMap().get(fileName);
        if (file == null) {
            logMessage("上层目录找不到文件: " + fileName, true);
            return;
        }
        ViewDesktopUtil.openWithDesktop(file, this::logMessage);
    }

    private void openCurrExcelFile(String fileName) {
        ExcelOperate existed = fileMap.get(fileName);
        if (existed != null) {
            existed.releaseWorkbook();
        }
        ViewDesktopUtil.openWithDesktop(new File(baseCheck.CURR_DIR, fileName), this::logMessage);
    }

    private void handleParentToCurrDrop(File sourceFile) {
        if (sourceFile == null || !sourceFile.isFile()) {
            return;
        }
        String fileName = sourceFile.getName();
        File destFile = new File(baseCheck.CURR_DIR, fileName);
        if (destFile.exists()) {
            int choice = JOptionPane.showConfirmDialog(this, "当前目录已存在 " + fileName + "，是否覆盖？", "覆盖确认",
                JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                logMessage("已取消复制: " + fileName);
                return;
            }
        }
        busyUi.setStatus("状态：复制中…");
        logMessage("复制中: " + fileName);
        Thread worker = new Thread(() -> runCopyFromParentInBackground(sourceFile, fileName), "ExcelDragCopy");
        worker.setDaemon(true);
        worker.start();
    }

    private void runCopyFromParentInBackground(File sourceFile, String fileName) {
        try {
            ExcelOperate existed = fileMap.get(fileName);
            if (existed != null) {
                existed.releaseWorkbook();
            }
            ViewFileCopyUtil.copyIntoDirectory(sourceFile, fileName, baseCheck.CURR_DIR,
                message -> logMessageOnEdt(message, false));
            ViewEdtUtil.run(() -> finishCopyFromParentOnEdt(fileName));
        } catch (Exception ex) {
            logMessageOnEdt("复制失败: " + fileName + " - " + ex.getMessage(), true);
            busyUi.setStatus("状态：就绪");
        }
    }

    private void finishCopyFromParentOnEdt(String fileName) {
        File destFile = new File(baseCheck.CURR_DIR, fileName);
        ExcelOperate eo = new ExcelOperate(destFile);
        if (eo.probeReadable(log)) {
            boolean isNew = !fileMap.containsKey(fileName);
            fileMap.put(fileName, eo);
            if (isNew) {
                listModel.addElement(fileName);
            }
        }
        fileTables.showCurrCard();
        fileTables.rebuildCurrTable();
        busyUi.setStatus("状态：就绪");
        logMessage("复制完成: " + fileName);
    }

    // ======================== 工具条动作 ========================

    private void closeAllOpenWorkbooks() throws InterruptedException {
        logMessage("正在关闭已打开的文件...");
        closeExcelOperate(currentExcelOperate);
        currentExcelOperate = null;
        closeAllExcelOperates();
        fileMap.clear();
        Thread.sleep(150);
    }

    private void pullChangedXlsxFiles() {
        try {
            closeAllOpenWorkbooks();
        } catch (InterruptedException e) {
            logMessage("关闭文件失败: " + e.getMessage());
            return;
        }
        clearAllFileLists();
        startBackgroundOperation("复制变更", "SvnPullChanged", "扫描 SVN 变更 xlsx，请稍候…", batch -> {
            batch.logWait("扫描目录: " + baseCheck.XML_PATH.getAbsolutePath());
            SvnChangedXlsxPuller.pull(baseCheck.XML_PATH, baseCheck.CURR_DIR, batch::logWait);
            batch.logWait("复制变更结束，刷新文件列表…");
            scanAndOpenExcelFilesInBackground(true, batch);
        });
    }

    private void copyFilesToTargetDirsNew() {
        try {
            closeAllOpenWorkbooks();
            logMessage("文件已关闭，开始复制...");
        } catch (InterruptedException e) {
            logMessage("关闭文件失败: " + e.getMessage());
            return;
        }
        int count = ViewFileCopyUtil.copyExcelGdFromCurrAndDelete(baseCheck.CURR_DIR, baseCheck.XML_PATH,
            baseCheck.GD_PATH, GD, this::logMessage);
        logMessage("复制" + count + "个文件完成");
        reloadCurrentDirFiles();
    }

    private void confirmAndDeleteAllCurrExcelGd() {
        File currDir = baseCheck.CURR_DIR;
        File[] pending = ViewFileCopyUtil.listExcelAndGdFiles(currDir, GD);
        if (pending == null || pending.length == 0) {
            logMessage("当前目录没有 xlsx 或 gd 文件");
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
            "确定删除当前目录下全部 " + pending.length + " 个 xlsx / gd 文件吗？\n" + currDir.getAbsolutePath(), "清空确认",
            JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) {
            logMessage("已取消清空");
            return;
        }
        try {
            closeAllOpenWorkbooks();
        } catch (InterruptedException e) {
            logMessage("关闭文件失败: " + e.getMessage());
            return;
        }
        int deleted = ViewFileCopyUtil.deleteAllExcelAndGdInDir(currDir, GD, this::logMessage);
        logMessage("清空完成，已删除 " + deleted + " 个文件");
        reloadCurrentDirFiles();
    }

    private void generateGdFiles() {
        startBackgroundOperation("生成GD", "GdGenerate", "开始生成 GD 文件，请勿重复点击按钮…", this::runGenerateGdFilesAsync);
    }

    private void runGenerateGdFilesAsync(final GdProgressContext batch) {
        final long startMs = busyUi.getOperationStartMs();
        try {
            File[] excelFiles = listExcelFiles(baseCheck.CURR_DIR);
            if (excelFiles == null || excelFiles.length == 0) {
                batch.logError("生成失败：当前目录没有 Excel 文件");
                return;
            }
            DiffGdGenerateSupport.Outcome outcome =
                gdGenerate.generateAll(excelFiles, batch.getLog(), batch, busyUi::setStatus);
            if (gdGenerate.isCancelled()) {
                return;
            }
            // 方案 A：只对比本次写出 Sheet 的 GD
            candidateScanner.compareWrittenGds(outcome.writtenSheetNames, batch.getLog());
            finishGenerateGdOnEdt(batch, startMs, outcome);
        } catch (Exception e) {
            batch.logError("生成 GD 失败: " + e.getMessage());
        }
    }

    private void finishGenerateGdOnEdt(final GdProgressContext batch, final long startMs,
        final DiffGdGenerateSupport.Outcome outcome) {
        final String summary = "GD 生成汇总: 成功 " + outcome.okFiles + " 个文件(" + outcome.okSheets + " 个Sheet), 失败 "
            + outcome.failFiles + " 个";
        ViewEdtUtil.run(() -> {
            if (outcome.failFiles > 0) {
                batch.logError(summary + "，耗时 " + GdProgressContext.formatElapsed(startMs));
            } else {
                batch.logSummaryDone(startMs, summary);
            }
        });
    }

    private void reloadCurrentDirFiles() {
        closeWebhook();
        clearAllFileLists();
        startBackgroundOperation("重加载", "ExcelReload", "开始重新加载上层与当前目录 Excel 文件，请稍候…",
            progress -> scanAndOpenExcelFilesInBackground(true, progress));
    }

    private void confirmAndDeleteExcelLimit() {
        String excelName = fileTables.getSelectedCurrFileName();
        if (excelName == null) {
            logMessage("请先选中要删除限制的 Excel 文件", true);
            return;
        }
        File limitDir = LimitPathUtil.resolveLimitDirFromCurrDir();
        if (limitDir == null) {
            logMessage("limit 目录不可用，无法删除限制", true);
            return;
        }
        File limitFile = ColumnLengthLimitStore.buildLimitFile(limitDir, excelName);
        int choice = JOptionPane.showConfirmDialog(this,
            "确定删除 " + excelName + " 的全部限制配置吗？\n将删除：\n" + limitFile.getAbsolutePath(), "删除限制",
            JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        if (!limitFile.isFile()) {
            logMessage("该表无限制文件: " + limitFile.getName());
            return;
        }
        if (ColumnLengthLimitStore.deleteExcelLimitFile(limitDir, excelName)) {
            logMessage("已删除限制: " + limitFile.getName());
        } else {
            logMessage("删除限制失败: " + limitFile.getAbsolutePath(), true);
        }
    }

    // ======================== 目录扫描 + SVN 候选对比 ========================

    private void loadCurrentDirExcelFilesAsync() {
        startBackgroundOperation("启动加载", "StartupExcelLoad", "开始扫描上层与当前目录 Excel 文件，请稍候…",
            progress -> scanAndOpenExcelFilesInBackground(false, progress));
    }

    private void startLimitSvnUpdate() {
        File serverDir = LimitPathUtil.resolveCodeToolServerDir(baseCheck.baseDir);
        LimitSvnSync.updateAsync(serverDir, this::logMessageOnEdt);
    }

    private void scanAndOpenExcelFilesInBackground(final boolean fromReload, final GdProgressContext batch) {
        final long startMs = fromReload ? busyUi.getOperationStartMs() : System.currentTimeMillis();
        try {
            if (baseCheck.CURR_DIR == null || !baseCheck.CURR_DIR.exists()) {
                batch.logError("当前目录不存在: " + (baseCheck.CURR_DIR != null ? baseCheck.CURR_DIR.getPath() : "null"));
                ViewEdtUtil.run(this::showExcelFileList);
                return;
            }
            Map<String, File> newParentMap = fileScan.scanParentDir(baseCheck.CURR_DIR, batch);
            Map<String, String> newParentSvnMap = SvnChangedXlsxPuller.loadXlsxStatusByName(baseCheck.XML_PATH);
            batch.logWait("开始扫描当前目录: " + baseCheck.CURR_DIR.getAbsolutePath());
            File[] currFiles = listExcelFiles(baseCheck.CURR_DIR);
            int scanTotal = currFiles == null ? 0 : currFiles.length;
            batch.logIndexedProgress(0, Math.max(scanTotal, 1), "当前目录 xlsx 共 " + scanTotal + " 个");
            Map<String, ExcelOperate> newFileMap =
                fileScan.registerReadable(currFiles, scanTotal, batch, busyUi::setProgress);
            busyUi.setProgress(scanTotal, scanTotal, "完成");

            final int fileCount = newFileMap.size();
            final String doneMsg = (fromReload ? "重加载完成，当前目录可用 " : "当前目录找到 ") + fileCount + " 个 Excel 文件"
                + (scanTotal > fileCount ? "（" + (scanTotal - fileCount) + " 个校验失败已跳过）" : "");
            // 先刷新台账（不选中），再对 SVN 候选做内容对比
            ViewEdtUtil.runAndWait(() -> {
                fileTables.applyParentScan(newParentMap, newParentSvnMap);
                for (Map.Entry<String, ExcelOperate> entry : newFileMap.entrySet()) {
                    fileMap.put(entry.getKey(), entry.getValue());
                    listModel.addElement(entry.getKey());
                }
                showExcelFileList();
            });
            batch.logSummaryDone(startMs, doneMsg);
            candidateScanner.compareSvnCandidates(fileMap, batch.getLog());
        } catch (Exception e) {
            batch.logError("加载 Excel 文件列表失败: " + e.getMessage());
            ViewEdtUtil.run(this::showExcelFileList);
        }
    }

    private void clearAllFileLists() {
        candidateScanner.cancel();
        gdGenerate.requestCancel();
        currentExcelOperate = null;
        fileMap.clear();
        if (listModel != null) {
            listModel.clear();
        }
        fileTables.clearCurrSelection();
        fileTables.showCurrCard();
        fileTables.clearParent();
    }

    @Override
    public void showExcelFileList() {
        fileTables.showCurrCard();
        fileTables.rebuildCurrTable();
        // 不默认选中第一行
    }

    // ======================== 后台任务 / 日志 ========================

    private void startBackgroundOperation(String operationName, String threadName, String waitMessage,
        ViewTasks.Task<GdProgressContext> task) {
        backgroundTasks.start(operationName, threadName, waitMessage, task);
    }

    private ViewTasks<GdProgressContext> createTasks() {
        return new ViewTasks<>(new ViewTasks.Lifecycle<GdProgressContext>() {
            @Override
            public boolean tryBegin(String operationName) {
                return busyUi.tryBegin(operationName);
            }

            @Override
            public GdProgressContext createContext(String waitMessage) {
                GdProgressContext progress = GdProgressContext.batch(createAsyncSafeLog());
                progress.logWait(waitMessage);
                return progress;
            }

            @Override
            public void onFailure(Exception error) {
                logMessageOnEdt("后台操作失败: " + error.getMessage(), true);
            }

            @Override
            public void finish() {
                busyUi.end();
            }
        });
    }

    private Log createAsyncSafeLog() {
        return ProgressLogFactory.createEdtSafeMultiLineLog(new ProgressLogFactory.LineLogSink() {
            @Override
            public void logMessage(String message, boolean red) {
                logMessageOnEdt(message, red);
            }

            @Override
            public void rewriteProgressBlock(final String blockText, final boolean syncOnEdt) {
                Runnable task = () -> {
                    synchronized (logDocumentLock) {
                        if (fixedLineLog != null) {
                            fixedLineLog.rewriteProgressBlock(blockText);
                        }
                    }
                };
                if (syncOnEdt) {
                    ViewEdtUtil.runAndWait(task);
                } else {
                    ViewEdtUtil.run(task);
                }
            }
        });
    }

    @Override
    public void logMessage(String message, boolean redShow) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> logMessage(message, redShow));
            return;
        }
        if (fixedLineLog == null) {
            appendLogLine(message, redShow);
            return;
        }
        synchronized (logDocumentLock) {
            appendLogLine(message, redShow);
        }
    }

    private void appendLogLine(String message, boolean redShow) {
        if (!redShow && DiffLogHighlighter.tryAppendDiffLine(logArea, message)) {
            return;
        }
        super.logMessage(message, redShow);
    }

    private void logMessageOnEdt(final String message, final boolean redShow) {
        ViewEdtUtil.run(() -> ExcelGDDiffView.this.logMessage(message, redShow));
    }
}

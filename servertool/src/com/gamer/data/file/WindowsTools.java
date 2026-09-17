package com.gamer.data.file;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import com.gamer.data.file.analysis.AuthTokenPanel;
import com.gamer.data.file.client.gd.ClientGdPanel;
import com.gamer.data.file.cmd.CmdPanel;
import com.gamer.data.file.config.PathConfig;
import com.gamer.data.file.db.BrowseModule;
import com.gamer.data.file.module.FilePreviewModule;
import com.gamer.data.file.module.GdPreviewModule;
import com.gamer.data.file.search.SearchKeyDispatcher;
import com.gamer.data.file.search.SearchPanel;
import com.gamer.data.file.task.TaskPanel;
import com.gamer.data.file.tree.Collapse;
import com.gamer.data.file.tree.Renderer;
import com.gamer.data.file.tree.Reset;
import com.gamer.data.file.utils.Const;
import com.gamer.data.file.utils.Utils;
import com.gamer.data.ui.ViewUi;

/**
 * 高级文件管理器主窗口：目录树、文件列表、GD 预览与拖放。
 * 目录树 / CMD 默认配置见 {@link com.gamer.data.file.config.PathConfig}；运行时追加仅内存，见 {@link CmdPanel}。
 */
public class WindowsTools extends JFrame {
    private static final long serialVersionUID = 1L;

    /** 文件管理器运行日志 */
    private static final Logger LOGGER = Logger.getLogger(WindowsTools.class.getName());

    /** 单击与双击判定的间隔（毫秒），与文件名搜索防抖一致 */
    private static final int CLICK_INTERVAL_MS = 200;

    /** 单次文件名检索最多展示的结果数，防止大目录占满内存与界面 */
    private static final int MAX_SEARCH_RESULTS = 500;

    /** 目录节点尚未加载子项时使用的占位对象 */
    private static final String TREE_LOADING_PLACEHOLDER = "正在加载...";

    /** 根目录页签标题 */
    private static final String ROOT_TAB = "根目录";

    // 中间目录树
    private JTree directoryTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;

    // 主功能页签区
    private JTabbedPane rightTabbedPane;

    // 数据
    private final Map<String, DefaultMutableTreeNode> pathNodes = new HashMap<>();

    // 打开 GD（选择框 / 拖放共用）
    private JButton dropGdButton;

    /** 目录树单击/双击连击判定状态 */
    private final ClickDebounceState directoryTreeClickState = new ClickDebounceState();

    /** GD 预览；主窗只负责装配和切页签。 */
    private final GdPreviewModule gdPreviewModule;

    /** Text preview Module; owns file loading and pagination state. */
    private final FilePreviewModule filePreviewModule;

    /** 文件检索面板；主窗口只负责切换范围与执行结果动作。 */
    private final SearchPanel searchPanel;

    /** 数据库浏览 Module（首次打开「数据库」Tab 时懒加载） */
    private BrowseModule browseModule;

    /** 抑制树选中同步搜索范围（重置过程由 TreeReset 显式 setScope） */
    private boolean suppressScopeSync;

    /** 上一次主 Tab 下标，用于判断是否从其它 Tab 切入根目录 */
    private int lastMainTabIndex = -1;

    /**
     * 单击/双击连击判定：持有待处理项与 {@link Timer}，供目录树与文件列表各自独立使用。
     */
    private static final class ClickDebounceState {
        /** 连击判定定时器 */
        private Timer timer;

        /** 待判定是否为连击的文件/目录 */
        private File pendingFile;
    }

    /**
     * 目录树单击打开、双击跳转；{@link #CLICK_INTERVAL_MS} 内同项再点视为双击，点不同项则取消上一份待处理单击。
     *
     * @param state
     *            目录树连击状态
     * @param file
     *            本次点击对应的文件或目录
     */
    private void scheduleDebouncedItemClick(ClickDebounceState state, File file) {
        boolean isSameItem = state.pendingFile != null && state.pendingFile.equals(file);
        if (state.timer != null && state.timer.isRunning() && isSameItem) {
            state.timer.stop();
            state.timer = null;
            state.pendingFile = null;
            handleItemDoubleClick(file);
            return;
        }
        if (state.timer != null) {
            state.timer.stop();
            state.timer = null;
        }
        state.pendingFile = file;
        final File fileForTimer = file;
        state.timer = new Timer(CLICK_INTERVAL_MS, e -> {
            state.timer.stop();
            state.timer = null;
            state.pendingFile = null;
            handleItemSingleClick(fileForTimer);
        });
        state.timer.setRepeats(false);
        state.timer.start();
    }

    /**
     * 单击虚拟根「我的目录」：取消待处理单击，收起全部已展开子目录，根节点本身保持展开。
     */
    private void handleVirtualRootClick() {
        ClickDebounceState state = directoryTreeClickState;
        if (state.timer != null) {
            state.timer.stop();
            state.timer = null;
        }
        state.pendingFile = null;
        Collapse.collapseAll(directoryTree, rootNode);
    }

    /**
     * 单击落定：目录在原树中懒加载并展开，文件按扩展名使用现有查看逻辑打开。
     *
     * @param file
     *            当前项
     */
    private void handleItemSingleClick(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            TreePath selectedPath = directoryTree.getSelectionPath();
            if (selectedPath != null && isTreePathForFile(selectedPath, file)) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectedPath.getLastPathComponent();
                // 已展开再点：直接重扫；未展开走 willExpand，避免 load+expand 扫两次
                if (directoryTree.isExpanded(selectedPath)) {
                    loadDirectoryNodeChildren(node);
                }
                directoryTree.expandPath(selectedPath);
                collapseOtherBranches(selectedPath);
            }
        } else {
            choiceOpenFile(file);
            collapseOtherBranches(resolveRetainedPathFor(file));
        }
    }

    /**
     * 双击落定：在系统资源管理器中定位该项（目录本身或文件的父目录）。
     *
     * @param file
     *            当前项
     */
    private void handleItemDoubleClick(File file) {
        if (file == null) {
            return;
        }
        openFileLocation(file);
    }

    /**
     * 按标题选择主功能页签，避免布局调整后依赖固定下标。
     *
     * @param title
     *            主功能页签标题
     */
    private void selectMainTab(String title) {
        if ("数据库".equals(title)) {
            ensureDbModule();
        }
        for (int i = 0; i < rightTabbedPane.getTabCount(); i++) {
            if (title.equals(rightTabbedPane.getTitleAt(i))) {
                rightTabbedPane.setSelectedIndex(i);
                return;
            }
        }
    }

    /**
     * 懒加载数据库 Tab，避免启动时加载 DB 相关 jar 影响其它功能。
     */
    private void ensureDbModule() {
        if (browseModule != null) {
            return;
        }
        browseModule = new BrowseModule(this);
        int idx = rightTabbedPane.indexOfTab("数据库");
        if (idx >= 0) {
            rightTabbedPane.setComponentAt(idx, browseModule.getView());
        }
    }

    /**
     * 目录树鼠标适配器：单击打开、双击跳转，间隔 {@link #CLICK_INTERVAL_MS} ms。
     * 单击虚拟根「我的目录」收起全部已展开子目录。
     */
    private final MouseAdapter mouseAdapter = new MouseAdapter() {
        /**
         * 树节点点击：虚拟根收起全部子目录；其它节点选中后交给连击判定。
         *
         * @param e
         *            鼠标点击事件
         */
        @Override
        public void mouseClicked(MouseEvent e) {
            TreePath path = treeItemPathAt(e.getX(), e.getY());
            if (path == null) {
                return;
            }
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
            Object userObject = node.getUserObject();
            // 虚拟根不是 File，单独处理：单击收起全部已展开子目录
            if (node == rootNode) {
                directoryTree.setSelectionPath(path);
                handleVirtualRootClick();
                return;
            }
            if (!(userObject instanceof File)) {
                return;
            }
            File file = (File)userObject;
            directoryTree.setSelectionPath(path);
            scheduleDebouncedItemClick(directoryTreeClickState, file);
        }
    };

    /**
     * 整行命中判定：{@link JTree#getPathForLocation} 只认图标与文字那一段，点在文件名右侧空白会丢弃事件。
     * 这里按行号取节点，行内任意位置都算点中；展开箭头所在的左侧缩进区仍交给 JTree 自己处理。
     *
     * @param x
     *            相对目录树的横坐标
     * @param y
     *            相对目录树的纵坐标
     * @return 命中的树路径；未命中或落在箭头区时返回 null
     */
    private TreePath treeItemPathAt(int x, int y) {
        TreePath path = directoryTree.getClosestPathForLocation(x, y);
        if (path == null) {
            return null;
        }
        Rectangle bounds = directoryTree.getPathBounds(path);
        if (bounds == null || y < bounds.y || y >= bounds.y + bounds.height) {
            return null;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        // 虚拟根整行可点收起（含左侧箭头）；其它节点箭头区仍交给 JTree 展开
        if (node == rootNode) {
            return path;
        }
        if (x < bounds.x) {
            return null;
        }
        return path;
    }

    /**
     * 构造主窗口：载入配置路径、构建布局、拖放与全局文件搜索键盘分发。
     */
    public WindowsTools() {
        // Build deep preview Modules before the tab container is assembled.
        gdPreviewModule = new GdPreviewModule(this);
        filePreviewModule = new FilePreviewModule();
        searchPanel = createSearchPanel();
        initUI();
        loadInitialPaths();
        setupDragAndDrop();
    }

    /**
     * 创建文件搜索面板 Module，并把结果动作回接到主窗口现有文件打开与目录树定位能力。
     *
     * @return 文件搜索面板 Module
     */
    private SearchPanel createSearchPanel() {
        return new SearchPanel(MAX_SEARCH_RESULTS, CLICK_INTERVAL_MS, new SearchPanel.Listener() {
            @Override
            public void onFileSingleClick(File file) {
                handleItemSingleClick(file);
            }

            @Override
            public void onFileDoubleClick(File file) {
                handleItemDoubleClick(file);
            }

            @Override
            public void onDirectoryDoubleClick(File directory) {
                revealInDirectoryTree(directory);
            }
        });
    }

    /**
     * 初始化窗口标题、尺寸与主功能页签布局。
     */
    private void initUI() {
        setTitle("文件管理器-GD文件拖放支持");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = Math.min((int)(screenSize.width * 0.62), 1180);
        int height = (int)(screenSize.height * 0.6);

        setSize(width, height);
        setLocationRelativeTo(null);

        Container mainContainer = getContentPane();
        mainContainer.setBackground(ViewUi.PAGE);
        mainContainer.setLayout(new BorderLayout());

        createRightPanel();
        mainContainer.add(rightTabbedPane, BorderLayout.CENTER);
        installSearchKeyDispatcher();
    }

    /**
     * 注册根目录页签全局检索键盘分发，选中目录后可在页面任意位置直接输入关键词。
     */
    private void installSearchKeyDispatcher() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(
            new SearchKeyDispatcher(this, rightTabbedPane, ROOT_TAB, searchPanel));
    }

    /**
     * 创建已添加目录总列表：卡片、目录树及树点击适配器。
     *
     * @return 已组装的目录树卡片
     */
    private JPanel createCenterPanel() {
        rootNode = new DefaultMutableTreeNode("我的目录");
        treeModel = new DefaultTreeModel(rootNode);
        directoryTree = new JTree(treeModel);
        ViewUi.tree(directoryTree);
        // 目录双击由业务监听打开资源管理器，禁止 JTree 同时自动切换展开状态。
        directoryTree.setToggleClickCount(0);

        directoryTree.setCellRenderer(new Renderer());
        directoryTree.addMouseListener(mouseAdapter);
        directoryTree.addTreeSelectionListener(e -> updateSearchScopeFromTreeSelection());
        directoryTree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                loadDirectoryNodeChildren((DefaultMutableTreeNode)event.getPath().getLastPathComponent());
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
            }
        });
        // 任意展开（点名称、点箭头、程序 expandPath）都只保留当前分支
        directoryTree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                TreePath path = event.getPath();
                if (path == null || path.getPathCount() <= 1) {
                    return;
                }
                collapseOtherBranches(path);
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
            }
        });

        dropGdButton = ViewUi.click("打开 GD", this::selectGdFile);
        dropGdButton.setToolTipText("选择 GD 文件，或直接拖到窗口");
        JPanel body = new JPanel(new BorderLayout(4, 6));
        body.add(ViewUi.westEast(ViewUi.hint("单击文件打开 · 双击目录 · 单击「我的目录」收起"), dropGdButton),
            BorderLayout.NORTH);
        body.add(ViewUi.scroll(directoryTree), BorderLayout.CENTER);
        return ViewUi.card("目录树", body);
    }

    /**
     * 为主窗口注册拖放目标，落地时按扩展名分发到 GD 预览或询问加入目录树。
     */
    private void setupDragAndDrop() {
        new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE, new DropTargetAdapter() {
            /**
             * 拖放释放：转交主窗口统一处理。
             *
             * @param dtde
             *            拖放上下文事件
             */
            @Override
            public void drop(DropTargetDropEvent dtde) {
                handleMainWindowDrop(dtde);
            }
        });
    }

    /**
     * 主窗口拖放落地：GD 走预览；其它类型询问是否把目录加入左侧树。
     *
     * @param dtde
     *            拖放释放事件
     */
    private void handleMainWindowDrop(DropTargetDropEvent dtde) {
        try {
            dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
            Transferable transferable = dtde.getTransferable();

            if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                dtde.dropComplete(true);
                return;
            }

            List<File> files = (List<File>)transferable.getTransferData(DataFlavor.javaFileListFlavor);
            if (files.isEmpty()) {
                dtde.dropComplete(true);
                return;
            }

            File file = files.get(0);
            String extension = Utils.getFileExtension(file.getName());

            if (Const.GD.equalsIgnoreCase(extension)) {
                runGdDropWithUiFeedback(file);
                dtde.dropComplete(true);
                return;
            }

            promptAddNonGdFileToTree(file);
            dtde.dropComplete(true);
        } catch (Exception e) {
            dtde.dropComplete(false);
            JOptionPane.showMessageDialog(this, "拖放失败: " + e, "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 拖放 GD 时先禁用「打开 GD」按钮，读完后在 EDT 中打开预览并恢复按钮。
     *
     * @param file
     *            被拖入的 GD 文件
     */
    private void runGdDropWithUiFeedback(final File file) {
        dropGdButton.setText("正在加载...");
        dropGdButton.setEnabled(false);
        openGdFile(file);
    }

    /**
     * 非 GD 拖入时询问用户是否将文件所在目录（或目录本身）加入左侧树。
     *
     * @param file
     *            拖入的首个文件或目录
     */
    private void promptAddNonGdFileToTree(File file) {
        int choice = JOptionPane.showConfirmDialog(this, "文件: " + file.getName() + "\n不是GD文件。是否添加到目录树？", "添加文件",
            JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        if (file.isDirectory()) {
            addDirectoryToTree(file);
        } else {
            addDirectoryToTree(file.getParentFile());
        }
    }

    /**
     * 读取 GD 数据、切换到「GD 文件查看」选项卡并刷新表格展示。
     *
     * @param file
     *            磁盘上的 GD 文件
     */
    private void openGdFile(File file) {
        gdPreviewModule.openFileAsync(file, success -> {
            if (success) {
                selectMainTab("GD文件查看");
            }
            // 拖放和文件选择共用收尾；普通打开时重复设置无副作用。
            dropGdButton.setText("打开 GD");
            dropGdButton.setEnabled(true);
        });
    }

    /**
     * 通过文件选择框选择 GD 文件并打开。
     */
    private void selectGdFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择GD文件");
        chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            /**
             * 仅目录与 gd 扩展名通过过滤。
             *
             * @param f
             *            候选文件或目录
             * @return 接受则 true
             */
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                }
                return Const.GD.equalsIgnoreCase(Utils.getFileExtension(f.getName()));
            }

            /**
             * 描述文件过滤器说明文案。
             *
             * @return 说明字符串
             */
            @Override
            public String getDescription() {
                return "GD文件 (*.gd)";
            }
        });

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            openGdFile(file);
        }
    }

    /**
     * 创建主功能页签：目录、CMD、GD 文件查看；目录页使用左右分栏展示目录树与文件检索。
     */
    private void createRightPanel() {
        rightTabbedPane = ViewUi.tabs();
        rightTabbedPane.setPreferredSize(new Dimension(720, 600));

        JPanel directoryPanel = new JPanel(new BorderLayout(6, 6));
        ViewUi.page(directoryPanel);
        directoryPanel.add(ViewUi.splitH(createCenterPanel(), ViewUi.card("文件检索", searchPanel.getView()), 0.38),
            BorderLayout.CENTER);
        rightTabbedPane.addTab(ROOT_TAB, directoryPanel);

        JPanel cmdPanel = CmdPanel.getOrCreateCmdPanel();
        rightTabbedPane.addTab("CMD", cmdPanel);
        rightTabbedPane.addTab(TaskPanel.TAB_TITLE, TaskPanel.create());

        JPanel gdViewerPanel = gdPreviewModule.getView();
        rightTabbedPane.addTab("GD文件查看", gdViewerPanel);
        rightTabbedPane.addTab(ClientGdPanel.TAB_TITLE, new ClientGdPanel(this::revealInDirectoryTree).getView());

        rightTabbedPane.addTab("数据库", new JPanel());
        rightTabbedPane.addTab("analysis", AuthTokenPanel.create());

        rightTabbedPane.setSelectedIndex(0);
        initRootTabListener();
    }

    /**
     * 启动时把 {@link PathConfig#INITIAL_PATHS} 中存在的目录全部挂到树根下。
     */
    private void loadInitialPaths() {
        for (String pathStr : PathConfig.INITIAL_PATHS) {
            File path = new File(pathStr);
            if (path.exists() && path.isDirectory()) {
                addDirectoryToTree(path);
            }
        }
    }

    /**
     * 将指定目录作为树节点挂入根；若为新路径则追加到 {@link PathConfig#INITIAL_PATHS}（仅内存）。
     *
     * @param directory
     *            要挂入的树目录（需为存在且可访问）
     */
    void addDirectoryToTree(File directory) {
        if (pathNodes.containsKey(directory.getAbsolutePath())) {
            return;
        }
        String absPath = directory.getAbsolutePath();
        if (!PathConfig.INITIAL_PATHS.contains(absPath)) {
            PathConfig.INITIAL_PATHS.add(absPath);
        }

        DefaultMutableTreeNode dirNode = new DefaultMutableTreeNode(directory);
        prepareDirectoryNode(dirNode);
        rootNode.add(dirNode);
        pathNodes.put(directory.getAbsolutePath(), dirNode);
        treeModel.reload();

        directoryTree.expandPath(new TreePath(rootNode.getPath()));
    }

    /**
     * 按扩展名分流：GD 走表格预览，文本类走内置查看器，其余 {@link Desktop#open}。
     *
     * @param file
     *            要打开的文件
     */
    private void choiceOpenFile(File file) {
        String extension = Utils.getFileExtension(file.getName());
        switch (extension) {
            case Const.GD:
                openGdFile(file);
                break;
            case Const.TXT:
            case Const.PROTO:
            case Const.BAT:
                createFileContentViewer(file);
                break;
            default:
                try {
                    Desktop.getDesktop().open(file);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "无法打开文件: " + ex, "错误", JOptionPane.ERROR_MESSAGE);
                }
        }
    }

    /**
     * 读取文件全文至内存，创建分页文本区并挂到「文件内容」选项卡。
     *
     * @param file
     *            要预览的文本类文件
     */
    private void createFileContentViewer(File file) {
        try {
            // Delegate file loading, metadata and page rendering to one deep Module.
            installOrReplaceFileContentTab(filePreviewModule.open(file));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "无法读取文件: " + e.getMessage());
        }
    }

    /**
     * 若已存在「文件内容」选项卡则替换组件，否则新建并选中。
     *
     * @param fileContentPanel
     *            文件内容整页面板
     */
    private void installOrReplaceFileContentTab(JPanel fileContentPanel) {
        int tabCount = rightTabbedPane.getTabCount();
        for (int i = 0; i < tabCount; i++) {
            if ("文件内容".equals(rightTabbedPane.getTitleAt(i))) {
                rightTabbedPane.setComponentAt(i, fileContentPanel);
                rightTabbedPane.setSelectedIndex(i);
                return;
            }
        }
        int gdTabIndex = rightTabbedPane.indexOfTab("GD文件查看");
        int insertIndex = gdTabIndex >= 0 ? gdTabIndex : rightTabbedPane.getTabCount();
        rightTabbedPane.insertTab("文件内容", null, fileContentPanel, null, insertIndex);
        rightTabbedPane.setSelectedIndex(rightTabbedPane.indexOfComponent(fileContentPanel));
    }

    /**
     * 未加载目录先放占位，Swing 才能显示展开箭头。
     */
    private void prepareDirectoryNode(DefaultMutableTreeNode node) {
        node.removeAllChildren();
        node.add(new DefaultMutableTreeNode(TREE_LOADING_PLACEHOLDER));
    }

    /**
     * 判断树路径末端是否对应指定文件。
     *
     * @param path
     *            待判断树路径
     * @param file
     *            目标文件或目录
     * @return 路径末端对应同一绝对路径时返回 true
     */
    private boolean isTreePathForFile(TreePath path, File file) {
        Object last = ((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
        return last instanceof File && ((File)last).getAbsolutePath().equals(file.getAbsolutePath());
    }

    /**
     * 重扫磁盘填充直接子项。目录优先，同组忽略大小写。
     */
    private void loadDirectoryNodeChildren(DefaultMutableTreeNode node) {
        Object userObject = node.getUserObject();
        if (!(userObject instanceof File)) {
            return;
        }
        File directory = (File)userObject;
        if (!directory.isDirectory()) {
            return;
        }

        node.removeAllChildren();
        File[] children = directory.listFiles();
        if (children != null) {
            List<File> sortedChildren = new ArrayList<>();
            Collections.addAll(sortedChildren, children);
            sortedChildren.sort((left, right) -> {
                if (left.isDirectory() != right.isDirectory()) {
                    return left.isDirectory() ? -1 : 1;
                }
                return left.getName().compareToIgnoreCase(right.getName());
            });
            for (File child : sortedChildren) {
                DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(child);
                if (child.isDirectory()) {
                    prepareDirectoryNode(childNode);
                }
                node.add(childNode);
            }
        }
        final TreePath nodePath = new TreePath(node.getPath());
        treeModel.nodeStructureChanged(node);
        // nodeStructureChanged 会清展开态；推迟到下一帧再 expand，避免 willExpand 重入
        SwingUtilities.invokeLater(() -> directoryTree.expandPath(nodePath));
    }

    /**
     * 根据目录树选中项更新文件检索范围；选中文件时沿用其所在目录，仅虚拟根节点禁用输入。
     */
    private void updateSearchScopeFromTreeSelection() {
        if (suppressScopeSync) {
            return;
        }
        TreePath selectedPath = directoryTree.getSelectionPath();
        if (selectedPath == null) {
            clearSearchScope();
            return;
        }
        Object selectedObject = ((DefaultMutableTreeNode)selectedPath.getLastPathComponent()).getUserObject();
        if (!(selectedObject instanceof File)) {
            clearSearchScope();
            return;
        }
        File selected = (File)selectedObject;
        File selectedDirectory = selected.isDirectory() ? selected : selected.getParentFile();
        if (selectedDirectory == null || !selectedDirectory.isDirectory()) {
            clearSearchScope();
            return;
        }

        searchPanel.setScope(selectedDirectory);
        LOGGER.info("文件检索范围切换: " + selectedDirectory.getAbsolutePath());
    }

    /**
     * 清除检索范围与结果，并将输入区恢复为等待选择目录状态。
     */
    private void clearSearchScope() {
        searchPanel.clearScope();
    }

    /**
     * 重置目录树：全收后定位到当前上下文所属的已添加根。
     */
    private void resetTree() {
        suppressScopeSync = true;
        try {
            Reset.apply(directoryTree, rootNode, pathNodes, createTreeScope(), resolveTreeSelected());
        } finally {
            suppressScopeSync = false;
        }
    }

    /**
     * @return 树选中项（文件或目录），无选中或非 File 时 null
     */
    private File resolveTreeSelected() {
        TreePath path = directoryTree.getSelectionPath();
        if (path == null) {
            return null;
        }
        Object userObject = ((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
        if (userObject instanceof File) {
            return (File)userObject;
        }
        return null;
    }

    /**
     * @return 供 TreeReset 读写搜索范围的桥接
     */
    private Reset.Scope createTreeScope() {
        return new Reset.Scope() {
            @Override
            public File getScope() {
                return searchPanel.getScope();
            }

            @Override
            public void setScope(File directory) {
                searchPanel.setScope(directory);
            }

            @Override
            public void clearScope() {
                clearSearchScope();
            }
        };
    }

    /**
     * 从根目录切走时收起目录树；从其它 Tab 再切入时 reset。启动首次选中不触发。
     */
    private void initRootTabListener() {
        final int rootTabIndex = rightTabbedPane.indexOfTab(ROOT_TAB);
        lastMainTabIndex = rootTabIndex;
        rightTabbedPane.addChangeListener(e -> {
            int idx = rightTabbedPane.getSelectedIndex();
            if (idx < 0) {
                return;
            }
            String title = rightTabbedPane.getTitleAt(idx);
            if ("数据库".equals(title)) {
                ensureDbModule();
            }
            // 从根目录切走：先收起，避免其它页回来时仍摊开多支
            if (lastMainTabIndex == rootTabIndex && idx != rootTabIndex) {
                Collapse.collapseAll(directoryTree, rootNode);
                Collapse.expandVirtualRoot(directoryTree, rootNode);
            }
            // 从其它 Tab 切入根目录时整理目录树
            if (idx == rootTabIndex && lastMainTabIndex >= 0 && lastMainTabIndex != rootTabIndex) {
                resetTree();
            }
            lastMainTabIndex = idx;
        });
    }

    /**
     * 切到「根目录」并展开目标文件夹。单击打开文件、双击打开目录仍走树监听。
     *
     * @param target
     *            文件则展开其父目录
     */
    void revealInDirectoryTree(final File target) {
        SwingUtilities.invokeLater(() -> {
            File dir = target == null ? null : (target.isDirectory() ? target : target.getParentFile());
            if (dir == null || !dir.isDirectory()) {
                return;
            }
            File mount = Reset.findAddedRoot(dir, pathNodes);
            if (mount == null) {
                addDirectoryToTree(dir);
                mount = dir;
            }
            selectMainTab(ROOT_TAB);
            final File mountRoot = mount;
            final File expandTarget = dir;
            SwingUtilities.invokeLater(() -> expandMountedDirectory(mountRoot, expandTarget));
        });
    }

    /**
     * 从已挂载根懒加载并展开到目标目录，选中后收起其它分支。
     *
     * @param mount
     *            pathNodes 中的挂载根
     * @param target
     *            要展开到的目录
     */
    private void expandMountedDirectory(File mount, File target) {
        DefaultMutableTreeNode currentNode = pathNodes.get(mount.getAbsolutePath());
        if (currentNode == null) {
            return;
        }
        TreePath currentTreePath = new TreePath(currentNode.getPath());
        java.nio.file.Path relativePath = mount.toPath().toAbsolutePath().normalize()
            .relativize(target.toPath().toAbsolutePath().normalize());
        loadDirectoryNodeChildren(currentNode);
        directoryTree.expandPath(currentTreePath);
        for (java.nio.file.Path segment : relativePath) {
            File childFile = new File((File)currentNode.getUserObject(), segment.toString());
            DefaultMutableTreeNode childNode = findChildNode(currentNode, childFile);
            if (childNode == null) {
                break;
            }
            currentNode = childNode;
            currentTreePath = currentTreePath.pathByAddingChild(childNode);
            if (childFile.isDirectory()) {
                loadDirectoryNodeChildren(currentNode);
                directoryTree.expandPath(currentTreePath);
            }
        }
        directoryTree.setSelectionPath(currentTreePath);
        directoryTree.scrollPathToVisible(currentTreePath);
        collapseOtherBranches(currentTreePath);
    }

    /**
     * 为文件或目录找到需要保留展开的目录树路径。
     *
     * @param file
     *            目标文件或目录
     * @return 目录树路径；找不到时返回 null
     */
    private TreePath resolveRetainedPathFor(File file) {
        if (file == null) {
            return null;
        }
        File directory = file.isDirectory() ? file : file.getParentFile();
        TreePath selectedPath = directoryTree.getSelectionPath();
        if (directory != null && selectedPath != null && isTreePathForFile(selectedPath, directory)) {
            return selectedPath;
        }
        return null;
    }

    /**
     * 折叠除当前路径及其祖先、子孙以外的所有展开目录，让目录树始终只保留一个展开分支。
     *
     * @param retainedPath
     *            需要保留展开的当前路径（含自身及已展开子节点）
     */
    private void collapseOtherBranches(TreePath retainedPath) {
        Collapse.collapseOthers(directoryTree, rootNode, retainedPath);
    }

    /**
     * 在已加载父节点的直接子项中查找指定绝对路径节点。
     *
     * @param parentNode
     *            已加载父节点
     * @param targetFile
     *            目标文件或目录
     * @return 匹配节点，未找到时返回 null
     */
    private DefaultMutableTreeNode findChildNode(DefaultMutableTreeNode parentNode, File targetFile) {
        for (int i = 0; i < parentNode.getChildCount(); i++) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)parentNode.getChildAt(i);
            Object childObject = childNode.getUserObject();
            if (childObject instanceof File
                && ((File)childObject).getAbsolutePath().equals(targetFile.getAbsolutePath())) {
                return childNode;
            }
        }
        return null;
    }

    /**
     * 打开文件所在目录
     *
     * @param file
     *            文件
     */
    private void openFileLocation(File file) {
        try {
            File location = file.isDirectory() ? file : file.getParentFile();
            if (location != null && location.exists()) {
                Desktop.getDesktop().open(location);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "无法打开目录: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 程序入口：统一外观后打开主窗口。
     *
     * @param args
     *            命令行参数（未使用）
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ViewUi.installLook();
            WindowsTools app = new WindowsTools();
            app.setVisible(true);
        });
    }
}

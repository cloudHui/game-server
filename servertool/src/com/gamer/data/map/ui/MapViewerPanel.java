package com.gamer.data.map.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.HierarchyEvent;
import java.io.File;
import java.util.List;

import javax.swing.JLayeredPane;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

import com.gamer.data.map.grid.MapData;
import com.gamer.data.map.grid.MapFileLoader;
import com.gamer.data.map.grid.MapRingUtil;
import com.gamer.data.map.chapter.ChapterOffset;
import com.gamer.data.map.chapter.ChapterOffsetLoader;
import com.gamer.data.map.level.LevelDocument;
import com.gamer.data.map.level.LevelFileLoader;
import com.gamer.data.map.level.LevelNodeBean;
import com.gamer.data.map.ui.canvas.MapMiniMapPanel;
import com.gamer.data.map.ui.canvas.MapViewerCanvas;
import com.gamer.data.map.ui.distance.MeasureController;
import com.gamer.data.map.ui.path.PathDebugController;
import com.gamer.data.map.ui.path.PathDebugPanel;
import com.gamer.data.map.ui.sidebar.LevelNodeSidebarPanel;
import com.gamer.data.map.grid.MapFileCatalogModule;
import com.gamer.data.map.ui.sidebar.MapChapterFilePanel;
import com.gamer.data.map.ui.sidebar.MapLevelFilePanel;
import com.gamer.data.map.ui.sidebar.MapToolControlPanel;

/**
 * 地图查看器编排层：组装侧栏、画布、寻路与测距子模块。
 */
public class MapViewerPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** 左侧整体分割比例：上方地图文件区占比 */
    private static final double LEFT_FULL_SPLIT_RATIO = 0.35D;
    /** 左侧下半分割比例：上方关卡文件区占比 */
    private static final double LEFT_LOWER_SPLIT_RATIO = 0.45D;
    /** 左侧文件区固定宽度，避免按窗口比例撑出空白列 */
    private static final int LEFT_SIDEBAR_WIDTH_PX = 270;

    private final File mapDir;
    private final boolean edit;
    private final MapViewerSession session = new MapViewerSession();
    private final PathDebugController pathDebugController;
    private final MeasureController distanceController;
    private JLabel clickCordLabel;
    private JScrollPane mapScrollPane;
    private MapMiniMapPanel miniMapPanel;
    private MapChapterFilePanel chapterFilePanel;
    private MapLevelFilePanel levelFilePanel;
    private MapToolControlPanel toolControlPanel;
    private LevelNodeSidebarPanel levelNodeSidebar;

    /**
     * @param mapDir
     *            地图 txt 目录
     * @param edit
     *            是否可编辑关卡节点
     */
    public MapViewerPanel(File mapDir, boolean edit) {
        this(mapDir, edit, null);
    }

    /**
     * @param mapDir
     *            地图 txt 目录
     * @param edit
     *            是否可编辑关卡节点
     * @param gdDataDir
     *            GD/data 目录；CodeTool 与 StrategyTool 各自传入 {@code baseCheck.GD_PATH}
     */
    public MapViewerPanel(File mapDir, boolean edit, File gdDataDir) {
        this.edit = edit;
        this.mapDir = mapDir != null ? mapDir : new File(".");
        // 记下 GD 目录，切地图时读 LevelChapter 偏移
        session.gdDataDir = gdDataDir;
        distanceController = new MeasureController(session);
        pathDebugController = new PathDebugController(session, this::onPathDebugMapViewChanged);
        setLayout(new BorderLayout(5, 5));
        add(createMainLayout(), BorderLayout.CENTER);
        loadFileCatalog();
    }

    /**
     * 主布局：左栏固定宽度 + 右侧地图区。
     */
    private JPanel createMainLayout() {
        JPanel mapArea = createMapPanel();
        JPanel leftPanel = createLeftPanel();

        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.add(leftPanel, BorderLayout.WEST);
        panel.add(mapArea, BorderLayout.CENTER);
        toolControlPanel.updateZoomButtonStates();
        return panel;
    }

    /**
     * 左侧文件列表、工具栏与关卡节点侧栏。
     */
    private JPanel createLeftPanel() {
        toolControlPanel = new MapToolControlPanel(session, distanceController);
        levelNodeSidebar = new LevelNodeSidebarPanel(session, edit, this, createLevelNodeListener());
        chapterFilePanel = new MapChapterFilePanel(toolControlPanel, this::loadMapFile);
        levelFilePanel = new MapLevelFilePanel(this::loadLevelFile);

        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(createLeftSplitPane(), BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(LEFT_SIDEBAR_WIDTH_PX, 320));
        panel.setMinimumSize(new Dimension(LEFT_SIDEBAR_WIDTH_PX, 200));
        return panel;
    }

    /**
     * 关卡节点侧栏事件：选中同步、删除重置、寻路网格刷新。
     */
    private LevelNodeSidebarPanel.LevelNodeListener createLevelNodeListener() {
        return new LevelNodeSidebarPanel.LevelNodeListener() {
            @Override
            public void onLevelNodeSelected(LevelNodeBean node) {
                syncUiToSelectedLevelNode(node);
            }

            @Override
            public void onLevelNodesChanged() {
                pathDebugController.onPathSourceChanged();
            }

            @Override
            public void onLevelNodeDeleted() {
                toolControlPanel.getWorldXField().setText("0");
                toolControlPanel.getWorldZField().setText("0");
                if (clickCordLabel != null) {
                    clickCordLabel.setText("点击地图 → 世界: (—, —)  格子: (—, —)");
                }
            }

            @Override
            public void onStatusMessage(String message) {
                if (clickCordLabel != null) {
                    clickCordLabel.setText(message);
                }
            }
        };
    }

    /**
     * 左栏纵向分割：地图文件 / 关卡文件 / 关卡节点。
     */
    private JSplitPane createLeftSplitPane() {
        JSplitPane lowerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        lowerSplit.setTopComponent(levelFilePanel);
        lowerSplit.setBottomComponent(levelNodeSidebar);
        lowerSplit.setDividerLocation(LEFT_LOWER_SPLIT_RATIO);
        lowerSplit.setResizeWeight(LEFT_LOWER_SPLIT_RATIO);

        JSplitPane fullSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        fullSplit.setTopComponent(chapterFilePanel);
        fullSplit.setBottomComponent(lowerSplit);
        fullSplit.setDividerLocation(LEFT_FULL_SPLIT_RATIO);
        fullSplit.setResizeWeight(LEFT_FULL_SPLIT_RATIO);
        bindLeftSplitOnFirstShow(fullSplit, lowerSplit);
        return fullSplit;
    }

    /**
     * 窗口首次显示后恢复左栏内部分割比例（避免初始 layout 宽度为 0 时失效）。
     */
    private void bindLeftSplitOnFirstShow(final JSplitPane fullSplit, final JSplitPane lowerSplit) {
        fullSplit.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && fullSplit.isShowing()) {
                SwingUtilities.invokeLater(() -> {
                    fullSplit.setDividerLocation(LEFT_FULL_SPLIT_RATIO);
                    lowerSplit.setDividerLocation(LEFT_LOWER_SPLIT_RATIO);
                });
            }
        });
    }

    /**
     * 扫描地图目录，填充章节与关卡文件列表。
     */
    private void loadFileCatalog() {
        MapFileCatalogModule.Catalog catalog = MapFileCatalogModule.scan(mapDir);
        if (levelFilePanel != null) {
            levelFilePanel.setFiles(catalog.levelFiles);
        }
        if (chapterFilePanel != null) {
            chapterFilePanel.setFiles(catalog.mapFiles);
        }
    }

    /**
     * 右侧地图区：寻路栏 + 主画布 + 小地图 + 坐标状态栏。
     */
    private JPanel createMapPanel() {
        JPanel right = new JPanel(new BorderLayout(0, 0));
        session.canvas = new MapViewerCanvas();
        wireMapCanvasListeners(session.canvas);
        clickCordLabel = new JLabel("点击地图 → 世界: (—, —)  格子: (—, —)；可拖拽平移");
        right.add(clickCordLabel, BorderLayout.SOUTH);
        right.add(createMapColumn(new PathDebugPanel(pathDebugController)), BorderLayout.CENTER);
        return right;
    }

    /**
     * 注册主画布缩放、点击与右键菜单回调。
     */
    private void wireMapCanvasListeners(MapViewerCanvas canvas) {
        canvas.setZoomStateListener(this::onMainMapZoomOrLayoutChanged);
        canvas.setMapClickListener(this::onMapClicked);
        canvas.setMapPopupListener(this::onMapCanvasPopup);
    }

    /**
     * 寻路栏（西）+ 主地图叠层（中）。
     */
    private JPanel createMapColumn(PathDebugPanel pathDebugPanel) {
        mapScrollPane = new JScrollPane(session.canvas);
        mapScrollPane.setBorder(null);
        mapScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        mapScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        miniMapPanel = new MapMiniMapPanel();
        miniMapPanel.setNavigateListener(MapViewerPanel.this::onMiniMapNavigated);
        miniMapPanel.bind(session.canvas, mapScrollPane);

        JLayeredPane mapOverlay = createMapOverlayContainer(mapScrollPane, miniMapPanel);
        JPanel mapColumn = new JPanel(new BorderLayout(0, 0));
        mapColumn.add(pathDebugPanel, BorderLayout.WEST);
        mapColumn.add(mapOverlay, BorderLayout.CENTER);
        return mapColumn;
    }

    /**
     * 主地图滚动区与小地图浮层叠放。
     */
    private JLayeredPane createMapOverlayContainer(JScrollPane scroll, MapMiniMapPanel miniMap) {
        MapLayeredPane layered = new MapLayeredPane();
        layered.add(scroll, JLayeredPane.DEFAULT_LAYER);

        MapMiniMapPanel.MapMiniMapOverlayHost miniWrap = createMiniMapOverlayWrapper(miniMap);
        layered.add(miniWrap, JLayeredPane.PALETTE_LAYER);
        layered.setMiniMapOverlay(miniWrap);
        return layered;
    }

    private MapMiniMapPanel.MapMiniMapOverlayHost createMiniMapOverlayWrapper(MapMiniMapPanel miniMap) {
        MapMiniMapPanel.MapMiniMapOverlayHost wrap = new MapMiniMapPanel.MapMiniMapOverlayHost(new BorderLayout(0, 0));
        wrap.add(miniMap, BorderLayout.CENTER);
        Dimension miniSize = miniMap.getPreferredSize();
        wrap.setPreferredSize(miniSize);
        wrap.setMinimumSize(miniSize);
        wrap.setMaximumSize(miniSize);
        return wrap;
    }

    private void onMainMapZoomOrLayoutChanged() {
        if (toolControlPanel != null) {
            toolControlPanel.updateZoomButtonStates();
        }
        if (miniMapPanel != null) {
            miniMapPanel.onMainViewChanged();
        }
    }

    private void onPathDebugMapViewChanged() {
        if (miniMapPanel != null) {
            miniMapPanel.onMapChanged();
        }
        if (toolControlPanel != null) {
            toolControlPanel.updateZoomButtonStates();
        }
    }

    /**
     * 小地图点击跳转后，更新底部坐标状态栏。
     */
    private void onMiniMapNavigated(int canvasX, int canvasY) {
        if (session.canvas == null || session.currentMapData == null) {
            return;
        }
        int[] world = session.canvas.pixelToWorld(canvasX, canvasY);
        if (world == null) {
            return;
        }
        int col = MapRingUtil.worldToTileX(world[0], session.currentMapData.getWidth());
        int row = MapRingUtil.worldToTileZ(world[1], session.currentMapData.getHeight());
        clickCordLabel.setText("世界: (" + world[0] + ", " + world[1] + ")  格子: (" + col + ", " + row + ")");
    }

    /**
     * 主地图左键：寻路模式下选点，否则选中节点或打标记。
     */
    private void onMapClicked(int pixelX, int pixelY) {
        if (session.canvas == null || session.currentMapData == null) {
            return;
        }
        int[] world = session.canvas.pixelToWorld(pixelX, pixelY);
        if (world == null) {
            session.canvas.setSelectedLevelNode(null);
            clickCordLabel.setText("点击地图 → 世界: (—, —)  格子: (—, —)");
            return;
        }
        if (pathDebugController.isPathSimulationActive()) {
            handlePathModeMapClick(world[0], world[1]);
            return;
        }
        handleEditModeMapClick(pixelX, pixelY, world[0], world[1]);
    }

    /**
     * 寻路模拟开启时：同步坐标输入并交给控制器选起/终点。
     */
    private void handlePathModeMapClick(int worldX, int worldZ) {
        int pathCol = MapRingUtil.worldToTileX(worldX, session.currentMapData.getWidth());
        int pathRow = MapRingUtil.worldToTileZ(worldZ, session.currentMapData.getHeight());
        clickCordLabel.setText("世界: (" + worldX + ", " + worldZ + ")  格子: (" + pathCol + ", " + pathRow + ")");
        toolControlPanel.getWorldXField().setText(String.valueOf(worldX));
        toolControlPanel.getWorldZField().setText(String.valueOf(worldZ));
        pathDebugController.handlePathClick(worldX, worldZ);
    }

    /**
     * 普通模式：优先选中关卡节点，否则更新坐标标记。
     */
    private void handleEditModeMapClick(int pixelX, int pixelY, int worldX, int worldZ) {
        LevelNodeBean node = session.canvas.getLevelNodeAt(pixelX, pixelY);
        if (node != null) {
            syncUiToSelectedLevelNode(node);
            return;
        }
        session.canvas.setSelectedLevelNode(null);
        int col = MapRingUtil.worldToTileX(worldX, session.currentMapData.getWidth());
        int row = MapRingUtil.worldToTileZ(worldZ, session.currentMapData.getHeight());
        clickCordLabel.setText("世界: (" + worldX + ", " + worldZ + ")  格子: (" + col + ", " + row + ")");
        toolControlPanel.getWorldXField().setText(String.valueOf(worldX));
        toolControlPanel.getWorldZField().setText(String.valueOf(worldZ));
        session.canvas.setMarkerPoint(worldX, worldZ);
    }

    /**
     * 加载章节地图 txt 并刷新画布；有配对关卡则继续加载关卡。
     *
     * @param fileName
     *            地图文件名
     */
    private void loadMapFile(String fileName) {
        // 空名直接返回
        if (fileName == null) {
            return;
        }

        // 读章节地块
        File file = new File(mapDir, fileName);
        MapData data = MapFileLoader.load(file);
        if (data == null) {
            clickCordLabel.setText("加载地图失败: " + file.getAbsolutePath());
            return;
        }

        // 写入会话：当前地图
        session.currentMapData = data;
        session.currentMapFileName = fileName;
        pathDebugController.setToolMapData(data);
        session.canvas.setMapData(data);
        pathDebugController.setCurrentMapFile(file);
        if (miniMapPanel != null) {
            miniMapPanel.onMapChanged();
        }

        // 清标记与测距
        session.canvas.clearMarkerPoint();
        distanceController.clearDistanceInputs();
        pathDebugController.onPathSourceChanged();

        // 有配对关卡交给 loadLevelFile（里面读偏移）；没有则只按地图名读偏移
        if (!loadPairedLevelFile(fileName)) {
            refreshChapterOffset();
            clickCordLabel.setText("点击地图 → 世界: (—, —)  格子: (—, —)；" + session.chapterOffset.info);
        }
    }

    /**
     * 按地图名拼 _Level.txt；存在则加载。
     *
     * @param mapFileName
     *            地图文件名
     * @return true 已加载关卡；false 没有配对文件
     */
    private boolean loadPairedLevelFile(String mapFileName) {
        // 参数不合法
        if (mapFileName == null || !mapFileName.endsWith(".txt") || levelFilePanel == null) {
            return false;
        }

        // 拼关卡名并检查是否存在
        String levelName = mapFileName.substring(0, mapFileName.length() - 4) + "_Level.txt";
        if (!new File(mapDir, levelName).isFile()) {
            return false;
        }

        // 选中列表并加载
        levelFilePanel.selectFile(levelName);
        loadLevelFile(levelName);
        return true;
    }

    /**
     * 加载关卡：读 GD 偏移 → 判断要不要用 → 节点改本地坐标 → 刷新界面。
     *
     * @param fileName
     *            关卡文件名
     */
    private void loadLevelFile(String fileName) {
        // 画布未就绪则返回
        if (fileName == null || session.canvas == null) {
            return;
        }

        // 记下关卡名并读 GD 里的章节偏移
        session.currentLevelFileName = fileName;
        refreshChapterOffset();

        // 读关卡文件
        File file = new File(mapDir, fileName);
        session.currentLevelDoc = null;
        try {
            // 先按世界坐标读出节点（此时文档原点仍为 0）
            session.currentLevelDoc = LevelDocument.load(file);
            // 决定是否应用 GD 偏移，再写入文档原点
            int[] origin = decideDisplayOrigin(session.currentLevelDoc.getFlatBeans());
            session.currentLevelDoc.setChapterWorldOrigin(origin[0], origin[1]);
            session.currentLevelNodes = session.currentLevelDoc.getFlatBeans();
        } catch (Exception e) {
            // 回退：只读列表
            clickCordLabel.setText(e.getMessage());
            session.currentLevelNodes = LevelFileLoader.load(file);
            // 同样先判断再减偏移
            int[] origin = decideDisplayOrigin(session.currentLevelNodes);
            shiftNodesToLocal(session.currentLevelNodes, origin);
        }

        // 刷新界面
        session.canvas.setLevelNodes(session.currentLevelNodes);
        levelNodeSidebar.refreshLevelNodeList();
        pathDebugController.onPathSourceChanged();
        clickCordLabel.setText(
            "已加载关卡；" + session.chapterOffset.info + "；节点 " + session.currentLevelNodes.size());

        // 有节点则选中第一个，方便立刻在图上看到圆点
        if (!session.currentLevelNodes.isEmpty()) {
            syncUiToSelectedLevelNode(session.currentLevelNodes.get(0));
        }
    }

    /**
     * 每次切地图/关卡都重新读 GD，不做缓存。
     */
    private void refreshChapterOffset() {
        ChapterOffset offset = ChapterOffsetLoader.load(session.gdDataDir, session.currentMapFileName
        );
        session.chapterOffset = offset == null ? ChapterOffset.zero() : offset;
    }

    /**
     * 决定显示用原点：GD 偏移只有在「减完后落在地图内的点更多」时才用。
     * <p>
     * 第 2 章节点是大世界坐标，需要减偏移；第 3/4、Newbee1 等节点已是本地坐标，再减会飞出地图。
     * </p>
     *
     * @param worldNodes
     *            文件里的原始坐标（尚未减偏移）
     * @return [originX, originZ]，不需要偏移时为 0,0
     */
    private int[] decideDisplayOrigin(List<LevelNodeBean> worldNodes) {
        // GD 算出的候选原点
        int[] gdOrigin = resolveChapterOriginPixels();
        // 本来就是 0，直接用
        if (gdOrigin[0] == 0 && gdOrigin[1] == 0) {
            return gdOrigin;
        }

        // 比较：不减 vs 减偏移，谁让更多点落在当前地图矩形内
        int rawIn = countNodesInMap(worldNodes, 0, 0);
        int shiftedIn = countNodesInMap(worldNodes, gdOrigin[0], gdOrigin[1]);
        if (shiftedIn > rawIn) {
            // 例如第 2 章：减偏移后才进图
            return gdOrigin;
        }

        // 节点本身已在本地（或减了更糟）→ 不应用偏移，并改状态文案
        ChapterOffset old = session.chapterOffset;
        int chapterId = old == null ? -1 : old.chapterId;
        String base = old == null ? "偏移" : old.info;
        session.chapterOffset = new ChapterOffset(0, 0, chapterId, base + "→未应用(本地坐标)");
        return new int[] {0, 0};
    }

    /**
     * 统计减掉原点后，有多少节点落在当前地图像素矩形内。
     *
     * @param nodes
     *            原始世界坐标节点
     * @param originX
     *            原点 X
     * @param originZ
     *            原点 Z
     * @return 落在图内的数量
     */
    private int countNodesInMap(List<LevelNodeBean> nodes, int originX, int originZ) {
        if (nodes == null || nodes.isEmpty() || session.currentMapData == null) {
            return 0;
        }
        // 地图像素宽高
        int mapW = session.currentMapData.getWidth() * MapRingUtil.WORLD_PIXEL_PER_CELL;
        int mapH = session.currentMapData.getHeight() * MapRingUtil.WORLD_PIXEL_PER_CELL;
        int hit = 0;
        for (LevelNodeBean node : nodes) {
            int lx = node.getX() - originX;
            int lz = node.getZ() - originZ;
            if (lx >= 0 && lx < mapW && lz >= 0 && lz < mapH) {
                hit++;
            }
        }
        return hit;
    }

    /**
     * 用当前偏移 × 地图尺寸，算本章本地原点的大世界像素。
     *
     * @return [originX, originZ]
     */
    private int[] resolveChapterOriginPixels() {
        // 没有地图时按 1 格算
        int cols = 1;
        int rows = 1;
        if (session.currentMapData != null) {
            cols = Math.max(1, session.currentMapData.getWidth());
            rows = Math.max(1, session.currentMapData.getHeight());
        }

        // 偏移兜底为零
        ChapterOffset offset = session.chapterOffset;
        if (offset == null) {
            offset = ChapterOffset.zero();
        }
        return new int[] {offset.originWorldX(cols), offset.originWorldZ(rows)};
    }

    /**
     * 回退加载时：按指定原点把节点从大世界改成章节本地。
     *
     * @param nodes
     *            节点列表
     * @param origin
     *            [originX, originZ]
     */
    private void shiftNodesToLocal(List<LevelNodeBean> nodes, int[] origin) {
        // 空列表或零原点不用处理
        if (nodes == null || nodes.isEmpty() || origin == null) {
            return;
        }
        if (origin[0] == 0 && origin[1] == 0) {
            return;
        }

        // 逐个减原点
        for (LevelNodeBean node : nodes) {
            node.setX(node.getX() - origin[0]);
            node.setZ(node.getZ() - origin[1]);
        }
    }

    /**
     * 地图右键：测距填点、关卡节点编辑（编辑模式下）。
     */
    private void onMapCanvasPopup(int pixelX, int pixelY) {
        if (session.canvas == null || session.currentMapData == null) {
            return;
        }
        int[] world = session.canvas.pixelToWorld(pixelX, pixelY);
        if (world == null) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        boolean hasDistanceItems = distanceController.appendDistanceFillMenuItems(menu, world[0], world[1]);
        boolean hasEditItems = false;
        if (edit && session.currentLevelDoc != null && session.canvas.isWorldInPaintedLevel(world[0], world[1])) {
            LevelNodeBean hit = session.canvas.getLevelNodeAt(pixelX, pixelY);
            if (hasDistanceItems) {
                menu.addSeparator();
            }
            if (hit != null) {
                syncUiToSelectedLevelNode(hit);
                levelNodeSidebar.appendNodeEditMenuItems(menu, hit);
            } else {
                JMenuItem newItem = new JMenuItem("新建节点...");
                newItem.addActionListener(e -> levelNodeSidebar.showNewNodeDialog(world[0], world[1]));
                menu.add(newItem);
            }
            hasEditItems = true;
        }
        if (!hasDistanceItems && !hasEditItems) {
            return;
        }
        menu.show(session.canvas, pixelX, pixelY);
    }

    /**
     * 节点选中后同步列表、坐标输入框、地图标记与状态栏。
     */
    private void syncUiToSelectedLevelNode(LevelNodeBean node) {
        if (session.canvas == null || node == null) {
            return;
        }
        session.canvas.setSelectedLevelNode(node);
        levelNodeSidebar.syncNodeListSelection(node);
        toolControlPanel.getWorldXField().setText(String.valueOf(node.getX()));
        toolControlPanel.getWorldZField().setText(String.valueOf(node.getZ()));
        int w = session.currentMapData == null ? 1 : session.currentMapData.getWidth();
        int h = session.currentMapData == null ? 1 : session.currentMapData.getHeight();
        int col = MapRingUtil.worldToTileX(node.getX(), w);
        int row = MapRingUtil.worldToTileZ(node.getZ(), h);
        String tilePart = session.currentMapData == null ? "格子: (—, —)" : "格子: (" + col + ", " + row + ")";
        clickCordLabel.setText("世界: (" + node.getX() + ", " + node.getZ() + ")  " + tilePart + "  节点: " + node);
        session.canvas.setMarkerPoint(node.getX(), node.getZ());
    }
}

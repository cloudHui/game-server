package com.gamer.data.map.ui.path;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import com.gamer.data.map.grid.MapData;
import com.gamer.data.map.grid.MapRingUtil;
import com.gamer.data.map.path.compare.PathSimulationMode;
import com.gamer.data.map.path.compare.PathSimulationReport;
import com.gamer.data.map.path.compare.PathSimulationRunner;
import com.gamer.data.map.path.common.PathCommonUtil;
import com.gamer.data.map.path.common.PathGridPoint;
import com.gamer.data.map.path.common.PathRequest;
import com.gamer.data.map.path.common.PathResult;
import com.gamer.data.map.path.common.PathWorldPoint;
import com.gamer.data.map.path.display.PathCoordinateProjector;
import com.gamer.data.map.path.display.CoordinateAdapter;
import com.gamer.data.map.path.tool.ToolPathStrategyType;
import com.gamer.data.map.path.tool.grid.GridPathGrid;
import com.gamer.data.map.path.tool.grid.GridPathGridFactory;
import com.gamer.data.map.path.tool.grid.GridPathRequest;
import com.gamer.data.map.path.world.WorldMapData;
import com.gamer.data.map.path.world.WorldMapDisplayMode;
import com.gamer.data.map.path.world.WorldMapFileLoader;
import com.gamer.data.map.path.world.WorldMapFileLocator;
import com.gamer.data.map.ui.MapUiUtils;
import com.gamer.data.map.ui.MapViewerSession;
import com.gamer.data.map.ui.canvas.MapViewerCanvas;
import com.gamer.data.map.ui.path.replay.ReplayController;

/**
 * 寻路模拟状态机与后台计算。
 */
public final class PathDebugController {

    /** 寻路模拟关闭 */
    private static final int PATH_STATE_OFF = 0;
    /** 等待选择起点 */
    private static final int PATH_STATE_WAIT_START = 1;
    /** 等待选择终点 */
    private static final int PATH_STATE_WAIT_END = 2;
    /** 正在后台计算 */
    private static final int PATH_STATE_RUNNING = 3;
    /** 已生成路径 */
    private static final int PATH_STATE_DONE = 4;
    /** 寻路失败 */
    private static final int PATH_STATE_FAILED = 5;

    private final MapViewerSession session;// 会话
    private final Runnable mapViewChangedListener;// 地图视图变化监听器

    private PathDebugUi ui;

    private GridPathGrid pathGrid;// 路径网格
    private PathGridPoint pathStartPoint;// 起点网格点
    private PathGridPoint pathEndPoint;// 终点网格点
    private int pathStartWorldX;// 起点世界 X 坐标
    private int pathStartWorldZ;// 起点世界 Z 坐标
    private int pathEndWorldX;// 终点世界 X 坐标
    private int pathEndWorldZ;// 终点世界 Z 坐标
    /** WorldServer真实地图文件 */
    private File worldMapFile = null;
    /** 已加载的WorldServer地图数据，避免每次寻路把读取耗时算进去 */
    private WorldMapData cachedWorldMapData;
    /** 缓存地图文件最后修改时间 */
    private long cachedWorldMapLastModified = -1L;
    /** World原始方向预览图，地图重新加载时失效。 */
    private final PathMapViewState mapViewState = new PathMapViewState();
    /** 是否正在用World地图作为画布数据源 */
    private int pathState = PATH_STATE_OFF;
    private SwingWorker<List<PathSimulationReport>, Void> pathWorker;
    /** 搜索过程回放控制器（寻路完成后逐帧回放搜索点） */
    private final ReplayController pathReplayController = new ReplayController();

    /**
     * @param session
     *            共享会话
     * @param mapViewChangedListener
     *            地图视图切换后刷新小地图等外部组件
     */
    public PathDebugController(MapViewerSession session, Runnable mapViewChangedListener) {
        this.session = session;
        this.mapViewChangedListener = mapViewChangedListener;
    }

    /**
     * 绑定寻路调试栏 UI。
     */
    public void bindUi(PathDebugUi ui) {
        this.ui = ui;
        refreshWorldMapFileUi();
        syncSimulationModeControls();
        applyPathLayers();
    }

    /**
     * 按勾选刷新图层显隐（结果出来后也可随时切换）。
     */
    public void applyPathLayers() {
        if (session.canvas == null || ui == null) {
            return;
        }
        session.canvas.setPathLayerVisible(ui.showSearchCheck.isSelected(), ui.showToolPathCheck.isSelected(),
            ui.showWorldPathCheck.isSelected());
    }

    private boolean isAllowCutCorner() {
        return ui != null && ui.allowCutCornerCheck != null && ui.allowCutCornerCheck.isSelected();
    }

    /**
     * 记录明确加载的工具地图，避免 World 预览图污染恢复源。
     *
     * @param mapData
     *            工具侧地图数据
     */
    public void setToolMapData(MapData mapData) {
        if (mapData == null) {
            return;
        }
        mapViewState.setToolMapData(mapData);
    }

    /**
     * 跟随左侧主地图选择，World侧仍按WorldServer规则独立解析同一个文件。
     *
     * @param mapFile
     *            当前主地图文件
     */
    public void setCurrentMapFile(File mapFile) {
        if (mapFile == null) {
            return;
        }
        File normalizedFile = WorldMapFileLocator.normalize(mapFile);
        if (normalizedFile.equals(worldMapFile)) {
            return;
        }
        worldMapFile = normalizedFile;
        cachedWorldMapData = null;
        cachedWorldMapLastModified = -1L;
        mapViewState.clearWorldPreview();
        refreshWorldMapFileUi();
        applyMapViewForCurrentMode();
    }

    /**
     * 运行模式变更时同步控件状态，并清理旧路径。
     */
    public void onSimulationModeChanged() {
        syncWorldDisplayModeSelection();
        syncSimulationModeControls();
        applyMapViewForCurrentMode();
        resetPathSelection("寻路: 模式已切换，请选择起点");
    }

    public void onWorldDisplayModeChanged() {
        WorldMapDisplayMode oldMode = mapViewState.getDisplayMode();
        WorldMapDisplayMode newMode = getEffectiveWorldDisplayMode();
        transformSelectedPoints(oldMode, newMode);
        applyMapViewForCurrentMode();
        rebuildPathGrid();
        refreshSelectedPointVisual();
        updatePathPointFields();
        updatePathUi("寻路: 地图方向已切换");
    }

    public void applyInputPathPoints() {
        if (pathState == PATH_STATE_OFF) {
            updatePathUi("寻路: 请先开启模拟");
            return;
        }
        int[] start = parsePointField(ui.startPointField, "起点");
        if (start == null) {
            return;
        }
        int[] end = parsePointField(ui.endPointField, "终点");
        stopPathWorker();
        PathPointCheckResult startCheck = checkPathWorldPoint(start[0], start[1]);
        if (startCheck.isNotValid()) {
            updatePathUi("寻路: 起点 " + startCheck.getMessage());
            showBlockReason(startCheck);
            return;
        }
        selectPathStart(startCheck);
        if (end == null) {
            return;
        }
        PathPointCheckResult endCheck = checkPathWorldPoint(end[0], end[1]);
        if (endCheck.isNotValid()) {
            updatePathUi("寻路: 终点 " + endCheck.getMessage());
            showBlockReason(endCheck);
            return;
        }
        selectPathEnd(endCheck);
    }

    private void syncSimulationModeControls() {
        PathSimulationMode mode = getSelectedSimulationMode();
        boolean toolEnabled = mode.isToolEnabled();
        if (ui.strategyCombo != null) {
            ui.strategyCombo.setEnabled(toolEnabled);
        }
        if (ui.worldDisplayModeCombo != null) {
            ui.worldDisplayModeCombo.setEnabled(mode == PathSimulationMode.COMPARE);
        }
    }

    private void syncWorldDisplayModeSelection() {
        if (ui.worldDisplayModeCombo == null) {
            return;
        }
        PathSimulationMode mode = getSelectedSimulationMode();
        if (mode == PathSimulationMode.TOOL_ONLY) {
            ui.worldDisplayModeCombo.setSelectedItem(WorldMapDisplayMode.TOOL_VIEW);
        } else if (mode == PathSimulationMode.WORLD_ONLY) {
            ui.worldDisplayModeCombo.setSelectedItem(WorldMapDisplayMode.WORLD_RAW);
        } else {
            ui.worldDisplayModeCombo.setSelectedItem(WorldMapDisplayMode.TOOL_VIEW);
        }
    }

    private void refreshWorldMapFileUi() {
        if (ui.currentMapLabel != null) {
            setPathLabelText(ui.currentMapLabel, "当前: " + getWorldMapFileName());
            ui.currentMapLabel.setToolTipText(worldMapFile == null ? null : worldMapFile.getPath());
        }
    }

    private String getWorldMapFileName() {
        return worldMapFile == null ? "-" : worldMapFile.getName();
    }
 
    /**
     * 确保 World 地图数据已加载。
     *
     * @return World 地图数据
     */
    private WorldMapData ensureWorldMapData() {
        if (hasUsableWorldMapFile()) {
            cachedWorldMapData = null;
            mapViewState.clearWorldPreview();
            return null;
        }
        long lastModified = worldMapFile.lastModified();
        if (cachedWorldMapData != null && cachedWorldMapLastModified == lastModified) {
            return cachedWorldMapData;
        }
        try {
            cachedWorldMapData = WorldMapFileLoader.load(worldMapFile);
            cachedWorldMapLastModified = lastModified;
            mapViewState.clearWorldPreview();
            return cachedWorldMapData;
        } catch (Exception e) {
            cachedWorldMapData = null;
            cachedWorldMapLastModified = -1L;
            mapViewState.clearWorldPreview();
            return null;
        }
    }

    /**
     * 应用当前模式的地图视图。
     */
    private void applyMapViewForCurrentMode() {
        mapViewState.rememberToolMapData(session.currentMapData);
        if (session.canvas == null) {
            return;
        }
        WorldMapDisplayMode displayMode = getEffectiveWorldDisplayMode();
        MapData displayMapData = mapViewState.resolveDisplayMapData(displayMode,
            displayMode == WorldMapDisplayMode.WORLD_RAW ? ensureWorldMapData() : null, session.currentMapData);
        if (displayMapData == null) {
            return;
        }
        mapViewState.applyToSession(session, session.currentLevelNodes);
        notifyMapViewChanged();
    }

    private void restoreToolMapView() {
        mapViewState.restoreToolMapView(session, session.currentLevelNodes);
        notifyMapViewChanged();
    }

    private MapData resolveToolSourceMapData() {
        return mapViewState.getToolSourceMapData(session.currentMapData);
    }

    private void notifyMapViewChanged() {
        if (mapViewChangedListener != null) {
            mapViewChangedListener.run();
        }
    }

    /**
     * @return 寻路模拟是否已开启
     */
    public boolean isPathSimulationActive() {
        return pathState != PATH_STATE_OFF;
    }

    /**
     * 切换寻路模拟开关。
     */
    public void togglePathSimulation() {
        if (pathState == PATH_STATE_OFF) {
            // 无有效 World 地图文件时不允许开启模拟
            if (hasUsableWorldMapFile()) {
                updatePathUi("寻路: 无地图不能开启模拟");
                return;
            }
            applyMapViewForCurrentMode();
            rebuildPathGrid();
            pathState = PATH_STATE_WAIT_START;
            clearPathVisual();
            updatePathUi("寻路: 请选择起点");
            return;
        }
        stopPathWorker();
        pathState = PATH_STATE_OFF;
        pathGrid = null;
        clearPathVisual();
        restoreToolMapView();
        updatePathUi("寻路: 未开启");
    }

    /**
     * 校验当前 World 地图文件是否可用（由主界面选图写入，无默认回退）。
     *
     * @return 文件存在则 true
     */
    private boolean hasUsableWorldMapFile() {
        return worldMapFile == null || !worldMapFile.exists();
    }

    /**
     * 清空当前起终点并等待重新选择。
     */
    public void resetPathSelection(String statusText) {
        if (pathState == PATH_STATE_OFF) {
            return;
        }
        stopPathWorker();
        rebuildPathGrid();
        pathState = PATH_STATE_WAIT_START;
        clearPathVisual();
        updatePathUi(statusText);
    }

    /**
     * 地图或关卡节点变化时重置寻路状态。
     */
    public void onPathSourceChanged() {
        stopPathWorker();
        clearPathVisual();
        MapViewerCanvas canvas = session.canvas;
        if (pathState == PATH_STATE_OFF) {
            pathGrid = null;
            if (canvas != null) {
                canvas.setBlockedTiles(null);
            }
            updatePathUi("寻路: 未开启");
            return;
        }
        applyMapViewForCurrentMode();
        rebuildPathGrid();
        pathState = PATH_STATE_WAIT_START;
        updatePathUi("寻路: 请选择起点");
    }

    /**
     * 寻路模拟模式下的地图点击。
     */
    public void handlePathClick(int worldX, int worldZ) {
        if (pathState == PATH_STATE_OFF) {
            return;
        }
        PathSimulationMode mode = getSelectedSimulationMode();
        if (pathGrid == null && mode != PathSimulationMode.WORLD_ONLY) {
            rebuildPathGrid();
        }
        if (pathGrid == null && mode != PathSimulationMode.WORLD_ONLY) {
            updatePathUi("寻路: 请先加载地图");
            return;
        }
        PathPointCheckResult check = checkPathWorldPoint(worldX, worldZ);
        if (check.isNotValid()) {
            updatePathUi("寻路: " + check.getMessage());
            showBlockReason(check);
            return;
        }
        if (pathState == PATH_STATE_WAIT_START || pathState == PATH_STATE_DONE || pathState == PATH_STATE_FAILED) {
            selectPathStart(check);
        } else if (pathState == PATH_STATE_WAIT_END) {
            selectPathEnd(check);
        }
    }

    private void rebuildPathGrid() {
        MapViewerCanvas canvas = session.canvas;
        if (getSelectedSimulationMode() == PathSimulationMode.WORLD_ONLY) {
            pathGrid = null;
            if (canvas != null) {
                canvas.setBlockedTiles(null);
            }
            return;
        }
        MapData toolSourceMapData = resolveToolSourceMapData();
        if (toolSourceMapData == null || toolSourceMapData.getMap() == null) {
            pathGrid = null;
            if (canvas != null) {
                canvas.setBlockedTiles(null);
            }
            return;
        }
        pathGrid = GridPathGridFactory.create(toolSourceMapData, session.currentLevelNodes);
        if (canvas != null) {
            WorldMapDisplayMode displayMode = getEffectiveWorldDisplayMode();
            canvas.setBlockedTiles(CoordinateAdapter.toDisplayGridPoints(pathGrid.getBlockedTiles(),
                displayMode, toolSourceMapData.getHeight()));
        }
    }

    private void clearPathVisual() {
        pathStartPoint = null;
        pathEndPoint = null;
        MapViewerCanvas canvas = session.canvas;
        if (canvas != null) {
            canvas.clearPathDebugData();
        }
        if (ui.startLabel != null) {
            setPathLabelText(ui.startLabel, "起点: -");
        }
        if (ui.endLabel != null) {
            setPathLabelText(ui.endLabel, "终点: -");
        }
        if (ui.resultLabel != null) {
            setPathLabelText(ui.resultLabel, "结果: -");
        }
        if (ui.logArea != null) {
            ui.logArea.setText("");
        }
        updatePathPointFields();
    }

    private void updatePathUi(String statusText) {
        if (ui.statusLabel != null) {
            setPathLabelText(ui.statusLabel, statusText);
        }
    }

    private static void setPathLabelText(JLabel label, String text) {
        label.setText(text);
        label.setToolTipText(text);
    }

    private int[] parsePointField(JTextField field, String name) {
        if (field == null || field.getText() == null || field.getText().trim().isEmpty()) {
            updatePathUi("寻路: 请输入" + name + "，格式 x,z");
            return null;
        }
        int[] point = MapUiUtils.parsePoint(field.getText());
        if (point == null) {
            updatePathUi("寻路: " + name + "格式错误，请输入数字 x,z");
            return null;
        }
        return point;
    }

    private void updatePathPointFields() {
        if (ui.startPointField != null) {
            ui.startPointField.setText(pathStartPoint == null ? "" : MapUiUtils.formatPoint(pathStartWorldX, pathStartWorldZ));
        }
        if (ui.endPointField != null) {
            ui.endPointField.setText(pathEndPoint == null ? "" : MapUiUtils.formatPoint(pathEndWorldX, pathEndWorldZ));
        }
    }

    private void transformSelectedPoints(WorldMapDisplayMode oldMode, WorldMapDisplayMode newMode) {
        if (oldMode == newMode) {
            return;
        }
        int mapHeight = resolveCurrentMapHeight();
        if (mapHeight <= 0) {
            return;
        }
        if (pathStartPoint != null) {
            pathStartWorldZ = transformDisplayWorldZ(pathStartWorldZ, mapHeight, oldMode, newMode);
            pathStartPoint = new PathGridPoint(pathStartPoint.getCol(), mapHeight - 1 - pathStartPoint.getRow());
        }
        if (pathEndPoint != null) {
            pathEndWorldZ = transformDisplayWorldZ(pathEndWorldZ, mapHeight, oldMode, newMode);
            pathEndPoint = new PathGridPoint(pathEndPoint.getCol(), mapHeight - 1 - pathEndPoint.getRow());
        }
    }

    private int resolveCurrentMapHeight() {
        MapData mapData = resolveToolSourceMapData();
        if (mapData != null) {
            return mapData.getHeight();
        }
        WorldMapData worldMapData = ensureWorldMapData();
        return worldMapData == null ? 0 : worldMapData.getHeight();
    }

    private static int transformDisplayWorldZ(int worldZ, int height, WorldMapDisplayMode oldMode,
        WorldMapDisplayMode newMode) {
        int sourceZ = PathCoordinateProjector.toSourceWorldZ(worldZ, height, oldMode);
        return PathCoordinateProjector.toDisplayWorldZ(sourceZ, height, newMode);
    }

    private void refreshSelectedPointVisual() {
        MapViewerCanvas canvas = session.canvas;
        if (canvas == null) {
            return;
        }
        canvas.setPathDebugData(pathStartPoint, pathEndPoint, getPathStartWorldPoint(), getPathEndWorldPoint(), null,
            null, null);
        canvas.setComparePathDebugData(null, null);
        canvas.setPathBlockReason(null);
        canvas.setPathFailureDetail(null);
    }

    private void showBlockReason(PathPointCheckResult check) {
        if (session.canvas != null) {
            session.canvas.setPathBlockReason(check == null ? null : check.getBlockReason());
            session.canvas.setPathFailureDetail(null);
        }
    }

    private PathPointCheckResult checkPathWorldPoint(int worldX, int worldZ) {
        PathSimulationMode mode = getSelectedSimulationMode();
        ToolPathStrategyType type = getSelectedToolPathStrategyType();
        if (mode == PathSimulationMode.WORLD_ONLY) {
            WorldMapData worldMapData = ensureWorldMapData();
            if (worldMapData == null) {
                return PathPointCheckResult.fail("World地图未加载");
            }
            int col = MapRingUtil.worldToTileX(worldX, worldMapData.getWidth());
            int displayRow = MapRingUtil.worldToTileZ(worldZ, worldMapData.getHeight());
            int landRow = getSelectedWorldDisplayMode().toWorldY(displayRow, worldMapData.getHeight());
            if (!worldMapData.isInBounds(col, landRow)) {
                return PathPointCheckResult.fail("点不在World地图范围内");
            }
            if (pathState != PATH_STATE_WAIT_END && !worldMapData.isWalkable(col, landRow)) {
                return PathPointCheckResult.fail("World地图起点land格不可走");
            }
            return PathPointCheckResult.ok(worldX, worldZ, col, displayRow);
        }
        MapData toolSourceMapData = resolveToolSourceMapData();
        if (toolSourceMapData == null) {
            return PathPointCheckResult.fail("tool map not loaded");
        }
        int toolWorldZ = CoordinateAdapter.toToolWorldZ(worldZ, toolSourceMapData.getHeight(),
            getEffectiveWorldDisplayMode());
        if (type == ToolPathStrategyType.GRID) {
            GridPathGrid.CheckResult check = pathGrid.checkWorldPoint(worldX, toolWorldZ);
            if (check.isNotValid()) {
                return PathPointCheckResult.fail(check.getMessage(), CoordinateAdapter.toDisplayBlockReason(
                    check.getBlockReason(), getEffectiveWorldDisplayMode(), toolSourceMapData.getHeight()));
            }
            int displayCol = MapRingUtil.worldToTileX(worldX, session.currentMapData.getWidth());
            int displayRow = MapRingUtil.worldToTileZ(worldZ, session.currentMapData.getHeight());
            return PathPointCheckResult.ok(worldX, worldZ, displayCol, displayRow);
        }
        if (PathCommonUtil.isNotWorldInBounds(toolSourceMapData, worldX, toolWorldZ)) {
            return PathPointCheckResult.fail("点不在地图范围内", CoordinateAdapter.toDisplayBlockReason(
                PathCommonUtil.diagnoseWorldPoint(toolSourceMapData, pathGrid.getObstacles(),
                    pathGrid.getObstacleIndex(), worldX, toolWorldZ), getEffectiveWorldDisplayMode(),
                toolSourceMapData.getHeight()));
        }
        if (PathCommonUtil.isNotBaseWorldWalkable(toolSourceMapData, worldX, toolWorldZ)) {
            return PathPointCheckResult.fail("该点不是可走地图区域", CoordinateAdapter.toDisplayBlockReason(
                PathCommonUtil.diagnoseWorldPoint(toolSourceMapData, pathGrid.getObstacles(),
                    pathGrid.getObstacleIndex(), worldX, toolWorldZ), getEffectiveWorldDisplayMode(),
                toolSourceMapData.getHeight()));
        }
        if (!PathCommonUtil.isSafeWorldPoint(toolSourceMapData, pathGrid.getObstacles(),
            pathGrid.getObstacleIndex(), worldX, toolWorldZ)) {
            return PathPointCheckResult.fail("该点在固定障碍安全半径内", CoordinateAdapter.toDisplayBlockReason(
                PathCommonUtil.diagnoseWorldPoint(toolSourceMapData, pathGrid.getObstacles(),
                    pathGrid.getObstacleIndex(), worldX, toolWorldZ), getEffectiveWorldDisplayMode(),
                toolSourceMapData.getHeight()));
        }
        int col = MapRingUtil.worldToTileX(worldX, session.currentMapData.getWidth());
        int row = MapRingUtil.worldToTileZ(worldZ, session.currentMapData.getHeight());
        return PathPointCheckResult.ok(worldX, worldZ, col, row);
    }

    private ToolPathStrategyType getSelectedToolPathStrategyType() {
        if (ui == null || ui.strategyCombo == null || ui.strategyCombo.getSelectedItem() == null) {
            return ToolPathStrategyType.GRID;
        }
        return (ToolPathStrategyType)ui.strategyCombo.getSelectedItem();
    }

    private PathSimulationMode getSelectedSimulationMode() {
        if (ui == null || ui.modeCombo == null || ui.modeCombo.getSelectedItem() == null) {
            return PathSimulationMode.TOOL_ONLY;
        }
        return (PathSimulationMode)ui.modeCombo.getSelectedItem();
    }

    private WorldMapDisplayMode getSelectedWorldDisplayMode() {
        if (ui == null || ui.worldDisplayModeCombo == null || ui.worldDisplayModeCombo.getSelectedItem() == null) {
            return WorldMapDisplayMode.TOOL_VIEW;
        }
        return (WorldMapDisplayMode)ui.worldDisplayModeCombo.getSelectedItem();
    }

    private WorldMapDisplayMode getEffectiveWorldDisplayMode() {
        PathSimulationMode mode = getSelectedSimulationMode();
        if (mode == PathSimulationMode.WORLD_ONLY) {
            return WorldMapDisplayMode.WORLD_RAW;
        }
        if (mode == PathSimulationMode.COMPARE) {
            return getSelectedWorldDisplayMode();
        }
        return WorldMapDisplayMode.TOOL_VIEW;
    }

    private void selectPathStart(PathPointCheckResult check) {
        stopPathWorker();
        pathStartPoint = new PathGridPoint(check.getCol(), check.getRow());
        pathEndPoint = null;
        pathStartWorldX = check.getWorldX();
        pathStartWorldZ = check.getWorldZ();
        pathState = PATH_STATE_WAIT_END;
        MapViewerCanvas canvas = session.canvas;
        if (canvas != null) {
            canvas.setPathDebugData(pathStartPoint, null, getPathStartWorldPoint(), null, null, null, null);
            canvas.setComparePathDebugData(null, null);
            canvas.setPathBlockReason(null);
            canvas.setPathFailureDetail(null);
        }
        setPathLabelText(ui.startLabel, "起点: " + formatPathEndpoint(pathStartWorldX, pathStartWorldZ, pathStartPoint));
        setPathLabelText(ui.endLabel, "终点: -");
        setPathLabelText(ui.resultLabel, "结果: -");
        if (ui.logArea != null) {
            ui.logArea.setText("");
        }
        updatePathPointFields();
        updatePathUi("寻路: 请选择终点");
    }

    private void selectPathEnd(PathPointCheckResult check) {
        pathEndPoint = new PathGridPoint(check.getCol(), check.getRow());
        pathEndWorldX = check.getWorldX();
        pathEndWorldZ = check.getWorldZ();
        setPathLabelText(ui.endLabel, "终点: " + formatPathEndpoint(pathEndWorldX, pathEndWorldZ, pathEndPoint));
        pathState = PATH_STATE_RUNNING;
        updatePathUi("寻路: 计算中...");
        if (ui.logArea != null) {
            ui.logArea.setText("寻路计算中...\n");
        }
        MapViewerCanvas canvas = session.canvas;
        if (canvas != null) {
            canvas.setPathDebugData(pathStartPoint, pathEndPoint, getPathStartWorldPoint(), getPathEndWorldPoint(), null,
                null, null);
            canvas.setComparePathDebugData(null, null);
            canvas.setPathBlockReason(null);
            canvas.setPathFailureDetail(null);
        }
        updatePathPointFields();
        runPathWorker();
    }

    private void runPathWorker() {
        stopPathWorker();
        final PathSimulationMode mode = getSelectedSimulationMode();
        final ToolPathStrategyType strategyType = getSelectedToolPathStrategyType();
        final MapData toolSourceMapData = resolveToolSourceMapData();
        final WorldMapDisplayMode displayMode = getEffectiveWorldDisplayMode();
        final boolean recordSearch = mode != PathSimulationMode.COMPARE;
        final boolean allowCutCorner = isAllowCutCorner();
        final int toolStartWorldZ = toolSourceMapData == null ? pathStartWorldZ
            : CoordinateAdapter.toToolWorldZ(pathStartWorldZ, toolSourceMapData.getHeight(), displayMode);
        final int toolEndWorldZ = toolSourceMapData == null ? pathEndWorldZ
            : CoordinateAdapter.toToolWorldZ(pathEndWorldZ, toolSourceMapData.getHeight(), displayMode);
        final PathRequest request = pathGrid == null
            ? new PathRequest(toolSourceMapData, session.currentLevelNodes, pathStartWorldX, toolStartWorldZ,
                pathEndWorldX, toolEndWorldZ, recordSearch, allowCutCorner)
            : new GridPathRequest(toolSourceMapData, session.currentLevelNodes, pathGrid, pathStartWorldX,
                toolStartWorldZ, pathEndWorldX, toolEndWorldZ, recordSearch, allowCutCorner);
        final WorldMapData selectedWorldMapData = mode.isWorldEnabled() ? ensureWorldMapData() : null;
        final PathGridPoint startLand = CoordinateAdapter.toWorldLandPoint(selectedWorldMapData,
            pathStartWorldX, pathStartWorldZ, displayMode);
        final PathGridPoint targetLand = CoordinateAdapter.toWorldLandPoint(selectedWorldMapData,
            pathEndWorldX, pathEndWorldZ, displayMode);
        final int startLandX = startLand == null ? 0 : startLand.getCol();
        final int startLandY = startLand == null ? 0 : startLand.getRow();
        final int targetLandX = targetLand == null ? 0 : targetLand.getCol();
        final int targetLandY = targetLand == null ? 0 : targetLand.getRow();
        final long startTime = System.currentTimeMillis();
        pathWorker = new SwingWorker<List<PathSimulationReport>, Void>() {
            @Override
            protected List<PathSimulationReport> doInBackground() {
                return PathSimulationRunner.run(mode, strategyType, request, selectedWorldMapData, startLandX,
                    startLandY, targetLandX, targetLandY, recordSearch, displayMode);
            }

            @Override
            protected void done() {
                handlePathWorkerDone(this, startTime);
            }
        };
        pathWorker.execute();
    }

    private void handlePathWorkerDone(SwingWorker<List<PathSimulationReport>, Void> worker, long startTime) {
        if (worker.isCancelled() || worker != pathWorker) {
            return;
        }
        long endTime = System.currentTimeMillis();
        try {
            List<PathSimulationReport> reports = ReportPresenter.toDisplayReports(worker.get(),
                getEffectiveWorldDisplayMode(), mapViewState.getToolMapData());
            PathSimulationReport primaryReport = ReportPresenter.selectPrimaryReport(reports);
            PathSimulationReport compareReport = ReportPresenter.selectCompareReport(reports,
                primaryReport);
            PathResult primaryResult = primaryReport == null ? null : primaryReport.getPathResult();
            writePathLog(ReportLog.format(reports, startTime, endTime,
                formatPathEndpoint(pathStartWorldX, pathStartWorldZ, pathStartPoint),
                formatPathEndpoint(pathEndWorldX, pathEndWorldZ, pathEndPoint), reports != null && reports.size() > 1));
            // 单结果走回放：保持起终点，不先刷满路径/搜索点，避免闪屏
            if (primaryResult != null && reports.size() == 1) {
                startPathReplay(primaryResult);
                return;
            }
            refreshPathCanvas(primaryResult, compareReport);
            if (ReportPresenter.isAllSuccess(reports)) {
                pathState = PATH_STATE_DONE;
                setPathLabelText(ui.resultLabel, ReportPresenter.buildSummary(reports));
                updatePathUi("寻路: 成功");
            } else {
                pathState = PATH_STATE_FAILED;
                setPathLabelText(ui.resultLabel, ReportPresenter.buildSummary(reports));
                updatePathUi("寻路: 存在失败结果");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            updatePathUi("寻路: 已中断");
            writePathLog("寻路被中断，耗时 " + (endTime - startTime) + " ms");
        } catch (ExecutionException e) {
            String message = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
            updatePathUi("寻路: 计算失败 " + message);
            writePathLog("寻路计算失败，耗时 " + (endTime - startTime) + " ms\n原因: " + message);
        }
    }

    private void refreshPathCanvas(PathResult primaryResult, PathSimulationReport compareReport) {
        MapViewerCanvas canvas = session.canvas;
        if (canvas == null) {
            return;
        }
        if (primaryResult == null) {
            canvas.setPathDebugData(pathStartPoint, pathEndPoint, getPathStartWorldPoint(), getPathEndWorldPoint(), null,
                null, null);
        } else {
            canvas.setPathDebugData(pathStartPoint, pathEndPoint, getPathStartWorldPoint(), getPathEndWorldPoint(),
                primaryResult.getGridPath(), primaryResult.getWorldPath(), primaryResult.getSearchedGrids(),
                primaryResult.getSearchedWorlds());
        }
        PathResult compareResult = compareReport == null ? null : compareReport.getPathResult();
        if (compareResult != null) {
            canvas.setComparePathDebugData(compareResult.getGridPath(), compareResult.getWorldPath());
        } else {
            canvas.setComparePathDebugData(null, null);
        }
        PathResult failureResult = primaryResult != null && !primaryResult.isSuccess() ? primaryResult
            : compareResult != null && !compareResult.isSuccess() ? compareResult : null;
        canvas.setPathBlockReason(failureResult == null || failureResult.getFailureDetail().isPresent() ? null
            : failureResult.getBlockReason());
        canvas.setPathFailureDetail(failureResult == null ? null : failureResult.getFailureDetail());
    }

    /**
     * 启动搜索过程回放：切到回放态并交给回放控制器逐帧播放搜索点，播放结束回调 {@link #handlePathReplayFinished}。
     *
     * @param result
     *            寻路结果（含搜索点与最终路径）
     */
    private void startPathReplay(final PathResult result) {
        // 回放期间复用 RUNNING 态，避免用户中途重选起终点打断回放
        pathState = PATH_STATE_RUNNING;
        setPathLabelText(ui.resultLabel, "结果: 回放中 搜索=" + getSearchCount(result));
        updatePathUi("寻路: 搜索回放中...");
        pathReplayController.start(session.canvas, pathStartPoint, pathEndPoint, getPathStartWorldPoint(),
            getPathEndWorldPoint(), result, resolveReplayBatchSize(), this::handlePathReplayFinished);
    }

    /**
     * 读取界面「每帧」点数；非法或非正数时回退默认值。
     *
     * @return 每帧发布点数
     */
    private int resolveReplayBatchSize() {
        if (ui.batchSizeField == null || ui.batchSizeField.getText() == null) {
            return ReplayController.DEFAULT_BATCH_SIZE;
        }
        try {
            int batchSize = Integer.parseInt(ui.batchSizeField.getText().trim());
            return batchSize > 0 ? batchSize : ReplayController.DEFAULT_BATCH_SIZE;
        } catch (NumberFormatException e) {
            return ReplayController.DEFAULT_BATCH_SIZE;
        }
    }

    /**
     * 回放结束回调：根据结果落到最终成功/失败态并刷新 UI 标签。
     *
     * @param result
     *            回放的寻路结果
     */
    private void handlePathReplayFinished(PathResult result) {
        // 有成功路径：落到已生成路径态
        if (result != null && result.isSuccess()) {
            pathState = PATH_STATE_DONE;
            setPathLabelText(ui.resultLabel, "结果: 路径=" + result.getGridPath().size() + " 搜索="
                + getSearchCount(result));
            updatePathUi("寻路: 成功");
            return;
        }
        // 无结果或无路径：落到失败态
        pathState = PATH_STATE_FAILED;
        setPathLabelText(ui.resultLabel, "结果: 无路径 搜索=" + getSearchCount(result));
        updatePathUi(result == null ? "寻路: 失败" : "寻路: " + result.getMessage());
    }

    private void writePathLog(String text) {
        if (ui.logArea == null) {
            return;
        }
        ui.logArea.setText(text == null ? "" : text);
        ui.logArea.setCaretPosition(0);
    }

    private static String formatPathEndpoint(int worldX, int worldZ, PathGridPoint gridPoint) {
        if (gridPoint == null) {
            return "世界(" + worldX + "," + worldZ + ") 格(-,-)";
        }
        return "世界(" + worldX + "," + worldZ + ") 格(" + gridPoint.getCol() + "," + gridPoint.getRow() + ")";
    }

    private PathWorldPoint getPathStartWorldPoint() {
        return pathStartPoint == null ? null : new PathWorldPoint(pathStartWorldX, pathStartWorldZ);
    }

    private PathWorldPoint getPathEndWorldPoint() {
        return pathEndPoint == null ? null : new PathWorldPoint(pathEndWorldX, pathEndWorldZ);
    }

    private static int getSearchCount(PathResult result) {
        if (result == null) {
            return 0;
        }
        return result.getSearchedGrids().size() + result.getSearchedWorlds().size();
    }

    private void stopPathWorker() {
        // 先停回放，避免新旧任务的回放帧叠加
        pathReplayController.stop();
        if (pathWorker != null && !pathWorker.isDone()) {
            pathWorker.cancel(true);
        }
        pathWorker = null;
    }

    /**
     * 寻路方式切换时回调。
     */
    public void onStrategyChanged() {
        resetPathSelection("寻路: 方式已切换，请选择起点");
    }

    /**
     * 显示开关变更时回调。
     */
    /**
     * 更新开关按钮文案（由面板在 toggle 后调用）。
     */
    public void refreshToggleButtons(javax.swing.JButton toggleButton, javax.swing.JButton clearButton) {
        if (toggleButton != null) {
            toggleButton.setText(pathState == PATH_STATE_OFF ? "开启模拟" : "关闭模拟");
        }
        if (clearButton != null) {
            clearButton.setEnabled(pathState != PATH_STATE_OFF);
        }
    }
}

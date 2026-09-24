package com.cloud.hub.lobby.manager.table;

import com.cloud.hub.lobby.db.CustomRoomRepository;
import com.cloud.hub.lobby.db.SqliteDatabase;
import com.cloud.hub.lobby.manager.User;
import model.tablemodel.RobotRoomTemplates;
import model.tablemodel.TableModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.LobbyProto;
import proto.ModelProto;
import tool.config.TableConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大厅房间玩法模板与桌面运行管理器。
 * <p>
 * 维护系统内置玩法规则（麻将、斗地主、跑得快、拖拉机）及动态注册的运行时模板，
 * 管理全局桌子映射（roomTables / tableInfoMap），处理玩家入座、空桌销毁及恢复同步。
 * </p>
 *
 * @author cloud
 */
public class TableManager {

    private static final Logger logger = LoggerFactory.getLogger(TableManager.class);
    private static TableManager instance;

    /** Excel 与本地配表加载器 */
    private final TableConfigManager configManager;
    /** 自定义房间持久化仓储 */
    private final CustomRoomRepository customRoomRepository;
    /** 房间模板 ID -> 桌子 ID -> 桌子详情 映射表 */
    private final Map<Integer, Map<Long, TableInfo>> roomTables = new ConcurrentHashMap<>();
    /** 桌子全局唯一 ID -> 桌子详情 快速检索映射表 */
    private final Map<Long, TableInfo> tableInfoMap = new ConcurrentHashMap<>();

    private TableManager() {
        configManager = new TableConfigManager();
        customRoomRepository = new CustomRoomRepository(SqliteDatabase.getInstance());
    }

    /**
     * 获取房间管理器全局单例。
     *
     * @return 单例实例
     */
    public static synchronized TableManager getInstance() {
        if (instance == null) {
            instance = new TableManager();
        }
        return instance;
    }

    /**
     * 初始化模板加载、配置监听与内置机器人房间注册。
     */
    public synchronized void init() {
        if (configManager.loadFail()) {
            throw new RuntimeException("加载配置文件失败: "
                    + System.getProperty("table.config.dir",
                    System.getProperty("tableConfigDir", "../config")));
        }
        // 历史自定义模板曾污染房间列表；官方只保留 Excel + 内置机器人房间。
        customRoomRepository.disableAll();
        // 内置机器人模板不落库，服务启动时始终注册，保证大厅和游戏服都能看到。
        RobotRoomTemplates.register(configManager::putRuntimeModel);
        configManager.startWatch();
        roomTables.clear();
        for (TableModel model : configManager.getAllTableModels().values()) {
            if (isOfficialRoom(model.getId())) {
                roomTables.computeIfAbsent(model.getId(), k -> new ConcurrentHashMap<>());
            }
        }
        logger.info("房间管理器初始化完成,加载模板数量: {}", roomTables.size());
    }

    /**
     * 判断是否属于大厅对外展示的官方模板房间。
     * <p>
     * 麻将 1(4人)/11(2人)/12(3人)/9001(4人快速)；斗地主 2/9002/9003；跑得快/拖拉机。
     * </p>
     *
     * @param roomId 房间模板 ID
     * @return true 表示为官方房间
     */
    public static boolean isOfficialRoom(int roomId) {
        return roomId == 1 || roomId == 11 || roomId == 12 || roomId == 2
                || roomId == RobotRoomTemplates.MAHJONG_ROOM_ID
                || roomId == RobotRoomTemplates.DOU_DIZHU_ROB_ROOM_ID
                || roomId == RobotRoomTemplates.DOU_DIZHU_ROOM_ID
                || roomId == RobotRoomTemplates.PAO_DE_KUAI_ROOM_ID
                || roomId == RobotRoomTemplates.TRACTOR_ROOM_ID;
    }

    /**
     * 获取全量对外开放的房间桌面结构视图。
     *
     * @return Protobuf 协议房间列表响应包
     */
    public synchronized LobbyProto.AckRoomList getAllRoomTable() {
        LobbyProto.AckRoomList.Builder response = LobbyProto.AckRoomList.newBuilder();
        try {
            ensureOfficialRoomsRegistered();
            int totalRooms = 0;
            for (Map.Entry<Integer, Map<Long, TableInfo>> roomEntry : roomTables.entrySet()) {
                if (!isOfficialRoom(roomEntry.getKey())) {
                    continue;
                }
                ModelProto.Room roomProto = buildRoomProto(roomEntry.getKey(), roomEntry.getValue());
                response.addRoomList(roomProto);
                totalRooms += roomEntry.getValue().size();
            }
            logger.debug("返回房间列表,房间类型数: {}, 总房间数: {}", response.getRoomListCount(), totalRooms);
        } catch (Exception e) {
            logger.error("获取房间列表失败", e);
        }
        return response.build();
    }

    private void ensureOfficialRoomsRegistered() {
        for (TableModel model : configManager.getAllTableModels().values()) {
            if (isOfficialRoom(model.getId())) {
                roomTables.computeIfAbsent(model.getId(), k -> new ConcurrentHashMap<>());
            }
        }
    }

    private ModelProto.Room buildRoomProto(int roomId, Map<Long, TableInfo> tables) {
        ModelProto.Room.Builder roomBuilder = ModelProto.Room.newBuilder();
        roomBuilder.setRoomId(roomId);
        TableModel tableModel = configManager.getTableModel(roomId);
        if (tableModel != null) {
            roomBuilder.setGameType(tableModel.getType());
        }
        if (tables != null && !tables.isEmpty()) {
            for (TableInfo tableInfo : tables.values()) {
                roomBuilder.addTables(tableInfo.getTableInfo());
            }
        }
        return roomBuilder.build();
    }

    public synchronized TableModel getTableModel(int modelId) {
        return configManager.getTableModel(modelId);
    }

    public synchronized void putRuntimeModel(TableModel model) {
        putRuntimeModel(model, "system");
    }

    /**
     * 注册仅在内存中生效的运行时房间模板（不落库，避免历史冗余模板刷屏）。
     *
     * @param model 规则模板
     * @param createdBy 创建者
     */
    public synchronized void putRuntimeModel(TableModel model, String createdBy) {
        configManager.putRuntimeModel(model);
        if (isOfficialRoom(model.getId()) || model.getId() >= 10000) {
            roomTables.computeIfAbsent(model.getId(), k -> new ConcurrentHashMap<>());
        }
        logger.info("注册运行时房间模板(不落库), id: {}, type: {}, by: {}",
                model.getId(), model.getType(), createdBy);
    }

    public synchronized int nextRuntimeModelId() {
        int max = 10000;
        for (Integer id : configManager.getAllTableModels().keySet()) {
            if (id >= max) max = id + 1;
        }
        return max;
    }

    public synchronized TableInfo getTableById(long tableId) {
        return tableInfoMap.get(tableId);
    }

    /**
     * 从大厅中完全移除指定桌子，并清理在桌玩家的桌面引用绑定。
     *
     * @param tableId 桌子 ID
     */
    public synchronized void removeTable(long tableId) {
        TableInfo tableInfo = tableInfoMap.remove(tableId);
        if (tableInfo != null) {
            for (User user : new ArrayList<>(tableInfo.getTableRoles())) {
                if (user != null) {
                    tableInfo.removeUser(user);
                }
            }
            Map<Long, TableInfo> tables = roomTables.get(tableInfo.getModel().getId());
            if (tables != null) {
                tables.remove(tableId);
            }
            logger.info("大厅移除桌子, tableId: {}", tableId);
        }
    }

    /**
     * 快速匹配：寻找指定玩法模板下一个可立即入座的等待中空桌。
     *
     * @param modelId 玩法模板 ID
     * @return 可入座桌子，无空位时返回 null
     */
    public synchronized TableInfo getCanJoinTable(int modelId) {
        Map<Long, TableInfo> model = roomTables.get(modelId);
        if (model == null || model.isEmpty()) {
            return null;
        }
        for (Map.Entry<Long, TableInfo> entry : model.entrySet()) {
            if (entry.getValue().canJoin()) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 同步并登记游戏服上报的桌子信息。
     *
     * @param roomTable Protobuf 桌面信息
     * @return 装配后的 TableInfo 对象
     */
    public synchronized TableInfo putRoomInfo(ModelProto.RoomTableInfo roomTable) {
        TableModel model = configManager.getTableModel(roomTable.getRoomId());
        if (model == null) {
            logger.warn("putRoomInfo失败, 房间模板不存在, roomId: {}", roomTable.getRoomId());
            return null;
        }
        TableInfo tableInfo = new TableInfo(roomTable.getTableId(), roomTable.getCreatorId(), model);
        tableInfo.setOwnerId(roomTable.getOwnerId());
        roomTables.computeIfAbsent(model.getId(), k -> new ConcurrentHashMap<>())
                .put(tableInfo.getTableId(), tableInfo);
        tableInfoMap.put(tableInfo.getTableId(), tableInfo);
        return tableInfo;
    }

    public synchronized void clearAllTables() {
        int count = tableInfoMap.size();
        tableInfoMap.clear();
        for (Map<Long, TableInfo> tables : roomTables.values()) {
            tables.clear();
        }
        logger.info("已清空所有桌子,数量: {}", count);
    }

    public synchronized void restoreTables(List<ModelProto.RoomTableInfo> tableList) {
        if (tableList == null || tableList.isEmpty()) {
            return;
        }
        for (ModelProto.RoomTableInfo info : tableList) {
            putRoomInfo(info);
        }
        logger.info("恢复桌子完成,数量: {}", tableList.size());
    }

    public synchronized void shutdown() {
        clearAllTables();
        configManager.stopWatch();
    }

    /**
     * 获取当前系统活跃桌子总数。
     *
     * @return 活跃桌子总数
     */
    public synchronized int getTableCount() {
        return tableInfoMap.size();
    }
}


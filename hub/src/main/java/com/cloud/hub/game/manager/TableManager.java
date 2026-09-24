package com.cloud.hub.game.manager;

import com.cloud.hub.framework.metrics.HubMetrics;
import com.cloud.hub.game.Game;
import com.cloud.hub.game.domain.table.Table;
import com.cloud.hub.game.domain.table.TableUser;
import com.cloud.hub.game.domain.ddz.DdzTable;
import com.cloud.hub.game.domain.mj.MjTable;
import com.cloud.hub.game.domain.pdk.PdkTable;
import com.cloud.hub.game.domain.tractor.TractorTable;
import com.cloud.hub.game.manager.thread.GameThreadPoolManager;
import model.tablemodel.RobotRoomTemplates;
import model.tablemodel.TableModel;
import model.tablemodel.TableModelJson;
import msg.registor.message.GMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.GameProto;
import proto.ModelProto;
import tool.config.TableConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 游戏对局桌运行与生命周期管理器。
 * <p>
 * 负责麻将 (MjTable)、斗地主 (DdzTable)、跑得快 (PdkTable)、拖拉机 (TractorTable) 的具体实例创建，
 * 并与 {@link GameThreadPoolManager} 联动完成同桌串行投递调度与定时心跳注册。
 * </p>
 *
 * @author cloud
 */
public class TableManager {

    private static final Logger logger = LoggerFactory.getLogger(TableManager.class);

    /** 桌子唯一 ID 到对局桌运行实例映射表 */
    private final Map<Long, Table> tableMap;
    /** 桌号发号器：时间戳毫秒 + 序号，避免并发碰撞 */
    private final AtomicLong tableIdSeq = new AtomicLong(System.currentTimeMillis());

    /** 房间配置模板管理器 */
    private final TableConfigManager configManager;
    /** 全局统一线程池管理器 */
    private final GameThreadPoolManager threadPoolManager;

    /**
     * 构造桌子管理器，初始化配置加载与动态文件监听。
     */
    public TableManager() {
        threadPoolManager = Game.getInstance().getThreadPoolManager();
        tableMap = new ConcurrentHashMap<>();
        configManager = new TableConfigManager();
        if (configManager.loadFail()) {
            throw new RuntimeException("加载配置文件失败");
        }
        // 与大厅注册同一组固定模板，机器人桌无需依赖外部配置文件。
        RobotRoomTemplates.register(configManager::putRuntimeModel);
        configManager.startWatch();
        logger.info("桌子管理器初始化完成");
    }


    /**
     * 添加桌子
     */
    private void addTable(Table table) {
        if (table == null) {
            logger.warn("尝试添加空桌子");
            return;
        }

        long tableId = table.getTableId();
        Table existingTable = tableMap.get(tableId);

        if (existingTable != null) {
            logger.warn("桌子已存在,添加失败, tableId: {}", tableId);
        } else {
            tableMap.put(tableId, table);
            threadPoolManager.registerTable(tableId);
            HubMetrics metrics = HubMetrics.getInstance();
            if (metrics != null) {
                metrics.recordTableCreated();
            }
            logger.debug("添加新桌子, tableId: {}", tableId);
        }
    }

    /**
     * 获取桌子
     */
    public Table getTable(long tableId) {
        Table table = tableMap.get(tableId);
        if (table == null) {
            logger.debug("桌子不存在, tableId: {}", tableId);
        }
        return table;
    }

    /**
     * 获取当前所有活跃中的牌桌实例列表（只读浅拷贝）。
     *
     * @return 活跃桌子列表
     */
    public List<Table> getAllTables() {
        return new ArrayList<>(tableMap.values());
    }

    /**
     * 删除桌子
     */
    private void removeTable(long tableId) {
        Table removedTable = tableMap.remove(tableId);
        if (removedTable != null) {
            notifyPlayersTableDestroyed(removedTable);
            removedTable.stop();
            threadPoolManager.removeTable(tableId);
            HubMetrics metrics = HubMetrics.getInstance();
            if (metrics != null) {
                metrics.recordTableDestroyed();
            }
            logger.info("删除桌子, tableId: {}", tableId);
            notifyRoomTableDestroyed(tableId);
        } else {
            logger.warn("桌子不存在,无法删除, tableId: {}", tableId);
        }
    }

    /**
     * 通知仍在桌内的客户端桌子已解散，客户端收到后应清理桌面并返回大厅。
     */
    private void notifyPlayersTableDestroyed(Table table) {
        GameProto.NotTableState notification = table.buildStateNotification(
                utils.registry.enums.TableState.TABLE_DIS.getId(), System.currentTimeMillis(), 0);
        for (TableUser user : table.getSeatUsers().values()) {
            try {
                user.sendRoleMessage(notification, GMsg.NOT_TABLE_STATE, table.getTableId());
            } catch (Exception e) {
                logger.warn("通知玩家桌子解散失败, tableId: {}, userId: {}", table.getTableId(), user.getUserId(), e);
            }
        }
    }

    /**
     * 在桌子管理线程创建桌子，网络线程只接收 Future。
     */
    public CompletableFuture<Table> createTableAsync(int roomId, ModelProto.RoomRole role) {
        return threadPoolManager.submitTableManager(() -> createTable(roomId, role));
    }

    /**
     * 在桌子管理线程删除桌子，桌子线程不直接修改全局索引。
     */
    public void removeTableAsync(long tableId) {
        threadPoolManager.submitTableManager(() -> {
            removeTable(tableId);
            return null;
        });
    }

    public CompletableFuture<List<Table>> findTablesByUserIdAsync(int userId) {
        return threadPoolManager.submitTableManager(() -> findTablesByUserId(userId));
    }

    public CompletableFuture<List<ModelProto.RoomTableInfo>> getAllTableInfoAsync() {
        return threadPoolManager.submitTableManager(this::snapshotTables).thenCompose(this::collectTableInfo);
    }

    /**
     * 线程池由 GameThreadPoolManager 统一关闭，这里仅做业务侧占位。
     */
    public void shutdown() {
        // no-op：共享线程池生命周期归属 GameThreadPoolManager
    }

    private List<Table> snapshotTables() {
        return new ArrayList<>(tableMap.values());
    }

    private CompletableFuture<List<ModelProto.RoomTableInfo>> collectTableInfo(List<Table> tables) {
        List<CompletableFuture<ModelProto.RoomTableInfo>> futures = new ArrayList<>();
        for (Table table : tables) futures.add(table.getRoomTableInfoAsync());
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> collectResults(futures));
    }

    private List<ModelProto.RoomTableInfo> collectResults(List<CompletableFuture<ModelProto.RoomTableInfo>> futures) {
        List<ModelProto.RoomTableInfo> result = new ArrayList<>();
        for (CompletableFuture<ModelProto.RoomTableInfo> future : futures) result.add(future.join());
        return result;
    }

    /**
     * 通知大厅桌子已销毁（进程内直接调用）。
     */
    private void notifyRoomTableDestroyed(long tableId) {
        try {
            com.cloud.hub.lobby.manager.table.TableManager.getInstance().removeTable(tableId);
            logger.debug("内置Lobby已移除桌子, tableId: {}", tableId);
        } catch (Exception e) {
            logger.error("通知Lobby桌子销毁失败, tableId: {}", tableId, e);
        }
    }

    /**
     * 通知大厅玩家离桌（进程内直接调用）。
     */
    public void notifyRoomPlayerLeft(long tableId, int roleId) {
        try {
            com.cloud.hub.lobby.manager.table.TableInfo info =
                    com.cloud.hub.lobby.manager.table.TableManager.getInstance().getTableById(tableId);
            com.cloud.hub.lobby.manager.User user =
                    com.cloud.hub.lobby.manager.UserManager.getInstance().getUser(roleId);
            if (info != null && user != null) {
                info.removeUser(user);
            }
            logger.debug("内置Lobby已同步玩家离桌, tableId: {}, roleId: {}", tableId, roleId);
        } catch (Exception e) {
            logger.error("通知Lobby玩家离桌失败, tableId: {}, roleId: {}", tableId, roleId, e);
        }
    }

    /**
     * 获取新的桌子ID（单调递增，防撞号）
     */
    private long getTableId() {
        long id = tableIdSeq.incrementAndGet();
        logger.info("创建新桌子ID: {}", id);
        return id;
    }

    /**
     * 创建桌子
     *
     * @param roomId 桌子类型
     * @param role   创建的玩家（avatar 若以 TMJSON: 开头则为自定义模板覆盖）
     * @return 桌子实例
     */
    public Table createTable(int roomId, ModelProto.RoomRole role) {
        synchronized (TableManager.class) {
            TableModel model = resolveModel(roomId, role);
            if (model == null) {
                throw new IllegalArgumentException("未知房间模板 roomId=" + roomId);
            }
            Table table;
            if (model.getType() == 1) {
                table = new MjTable(getTableId(), model, role);
            } else if (model.getType() == 3) {
                table = new PdkTable(getTableId(), model, role);
            } else if (model.getType() == 4) {
                table = new TractorTable(getTableId(), model, role);
            } else {
                table = new DdzTable(getTableId(), model, role);
            }
            addTable(table);
            return table;
        }
    }

    private TableModel resolveModel(int roomId, ModelProto.RoomRole role) {
        if (role != null && !role.getAvatar().isEmpty()) {
            String avatar = role.getAvatar().toStringUtf8();
            if (avatar.startsWith("TMJSON:")) {
                TableModel custom = TableModelJson.parse(avatar.substring("TMJSON:".length()));
                if (custom != null) {
                    if (custom.getId() <= 0) {
                        custom.setId(roomId > 0 ? roomId : (10000 + (int) (System.currentTimeMillis() % 100000)));
                    }
                    configManager.putRuntimeModel(custom);
                    return custom;
                }
            }
        }
        return configManager.getTableModel(roomId);
    }

    /**
     * 查找用户所在的桌子
     */
    public List<Table> findTablesByUserId(int userId) {
        List<Table> result = new ArrayList<>();
        for (Table table : tableMap.values()) {
            if (table.getUsers().containsKey(userId)) {
                result.add(table);
            }
        }
        return result;
    }

    /**
     * 获取所有桌子的RoomTableInfo（用于Room重启恢复）
     */
    public List<ModelProto.RoomTableInfo> getAllTableInfo() {
        List<ModelProto.RoomTableInfo> list = new ArrayList<>();
        for (Table table : tableMap.values()) {
            ModelProto.RoomTableInfo.Builder builder = ModelProto.RoomTableInfo.newBuilder()
                    .setTableId(table.getTableId())
                    .setRoomId(table.getTableModel().getId())
                    .setOwnerId(table.getOwnerId())
                    .setCreatorId(table.getOwnerId())
                    .setGameType(table.getTableModel().getType());

            for (TableUser user : table.getSeatUsers().values()) {
                if (user != null) {
                    builder.addTableRoles(ModelProto.RoomRole.newBuilder()
                            .setRoleId(user.getUserId())
                            .setNickName(com.google.protobuf.ByteString.copyFromUtf8(user.getNick()))
                            .build());
                }
            }
            list.add(builder.build());
        }
        return list;
    }
}

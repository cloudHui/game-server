package com.cloud.hub.lobby.manager.table;

import com.cloud.hub.lobby.manager.User;
import model.tablemodel.TableModel;
import proto.ConstProto;
import proto.ModelProto;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大厅桌面运行信息包装类。
 * <p>
 * 缓存特定桌子的唯一 ID、创建者、房主、玩法规则模板 {@link TableModel}、
 * 运行状态 {@link ConstProto.TableState} 以及入座的在线用户集合，
 * 并支持转化为对外广播的 Protobuf 视图对象。
 * </p>
 *
 * @author cloud
 */
public class TableInfo {

    /** 桌子唯一 ID */
    private final long tableId;
    /** 桌子创建者玩家 ID */
    private final int creatorId;
    /** 绑定的房间玩法配置模板 */
    private final TableModel model;
    /** 当前房主 ID */
    private int ownerId;
    /** 桌子当前运行状态（等待中、进行中等） */
    private ConstProto.TableState tableState = ConstProto.TableState.WAIT;
    /** 当前在桌的玩家用户安全集合 */
    private final Set<User> tableRoles = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * 构造桌子信息。
     *
     * @param tableId 桌子 ID
     * @param creatorId 创建者用户 ID
     * @param model 规则配置模型
     */
    public TableInfo(long tableId, int creatorId, TableModel model) {
        this.tableId = tableId;
        this.creatorId = creatorId;
        this.model = model;
    }

    public Long getTableId() {
        return tableId;
    }

    public int getCreatorId() {
        return creatorId;
    }

    public TableModel getModel() {
        return model;
    }

    public int getOwnerId() {
        return ownerId;
    }

    public ConstProto.TableState getTableState() {
        return tableState;
    }

    public Set<User> getTableRoles() {
        return tableRoles;
    }

    public void setOwnerId(int ownerId) {
        this.ownerId = ownerId;
    }

    public void setTableState(ConstProto.TableState tableState) {
        this.tableState = tableState;
    }

    /**
     * 将玩家加入桌子并建立双向绑定。
     *
     * @param user 加入的用户
     */
    public void joinRole(User user) {
        tableRoles.add(user);
        user.addTable(tableId);
    }

    /**
     * 玩家离开桌子并解除双向绑定。
     *
     * @param user 离开的用户
     */
    public void removeUser(User user) {
        tableRoles.remove(user);
        user.removeTable(tableId);
    }

    /**
     * 检查当前桌子是否允许新玩家加入。
     * <p>
     * 仅当桌子处于 WAIT 等待就绪状态且当前在座人数未满模板座位上限时才允许加入。
     * </p>
     *
     * @return true 表示可以加入
     */
    public boolean canJoin() {
        return tableState == ConstProto.TableState.WAIT && tableRoles.size() < model.getSeatNum();
    }

    /**
     * 序列化构建 Protobuf 房间桌面详情对象。
     *
     * @return Protobuf RoomTableInfo 视图
     */
    public ModelProto.RoomTableInfo getTableInfo() {
        ModelProto.RoomTableInfo.Builder builder = ModelProto.RoomTableInfo.newBuilder()
                .setRoomId(model.getId())
                .setTableId(tableId)
                .setOwnerId(ownerId)
                .setStat(tableState.getNumber())
                .setCreatorId(creatorId)
                .setGameType(model.getType());

        for (User user : tableRoles) {
            if (user != null) {
                builder.addTableRoles(user.getRole());
            }
        }
        return builder.build();
    }
}


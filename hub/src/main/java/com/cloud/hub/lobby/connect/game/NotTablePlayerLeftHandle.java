package com.cloud.hub.lobby.connect.game;

import com.google.protobuf.Message;
import com.cloud.hub.lobby.manager.User;
import com.cloud.hub.lobby.manager.UserManager;
import com.cloud.hub.lobby.manager.table.TableInfo;
import com.cloud.hub.lobby.manager.table.TableManager;
import msg.annotation.ProcessType;
import msg.registor.message.SMsg;
import net.client.Sender;
import net.handler.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ServerProto;

/**
 * 游戏服务通知大厅「玩家已离桌」的内部通知处理器。
 *
 * <p><b>业务流转背景：</b>
 * 当玩家在游戏桌内主动退出、解散或被踢出时，Game 服务向大厅广播 {@link ServerProto.NotTablePlayerLeft}。
 * 大厅接收后需同步更新内部 {@link TableInfo} 维护的玩家列表。
 *
 * <p><b>空桌自清理机制：</b>
 * 移除玩家后若该桌已无人（{@code tableInfo.getTableRoles().isEmpty()}），
 * 则立即从 {@link TableManager} 中销毁该大厅桌子镜像，防止形成无人的“僵尸桌”。
 */
@ProcessType(SMsg.NOT_TABLE_PLAYER_LEFT_MSG)
public class NotTablePlayerLeftHandle implements Handler {

    /**
     * 日志记录器
     */
    private static final Logger logger = LoggerFactory.getLogger(NotTablePlayerLeftHandle.class);

    @Override
    public boolean handler(Sender sender, int clientId, Message message, long mapId, int sequence) {
        try {
            ServerProto.NotTablePlayerLeft not = (ServerProto.NotTablePlayerLeft) message;
            long tableId = not.getTableId();
            int roleId = not.getRoleId();
            TableInfo tableInfo = TableManager.getInstance().getTableById(tableId);
            if (tableInfo == null) {
                logger.info("收到离桌通知但桌子不存在, tableId: {}, roleId: {}", tableId, roleId);
                return true;
            }
            User user = UserManager.getInstance().getUser(roleId);
            if (user != null) {
                tableInfo.removeUser(user);
            } else {
                // 用户已离线：按 roleId 从桌内移除归属
                for (User u : tableInfo.getTableRoles()) {
                    if (u != null && u.getUserIdInt() == roleId) {
                        tableInfo.removeUser(u);
                        break;
                    }
                }
            }
            if (tableInfo.getTableRoles().isEmpty()) {
                TableManager.getInstance().removeTable(tableId);
                logger.info("离桌后空桌，移除大厅桌子, tableId: {}, roleId: {}", tableId, roleId);
            } else {
                logger.info("大厅同步玩家离桌, tableId: {}, roleId: {}, remain: {}",
                        tableId, roleId, tableInfo.getTableRoles().size());
            }
        } catch (Exception e) {
            logger.error("处理玩家离桌通知失败", e);
        }
        return true;
    }
}

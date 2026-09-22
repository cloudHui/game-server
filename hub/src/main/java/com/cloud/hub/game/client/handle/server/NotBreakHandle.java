package com.cloud.hub.game.client.handle.server;

import com.cloud.hub.game.Game;
import com.cloud.hub.game.domain.table.Table;
import com.cloud.hub.game.domain.table.TableUser;
import com.google.protobuf.Message;
import msg.annotation.ProcessType;
import msg.registor.message.CMsg;
import net.client.Sender;
import net.handler.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ServerProto;
import utils.trace.TraceContext;

/**
 * 处理网关通知的玩家断线事件处理器。
 * <p>
 * 设置玩家离线状态，并在所属桌串行队列排他执行断线状态标记。
 */
@ProcessType(CMsg.NOT_BREAK)
public class NotBreakHandle implements Handler {

    /**
     * 日志记录器。
     */
    private static final Logger logger = LoggerFactory.getLogger(NotBreakHandle.class);

    /**
     * 处理网关推送的玩家断线通知。
     *
     * @param sender   消息发送者
     * @param clientId 客户端连接 ID
     * @param message  断线通知协议体
     * @param mapId    桌号（通知时常为 0，需动态检索玩家所在桌）
     * @param sequence 消息序列号
     * @return 处理结果状态
     */
    @Override
    public boolean handler(Sender sender, int clientId, Message message, long mapId, int sequence) {
        try {
            ServerProto.NotBreak notification = (ServerProto.NotBreak) message;
            int userId = notification.getUserId();
            int gateClientId = notification.getGateClientId();

            TraceContext.setUserId(userId);
            logger.info("处理玩家断线通知, userId: {}, gateClientId: {}", userId, gateClientId);
            Game.getInstance().getTableManager().findTablesByUserIdAsync(userId)
                    .whenComplete((tables, error) -> {
                        if (error != null) {
                            logger.error("查找断线玩家所在桌子失败, userId: {}", userId, error);
                            return;
                        }
                        for (Table table : tables) {
                            table.execute("玩家断线", () -> processUserDisconnect(table, userId, gateClientId));
                        }
                    });
            return true;
        } catch (Exception e) {
            logger.error("处理玩家断线通知失败, clientId: {}", clientId, e);
            return false;
        }
    }

    /**
     * 在桌串行线程中执行玩家离线标记。
     *
     * @param table        目标桌子
     * @param userId       玩家 ID
     * @param gateClientId 网关连接句柄 ID
     */
    private void processUserDisconnect(Table table, int userId, int gateClientId) {
        TableUser user = table.getUsers().get(userId);
        if (user == null) return;
        // 防旧断线竞争：如果玩家已经建立新连接且 gateId 不匹配，忽略过期的旧断线事件
        if (gateClientId != 0 && user.getGateId() != 0 && user.getGateId() != gateClientId) {
            logger.info("忽略旧连接断线, userId: {}, tableId: {}, noticeGate: {}, currentGate: {}",
                    userId, table.getTableId(), gateClientId, user.getGateId());
            return;
        }
        user.setOnLine(false);
        TraceContext.setTableId(table.getTableId());
        logger.info("玩家标记离线, userId: {}, tableId: {}", userId, table.getTableId());
        if (table.gaming()) {
            logger.info("游戏进行中玩家断线, userId: {}, tableId: {}，等待超时自动托管",
                    userId, table.getTableId());
        }
    }
}

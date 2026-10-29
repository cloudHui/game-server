package game.client.handle.server;

import com.google.protobuf.Message;
import game.Game;
import game.domain.table.Table;
import game.domain.table.TableUser;
import msg.registor.message.CMsg;
import net.client.Sender;
import net.handler.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ServerProto;
import utils.registry.annotation.ProcessType;
import utils.trace.TraceContext;

/**
 * 玩家断线事件处理器
 * <p>
 * 接收网关发送的玩家连接断开通知，标记对应桌内玩家为离线状态。
 */
@ProcessType(CMsg.NOT_BREAK)
public class NotBreakHandle implements Handler {

    private static final Logger logger = LoggerFactory.getLogger(NotBreakHandle.class);

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
                            table.execute(() -> processUserDisconnect(table, userId, gateClientId));
                        }
                    });
        } catch (Exception e) {
            logger.error("处理玩家断线通知失败, clientId: {}", clientId, e);
        }
        return true;
    }

    /**
     * 桌线程内处理离线标记
     *
     * @param table        桌子实例
     * @param userId       玩家 ID
     * @param gateClientId 断开连接对应的网关 Client ID
     */
    private void processUserDisconnect(Table table, int userId, int gateClientId) {
        TableUser user = table.getUsers().get(userId);
        if (user == null) {
            return;
        }

        // 仅在断线通知的 gateClientId 匹配当前活跃 gateId 时才标记离线
        if (gateClientId != 0 && user.getGateId() != 0 && user.getGateId() != gateClientId) {
            logger.info("忽略旧连接断线通知, tableId: {}, userId: {}, noticeGateId: {}, currentGateId: {}",
                    table.getTableId(), userId, gateClientId, user.getGateId());
            return;
        }

        user.setGateId(0);
        user.setOnLine(false);
        logger.info("玩家已离线, tableId: {}, userId: {}", table.getTableId(), userId);
    }
}

package game.client.handle.server;

import game.Game;
import game.manager.table.Table;
import game.manager.table.TableUser;
import msg.registor.message.CMsg;
import net.msg.Msg;
import net.msg.MsgContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ServerProto;
import utils.trace.TraceContext;

/**
 * 玩家断线事件处理器
 * <p>
 * 接收网关发送的玩家连接断开通知，标记对应桌内玩家为离线状态。
 */
public class NotBreakHandle {

    private static final Logger logger = LoggerFactory.getLogger(NotBreakHandle.class);

    /**
     * 处理玩家断线事件
     *
     * @param ctx 消息上下文
     */
    @Msg(id = CMsg.NOT_BREAK, desc = "玩家断线通知")
    public void handle(MsgContext<ServerProto.NotBreak> ctx) {
        try {
            ServerProto.NotBreak notification = ctx.getMsg();
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
            logger.error("处理玩家断线通知失败, clientId: {}", ctx.getClientId(), e);
        }
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
        if (gateClientId != 0 && user.getGateId() != 0 && user.getGateId() != gateClientId) {
            logger.info("忽略旧连接断线, userId: {}, tableId: {}, noticeGate: {}, currentGate: {}",
                    userId, table.getTableId(), gateClientId, user.getGateId());
            return;
        }
        user.setOnLine(false);
        TraceContext.setTableId(table.getTableId());
        logger.info("玩家标记离线, userId: {}, tableId: {}", userId, table.getTableId());
        if (table.gaming()) {
            logger.info("游戏进行中玩家断线, userId: {}, tableId: {}，等待超时自动处理",
                    userId, table.getTableId());
        }
    }
}

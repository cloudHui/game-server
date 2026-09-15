package lobby.client.handle.server.gate;

import lobby.manager.User;
import lobby.manager.UserManager;
import msg.registor.message.CMsg;
import net.msg.Msg;
import net.msg.MsgContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ServerProto;

/**
 * 玩家断线事件处理器（大厅）
 * <p>
 * 仅标记 offline，保留 User 与 tables，便于玩家重登拉回桌子。
 * 若已被新连接顶替，则忽略旧连接断线。
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
            ServerProto.NotBreak notice = ctx.getMsg();
            int userId = notice.getUserId();
            int gateClientId = notice.getGateClientId();
            logger.info("收到玩家断线通知, userId: {}, gateClientId: {}", userId, gateClientId);
            handleUserDisconnect(userId, gateClientId);
        } catch (Exception e) {
            logger.error("处理断线通知失败, clientId: {}", ctx.getClientId(), e);
        }
    }

    /**
     * 处理玩家离线状态
     *
     * @param userId       玩家 ID
     * @param gateClientId 网关连接 ID
     */
    private void handleUserDisconnect(int userId, int gateClientId) {
        User user = UserManager.getInstance().getUser(userId);
        if (user == null) {
            logger.warn("用户不存在,无法处理断线, userId: {}", userId);
            return;
        }
        // 已被新连接顶替：旧连接断线忽略，避免把新会话标 offline / 清桌
        if (gateClientId != 0 && user.getGateId() != 0 && user.getGateId() != gateClientId) {
            logger.info("忽略旧连接断线, userId: {}, noticeGate: {}, currentGate: {}",
                    userId, gateClientId, user.getGateId());
            return;
        }
        user.setOffline(true);
        logger.info("玩家标记离线(保留桌子), userId: {}, tables: {}", userId, user.getAllTables().size());
    }
}

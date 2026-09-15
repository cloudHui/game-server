package center.client.handle;

import msg.registor.message.CMsg;
import net.msg.Msg;
import net.msg.MsgContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ServerProto;

/**
 * 玩家断线事件处理器（中心服）
 * <p>
 * 处理网关服务器通知的玩家断线事件，清理客户端连接与网关的映射关系。
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
            String clientIp = notification.getCert().toStringUtf8();

            logger.info("处理玩家断线通知, clientIp: {}, userId: {}", clientIp, notification.getUserId());

            // 清理客户端连接映射
            NotClientLinkHandle.clientDisconnect(clientIp);
        } catch (Exception e) {
            logger.error("处理断线通知失败, clientId: {}", ctx.getClientId(), e);
        }
    }
}

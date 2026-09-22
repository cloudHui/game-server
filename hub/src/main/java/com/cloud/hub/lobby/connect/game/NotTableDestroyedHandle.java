package com.cloud.hub.lobby.connect.game;

import com.google.protobuf.Message;
import com.cloud.hub.lobby.manager.table.TableManager;
import msg.annotation.ProcessType;
import msg.registor.message.SMsg;
import net.client.Sender;
import net.handler.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ServerProto;

/**
 * 游戏服务通知大厅「桌子已销毁」的内部通知处理器。
 *
 * <p><b>业务流转背景：</b>
 * 当牌局结束、超时未开局、所有玩家离桌或被 GM 命令强制解散时，
 * 游戏服务释放该桌物理内存并向大厅发送 {@link ServerProto.NotTableDestroyed}。
 * 大厅收到后立即从 {@link TableManager} 移除对应桌子映射，保证大厅房间与桌子列表的数据一致性。
 */
@ProcessType(SMsg.NOT_TABLE_DESTROYED_MSG)
public class NotTableDestroyedHandle implements Handler {

    /**
     * 日志记录器
     */
    private static final Logger logger = LoggerFactory.getLogger(NotTableDestroyedHandle.class);

    @Override
    public boolean handler(Sender sender, int clientId, Message message, long mapId, int sequence) {
        try {
            ServerProto.NotTableDestroyed not = (ServerProto.NotTableDestroyed) message;
            TableManager.getInstance().removeTable(not.getTableId());
            logger.info("收到桌子销毁通知, tableId: {}", not.getTableId());
        } catch (Exception e) {
            logger.error("处理桌子销毁通知失败", e);
        }
        return true;
    }
}

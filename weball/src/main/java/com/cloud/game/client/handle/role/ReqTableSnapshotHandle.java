package com.cloud.game.client.handle.role;

import com.cloud.game.Game;
import com.cloud.game.manager.table.Table;
import com.cloud.game.manager.table.TableUser;
import com.google.protobuf.Message;
import msg.annotation.ProcessType;
import msg.registor.message.GMsg;
import net.client.Sender;
import net.handler.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.GameProto;

/**
 * 独立只读牌桌快照：不复用入桌/重连链路，不修改玩家连接与在线状态。
 */
@ProcessType(GMsg.REQ_TABLE_SNAPSHOT)
public class ReqTableSnapshotHandle implements Handler {
    private static final Logger logger = LoggerFactory.getLogger(ReqTableSnapshotHandle.class);

    @Override
    public boolean handler(Sender sender, int clientId, Message message, long mapId, int sequence) {
        GameProto.ReqTableSnapshot request = (GameProto.ReqTableSnapshot) message;
        Table table = Game.getInstance().getTableManager().getTable(request.getTableId());
        if (table == null) return true;
        table.execute(() -> {
            TableUser viewer = table.getUsers().get(clientId);
            if (viewer == null) return;
            GameProto.AckTableSnapshot snapshot = table.buildTableSnapshot(viewer);
            sender.sendMessage(clientId, GMsg.ACK_TABLE_SNAPSHOT,
                    table.getTableId(), snapshot, sequence);
        }).exceptionally(error -> {
            logger.error("生成牌桌快照失败, tableId: {}, userId: {}",
                    request.getTableId(), clientId, error);
            return null;
        });
        return true;
    }
}

package com.cloud.hub.game.client.handle.role;

import com.cloud.hub.game.client.handle.TableHandlerHelper;
import com.cloud.hub.game.domain.table.TableUser;
import com.google.protobuf.Message;
import utils.registry.annotation.ProcessType;
import msg.registor.message.GMsg;
import net.client.Sender;
import net.handler.Handler;
import proto.GameProto;

/**
 * 独立只读牌桌快照：不复用入桌/重连链路，不修改玩家连接与在线状态。
 */
@ProcessType(GMsg.REQ_TABLE_SNAPSHOT)
public class ReqTableSnapshotHandle implements Handler {

    @Override
    public boolean handler(Sender sender, int clientId, Message message, long mapId, int sequence) {
        GameProto.ReqTableSnapshot request = (GameProto.ReqTableSnapshot) message;
        return TableHandlerHelper.dispatch(request.getTableId(), "生成牌桌快照", table -> {
            TableUser viewer = table.getUsers().get(clientId);
            if (viewer == null) return;
            GameProto.AckTableSnapshot snapshot = table.buildTableSnapshot(viewer);
            sender.sendMessage(clientId, GMsg.ACK_TABLE_SNAPSHOT,
                    table.getTableId(), snapshot, sequence);
        });
    }
}

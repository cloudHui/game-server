package com.cloud.weball.game.client.handle.role;

import com.cloud.weball.game.Game;
import com.cloud.weball.game.domain.table.Table;
import com.cloud.weball.game.domain.table.TableUser;
import com.google.protobuf.Message;
import msg.annotation.ProcessType;
import msg.registor.message.GMsg;
import net.client.Sender;
import net.handler.Handler;
import proto.GameProto;

/** 接收网页牌桌心跳，并在所属桌线程更新玩家最后活动时间。 */
@ProcessType(GMsg.REQ_TABLE_HEARTBEAT)
public final class ReqTableHeartbeatHandle implements Handler {

    /** 心跳无需业务回包；连接有效性由后续桌推送和 WebSocket 状态共同确认。 */
    @Override
    public boolean handler(Sender sender, int clientId, Message message, long mapId, int sequence) {
        GameProto.ReqTableHeartbeat request = (GameProto.ReqTableHeartbeat) message;
        long tableId = request.getTableId() != 0 ? request.getTableId() : mapId;
        Table table = Game.getInstance().getTableManager().getTable(tableId);
        if (table == null) return true;
        table.execute(() -> record(table, clientId));
        return true;
    }

    /** 只接受桌内现存真人的心跳，避免脏请求改变机器人状态。 */
    private static void record(Table table, int userId) {
        TableUser user = table.getUsers().get(userId);
        if (user != null && !user.isRobot()) user.recordWebHeartbeat(System.currentTimeMillis());
    }
}

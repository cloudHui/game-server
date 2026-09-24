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
 * 独立只读牌桌快照请求处理器。
 * <p>
 * 满足前端页面刷新或观察者视角拉取当前牌面，
 * 不复用入桌/重连链路，不修改玩家连接与在线状态。
 *
 * @author cloud
 */
@ProcessType(GMsg.REQ_TABLE_SNAPSHOT)
public class ReqTableSnapshotHandle implements Handler {

    /**
     * 接收客户端拉取快照请求，调度至桌线程组装并回送当前快照。
     *
     * @param sender   消息发送者
     * @param clientId 玩家 ID
     * @param message  快照请求体 (ReqTableSnapshot)
     * @param mapId    桌号
     * @param sequence 消息序列号
     * @return 恒为 true
     */
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

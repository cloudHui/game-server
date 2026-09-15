package game.client.handle.role;

import game.Game;
import game.manager.table.Table;
import game.manager.table.TableUser;
import msg.registor.message.GMsg;
import net.msg.Msg;
import net.msg.MsgContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.GameProto;

/**
 * 独立只读牌桌快照处理器
 * <p>
 * 不复用入桌/重连链路，不修改玩家连接与在线状态，仅返回当前桌内状态快照。
 */
public class ReqTableSnapshotHandle {

    private static final Logger logger = LoggerFactory.getLogger(ReqTableSnapshotHandle.class);

    /**
     * 处理牌桌快照请求
     *
     * @param ctx 消息上下文
     */
    @Msg(id = GMsg.REQ_TABLE_SNAPSHOT, desc = "获取牌桌只读快照")
    public void handle(MsgContext<GameProto.ReqTableSnapshot> ctx) {
        GameProto.ReqTableSnapshot request = ctx.getMsg();
        int clientId = ctx.getClientId();
        long tableId = request.getTableId();

        Table table = Game.getInstance().getTableManager().getTable(tableId);
        if (table == null) {
            return;
        }

        table.execute(() -> {
            TableUser viewer = table.getUsers().get(clientId);
            if (viewer == null) {
                return;
            }
            GameProto.AckTableSnapshot snapshot = table.buildTableSnapshot(viewer);
            ctx.reply(GMsg.ACK_TABLE_SNAPSHOT, table.getTableId(), snapshot);
        }).exceptionally(error -> {
            logger.error("生成牌桌快照失败, tableId: {}, userId: {}", tableId, clientId, error);
            return null;
        });
    }
}

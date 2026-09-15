package game.client.handle.role;

import game.Game;
import game.manager.table.Table;
import game.manager.table.TableUser;
import msg.registor.message.GMsg;
import net.msg.Msg;
import net.msg.MsgContext;
import proto.GameProto;

/**
 * 接收网页牌桌心跳处理器
 * <p>
 * 在所属桌线程更新玩家最后活动时间，无需业务回包。
 */
public class ReqTableHeartbeatHandle {

    /**
     * 处理牌桌心跳请求
     *
     * @param ctx 消息上下文
     */
    @Msg(id = GMsg.REQ_TABLE_HEARTBEAT, desc = "更新牌桌玩家活跃心跳")
    public void handle(MsgContext<GameProto.ReqTableHeartbeat> ctx) {
        GameProto.ReqTableHeartbeat request = ctx.getMsg();
        long tableId = request.getTableId() != 0 ? request.getTableId() : ctx.getMapId();
        int clientId = ctx.getClientId();

        Table table = Game.getInstance().getTableManager().getTable(tableId);
        if (table == null) {
            return;
        }
        table.execute(() -> record(table, clientId));
    }

    /**
     * 只接受桌内现存真人的心跳，避免脏请求改变机器人状态
     *
     * @param table  桌子实例
     * @param userId 玩家 ID
     */
    private static void record(Table table, int userId) {
        TableUser user = table.getUsers().get(userId);
        if (user != null && !user.isRobot()) {
            user.recordWebHeartbeat(System.currentTimeMillis());
        }
    }
}

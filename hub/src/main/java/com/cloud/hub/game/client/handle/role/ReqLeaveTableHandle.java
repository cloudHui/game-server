package com.cloud.hub.game.client.handle.role;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloud.hub.game.Game;
import com.cloud.hub.game.client.handle.TableHandlerHelper;
import com.cloud.hub.game.domain.table.Table;
import com.cloud.hub.game.domain.table.TableUser;
import com.google.protobuf.Message;

import utils.registry.annotation.ProcessType;
import utils.registry.enums.TableState;
import msg.registor.message.GMsg;
import net.client.Sender;
import net.handler.Handler;
import net.message.TCPMessage;
import proto.ConstProto;
import proto.GameProto;

/**
 * 处理玩家请求离桌消息处理器。
 * <p>
 * <b>职责边界：</b>
 * <ul>
 * <li>等待阶段离桌：将玩家移出桌子，若桌空或无真人则异步销毁桌子；</li>
 * <li>对局中离桌：直接解散牌局，通过 {@link Table#dismissAndSettle()} 多态广播总结算并销毁桌子。</li>
 * </ul>
 */
@ProcessType(GMsg.REQ_LEAVE)
public class ReqLeaveTableHandle implements Handler {

    /**
     * 日志记录器。
     */
    private static final Logger logger = LoggerFactory.getLogger(ReqLeaveTableHandle.class);

    /**
     * 接收客户端离桌请求。
     *
     * @param sender   消息发送者
     * @param clientId 玩家 ID
     * @param message  离桌请求体
     * @param mapId    目标桌号
     * @param sequence 消息序列号
     * @return 恒为 true
     */
    @Override
    public boolean handler(Sender sender, int clientId, Message message, long mapId, int sequence) {
        logger.info("收到离开桌子请求, userId: {}, tableId: {}", clientId, mapId);

        return TableHandlerHelper.dispatchOrReplyNull(sender, mapId, "离开桌子", table -> {
            int result = processLeave(clientId, table);
            if (result == ConstProto.Result.SUCCESS_VALUE) {
                GameProto.AckLeaveTable response = GameProto.AckLeaveTable.newBuilder()
                        .setTableInfo(table.buildTableInfo())
                        .build();
                sender.sendMessage(clientId, GMsg.ACK_LEAVE, mapId, response, sequence);
            } else {
                sender.sendMessage(TCPMessage.newInstance(result));
            }
        });
    }

    /**
     * 处理玩家离桌逻辑。
     * 
     * @param userId 玩家ID
     * @param table  桌子对象
     * @return 结果码
     */
    private int processLeave(int userId, Table table) {
        TableUser user = table.getUsers().get(userId);
        if (user == null)
            return ConstProto.Result.ROLE_NULL_VALUE;

        long tableId = table.getTableId();

        // 游戏中离开: 解散牌局, 广播总结算并销毁桌子
        if (table.gaming()) {
            logger.info("游戏中玩家离开, 解散牌局, userId: {}, tableId: {}", userId, tableId);
            table.removeUser(user);
            table.dismissAndSettle();
            return ConstProto.Result.SUCCESS_VALUE;
        }

        table.removeUser(user);
        logger.info("等待阶段玩家离桌, userId: {}, tableId: {}, remain: {}, human: {}",
                userId, tableId, table.getUsers().size(), table.hasHumanPlayer());

        // 空桌或只剩机器人：立即销毁，并通知大厅
        if (table.isEmpty() || !table.hasHumanPlayer()
                || table.getTableState() == TableState.TABLE_DIS) {
            Game.getInstance().getTableManager().removeTableAsync(tableId);
        } else {
            // 仍有真人：同步大厅名单
            Game.getInstance().getTableManager().notifyRoomPlayerLeft(tableId, userId);
        }
        return ConstProto.Result.SUCCESS_VALUE;
    }
}

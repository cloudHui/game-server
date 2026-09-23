package game.client.handle.role;

import game.Game;
import game.manager.table.Table;
import game.manager.table.TableUser;
import game.manager.table.ddz.DdzSettleService;
import game.manager.table.mj.MjSettleService;
import game.manager.table.mj.MjTable;
import utils.registry.enums.TableState;
import msg.registor.message.GMsg;
import net.message.TCPMessage;
import net.msg.Msg;
import net.msg.MsgContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;
import proto.GameProto;

/**
 * 玩家离开桌子请求处理器
 * <p>
 * 处理玩家主动离桌、等待阶段退房或牌局中逃跑解散。
 */
public class ReqLeaveTableHandle {

    private static final Logger logger = LoggerFactory.getLogger(ReqLeaveTableHandle.class);

    /**
     * 处理离开桌子请求
     *
     * @param ctx 消息上下文
     */
    @Msg(id = GMsg.REQ_LEAVE, desc = "请求离开桌子")
    public void handle(MsgContext<GameProto.ReqLeaveTable> ctx) {
        try {
            int clientId = ctx.getClientId();
            long mapId = ctx.getMapId();

            logger.info("处理离开桌子请求, userId: {}, tableId: {}", clientId, mapId);

            Table table = Game.getInstance().getTableManager().getTable(mapId);
            if (table == null) {
                ctx.getSender().sendMessage(TCPMessage.newInstance(ConstProto.Result.TABLE_NULL_VALUE));
                return;
            }

            table.execute(() -> {
                int result = processLeave(clientId, table);
                if (result == ConstProto.Result.SUCCESS_VALUE) {
                    GameProto.AckLeaveTable response = GameProto.AckLeaveTable.newBuilder()
                            .setTableInfo(table.buildTableInfo())
                            .build();
                    ctx.reply(GMsg.ACK_LEAVE, mapId, response);
                } else {
                    ctx.getSender().sendMessage(TCPMessage.newInstance(result));
                }
            }).exceptionally(error -> {
                logger.error("桌子线程处理离开请求失败, tableId: {}", mapId, error);
                return null;
            });
        } catch (Exception e) {
            logger.error("处理离开桌子请求失败, userId: {}", ctx.getClientId(), e);
        }
    }

    /**
     * 玩家离桌核心逻辑
     *
     * @param userId 玩家 ID
     * @param table  桌子实例
     * @return 返回码
     */
    private int processLeave(int userId, Table table) {
        TableUser user = table.getUsers().get(userId);
        if (user == null) return ConstProto.Result.ROLE_NULL_VALUE;

        long tableId = table.getTableId();

        // 游戏中离开: 解散牌局, 发送总结算
        if (table.gaming()) {
            logger.info("游戏中玩家离开, 解散牌局, userId: {}, tableId: {}", userId, tableId);
            if (table.isMultiRound()) {
                if (table.getGameType() == 1) {
                    MjSettleService.sendGameResult((MjTable) table);
                } else {
                    DdzSettleService.sendGameResult(table);
                }
            }
            table.removeUser(user);
            Game.getInstance().getTableManager().removeTableAsync(tableId);
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

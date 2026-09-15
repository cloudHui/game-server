package game.client.handle.role;

import game.Game;
import game.manager.TableManager;
import game.manager.table.Table;
import game.manager.table.TableUser;
import game.manager.table.state.TableSettleSupport;
import msg.registor.enums.TableState;
import msg.registor.message.GMsg;
import net.client.Sender;
import net.message.TCPMessage;
import net.msg.Msg;
import net.msg.MsgContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;
import proto.GameProto;

/**
 * 玩家操作请求处理器
 * <p>
 * 通用校验后调用 table.processOp() 多态分发到具体麻将/扑克玩法。
 */
public class ReqOpHandle {

    private static final Logger logger = LoggerFactory.getLogger(ReqOpHandle.class);

    /**
     * 处理玩家操作请求
     *
     * @param ctx 消息上下文
     */
    @Msg(id = GMsg.REQ_OP, desc = "玩家牌桌操作")
    public void handle(MsgContext<GameProto.ReqOp> ctx) {
        try {
            GameProto.ReqOp request = ctx.getMsg();
            int clientId = ctx.getClientId();
            long mapId = ctx.getMapId();
            Sender sender = ctx.getSender();
            int sequence = ctx.getSequence();

            logger.info("处理玩家操作请求, userId: {}, tableId: {}", clientId, mapId);

            TableManager tableManager = Game.getInstance().getTableManager();
            Table table = tableManager.getTable(mapId);
            if (table == null) {
                logger.warn("桌子不存在, tableId: {}", mapId);
                sender.sendMessage(TCPMessage.newInstance(ConstProto.Result.TABLE_NULL_VALUE));
                return;
            }

            table.execute(() -> {
                int result = processUserOp(clientId, request, table, sender, mapId, sequence);
                replyOp(ctx, clientId, mapId, sequence, request.getOp(), result);
                logger.info("玩家操作请求处理完成, userId: {}, tableId: {}, result: {}", clientId, mapId, result);
            }).exceptionally(error -> {
                logger.error("桌子线程处理玩家操作失败, tableId: {}", mapId, error);
                return null;
            });
        } catch (Exception e) {
            logger.error("处理操作请求失败, userId: {}", ctx.getMapId(), e);
        }
    }

    /**
     * 无论成败都带回原 sequence：失败若发无序 Result，Gate 等不到回复会超时并推「操作超时」
     *
     * @param ctx      消息上下文
     * @param clientId 玩家 ID
     * @param mapId    桌子 ID
     * @param sequence 序列号
     * @param op       操作详情
     * @param result   处理结果
     */
    private void replyOp(MsgContext<GameProto.ReqOp> ctx, int clientId, long mapId, int sequence,
                         GameProto.OpInfo op, int result) {
        if (result != ConstProto.Result.SUCCESS_VALUE) {
            TCPMessage err = TCPMessage.newInstance(result);
            err.setClientId(clientId);
            err.setMapId(mapId);
            err.setSequence(sequence);
            ctx.getSender().sendMessage(err);
            return;
        }
        // 成功时桌内可能已广播无序 ACK_OP；这里必须再带 sequence 回给请求方完成 Gate/Web 的 sendAndWait
        GameProto.AckOp ack = GameProto.AckOp.newBuilder()
                .setOp(op)
                .setOpId(clientId)
                .setOpFrom(clientId)
                .build();
        ctx.reply(GMsg.ACK_OP, mapId, ack);
    }

    /**
     * 执行具体操作或准备流程
     *
     * @param userId   玩家 ID
     * @param request  操作请求
     * @param table    桌子实例
     * @param sender   发送端
     * @param mapId    桌子 ID
     * @param sequence 序列号
     * @return 操作返回码
     */
    private int processUserOp(int userId, GameProto.ReqOp request, Table table, Sender sender, long mapId, int sequence) {
        try {
            GameProto.OpInfo op = request.getOp();
            TableState ts = table.getTableState();

            // TABLE_OVER状态: 处理准备下一局
            if (ts == TableState.TABLE_OVER) {
                return processPrepare(table, userId, op);
            }

            if (!table.gaming()) {
                return ConstProto.Result.TABLE_NOT_START_VALUE;
            }

            return table.processOp(userId, op, sender, mapId, sequence);
        } catch (Exception e) {
            logger.error("处理玩家操作请求失败, userId: {}", userId, e);
            return ConstProto.Result.SERVER_ERROR_VALUE;
        }
    }

    /**
     * 处理准备下一局
     *
     * @param table  桌子实例
     * @param userId 玩家 ID
     * @param op     操作信息
     * @return 处理结果
     */
    private int processPrepare(Table table, int userId, GameProto.OpInfo op) {
        if (op.getChoice() != ConstProto.Operation.PREPARE) {
            return ConstProto.Result.OP_CURR_ERROR_VALUE;
        }

        table.addReady(userId);
        // 有人点准备后，机器人立即自动准备，避免真人空等 15 秒
        for (TableUser seatUser : table.getSeatUsers().values()) {
            if (seatUser.isRobot()) {
                table.addReady(seatUser.getUserId());
            }
        }
        logger.info("玩家准备下一局, userId: {}, tableId: {}, ready: {}/{}",
                userId, table.getTableId(), table.getReadyCount(), table.getTableModel().getSeatNum());

        GameProto.AckOp ackOp = GameProto.AckOp.newBuilder()
                .setOp(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.PREPARE).build())
                .setOpId(userId).build();
        table.sendTableMessage(ackOp, GMsg.ACK_OP);

        if (table.allReady()) {
            if (table.isLastRound()) {
                TableSettleSupport.sendFinalResultAndRemove(table);
                logger.info("最后一局完成, 总结算已发送, tableId: {}", table.getTableId());
            } else {
                logger.info("所有玩家已准备, 开始下一局, tableId: {}", table.getTableId());
                table.resetForNextRound();
                table.upNextState(TableState.WAITING);
            }
        }
        return ConstProto.Result.SUCCESS_VALUE;
    }
}

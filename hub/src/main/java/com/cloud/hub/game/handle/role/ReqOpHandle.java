package com.cloud.hub.game.handle.role;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloud.hub.game.domain.table.Table;
import com.cloud.hub.game.domain.table.TableUser;
import com.cloud.hub.game.handle.TableHandlerHelper;
import com.google.protobuf.Message;

import msg.registor.message.GMsg;
import net.client.Sender;
import net.handler.Handler;
import net.message.TCPMessage;
import proto.ConstProto;
import proto.GameProto;
import utils.registry.annotation.ProcessType;
import utils.registry.enums.TableState;

/**
 * 玩家出牌/摸牌/准备等桌内操作请求处理器。
 * <p>
 * 统一通过 {@link TableHandlerHelper#dispatchOrReplyNull} 接入：
 * 自动完成“查桌 -> 判空自动回复 TABLE_NULL_VALUE -> 串行切入桌线程 -> 统一异常切面”，
 * 在桌内单线程排他保障下执行出牌、碰杠胡、抢庄下注或准备开局。
 */
@ProcessType(GMsg.REQ_OP)
public class ReqOpHandle implements Handler {

    /** 日志记录器 */
    private static final Logger logger = LoggerFactory.getLogger(ReqOpHandle.class);

    /**
     * 接收客户端操作请求并调度至对应桌串行队列执行。
     *
     * @param sender   消息发送者（客户端连接句柄）
     * @param clientId 玩家唯一 ID（userId）
     * @param message  操作请求消息体 (ReqOp)
     * @param mapId    目标桌号（tableId）
     * @param sequence 消息序列号，用于端对端异步时序确认
     * @return 恒为 true
     */
    @Override
    public boolean handler(Sender sender, int clientId, Message message, long mapId, int sequence) {
        GameProto.ReqOp request = (GameProto.ReqOp) message;
        logger.info("收到玩家操作请求, userId: {}, tableId: {}", clientId, mapId);

        // 统一调度：若桌不存在自动向客户端回送 TABLE_NULL_VALUE，存在则自动切入桌串行队列
        return TableHandlerHelper.dispatchOrReplyNull(sender, mapId, "玩家操作", table -> {
            int result = processUserOp(clientId, request, table, sender, mapId, sequence);
            replyOp(sender, clientId, mapId, sequence, request.getOp(), result);
            logger.info("玩家操作处理完成, userId: {}, tableId: {}, result: {}", clientId, mapId, result);
        });
    }

    /**
     * 向客户端回送操作确认结果。
     * <p>
     * <b>序列号契约：</b>
     * 无论成功还是失败，均必须携带原 sequence 返回给客户端；
     * 若仅发送无序错误码，网关/Web 端 {@code sendAndWait} 等不到匹配的 sequence 会超时报错。
     */
    private void replyOp(Sender sender, int clientId, long mapId, int sequence,
            GameProto.OpInfo op, int result) {
        if (result != ConstProto.Result.SUCCESS_VALUE) {
            TCPMessage err = TCPMessage.newInstance(result);
            err.setClientId(clientId);
            err.setMapId(mapId);
            err.setSequence(sequence);
            sender.sendMessage(err);
            return;
        }
        // 成功时可能已广播业务消息；此处再带原 sequence 回传给请求方完成端对端应答闭环
        GameProto.AckOp ack = GameProto.AckOp.newBuilder()
                .setOp(op)
                .setOpId(clientId)
                .setOpFrom(clientId)
                .build();
        sender.sendMessage(clientId, GMsg.ACK_OP, mapId, ack, sequence);
    }

    private int processUserOp(int userId, GameProto.ReqOp request, Table table, Sender sender, long mapId,
            int sequence) {
        try {
            GameProto.OpInfo op = request.getOp();
            TableState ts = table.getTableState();

            // 1. 等待准备状态 (WAITING) 与 局间结算状态 (TABLE_OVER)：支持玩家点击准备
            if (ts == TableState.WAITING || ts == TableState.TABLE_OVER) {
                if (op.getChoice() == ConstProto.Operation.PREPARE) {
                    return processPrepare(table, userId, op);
                }
                if (!table.gaming()) {
                    return ConstProto.Result.TABLE_NOT_START_VALUE;
                }
            }

            // 2. 校验牌局是否已正式开始
            if (!table.gaming()) {
                return ConstProto.Result.TABLE_NOT_START_VALUE;
            }

            // 3. 多态分发至麻将、斗地主、牛牛等具体玩法领域引擎
            return table.processOp(userId, op, sender, mapId, sequence);
        } catch (Exception e) {
            logger.error("处理玩家操作异常, userId: {}, tableId: {}", userId, mapId, e);
            return ConstProto.Result.SERVER_ERROR_VALUE;
        }
    }

    /**
     * 处理玩家点击准备（区分首局 WAITING 等待开局与 TABLE_OVER 局间续局）：
     * <ul>
     *   <li>首局 WAITING 阶段：仅登记准备并唤醒高频检测由 {@link com.cloud.hub.game.domain.state.Waiting} 开局，
     *       严禁在此调用 {@link Table#resetForNextRound()} 导致首局局号错误递增为第 2 局且清空 readySet；</li>
     *   <li>局间 TABLE_OVER 阶段：全员准备后推进下一局或触发末局总结算。</li>
     * </ul>
     */
    private int processPrepare(Table table, int userId, GameProto.OpInfo op) {
        if (op.getChoice() != ConstProto.Operation.PREPARE) {
            return ConstProto.Result.OP_CURR_ERROR_VALUE;
        }

        table.addReady(userId);
        // 真人点击准备后，同桌机器人立即联动自动准备，避免真人枯等 15 秒倒计时
        for (TableUser seatUser : table.getSeatUsers().values()) {
            if (seatUser.isRobot()) {
                table.addReady(seatUser.getUserId());
            }
        }
        logger.info("玩家准备操作, state: {}, userId: {}, tableId: {}, ready: {}/{}",
                table.getTableState(), userId, table.getTableId(),
                table.getReadyCount(), table.getSeatUsers().size());

        // 广播准备状态变更
        GameProto.AckOp ackOp = GameProto.AckOp.newBuilder()
                .setOp(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.PREPARE).build())
                .setOpId(userId)
                .setOpFrom(userId)
                .build();
        table.sendTableMessage(ackOp, GMsg.ACK_OP);

        if (table.getTableState() == TableState.WAITING) {
            // 缩短循环间隔，使 Waiting 状态机立即感知开局条件并在下一 tick 发牌开局
            if (table.canStartGame()) {
                table.setLoopInterval(Table.LOOP_INTERVAL);
            }
            return ConstProto.Result.SUCCESS_VALUE;
        }

        // TABLE_OVER 阶段：全部在座有效玩家都已准备，立即驱动下一局轮转或末局总结算
        if (table.allReady()) {
            if (table.isLastRound()) {
                table.dismissAndSettle();
                logger.info("最后一局完成, 总结算已触发, tableId: {}", table.getTableId());
            } else {
                logger.info("全员已准备, 启动下一局状态机, tableId: {}", table.getTableId());
                table.resetForNextRound();
                table.upNextState(TableState.WAITING);
                if (table.canStartGame()) {
                    table.setLoopInterval(Table.LOOP_INTERVAL);
                }
            }
        }
        return ConstProto.Result.SUCCESS_VALUE;
    }
}

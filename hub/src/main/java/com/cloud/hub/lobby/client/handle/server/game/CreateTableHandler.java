package com.cloud.hub.lobby.client.handle.server.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloud.hub.lobby.admin.AdminRobotMatchPending;
import com.cloud.hub.lobby.client.handle.role.PendingCreateJoin;
import com.cloud.hub.lobby.client.handle.role.ReqJoinTableHandle;
import com.cloud.hub.lobby.manager.User;
import com.cloud.hub.lobby.manager.UserManager;
import com.cloud.hub.lobby.manager.table.TableInfo;
import com.cloud.hub.lobby.manager.table.TableManager;
import com.google.protobuf.Message;

import utils.registry.annotation.ProcessClass;
import net.client.Sender;
import net.message.TCPMessage;
import proto.ConstProto;
import proto.ServerProto;
import tools.manager.ConnectHandle;

/**
 * 游戏服通知大厅「桌子创建成功」的响应处理器。
 *
 * <p><b>业务流转背景：</b>
 * 1. 玩家在大厅请求加入房间，若无合适空桌，大厅会向 Game 发送创桌请求，并将发起连接与 sequence
 *    记录在 {@link PendingCreateJoin} 中；
 * 2. Game 创桌完毕后回复 {@link ServerProto.AckCreateGameTable}，由本处理器接收；
 * 3. 本处理器在大厅内存中注册 {@link TableInfo} 并将创桌玩家加入该桌；
 * 4. 最终沿用原始 Gate 物理连接与序号回传 {@link proto.LobbyProto.AckJoinRoomTable}，形成闭环。
 *
 * <p><b>防超时挂死保障：</b>
 * 若遇桌子注册失败或玩家已离线，会立即向原连接返回对应的失败错误码与序号，
 * 避免网关与前端处于等待状态直到 3~5 秒超时。
 */
@ProcessClass(ServerProto.AckCreateGameTable.class)
public class CreateTableHandler implements ConnectHandle {

    /**
     * 日志记录器
     */
    private static final Logger logger = LoggerFactory.getLogger(CreateTableHandler.class);

    @Override
    public void handle(Message message, Sender handler, int sequence, int transId) {
        ServerProto.AckCreateGameTable ackMessage = (ServerProto.AckCreateGameTable) message;
        dealCreateSuccessTableJoin(sequence, ackMessage, transId);
        logger.info("创建桌子成功处理完成, userId: {}", transId);
    }

    /**
     * 处理创建桌子成功后续的大厅数据挂接与前端应答。
     *
     * @param sequence 协议消息序列号
     * @param ack      游戏服务返回的创桌成功报文（包含桌号与配置信息）
     * @param userId   创桌发起人 ID（对应 transId，机器人验收时可能为负数 requestId）
     */
    private void dealCreateSuccessTableJoin(int sequence, ServerProto.AckCreateGameTable ack, int userId) {
        // 一次性提取发起端保存的上下文，保证只消费一次且无论成败都能得到正确结算
        PendingCreateJoin pending = PendingCreateJoin.take(userId);
        Sender replyTo = pending != null ? pending.replyTo : null;
        int replySeq = pending != null ? pending.sequence : sequence;

        TableInfo tableInfo = TableManager.getInstance().putRoomInfo(ack.getTables());
        if (tableInfo == null) {
            logger.warn("创建桌子信息在大厅注册失败, userId: {}", userId);
            replyFail(replyTo, userId, replySeq, ConstProto.Result.TABLE_ERROR_VALUE);
            return;
        }

        // 管理员测试机器人快速对局验收链路
        if (userId < 0 && AdminRobotMatchPending.complete(userId, ack.getTables())) {
            logger.info("管理员机器人验收桌创建完成, requestId: {}, tableId: {}",
                    userId, ack.getTables().getTableId());
            return;
        }

        User user = UserManager.getInstance().getUser(userId);
        if (user == null) {
            logger.warn("创建桌子时用户不存在或已离线, userId: {}", userId);
            replyFail(replyTo, userId, replySeq, ConstProto.Result.SERVER_ERROR_VALUE);
            return;
        }

        // 将创桌玩家加入该大厅桌子视图
        tableInfo.joinRole(user);

        // 发送加入成功 ACK（优先沿用发起时记录的 replyTo 连接与 sequence）
        ReqJoinTableHandle.sendJoinTableAck(ack.getTables().getTableId(), replySeq, user, replyTo);
    }

    /**
     * 向请求方发送失败应答，避免网关与前端 CompletableFuture 悬挂超时。
     *
     * @param replyTo    发起端物理连接
     * @param userId     用户 ID
     * @param sequence   消息序号
     * @param resultCode 错误码常量
     */
    private void replyFail(Sender replyTo, int userId, int sequence, int resultCode) {
        if (replyTo != null) {
            TCPMessage errMsg = TCPMessage.newInstance(resultCode);
            errMsg.setClientId(userId);
            errMsg.setSequence(sequence);
            replyTo.sendMessage(errMsg);
        }
    }
}


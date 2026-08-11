package gate.client;

import com.google.protobuf.ByteString;
import gate.Gate;
import gate.client.handle.back.BackHandleManager;
import io.netty.channel.ChannelHandler;
import msg.registor.HandleTypeRegister;
import msg.registor.enums.ServerType;
import msg.registor.message.CMsg;
import net.client.handler.ClientHandler;
import net.client.handler.WsClientHandler;
import net.client.Sender;
import net.connect.handle.ConnectHandler;
import net.handler.Handler;
import net.handler.Handlers;
import net.message.Parser;
import net.message.TCPMessage;
import net.message.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;
import proto.ServerProto;
import tools.ServerManager;
import utils.trace.TracedHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端协议处理器
 * 负责消息解析、转发和客户端管理
 */
public class ClientProto {
    private static final Logger logger = LoggerFactory.getLogger(ClientProto.class);

    // 消息转发接口
    public static final Transfer TRANSFER = ClientProto::transferMessage;
    public static final Parser PARSER = HandleTypeRegister::parseMessage;
    private static final Map<Integer, Handler> HANDLER_MAP = new HashMap<>();
    public static final Handlers HANDLERS = HANDLER_MAP::get;

    // 服务器请求超时时间（秒）
    private static final int SERVER_REQUEST_TIMEOUT = 3;

    /**
     * 初始化协议处理器
     */
    public static void init() {
        try {
            // 绑定专用服务器消息处理
            HandleTypeRegister.initFactory(ClientProto.class, HANDLER_MAP);
            // 绑定通用服务器消息处理
            HandleTypeRegister.initFactory(HANDLER_MAP);
            TracedHandler.wrapAll(HANDLER_MAP);
            logger.info("ClientProto初始化完成,注册处理器数量: {}", HANDLER_MAP.size());
        } catch (Exception e) {
            logger.error("ClientProto初始化失败", e);
            throw new RuntimeException("ClientProto初始化失败", e);
        }
    }

    /**
     * 消息转发到后端服务器
     *
     * @param channelHandler 客户端连接
     * @param tcpMessage     TCP消息
     * @return 是否成功处理
     */
    private static boolean transferMessage(ChannelHandler channelHandler, TCPMessage tcpMessage) {
        if (!(channelHandler instanceof GateTcpClient)) {
            logger.warn("channelHandler不是GateTcpClient类型, 实际类型: {}", channelHandler.getClass().getName());
            return false;
        }

        if (handleLocalMessage(channelHandler, tcpMessage)) return true;

        GateTcpClient client = (GateTcpClient) channelHandler;
        int clientId = client.getId();
        int roleId = client.getRoleId();
        long mapId = client.getMapId();
        int msgId = tcpMessage.getMessageId();
        int sequence = tcpMessage.getSequence();

        tcpMessage.setClientId(roleId == 0 ? clientId : roleId);
        tcpMessage.setMapId(mapId == -1 ? roleId : mapId);
        ConnectHandler serverConnection = getTargetServerConnection(msgId, clientId, roleId, mapId);
        if (serverConnection == null) {
            logger.warn("消息无法路由, msgId: {}, clientId: {}, roleId: {}, mapId: {}, sequence: {}",
                    Integer.toHexString(msgId), clientId, roleId, mapId, sequence);
            return true;
        }

        return sendMessageToServer(serverConnection, tcpMessage, sequence, client);
    }

    private static boolean handleLocalMessage(ChannelHandler channelHandler, TCPMessage tcpMessage) {
        if (tcpMessage.getMessageId() >= CMsg.BASE_ID_INDEX) return false;
        Handler handler = HANDLER_MAP.get(tcpMessage.getMessageId());
        if (handler == null) return false;
        try {
            handler.handler((Sender) channelHandler, tcpMessage.getClientId(),
                    PARSER.parser(tcpMessage.getMessageId(), tcpMessage.getMessage()),
                    tcpMessage.getMapId(), tcpMessage.getSequence());
        } catch (Exception e) {
            logger.warn("处理本地消息失败, msgId: {}", Integer.toHexString(tcpMessage.getMessageId()), e);
        }
        return true;
    }

    /**
     * 发送消息到后端服务器
     */
    private static boolean sendMessageToServer(ConnectHandler serverConnection,
                                               TCPMessage tcpMessage, int sequence,
                                               GateTcpClient client) {
        long startTime = System.currentTimeMillis();
        int msgId = tcpMessage.getMessageId();

        serverConnection.sendTcpMessage(tcpMessage, SERVER_REQUEST_TIMEOUT)
                .whenComplete((response, error) -> {
                    if (error != null) {
                        handleSendError(error, msgId, sequence, startTime, serverConnection, client);
                    } else {
                        handleServerResponse(response, sequence, startTime, client, msgId);
                    }
                });

        return true;
    }

    /**
     * 处理发送错误（超时等）：保留 ERROR，带齐跨服关联字段。
     */
    private static void handleSendError(Throwable error, int msgId, int sequence, long startTime,
                                        ConnectHandler server, GateTcpClient client) {
        long costMs = System.currentTimeMillis() - startTime;
        logger.error("发送消息到服务器失败, msgId: {}, server: {}, clientId: {}, roleId: {}, mapId: {}, sequence: {}, costMs: {}, error: {}",
                Integer.toHexString(msgId), server.getConnectServer(),
                client.getId(), client.getRoleId(), client.getMapId(), sequence, costMs, error.getMessage());
        client.sendMessage(TCPMessage.newInstance(ConstProto.Result.TIME_OUT_VALUE));
    }

    /**
     * 处理服务器响应
     */
    private static void handleServerResponse(TCPMessage response, int sequence, long startTime, GateTcpClient client, int msgId) {
        try {
            response.setSequence(sequence);
            forwardResponseToClient(response, startTime, client);
        } catch (Exception e) {
            logger.error("处理服务器响应失败, msgId: {}, clientId: {}, error: {}", Integer.toHexString(msgId), client.getId(), e.getMessage(), e);
        }
    }

    /**
     * 转发响应到客户端
     */
    private static void forwardResponseToClient(TCPMessage response, long startTime, GateTcpClient client) {
        int msgId = response.getMessageId();

        BackHandleManager.handle(response, client);
        client.sendMessage(response);

        long costTime = System.currentTimeMillis() - startTime;
        logger.info("消息转发成功, msgId: {}, userId: {}, 耗时: {}ms", Integer.toHexString(msgId), client.getRoleId(), costTime);
    }


    /**
     * 获取目标服务器连接；无法路由时打 ERROR 并带上客户端关联字段。
     */
    private static ConnectHandler getTargetServerConnection(int msgId, int clientId, int roleId, long mapId) {
        ServerType serverType = getServerTypeByMessageId(msgId);
        if (serverType == null) return null;

        ConnectHandler connection = Gate.getInstance().getServerManager().getServerClient(serverType);
        if (connection == null) {
            logger.warn("服务器连接不可用, serverType: {}, msgId: {}, clientId: {}, roleId: {}, mapId: {}",
                    serverType, Integer.toHexString(msgId), clientId, roleId, mapId);
        }

        return connection;
    }

    /**
     * 根据消息ID获取服务器类型
     */
    private static ServerType getServerTypeByMessageId(int msgId) {
        if ((msgId & CMsg.GAME_TYPE) != 0) {
            return ServerType.Game;
        } else if ((msgId & CMsg.LOBBY_TYPE) != 0) {
            return ServerType.Lobby;
        }

        logger.debug("未知消息类型, msgId: {}", Integer.toHexString(msgId));
        return null;
    }

    /**
     * 通知服务器玩家断开连接
     */
    protected static void notifyServerDisconnect(int userId, ChannelHandler handler) {
        logger.info("通知服务器玩家断开连接, userId: {}", userId);

        ServerProto.NotBreak.Builder disconnectNotify = ServerProto.NotBreak.newBuilder();
        disconnectNotify.setUserId(userId);
        if (handler instanceof GateTcpClient) {
            disconnectNotify.setGateClientId(((GateTcpClient) handler).getId());
        } else if (handler instanceof GateWsClient) {
            disconnectNotify.setGateClientId(((GateWsClient) handler).getId());
        }

        ServerManager serverManager = Gate.getInstance().getServerManager();
        if (serverManager == null) {
            logger.error("服务器管理器未初始化");
            return;
        }

        // 通知所有相关服务器
        notifyAllServersDisconnect(disconnectNotify.build(), handler);
    }

    /**
     * 通知所有服务器玩家断开
     */
    private static void notifyAllServersDisconnect(ServerProto.NotBreak disconnectNotify, ChannelHandler handler) {
        sendDisconnectNotify(ServerType.Game, disconnectNotify);
        sendDisconnectNotify(ServerType.Lobby, disconnectNotify);

        // 通知中心服务器
        notifyCenterServerDisconnect(disconnectNotify, handler);
    }

    /**
     * 发送断开连接通知到指定服务器
     */
    private static void sendDisconnectNotify(ServerType serverType, ServerProto.NotBreak disconnectNotify) {
        ConnectHandler serverConnection = Gate.getInstance().getServerManager().getServerClient(serverType);
        if (serverConnection != null) {
            serverConnection.sendMessage(CMsg.NOT_BREAK, disconnectNotify);
            logger.debug("已通知服务器玩家断开, serverType: {}", serverType);
        } else {
            logger.debug("服务器连接不存在, serverType: {}", serverType);
        }
    }

    /**
     * 通知中心服务器断开连接
     */
    private static void notifyCenterServerDisconnect(ServerProto.NotBreak disconnectNotify, ChannelHandler handler) {
        ConnectHandler centerConnection = Gate.getInstance().getServerManager().getServerClient(ServerType.Center);
        if (centerConnection != null) {
            ServerProto.NotBreak.Builder notifyBuilder = disconnectNotify.toBuilder();
            setDisconnectCertificate(notifyBuilder, handler);
            centerConnection.sendMessage(CMsg.NOT_BREAK, notifyBuilder.build());
            logger.debug("已通知中心服务器玩家断开");
        }
    }

    /**
     * 设置断开连接的证书信息
     */
    private static void setDisconnectCertificate(ServerProto.NotBreak.Builder notifyBuilder, ChannelHandler handler) {
        String hostAddress = getHandlerHostAddress(handler);
        if (hostAddress != null) {
            notifyBuilder.setCert(ByteString.copyFromUtf8(hostAddress));
        }
    }

    /**
     * 获取处理器的远程主机地址
     */
    private static String getHandlerHostAddress(ChannelHandler handler) {
        if (handler instanceof ClientHandler) {
            return ClientHandler.getRemoteIP((ClientHandler) handler).getHostName();
        } else if (handler instanceof WsClientHandler) {
            return WsClientHandler.getRemoteIP((WsClientHandler) handler).getHostName();
        }
        return null;
    }
}

package net.client.handler;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import net.channel.ChannelAttr;
import net.client.Sender;
import net.client.event.EventHandle;
import net.connect.handle.CompleterGroup;
import net.connect.handle.CompleterTcpMsg;
import net.handler.Handler;
import net.handler.Handlers;
import net.message.Parser;
import net.message.TCPMaker;
import net.message.TCPMessage;
import net.message.Transfer;
import net.msg.MsgRouter;
import net.safe.Safe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

/**
 * WebSocket 客户端连接与消息分发处理器
 * <p>负责 WebSocket 长连接会话生命周期管理、协议解包校验与 MsgRouter 统一分发。</p>
 *
 * @author cloud
 */
public class WsClientHandler extends SimpleChannelInboundHandler<WebSocketFrame> implements Sender {

    private static final Logger logger = LoggerFactory.getLogger(WsClientHandler.class);
    private static final SenderManager CLIENT_MANAGER = SenderManager.INSTANCE;
    private static final byte[] DEFAULT_DATA = new byte[0];

    private final int id;
    private final Parser parser;
    private final Handlers handlers;
    private final Transfer transfer;
    private final TCPMaker maker;

    private Channel channel;
    private Safe safe;
    private EventHandle activeHandle;
    private EventHandle closeEvent;
    private CompleterGroup completerGroup;

    public static WsClientHandler getClient(long id) {
        return (WsClientHandler) CLIENT_MANAGER.getClient((int) id);
    }

    public static InetSocketAddress getRemoteIP(WsClientHandler clientHandler) {
        if (clientHandler.channel == null) return null;
        return (InetSocketAddress) clientHandler.channel.remoteAddress();
    }

    public WsClientHandler(Parser parser, Handlers handlers) {
        this(parser, handlers, (t, msg) -> Transfer.DEFAULT(), TCPMaker.INSTANCE);
    }

    public WsClientHandler(Parser parser, Handlers handlers, Transfer transfer) {
        this(parser, handlers, transfer, TCPMaker.INSTANCE);
    }

    public WsClientHandler(Parser parser, Handlers handlers, Transfer transfer, TCPMaker maker) {
        this.safe = (msgId) -> Safe.DEFAULT();
        this.id = CLIENT_MANAGER.getId();
        this.parser = parser;
        this.handlers = handlers;
        this.transfer = transfer;
        this.maker = maker;
    }

    public int getId() {
        return this.id;
    }

    public void setActiveEvent(EventHandle eventHandle) {
        this.activeHandle = eventHandle;
    }

    public void setCloseEvent(EventHandle closeEvent) {
        this.closeEvent = closeEvent;
    }

    public void setSafe(Safe safe) {
        this.safe = safe;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.channel = ctx.channel();
        this.completerGroup = new CompleterGroup(channel.eventLoop());
        ChannelAttr.setId(this.channel, this.getId());
        CLIENT_MANAGER.addClient(this);

        if (this.activeHandle != null) {
            try {
                this.activeHandle.handle(this);
            } catch (Exception e) {
                logger.error("[{}] 注册事件失败", ctx.channel(), e);
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.info("[{}] 连接关闭", ctx.channel());
        if (completerGroup != null) {
            completerGroup.destroy();
            completerGroup = null;
        }
        if (this.closeEvent != null) {
            try {
                this.closeEvent.handle(this);
            } catch (Exception e) {
                logger.error("[{}] 关闭事件处理失败", ctx.channel(), e);
            }
        }
        closeChannel();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (channel != null && channel.isActive()) {
            closeChannel();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) {
        if (event instanceof IdleStateEvent) {
            IdleStateEvent idle = (IdleStateEvent) event;
            if (IdleState.READER_IDLE == idle.state()) {
                ctx.channel().close();
            }
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object object) {
        if (object instanceof TCPMessage) {
            processTCPMessage((TCPMessage) object);
        } else {
            ctx.fireChannelRead(object);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg) {
        // WebSocket 帧由前置编解码器转换为 TCPMessage 后流转至 channelRead
    }

    private void processTCPMessage(TCPMessage tcpMsg) {
        try {
            if (!validateMessageSafety(tcpMsg)) return;
            if (this.transfer.isTransfer(this, tcpMsg)) return;

            if (handlers instanceof MsgRouter) {
                boolean shouldKeep = ((MsgRouter) handlers).dispatch(this, tcpMsg);
                if (!shouldKeep) {
                    channel.close();
                }
                return;
            }

            Message message = parseMessageContent(tcpMsg);
            boolean shouldKeepOpen = executeMessageHandler(tcpMsg, message);
            if (!shouldKeepOpen) {
                channel.close();
            }
        } catch (Exception e) {
            logger.error("[{}] 处理消息({})失败", channel, Integer.toHexString(tcpMsg.getMessageId()), e);
        }
    }

    private boolean validateMessageSafety(TCPMessage tcpMsg) {
        if (!safe.isValid(tcpMsg.getMessageId())) {
            logger.error("[{}] 错误! {} 不是安全的消息ID", channel, Integer.toHexString(tcpMsg.getMessageId()));
            channel.close();
            return false;
        }
        return true;
    }

    private Message parseMessageContent(TCPMessage tcpMsg) throws InvalidProtocolBufferException {
        if (tcpMsg.getMessage() != null && tcpMsg.getMessage().length > 0) {
            return parser.parser(tcpMsg.getMessageId(), tcpMsg.getMessage());
        }
        return parser.parser(tcpMsg.getMessageId(), DEFAULT_DATA);
    }

    private boolean executeMessageHandler(TCPMessage tcpMsg, Message message) {
        Handler handler = handlers.getHandler(tcpMsg.getMessageId());
        if (handler == null) {
            if (tcpMsg.getMessageId() == 0) {
                logger.debug("[{}] 忽略空消息(0x0)", channel);
                return true;
            }
            logger.error("[{}] 找不到消息({})的处理器", channel, Integer.toHexString(tcpMsg.getMessageId()));
            return false;
        }
        long startTime = System.currentTimeMillis();
        boolean shouldKeepOpen = handler.handler(this, tcpMsg.getClientId(), message, tcpMsg.getMapId(), tcpMsg.getSequence());
        logHandlerPerformance(handler, startTime);
        return shouldKeepOpen;
    }

    private void logHandlerPerformance(Handler handler, long startTime) {
        long costTime = System.currentTimeMillis() - startTime;
        String handlerName = handler.getClass().getSimpleName();
        if (costTime > 1000L) {
            logger.error("客户端处理器:{} 耗时过长:{}ms", handlerName, costTime);
        } else {
            logger.debug("客户端处理器:{} 耗时:{}ms", handlerName, costTime);
        }
    }

    @Override
    public void sendMessage(int msgId, Message msg, int sequence) {
        channel.writeAndFlush(maker.wrap(msgId, msg, sequence));
    }

    @Override
    public void sendMessage(TCPMessage msg) {
        channel.writeAndFlush(msg);
    }

    @Override
    public void sendMessage(int clientId, int msgId, long mapId, Message msg, int sequence) {
        channel.writeAndFlush(maker.wrap(clientId, msgId, mapId, msg, sequence));
    }

    @Override
    public CompletableFuture<TCPMessage> sendMessageBackTcp(Message msg, int msgId, int timeout) {
        int sequence = completerGroup.getSequence();
        CompleterTcpMsg completer = new CompleterTcpMsg(timeout);
        completerGroup.addCompleterTcpMsg(sequence, completer);
        sendMessage(msgId, msg, sequence);
        return completer;
    }

    @Override
    public CompletableFuture<TCPMessage> sendTcpMessage(TCPMessage msg, int timeout) {
        int sequence = completerGroup.getSequence();
        msg.setSequence(sequence);
        CompleterTcpMsg completer = new CompleterTcpMsg(timeout);
        completerGroup.addCompleterTcpMsg(sequence, completer);
        sendMessage(msg);
        return completer;
    }

    public void closeChannel() {
        CLIENT_MANAGER.removeClient(this);
        if (this.channel != null) {
            try {
                this.channel.close();
            } catch (Exception e) {
                logger.error("[{}] 强制关闭通道失败", this.channel, e);
            }
        }
    }
}

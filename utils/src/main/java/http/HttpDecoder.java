package http;

import http.handler.Handler;
import http.handler.Maker;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * HTTP 与 WebSocket 混合请求解码与分发基类
 * <p>继承 Netty 入站处理器，提供统一路由解析、耗时监控与响应消息回写支持。</p>
 *
 * @author cloud
 */
public abstract class HttpDecoder extends ChannelInboundHandlerAdapter implements Linker {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpDecoder.class);
    public static final String WEB_SOCKET = "websocket";

    private Channel channel;
    private String ip;
    private long lastMsgStamp;
    private Maker maker;

    public HttpDecoder() {
        this(null);
    }

    public HttpDecoder(Maker maker) {
        this.maker = maker;
    }

    public HttpDecoder setMaker(Maker maker) {
        this.maker = maker;
        return this;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        channel = ctx.channel();
        if (channel.remoteAddress() instanceof InetSocketAddress) {
            ip = ((InetSocketAddress) channel.remoteAddress()).getAddress().getHostAddress();
        }
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            long now = System.currentTimeMillis();
            if (lastMsgStamp != 0 && now - lastMsgStamp < 1000L) {
                return;
            }
            lastMsgStamp = now;
            if (msg instanceof FullHttpRequest) {
                dealFullHttpMsg((FullHttpRequest) msg);
            } else if (msg instanceof WebSocketFrame) {
                dealWebsocketMsg((WebSocketFrame) msg);
            } else {
                ctx.fireChannelRead(msg);
            }
        } catch (Throwable t) {
            LOGGER.error("[{}] 处理网络消息失败", ctx.channel(), t);
        }
    }

    /**
     * 解析并处理 HTTP 请求
     */
    private void dealFullHttpMsg(FullHttpRequest request) {
        try {
            String path = request.uri();
            if (path != null && path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path == null) {
                channel.close();
                LOGGER.info("[{}] 不支持空路径请求: {}", channel, request.content().toString(CharsetUtil.UTF_8));
                return;
            }
            String[] data = path.split("\\?");
            if (data.length <= 0) {
                channel.close();
                LOGGER.info("[{}] 无效请求路径: {}", channel, path);
                return;
            }
            if (HttpMethod.POST.equals(request.method())) {
                httpPost(data, request, path);
            } else if (HttpMethod.GET.equals(request.method())) {
                httpGet(data, path);
            } else {
                channel.close();
                LOGGER.info("[{}] 不支持的 HTTP 方法: {} path: {}", channel, request.method().name(), path);
            }
        } catch (Throwable e) {
            LOGGER.error("[{}] HTTP 分发异常", channel, e);
            channel.close();
        }
    }

    private void httpGet(String[] data, String path) {
        Handler handler = getHandler(data[0]);
        if (handler == null) {
            LOGGER.info("[{}] 未找到 GET 处理器: {} path: {}", channel, data[0], path);
            channel.close();
            return;
        }
        Object parsedParam = handler.parser(data.length > 1 ? data[1] : null);
        executeHttpHandler(handler, parsedParam, "httpGet");
    }

    private void httpPost(String[] data, FullHttpRequest request, String path) {
        Handler handler = getHandler(data[0]);
        if (handler == null) {
            LOGGER.info("[{}] 未找到 POST 处理器: {} path: {}", channel, data[0], path);
            channel.close();
            return;
        }
        Object parsedParam = handler.parser(getBody(request));
        executeHttpHandler(handler, parsedParam, "httpPost");
    }

    /**
     * 统一执行 HTTP 业务处理器并记录耗时
     */
    @SuppressWarnings("unchecked")
    private void executeHttpHandler(Handler handler, Object parsedParam, String methodDesc) {
        long start = System.currentTimeMillis();
        boolean keepChannel = handler.handler(this, parsedParam);
        long elapsed = System.currentTimeMillis() - start;

        if (elapsed > 1000L) {
            LOGGER.error("{} 处理器 [{}] 耗时过长: {}ms", methodDesc, handler.getClass().getSimpleName(), elapsed);
        } else {
            LOGGER.debug("{} 处理器 [{}] 耗时: {}ms", methodDesc, handler.getClass().getSimpleName(), elapsed);
        }
        if (!keepChannel) {
            LOGGER.info("[{}] 处理器返回关闭连接", channel);
            channel.close();
        }
    }

    /**
     * 处理 WebSocket 帧数据
     */
    @SuppressWarnings("unchecked")
    private void dealWebsocketMsg(WebSocketFrame frame) {
        try {
            ByteBuf buf = frame.content();
            if (buf.readableBytes() > 0) {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                Handler handler = getHandler(WEB_SOCKET);
                if (handler != null) {
                    if (!handler.handler(this, handler.parser(new String(bytes, CharsetUtil.UTF_8)))) {
                        LOGGER.info("[{}] WebSocket 处理器返回关闭连接", channel);
                        channel.close();
                    }
                } else {
                    LOGGER.info("[{}] 未注册 WebSocket 处理器", channel);
                }
            }
        } catch (Throwable e) {
            LOGGER.error("[{}] WebSocket 处理异常", channel, e);
            channel.close();
        } finally {
            ReferenceCountUtil.release(frame);
        }
    }

    private String getBody(FullHttpRequest request) {
        ByteBuf content = request.content();
        return content.isReadable() ? content.toString(CharsetUtil.UTF_8) : "";
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.error("[{}] 连接断开", ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable t) throws Exception {
        if (channel != null && channel.isActive()) {
            channel.close();
        }
    }

    @Override
    public String remoteIp() {
        return ip;
    }

    @Override
    public <T> void sendMessage(int msgId, T msg) {
        try {
            channel.writeAndFlush(maker.wrap(msgId, msg));
        } catch (Exception e) {
            LOGGER.error("[{}] 发送带ID消息失败(id:{} msg:{})", channel, msgId, msg, e);
        }
    }

    @Override
    public <T> void sendMessage(T msg) {
        try {
            channel.writeAndFlush(maker.wrap(msg));
        } catch (Exception e) {
            LOGGER.error("[{}] 发送泛型消息失败({})", channel, msg, e);
        }
    }

    @Override
    public void sendMessage(String msg) {
        try {
            channel.writeAndFlush(maker.wrap(msg));
        } catch (Exception e) {
            LOGGER.error("[{}] 发送文本消息失败({})", channel, msg, e);
        }
    }

    /**
     * 根据请求路径获取对应的业务处理器
     */
    public abstract Handler getHandler(String path);
}

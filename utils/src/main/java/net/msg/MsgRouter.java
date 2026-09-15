package net.msg;

import com.google.protobuf.Internal;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import net.client.Sender;
import net.handler.Handler;
import net.handler.Handlers;
import net.message.TCPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.other.ClazzUtil;
import utils.trace.TraceContext;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一消息路由引擎
 * <p>
 * 整合注解扫描、Proto解析器推导与预缓存、高性能单次寻址、链路追踪与自动回包。
 * 同时实现 Handlers 与 Parser 接口，天然兼容 Netty 传输层。
 */
public class MsgRouter implements Handlers, net.message.Parser {
    private static final Logger logger = LoggerFactory.getLogger(MsgRouter.class);
    private static final byte[] EMPTY_BYTES = new byte[0];

    private static final MsgRouter INSTANCE = new MsgRouter();

    public static MsgRouter getInstance() {
        return INSTANCE;
    }

    /**
     * 路由项
     */
    public static class RouteEntry {
        public final int msgId;
        public final int ackMsgId;
        public final String desc;
        public final Class<? extends Message> protoClass;
        public final Parser<? extends Message> parser;
        public final RouteInvoker invoker;

        public RouteEntry(int msgId, int ackMsgId, String desc, Class<? extends Message> protoClass,
                          Parser<? extends Message> parser, RouteInvoker invoker) {
            this.msgId = msgId;
            this.ackMsgId = ackMsgId;
            this.desc = desc;
            this.protoClass = protoClass;
            this.parser = parser;
            this.invoker = invoker;
        }
    }

    /**
     * 路由调用器函数接口
     */
    @FunctionalInterface
    public interface RouteInvoker {
        boolean invoke(Sender sender, int clientId, Message msg, long mapId, int sequence, TCPMessage tcpMsg) throws Exception;
    }

    // 主路由表（读多写少，启动期注册，原生 int 无装箱）
    private final IntObjectMap<RouteEntry> routes = new IntObjectHashMap<>();
    // Proto 类型到消息 ID 反查映射表
    private final ConcurrentHashMap<Class<?>, Integer> protoToMsgIdMap = new ConcurrentHashMap<>();

    // ==================== 注册 API ====================

    /**
     * 扫描指定包下的所有 Controller / Handler 并注册
     *
     * @param packageNames 扫描包路径
     */
    public synchronized void scan(String... packageNames) {
        if (packageNames == null) return;
        long start = System.currentTimeMillis();
        int beforeSize = routes.size();

        for (String pkg : packageNames) {
            try {
                List<Class<?>> classes = ClazzUtil.getClasses(pkg);
                for (Class<?> clazz : classes) {
                    if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                        continue;
                    }
                    // 检查类上是否有 @Msg，或者类中是否有方法带 @Msg
                    if (hasMsgAnnotation(clazz)) {
                        Object instance = clazz.getConstructor().newInstance();
                        register(instance);
                    }
                }
            } catch (Exception e) {
                logger.error("MsgRouter 扫描包 [{}] 失败", pkg, e);
            }
        }
        logger.info("MsgRouter 扫描完成, 新增路由: {} 条, 当前总路由: {} 条, 耗时: {}ms",
                (routes.size() - beforeSize), routes.size(), (System.currentTimeMillis() - start));
    }

    private boolean hasMsgAnnotation(Class<?> clazz) {
        if (clazz.isAnnotationPresent(Msg.class)) {
            return true;
        }
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Msg.class)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 注册控制器或处理器实例
     */
    public synchronized MsgRouter register(Object controller) {
        if (controller == null) return this;
        Class<?> clazz = controller.getClass();

        // 1. 处理类级别 @Msg
        if (controller instanceof Handler && clazz.isAnnotationPresent(Msg.class)) {
            Msg classMsg = clazz.getAnnotation(Msg.class);
            int msgId = getMsgId(classMsg);
            registerHandler(msgId, classMsg.ack(), classMsg.desc(), (Handler) controller);
        }

        // 2. 处理方法级别 @Msg
        for (Method method : clazz.getDeclaredMethods()) {
            Msg msgAnno = method.getAnnotation(Msg.class);
            if (msgAnno == null) {
                continue;
            }
            method.setAccessible(true);

            // 处理批量透传 forward
            int[] forwardIds = msgAnno.forward();
            if (forwardIds != null && forwardIds.length > 0) {
                for (int fId : forwardIds) {
                    bindMethodRoute(fId, 0, msgAnno.desc(), controller, method);
                }
                continue;
            }

            int msgId = getMsgId(msgAnno);
            if (msgId == 0) {
                logger.warn("类 {} 方法 {} 的 @Msg 未指定有效的消息 ID", clazz.getSimpleName(), method.getName());
                continue;
            }
            bindMethodRoute(msgId, msgAnno.ack(), msgAnno.desc(), controller, method);
        }
        return this;
    }

    /**
     * 纯协议类型注册（无本地处理器，用于服务端推送或外部通知的解析）
     */
    public synchronized MsgRouter register(int msgId, Class<? extends Message> protoClass, String desc) {
        Parser<? extends Message> parser = protoClass != null ? findParser(protoClass) : null;
        RouteEntry entry = new RouteEntry(msgId, 0, desc, protoClass, parser, null);
        routes.put(msgId, entry);
        if (protoClass != null) {
            protoToMsgIdMap.put(protoClass, msgId);
        }
        return this;
    }

    /**
     * 注册传统 Handler 实例
     */
    public synchronized void registerHandler(int msgId, int ackMsgId, String desc, Handler handler) {
        RouteEntry entry = new RouteEntry(msgId, ackMsgId, desc, null, null,
                (sender, clientId, msg, mapId, sequence, tcpMsg) -> handler.handler(sender, clientId, msg, mapId, sequence));
        routes.put(msgId, entry);
    }

    @SuppressWarnings("unchecked")
    private void bindMethodRoute(int msgId, int ackMsgId, String desc, Object target, Method method) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Class<? extends Message> protoClass = null;
        Parser<? extends Message> parser = null;

        // 模式 A: 方法入参为 MsgContext<T> 或 (MsgContext<T>, Message)
        if (paramTypes.length >= 1 && MsgContext.class.isAssignableFrom(paramTypes[0])) {
            final boolean twoParams = paramTypes.length == 2 && Message.class.isAssignableFrom(paramTypes[1]);
            if (twoParams) {
                protoClass = (Class<? extends Message>) paramTypes[1];
                parser = findParser(protoClass);
            } else if (paramTypes.length == 1) {
                Type genericType = method.getGenericParameterTypes()[0];
                if (genericType instanceof ParameterizedType) {
                    Type[] args = ((ParameterizedType) genericType).getActualTypeArguments();
                    if (args.length > 0 && args[0] instanceof Class) {
                        Class<?> actual = (Class<?>) args[0];
                        if (Message.class.isAssignableFrom(actual)) {
                            protoClass = (Class<? extends Message>) actual;
                            parser = findParser(protoClass);
                        }
                    }
                }
            }

            final int finalAck = ackMsgId;
            final boolean hasReturn = Message.class.isAssignableFrom(method.getReturnType());

            RouteInvoker invoker = (sender, clientId, msg, mapId, sequence, tcpMsg) -> {
                MsgContext context = new MsgContext(sender, clientId, mapId, sequence, msgId, finalAck, msg);
                Object result = twoParams ? method.invoke(target, context, msg) : method.invoke(target, context);
                if (hasReturn && result instanceof Message && finalAck != 0) {
                    sender.sendMessage(clientId, finalAck, mapId, (Message) result, sequence);
                }
                return true;
            };

            RouteEntry entry = new RouteEntry(msgId, ackMsgId, desc, protoClass, parser, invoker);
            routes.put(msgId, entry);
            if (protoClass != null) {
                protoToMsgIdMap.put(protoClass, msgId);
            }
            logger.debug("已注册消息路由: 0x{} -> {}.{} (Proto: {})",
                    Integer.toHexString(msgId), target.getClass().getSimpleName(), method.getName(),
                    protoClass != null ? protoClass.getSimpleName() : "None");
            return;
        }

        // 模式 B: 方法入参为 (Sender, TCPMessage) 或类似网关透传入参
        if (paramTypes.length == 2 && TCPMessage.class.isAssignableFrom(paramTypes[1])) {
            RouteInvoker invoker = (sender, clientId, msg, mapId, sequence, tcpMsg) -> {
                method.invoke(target, sender, tcpMsg);
                return true;
            };
            routes.put(msgId, new RouteEntry(msgId, ackMsgId, desc, null, null, invoker));
            return;
        }

        // 模式 C: 传统 5 参数模式 handler(sender, clientId, msg, mapId, sequence)
        if (paramTypes.length == 5 && Sender.class.isAssignableFrom(paramTypes[0])) {
            RouteInvoker invoker = (sender, clientId, msg, mapId, sequence, tcpMsg) -> {
                Object res = method.invoke(target, sender, clientId, msg, mapId, sequence);
                return res instanceof Boolean ? (Boolean) res : true;
            };
            routes.put(msgId, new RouteEntry(msgId, ackMsgId, desc, null, null, invoker));
            return;
        }

        logger.error("方法 {}.{} 参数签名不支持 @Msg 路由注册", target.getClass().getSimpleName(), method.getName());
    }

    private int getMsgId(Msg anno) {
        return anno.value() != 0 ? anno.value() : anno.id();
    }

    @SuppressWarnings("unchecked")
    private Parser<? extends Message> findParser(Class<? extends Message> protoClass) {
        try {
            Method parserMethod = protoClass.getMethod("parser");
            return (Parser<? extends Message>) parserMethod.invoke(null);
        } catch (Exception ignored) {
        }
        try {
            Class<MessageLite> liteClass = (Class<MessageLite>) (Class<?>) protoClass;
            MessageLite defaultInstance = Internal.getDefaultInstance(liteClass);
            return (Parser<? extends Message>) defaultInstance.getParserForType();
        } catch (Exception e) {
            logger.warn("未能获取 Protobuf 解析器: {}", protoClass.getName(), e);
            return null;
        }
    }

    // ==================== 运行期核心分发 ====================

    /**
     * 核心统一分发入口
     *
     * @param sender 客户端发送端
     * @param tcpMsg 网络数据包
     * @return 是否保持连接
     */
    public boolean dispatch(Sender sender, TCPMessage tcpMsg) {
        if (tcpMsg == null) {
            return true;
        }
        int msgId = tcpMsg.getMessageId();
        if (msgId == 0) {
            return true; // 忽略心跳或空包
        }

        RouteEntry route = routes.get(msgId);
        if (route == null || route.invoker == null) {
            logger.warn("未注册的消息处理器: 0x{}, seq: {}", Integer.toHexString(msgId), tcpMsg.getSequence());
            return true;
        }

        String traceId = TraceContext.beginTrace();
        long start = System.currentTimeMillis();
        try {
            Message msg = null;
            if (route.parser != null) {
                byte[] bytes = tcpMsg.getMessage();
                if (bytes != null && bytes.length > 0) {
                    msg = route.parser.parseFrom(bytes);
                } else {
                    msg = route.parser.parseFrom(EMPTY_BYTES);
                }
            }

            return route.invoker.invoke(sender, tcpMsg.getClientId(), msg, tcpMsg.getMapId(), tcpMsg.getSequence(), tcpMsg);
        } catch (Exception e) {
            logger.error("消息处理异常, msgId: 0x{}, clientId: {}, traceId: {}",
                    Integer.toHexString(msgId), tcpMsg.getClientId(), traceId, e);
            return true;
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (cost > 500) {
                logger.warn("消息处理慢, msgId: 0x{}, clientId: {}, cost: {}ms, traceId: {}",
                        Integer.toHexString(msgId), tcpMsg.getClientId(), cost, traceId);
            }
            TraceContext.endTrace();
        }
    }

    // ==================== 接口兼容实现 (Handlers & Parser) ====================

    @Override
    public Handler getHandler(int msgId) {
        RouteEntry route = routes.get(msgId);
        if (route == null || route.invoker == null) {
            return null;
        }
        return (sender, clientId, msg, mapId, sequence) -> {
            try {
                return route.invoker.invoke(sender, clientId, msg, mapId, sequence, null);
            } catch (Exception e) {
                logger.error("Handler 异常, msgId: 0x{}", Integer.toHexString(msgId), e);
                return true;
            }
        };
    }

    @Override
    public Message parser(int msgId, byte[] bytes) throws InvalidProtocolBufferException {
        return parseMessage(msgId, bytes);
    }

    public Message parseMessage(int msgId, byte[] bytes) {
        RouteEntry route = routes.get(msgId);
        if (route == null || route.parser == null) {
            return null;
        }
        try {
            if (bytes == null || bytes.length == 0) {
                return route.parser.parseFrom(EMPTY_BYTES);
            }
            return route.parser.parseFrom(bytes);
        } catch (Exception e) {
            logger.error("反序列化消息失败, msgId: 0x{}", Integer.toHexString(msgId), e);
            return null;
        }
    }

    public Integer getMsgIdByClass(Class<?> clazz) {
        return protoToMsgIdMap.get(clazz);
    }

    public RouteEntry getRoute(int msgId) {
        return routes.get(msgId);
    }

    public int getRouteCount() {
        return routes.size();
    }
}

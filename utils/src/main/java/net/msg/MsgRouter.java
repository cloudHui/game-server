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
 * 统一网络消息路由引擎
 * <p>提供注解扫描、Proto 解析器推导缓存、高性能单次寻址、链路追踪与自动回包能力。</p>
 *
 * @author cloud
 */
public class MsgRouter implements Handlers, net.message.Parser {

    private static final Logger logger = LoggerFactory.getLogger(MsgRouter.class);
    private static final byte[] EMPTY_BYTES = new byte[0];

    private static final MsgRouter INSTANCE = new MsgRouter();

    public static MsgRouter getInstance() {
        return INSTANCE;
    }

    /**
     * 路由实体项
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

    @FunctionalInterface
    public interface RouteInvoker {
        boolean invoke(Sender sender, int clientId, Message msg, long mapId, int sequence, TCPMessage tcpMsg) throws Exception;
    }

    private final IntObjectMap<RouteEntry> routes = new IntObjectHashMap<>();
    private final ConcurrentHashMap<Class<?>, Integer> protoToMsgIdMap = new ConcurrentHashMap<>();

    // ==================== 扫描与注册 API ====================

    /**
     * 扫描指定包名并注册带 @Msg 的控制器与消息处理器
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
                    if (hasMsgAnnotation(clazz)) {
                        Object instance = clazz.getConstructor().newInstance();
                        register(instance);
                    }
                }
            } catch (Exception e) {
                logger.error("MsgRouter 扫描包 [{}] 失败", pkg, e);
            }
        }
        logger.info("MsgRouter 扫描完成, 新增路由: {} 条, 总路由: {} 条, 耗时: {}ms",
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

    public synchronized MsgRouter register(Object controller) {
        if (controller == null) return this;
        Class<?> clazz = controller.getClass();

        if (controller instanceof Handler && clazz.isAnnotationPresent(Msg.class)) {
            Msg classMsg = clazz.getAnnotation(Msg.class);
            registerHandler(getMsgId(classMsg), classMsg.ack(), classMsg.desc(), (Handler) controller);
        }

        for (Method method : clazz.getDeclaredMethods()) {
            Msg msgAnno = method.getAnnotation(Msg.class);
            if (msgAnno == null) continue;
            method.setAccessible(true);

            int[] forwardIds = msgAnno.forward();
            if (forwardIds != null && forwardIds.length > 0) {
                for (int fId : forwardIds) {
                    bindMethodRoute(fId, 0, msgAnno.desc(), controller, method);
                }
                continue;
            }
            int msgId = getMsgId(msgAnno);
            if (msgId == 0) {
                logger.warn("类 {} 方法 {} 的 @Msg 未指定有效消息 ID", clazz.getSimpleName(), method.getName());
                continue;
            }
            bindMethodRoute(msgId, msgAnno.ack(), msgAnno.desc(), controller, method);
        }
        return this;
    }

    public synchronized MsgRouter register(int msgId, Class<? extends Message> protoClass, String desc) {
        Parser<? extends Message> parser = protoClass != null ? findParser(protoClass) : null;
        routes.put(msgId, new RouteEntry(msgId, 0, desc, protoClass, parser, null));
        if (protoClass != null) {
            protoToMsgIdMap.put(protoClass, msgId);
        }
        return this;
    }

    public synchronized void registerHandler(int msgId, int ackMsgId, String desc, Handler handler) {
        routes.put(msgId, new RouteEntry(msgId, ackMsgId, desc, null, null,
                (sender, clientId, msg, mapId, sequence, tcpMsg) -> handler.handler(sender, clientId, msg, mapId, sequence)));
    }

    private void bindMethodRoute(int msgId, int ackMsgId, String desc, Object target, Method method) {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length >= 1 && MsgContext.class.isAssignableFrom(paramTypes[0])) {
            bindMsgContextRoute(msgId, ackMsgId, desc, target, method, paramTypes);
            return;
        }
        if (paramTypes.length == 2 && TCPMessage.class.isAssignableFrom(paramTypes[1])) {
            bindTcpMessageRoute(msgId, ackMsgId, desc, target, method);
            return;
        }
        if (paramTypes.length == 5 && Sender.class.isAssignableFrom(paramTypes[0])) {
            bindLegacyRoute(msgId, ackMsgId, desc, target, method);
            return;
        }
        logger.error("方法 {}.{} 参数签名不支持 @Msg 路由注册", target.getClass().getSimpleName(), method.getName());
    }

    @SuppressWarnings("unchecked")
    private void bindMsgContextRoute(int msgId, int ackMsgId, String desc, Object target, Method method, Class<?>[] paramTypes) {
        boolean twoParams = paramTypes.length == 2 && Message.class.isAssignableFrom(paramTypes[1]);
        Class<? extends Message> protoClass = twoParams ? (Class<? extends Message>) paramTypes[1] : extractGenericProtoClass(method);
        Parser<? extends Message> parser = protoClass != null ? findParser(protoClass) : null;
        boolean hasReturn = Message.class.isAssignableFrom(method.getReturnType());

        RouteInvoker invoker = (sender, clientId, msg, mapId, sequence, tcpMsg) -> {
            MsgContext context = new MsgContext(sender, clientId, mapId, sequence, msgId, ackMsgId, msg);
            Object result = twoParams ? method.invoke(target, context, msg) : method.invoke(target, context);
            if (hasReturn && result instanceof Message && ackMsgId != 0) {
                sender.sendMessage(clientId, ackMsgId, mapId, (Message) result, sequence);
            }
            return true;
        };

        routes.put(msgId, new RouteEntry(msgId, ackMsgId, desc, protoClass, parser, invoker));
        if (protoClass != null) {
            protoToMsgIdMap.put(protoClass, msgId);
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Message> extractGenericProtoClass(Method method) {
        Type genericType = method.getGenericParameterTypes()[0];
        if (genericType instanceof ParameterizedType) {
            Type[] args = ((ParameterizedType) genericType).getActualTypeArguments();
            if (args.length > 0 && args[0] instanceof Class && Message.class.isAssignableFrom((Class<?>) args[0])) {
                return (Class<? extends Message>) args[0];
            }
        }
        return null;
    }

    private void bindTcpMessageRoute(int msgId, int ackMsgId, String desc, Object target, Method method) {
        RouteInvoker invoker = (sender, clientId, msg, mapId, sequence, tcpMsg) -> {
            method.invoke(target, sender, tcpMsg);
            return true;
        };
        routes.put(msgId, new RouteEntry(msgId, ackMsgId, desc, null, null, invoker));
    }

    private void bindLegacyRoute(int msgId, int ackMsgId, String desc, Object target, Method method) {
        RouteInvoker invoker = (sender, clientId, msg, mapId, sequence, tcpMsg) -> {
            Object res = method.invoke(target, sender, clientId, msg, mapId, sequence);
            return !(res instanceof Boolean) || (Boolean) res;
        };
        routes.put(msgId, new RouteEntry(msgId, ackMsgId, desc, null, null, invoker));
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
            logger.warn("获取 Protobuf 解析器失败: {}", protoClass.getName(), e);
            return null;
        }
    }

    // ==================== 运行期消息分发 ====================

    /**
     * 核心统一消息分发
     */
    public boolean dispatch(Sender sender, TCPMessage tcpMsg) {
        if (tcpMsg == null) return true;
        int msgId = tcpMsg.getMessageId();
        if (msgId == 0) return true;

        RouteEntry route = routes.get(msgId);
        if (route == null || route.invoker == null) {
            logger.warn("未注册的消息处理器: 0x{}, seq: {}", Integer.toHexString(msgId), tcpMsg.getSequence());
            return true;
        }
        return executeRoute(sender, tcpMsg, route, msgId);
    }

    private boolean executeRoute(Sender sender, TCPMessage tcpMsg, RouteEntry route, int msgId) {
        String traceId = TraceContext.beginTrace();
        long start = System.currentTimeMillis();
        try {
            Message msg = null;
            if (route.parser != null) {
                byte[] bytes = tcpMsg.getMessage();
                msg = (bytes != null && bytes.length > 0) ? route.parser.parseFrom(bytes) : route.parser.parseFrom(EMPTY_BYTES);
            }
            return route.invoker.invoke(sender, tcpMsg.getClientId(), msg, tcpMsg.getMapId(), tcpMsg.getSequence(), tcpMsg);
        } catch (Exception e) {
            logger.error("消息处理异常, msgId: 0x{}, clientId: {}, traceId: {}",
                    Integer.toHexString(msgId), tcpMsg.getClientId(), traceId, e);
            return true;
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (cost > 500) {
                logger.warn("消息处理慢, msgId: 0x{}, cost: {}ms, traceId: {}", Integer.toHexString(msgId), cost, traceId);
            }
            TraceContext.endTrace();
        }
    }

    @Override
    public Handler getHandler(int msgId) {
        RouteEntry route = routes.get(msgId);
        if (route == null || route.invoker == null) return null;
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
        if (route == null || route.parser == null) return null;
        try {
            return (bytes == null || bytes.length == 0) ? route.parser.parseFrom(EMPTY_BYTES) : route.parser.parseFrom(bytes);
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

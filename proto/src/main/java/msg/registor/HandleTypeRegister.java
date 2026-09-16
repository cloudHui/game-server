package msg.registor;

import com.google.protobuf.Message;
import msg.annotation.ClassField;
import msg.annotation.ClassType;
import msg.annotation.ProcessClass;
import msg.annotation.ProcessEnum;
import msg.annotation.ProcessType;
import msg.registor.enums.TableState;
import net.msg.MsgRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.registry.ClassScanner;
import utils.registry.HandlerRegistry;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一消息与处理器注册中心
 * <p>
 * 负责协议常量映射、基于 {@link HandlerRegistry} 的通用组件处理器装配，以及与 {@link MsgRouter} 协同提供 Protobuf 消息反序列化。
 */
public class HandleTypeRegister {

    private static final Logger logger = LoggerFactory.getLogger(HandleTypeRegister.class);

    /** 默认扫描的处理器包名 */
    private static final String DEFAULT_HANDLE_PACKAGE = "tools.handle";

    /** 消息 ID -> Protobuf 消息类的映射 */
    private static final Map<Integer, Class<?>> TRANS_MAP = new ConcurrentHashMap<>();

    /** Protobuf 消息类 -> 消息 ID 的映射 */
    private static final Map<Class<?>, Integer> MSG_TRANS_MAP = new ConcurrentHashMap<>();

    static {
        initLocalMethod();
    }

    /**
     * 扫描并初始化消息常量类中的消息 ID 与 Protobuf 消息类的对应关系
     */
    private static void initLocalMethod() {
        try {
            long start = System.currentTimeMillis();
            List<Class<? extends Object>> classes = ClassScanner.scan("msg.registor.message", Object.class, ClassType.class);
            for (Class<?> clazz : classes) {
                bindTransMap(clazz);
            }
            logger.info("init message id bind total size:{} cost:{}ms", TRANS_MAP.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            logger.error("init message id bind class failed", e);
        }
    }

    /**
     * 读取常量类中的字段注解并绑定到映射表，同时向 MsgRouter 注册
     *
     * @param constantClass 带有 @ClassType 注解的常量类（例如 CMsg, GMsg, LMsg, SMsg）
     */
    @SuppressWarnings("unchecked")
    private static void bindTransMap(Class<?> constantClass) {
        for (Field field : constantClass.getFields()) {
            ClassField annotation = field.getAnnotation(ClassField.class);
            if (annotation == null) {
                continue;
            }
            try {
                Object fieldValue = field.get(null);
                if (fieldValue instanceof Integer) {
                    int messageId = (Integer) fieldValue;
                    Class<? extends Message> protoClass = (Class<? extends Message>) (Class<?>) annotation.value();
                    TRANS_MAP.put(messageId, protoClass);
                    MSG_TRANS_MAP.put(protoClass, messageId);
                    MsgRouter.getInstance().register(messageId, protoClass, annotation.des());
                }
            } catch (Exception e) {
                logger.error("Bind field failed: {}.{}", constantClass.getSimpleName(), field.getName(), e);
            }
        }
    }

    /**
     * 获取消息 ID 对应的 Protobuf 类
     */
    public static Class<?> getProtoClass(int messageId) {
        return TRANS_MAP.get(messageId);
    }

    /**
     * 获取 Protobuf 类对应的消息 ID
     */
    public static Integer getMessageId(Class<?> protoClass) {
        return MSG_TRANS_MAP.get(protoClass);
    }

    /**
     * 使用默认包名扫描并初始化基于整数键的处理器
     *
     * @param handles 处理器存储容器
     * @param <T>     处理器接口类型
     */
    public static <T> void initFactory(Map<Integer, T> handles) {
        initFactory(DEFAULT_HANDLE_PACKAGE, handles);
    }

    /**
     * 根据指定类所在的包名扫描并初始化基于整数键的处理器
     *
     * @param packageClass 目标包内的代表类
     * @param handles      处理器存储容器
     * @param <T>          处理器接口类型
     */
    public static <T> void initFactory(Class<?> packageClass, Map<Integer, T> handles) {
        String packageName = packageClass.getPackage().getName();
        initFactory(packageName, handles);
    }

    /**
     * 根据包路径扫描带有 @ProcessType 注解的处理器并加入集合
     *
     * @param packageName 目标包名
     * @param handles     处理器存储容器
     * @param <T>         处理器接口类型
     */
    private static <T> void initFactory(String packageName, Map<Integer, T> handles) {
        Map<Integer, T> mapped = HandlerRegistry.buildSingle(packageName, null, ProcessType.class, Integer.class);
        handles.putAll(mapped);
    }

    /**
     * 扫描带有 @ProcessEnum 注解的处理器，绑定 TableState 状态机对应处理逻辑
     *
     * @param packageClass 目标包内的代表类
     * @param handles      状态处理器集合
     * @param <T>          处理器接口类型
     */
    public static <T> void initFactoryEnum(Class<?> packageClass, Map<TableState, T> handles) {
        Map<TableState, T> mapped = HandlerRegistry.buildSingle(packageClass.getPackage().getName(), null, ProcessEnum.class, TableState.class);
        handles.putAll(mapped);
    }

    /**
     * 扫描带有 @ProcessClass 注解的处理器，按消息 Class 类型绑定处理映射
     *
     * @param factoryClass 目标包内的代表类
     * @param handles      类映射处理器集合
     * @param <T>          处理器接口类型
     */
    @SuppressWarnings("unchecked")
    public static <T> void initClassFactory(Class<?> factoryClass, Map<Class<?>, T> handles) {
        Map<Class<?>, T> mapped = (Map<Class<?>, T>) (Map<?, ?>) HandlerRegistry.buildSingle(
                factoryClass.getPackage().getName(), null, ProcessClass.class, (Class<Class<?>>) (Class<?>) Class.class);
        handles.putAll(mapped);
    }

    /**
     * 解析消息字节数据为 Protocol Buffer 消息对象，统一委派由 MsgRouter 的高性能预编译 Parser 解析
     *
     * @param messageId 消息 ID
     * @param bytes     二进制消息数据
     * @return 解析后的消息对象，失败则返回 null
     */
    public static Message parseMessage(int messageId, byte[] bytes) {
        return MsgRouter.getInstance().parseMessage(messageId, bytes);
    }
}

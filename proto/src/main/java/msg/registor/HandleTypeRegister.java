package msg.registor;

import com.google.protobuf.Internal;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import msg.annotation.ClassField;
import msg.annotation.ClassType;
import msg.annotation.ProcessClass;
import msg.annotation.ProcessEnum;
import msg.annotation.ProcessType;
import msg.registor.enums.TableState;
import net.msg.MsgRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.other.ClazzUtil;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一消息与处理器注册中心
 * <p>
 * 负责协议常量映射、状态机枚举处理器及传统处理器的动态发现与绑定。
 * 同时与 {@link MsgRouter} 协同，提供 Protobuf 消息的反序列化支持。
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
            List<Class<?>> classes = ClazzUtil.getAllClassExceptPackageClass(HandleTypeRegister.class, "");
            for (Class<?> clazz : classes) {
                ClassType processType = clazz.getAnnotation(ClassType.class);
                if (processType != null) {
                    bindTransMap(clazz);
                }
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
        try {
            long start = System.currentTimeMillis();
            List<Class<?>> classes = ClazzUtil.getClasses(packageName);
            Map<Class<?>, T> classProcessMap = new HashMap<>();

            for (Class<?> aclass : classes) {
                ProcessType processesType = aclass.getAnnotation(ProcessType.class);
                if (processesType == null) {
                    continue;
                }
                putHandle(processesType.value(), aclass, handles, classProcessMap);
            }

            logger.info("{} bind success initFactory, size:{} cost:{}ms", packageName, handles.size(),
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            logger.error("{} bind processors error", packageName, e);
        }
    }

    /**
     * 扫描带有 @ProcessEnum 注解的处理器，绑定 TableState 状态机对应处理逻辑
     *
     * @param packageClass 目标包内的代表类
     * @param handles      状态处理器集合
     * @param <T>          处理器接口类型
     */
    public static <T> void initFactoryEnum(Class<?> packageClass, Map<TableState, T> handles) {
        String packageName = packageClass.getPackage().getName();

        try {
            long start = System.currentTimeMillis();
            List<Class<?>> classes = ClazzUtil.getClasses(packageName);
            Map<Class<?>, T> classProcessMap = new HashMap<>();

            for (Class<?> aclass : classes) {
                ProcessEnum processesType = aclass.getAnnotation(ProcessEnum.class);
                if (processesType == null) {
                    continue;
                }
                for (TableState state : processesType.value()) {
                    putHandle(state, aclass, handles, classProcessMap);
                }
            }

            logger.info("{} bind success initFactoryEnum, size:{} cost:{}ms", packageName, handles.size(),
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            logger.error("{} bind processors error", packageName, e);
        }
    }

    /**
     * 扫描带有 @ProcessClass 注解的处理器，按消息 Class 类型绑定处理映射
     *
     * @param factoryClass 目标包内的代表类
     * @param handles      类映射处理器集合
     * @param <T>          处理器接口类型
     */
    public static <T> void initClassFactory(Class<?> factoryClass, Map<Class<?>, T> handles) {
        try {
            long start = System.currentTimeMillis();
            List<Class<?>> classes = ClazzUtil.getClasses(factoryClass, "");
            Map<Class<?>, T> classProcessMap = new HashMap<>();

            for (Class<?> aclass : classes) {
                ProcessClass processesType = aclass.getAnnotation(ProcessClass.class);
                if (processesType == null) {
                    continue;
                }

                Class<?> value = processesType.value();
                putHandle(value, aclass, handles, classProcessMap);
            }

            logger.info("{} bind success, size:{} cost:{}ms", factoryClass.getPackage().getName(), handles.size(),
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            logger.error("{} bind processors error", factoryClass.getPackage().getName(), e);
        }
    }

    /**
     * 绑定处理器实例到容器中，避免同类实例重复创建
     *
     * @param key             映射键
     * @param aclass          处理器实现类
     * @param handles         目标映射表
     * @param classProcessMap 实例缓存表
     * @param <K>             键类型
     * @param <T>             处理器类型
     */
    public static <K, T> void putHandle(K key, Class<?> aclass, Map<K, T> handles, Map<Class<?>, T> classProcessMap) {
        T handler = handles.get(key);
        if (handler != null) {
            logger.error("putHandle same key:{} old:{} new:{} ", key, handler.getClass(), aclass);
        }

        handler = classProcessMap.computeIfAbsent(aclass, k -> newInstance(key, aclass));

        if (handler != null) {
            handles.put(key, handler);
        }
    }

    /**
     * 反射创建处理器实例
     *
     * @param key    调试键
     * @param aclass 处理器 Class
     * @param <T>    目标类型
     * @return 实例对象
     */
    @SuppressWarnings("unchecked")
    private static <T> T newInstance(Object key, Class<?> aclass) {
        try {
            return (T) aclass.getConstructor().newInstance();
        } catch (Exception e) {
            logger.error("init {} newInstance fail for key {} ", aclass, key, e);
        }
        return null;
    }

    /**
     * 解析消息字节数据为 Protocol Buffer 消息对象
     *
     * @param messageId 消息 ID
     * @param bytes     二进制消息数据
     * @return 解析后的消息对象，失败则返回 null
     */
    @SuppressWarnings("unchecked")
    public static Message parseMessage(int messageId, byte[] bytes) {
        Class<?> messageClass = TRANS_MAP.get(messageId);
        if (messageClass == null) {
            Message fallback = MsgRouter.getInstance().parseMessage(messageId, bytes);
            if (fallback != null) {
                return fallback;
            }
            logger.error("Unknown message ID: {}", messageId);
            return null;
        }

        try {
            return (Message) parseMessageData((Class<MessageLite>) messageClass, bytes);
        } catch (Exception e) {
            logger.error("Parse message failed, ID: {}, Class: {}",
                    messageId, messageClass.getSimpleName(), e);
            return null;
        }
    }

    /**
     * 使用 Protocol Buffer 原生 Parser 解析消息数据
     *
     * @param messageClass 目标消息类
     * @param bytes        二进制消息体
     * @return 解析得到的消息实例
     * @throws Exception 解析异常
     */
    private static MessageLite parseMessageData(Class<MessageLite> messageClass, byte[] bytes) throws Exception {
        MessageLite defaultInstance = Internal.getDefaultInstance(messageClass);
        if (bytes == null || bytes.length == 0) {
            return defaultInstance.newBuilderForType().build();
        }
        return defaultInstance.getParserForType().parseFrom(bytes);
    }
}

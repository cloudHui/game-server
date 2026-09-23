package utils.registry;

import com.google.protobuf.Message;
import net.msg.MsgRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.registry.annotation.ClassType;
import utils.registry.annotation.ProcessClass;
import utils.registry.annotation.ProcessEnum;
import utils.registry.annotation.ProcessType;
import utils.registry.enums.TableState;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 统一处理器注册与协议路由构建引擎。
 * <p>
 * <b>职责边界：</b>
 * 作为全工程唯一的组件、处理器装配与 Protobuf 消息分发注册中心。
 * 整合注解元驱动、Fail-Fast 冲突校验、单例去重共享与统一反序列化能力。
 */
public final class HandlerRegistry {

    private static final Logger logger = LoggerFactory.getLogger(HandlerRegistry.class);

    /**
     * 默认扫描的工具处理器包名
     */
    public static final String DEFAULT_HANDLE_PACKAGE = "tools.handle";

    /**
     * 默认扫描的消息常量定义包名
     */
    private static final String[] DEFAULT_MESSAGE_PACKAGES = {"msg.registor.message", "msg.message"};

    /**
     * 消息 ID -> Protobuf 消息类的映射
     */
    private static final Map<Integer, Class<?>> TRANS_MAP = new ConcurrentHashMap<>();

    /**
     * Protobuf 消息类 -> 消息 ID 的映射
     */
    private static final Map<Class<?>, Integer> MSG_TRANS_MAP = new ConcurrentHashMap<>();

    /**
     * 注解类型到解析器实例缓存（解析器无状态，全局共享）
     */
    private static final Map<Class<? extends Annotation>, KeyResolver<Annotation>> RESOLVERS = new ConcurrentHashMap<>();

    /**
     * 消息常量是否已初始化完成
     */
    private static final AtomicBoolean MESSAGE_INITIALIZED = new AtomicBoolean(false);

    static {
        ensureMessageInitialized();
    }

    private HandlerRegistry() {
    }

    // ==================== 协议常量映射与解析 API ====================

    /**
     * 确保协议常量类已完成扫描与注册绑定
     */
    public static void ensureMessageInitialized() {
        if (MESSAGE_INITIALIZED.compareAndSet(false, true)) {
            initMessageConstants(DEFAULT_MESSAGE_PACKAGES);
        }
    }

    /**
     * 扫描指定常量包并绑定消息映射关系到本注册中心与 {@link MsgRouter}
     *
     * @param packageNames 协议常量类包名
     */
    public static void initMessageConstants(String... packageNames) {
        long start = System.currentTimeMillis();
        int totalBound = 0;
        for (String pkg : packageNames) {
            try {
                List<Class<?>> classes = ClassScanner.scan(pkg, Object.class, null);
                for (Class<?> clazz : classes) {
                    if (isConstantClass(clazz)) {
                        totalBound += bindConstantClass(clazz);
                    }
                }
            } catch (Exception e) {
                logger.error("扫描协议常量包 [{}] 失败", pkg, e);
            }
        }
        logger.info("初始化协议常量映射完成, 注册消息ID数: {}, 耗时: {}ms", totalBound, (System.currentTimeMillis() - start));
    }

    private static boolean isConstantClass(Class<?> clazz) {
        if (clazz.isAnnotationPresent(ClassType.class)) {
            return true;
        }
        for (Annotation anno : clazz.getAnnotations()) {
            if ("ClassType".equals(anno.annotationType().getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static int bindConstantClass(Class<?> constantClass) {
        int bound = 0;
        for (Field field : constantClass.getFields()) {
            Annotation classFieldAnno = findAnnotationByName(field.getAnnotations(), "ClassField");
            if (classFieldAnno == null) {
                continue;
            }
            try {
                Method valueMethod = classFieldAnno.annotationType().getMethod("value");
                Method desMethod = classFieldAnno.annotationType().getMethod("des");
                Class<?> protoClass = (Class<?>) valueMethod.invoke(classFieldAnno);
                String desc = (String) desMethod.invoke(classFieldAnno);

                Object fieldValue = field.get(null);
                if (fieldValue instanceof Integer && protoClass != null) {
                    int messageId = (Integer) fieldValue;
                    TRANS_MAP.put(messageId, protoClass);
                    MSG_TRANS_MAP.put(protoClass, messageId);
                    if (Message.class.isAssignableFrom(protoClass)) {
                        MsgRouter.getInstance().register(messageId, (Class<? extends Message>) protoClass, desc);
                    }
                    bound++;
                }
            } catch (Exception e) {
                logger.error("绑定常量字段失败: {}.{}", constantClass.getSimpleName(), field.getName(), e);
            }
        }
        return bound;
    }

    private static Annotation findAnnotationByName(Annotation[] annotations, String simpleName) {
        for (Annotation a : annotations) {
            if (simpleName.equals(a.annotationType().getSimpleName())) {
                return a;
            }
        }
        return null;
    }

    /**
     * 获取消息 ID 对应的 Protobuf 类
     */
    public static Class<?> getProtoClass(int messageId) {
        ensureMessageInitialized();
        return TRANS_MAP.get(messageId);
    }

    /**
     * 获取 Protobuf 类对应的消息 ID
     */
    public static Integer getMessageId(Class<?> protoClass) {
        ensureMessageInitialized();
        return MSG_TRANS_MAP.get(protoClass);
    }

    /**
     * 解析消息字节数据为 Protocol Buffer 消息对象，统一委派由 MsgRouter 的高性能预编译 Parser 解析
     *
     * @param messageId 消息 ID
     * @param bytes     二进制消息数据
     * @return 解析后的消息对象，失败则返回 null
     */
    public static Message parseMessage(int messageId, byte[] bytes) {
        ensureMessageInitialized();
        return MsgRouter.getInstance().parseMessage(messageId, bytes);
    }

    // ==================== 便捷处理器装配 API（统一融合为 bind 系列） ====================

    /**
     * 使用默认包名扫描并绑定基于整数消息 ID 的处理器（@ProcessType）
     *
     * @param handles 处理器存储容器
     * @param <T>     处理器接口类型
     */
    public static <T> void bind(Map<Integer, T> handles) {
        bind(DEFAULT_HANDLE_PACKAGE, handles);
    }

    /**
     * 根据代表类所在包扫描带有 @ProcessType 的处理器并加入集合
     *
     * @param packageClass 目标包内的代表类
     * @param handles      处理器存储容器
     * @param <T>          处理器接口类型
     */
    public static <T> void bind(Class<?> packageClass, Map<Integer, T> handles) {
        bind(packageClass.getPackage().getName(), handles);
    }

    /**
     * 根据包路径扫描带有 @ProcessType 的处理器并加入集合
     *
     * @param packageName 目标包名
     * @param handles     处理器存储容器
     * @param <T>         处理器接口类型
     */
    public static <T> void bind(String packageName, Map<Integer, T> handles) {
        handles.putAll(buildSingle(packageName, null, ProcessType.class, Integer.class));
    }

    /**
     * 扫描代表类所在包带有 @ProcessEnum 注解的处理器，绑定 TableState 状态机逻辑
     *
     * @param packageClass 目标包内的代表类
     * @param handles      状态处理器集合
     * @param <T>          处理器接口类型
     */
    public static <T> void bindEnum(Class<?> packageClass, Map<TableState, T> handles) {
        bindEnum(packageClass.getPackage().getName(), handles);
    }

    /**
     * 根据包路径扫描带有 @ProcessEnum 注解的处理器并绑定 TableState 映射
     *
     * @param packageName 目标根包名（会递归扫描所有子包）
     * @param handles     状态处理器集合
     * @param <T>         处理器接口类型
     */
    public static <T> void bindEnum(String packageName, Map<TableState, T> handles) {
        handles.putAll(buildSingle(packageName, null, ProcessEnum.class, TableState.class));
    }

    /**
     * 扫描代表类所在包带有 @ProcessClass 注解的处理器，按消息 Class 类型绑定处理映射
     *
     * @param factoryClass 目标包内的代表类
     * @param handles      类映射处理器集合
     * @param <T>          处理器接口类型
     */
    public static <T> void bindClass(Class<?> factoryClass, Map<Class<?>, T> handles) {
        bindClass(factoryClass.getPackage().getName(), handles);
    }

    /**
     * 根据包路径扫描带有 @ProcessClass 注解的处理器，按消息 Class 类型绑定处理映射
     *
     * @param packageName 目标包名
     * @param handles     类映射处理器集合
     * @param <T>         处理器接口类型
     */
    @SuppressWarnings("unchecked")
    public static <T> void bindClass(String packageName, Map<Class<?>, T> handles) {
        Map<Class<?>, T> mapped = buildSingle(
                packageName, null, ProcessClass.class, (Class<Class<?>>) (Class<?>) Class.class);
        handles.putAll(mapped);
    }

    /**
     * 万能泛型绑定：根据 Key 类型自动推导对应注解并完成装配
     *
     * @param packageClass 代表类
     * @param keyType      Key 类型 Class (如 Integer.class, TableState.class, Class.class)
     * @param handles      处理器容器
     */
    @SuppressWarnings("unchecked")
    public static <K, T> void bind(Class<?> packageClass, Class<K> keyType, Map<K, T> handles) {
        Class<? extends Annotation> annotationType;
        if (keyType.equals(Integer.class)) {
            annotationType = ProcessType.class;
        } else if (keyType.equals(TableState.class)) {
            annotationType = ProcessEnum.class;
        } else if (Class.class.isAssignableFrom(keyType)) {
            annotationType = ProcessClass.class;
        } else {
            throw new IllegalArgumentException("未受支持的自动推导 Key 类型: " + keyType.getName());
        }
        handles.putAll(buildSingle(packageClass.getPackage().getName(), null, annotationType, keyType));
    }


    // ==================== 核心处理器映射表构建流程 ====================

    /**
     * 根据指定包名构建单处理器映射表。
     *
     * @param packageName    扫描包名
     * @param baseType       处理器基类型
     * @param annotationType 标注在处理器上的注册注解（须标注 {@link KeyBy}）
     * @param keyType        Key 类型 Class
     * @param <K>            Key 类型
     * @param <T>            处理器基类类型
     * @return 不可变的 Key 到处理器实例的映射表
     */
    public static <K, T> Map<K, T> buildSingle(String packageName, Class<T> baseType,
                                               Class<? extends Annotation> annotationType, Class<K> keyType) {
        return buildSingle(Collections.singletonList(packageName), baseType, annotationType, keyType);
    }

    /**
     * 根据多个指定包名构建单处理器映射表并统一合并去重与冲突校验。
     *
     * @param packageNames   扫描包名集合
     * @param baseType       处理器基类型
     * @param annotationType 标注在处理器上的注册注解（须标注 {@link KeyBy}）
     * @param keyType        Key 类型 Class
     * @param <K>            Key 类型
     * @param <T>            处理器基类类型
     * @return 不可变的 Key 到处理器实例的映射表
     */
    public static <K, T> Map<K, T> buildSingle(Iterable<String> packageNames, Class<T> baseType,
                                               Class<? extends Annotation> annotationType, Class<K> keyType) {
        Map<K, Set<T>> multiMap = build(packageNames, baseType, annotationType, keyType, false);
        Map<K, T> result = new LinkedHashMap<>();
        for (Map.Entry<K, Set<T>> entry : multiMap.entrySet()) {
            result.put(entry.getKey(), entry.getValue().iterator().next());
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 核心扫描、校验与实例化流程。
     */
    private static <K, T> Map<K, Set<T>> build(Iterable<String> packageNames, Class<T> baseType,
                                               Class<? extends Annotation> annotationType,
                                               Class<K> keyType, boolean multiple) {
        long start = System.currentTimeMillis();
        KeyResolver<Annotation> resolver = getResolver(annotationType);
        Map<K, Set<Class<? extends T>>> bindings = new LinkedHashMap<>();

        Set<Class<? extends T>> candidateClasses = new LinkedHashSet<>();
        for (String pkg : packageNames) {
            candidateClasses.addAll(ClassScanner.scan(pkg, baseType, annotationType));
            // 兼容支持历史 msg.annotation 包下的同名注解扫描
            Class<? extends Annotation> legacyAnno = findLegacyAnnotation(annotationType.getSimpleName());
            if (legacyAnno != null && !legacyAnno.equals(annotationType)) {
                candidateClasses.addAll(ClassScanner.scan(pkg, baseType, legacyAnno));
            }
        }

        // 1. 扫描所有类并严格校验 Key，若有冲突立即快速失败（Fail-Fast）
        for (Class<? extends T> type : candidateClasses) {
            Annotation annotation = type.getAnnotation(annotationType);
            KeyResolver<Annotation> currentResolver = resolver;
            if (annotation == null) {
                Class<? extends Annotation> legacyAnno = findLegacyAnnotation(annotationType.getSimpleName());
                if (legacyAnno != null) {
                    annotation = type.getAnnotation(legacyAnno);
                    currentResolver = getResolver(legacyAnno);
                }
            }
            if (annotation == null) {
                continue;
            }

            List<?> keys = currentResolver.keys(annotation, type);
            if (keys == null || keys.isEmpty()) {
                throw new IllegalStateException("处理器未声明有效的 Key: " + type.getName());
            }

            for (Object rawKey : keys) {
                if (rawKey == null) {
                    throw new IllegalStateException("处理器声明了 null Key: " + type.getName());
                }

                // 兼容枚举类型跨包（如 utils.registry.enums.TableState -> utils.registry.enums.TableState）
                Object convertedKey = rawKey;
                if (!keyType.isInstance(rawKey)) {
                    if (keyType.isEnum() && rawKey instanceof Enum) {
                        try {
                            convertedKey = Enum.valueOf((Class<Enum>) (Class<?>) keyType, ((Enum<?>) rawKey).name());
                        } catch (Exception ignored) {
                        }
                    }
                }

                if (!keyType.isInstance(convertedKey)) {
                    throw new IllegalStateException(String.format("处理器 Key 类型不匹配! 类: %s, 实际 Key: %s (%s), 期望类型: %s",
                            type.getName(), rawKey, rawKey.getClass().getName(), keyType.getName()));
                }
                K key = keyType.cast(convertedKey);
                Set<Class<? extends T>> owners = bindings.computeIfAbsent(key, k -> new LinkedHashSet<>());

                // 单处理器模式下，检测到不同类占用同一 Key 时抛出异常
                if (!multiple && !owners.isEmpty() && !owners.contains(type)) {
                    Class<? extends T> existing = owners.iterator().next();
                    throw new IllegalStateException(String.format("处理器 Key 冲突! Key: [%s], 已注册类: [%s], 冲突类: [%s]",
                            key, existing.getName(), type.getName()));
                }
                owners.add(type);
            }
        }

        // 2. 校验全部通过后再统一构建实例，同一 Class 只创建一次对象
        Map<Class<? extends T>, T> instanceCache = new LinkedHashMap<>();
        Map<K, Set<T>> resultMap = new LinkedHashMap<>();

        for (Map.Entry<K, Set<Class<? extends T>>> entry : bindings.entrySet()) {
            Set<T> handlerSet = new LinkedHashSet<>();
            for (Class<? extends T> clazz : entry.getValue()) {
                T instance = instanceCache.computeIfAbsent(clazz, HandlerRegistry::newInstance);
                handlerSet.add(instance);
            }
            resultMap.put(entry.getKey(), handlerSet);
        }

        logger.info("HandlerRegistry 注册完成! 包: {}, 注解: @{}, Key类型: {}, 映射条数: {}, 实例数: {}, 耗时: {}ms",
                packageNames, annotationType.getSimpleName(), keyType.getSimpleName(),
                resultMap.size(), instanceCache.size(), (System.currentTimeMillis() - start));
        return resultMap;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> findLegacyAnnotation(String simpleName) {
        try {
            Class<?> clazz = Class.forName("utils.registry.annotation." + simpleName);
            if (Annotation.class.isAssignableFrom(clazz)) {
                return (Class<? extends Annotation>) clazz;
            }
        } catch (ClassNotFoundException ignored) {
        }
        return null;
    }

    /**
     * 读取注解上的 {@link KeyBy} 获取对应的解析器单例。
     */
    @SuppressWarnings("unchecked")
    private static KeyResolver<Annotation> getResolver(Class<? extends Annotation> annotationType) {
        KeyResolver<Annotation> resolver = RESOLVERS.get(annotationType);
        if (resolver == null) {
            KeyBy keyBy = annotationType.getAnnotation(KeyBy.class);
            if (keyBy == null) {
                throw new IllegalArgumentException("注册注解未标注 @KeyBy: " + annotationType.getName());
            }
            resolver = (KeyResolver<Annotation>) newInstance(keyBy.value());
            RESOLVERS.put(annotationType, resolver);
        }
        return resolver;
    }

    /**
     * 通过公共无参构造函数创建实例。
     */
    private static <T> T newInstance(Class<T> clazz) {
        try {
            return clazz.getConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("实例化处理器失败，必须提供公共无参构造函数: " + clazz.getName(), e);
        }
    }
}

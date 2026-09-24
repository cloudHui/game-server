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
 * 统一处理器注册与协议路由构建引擎
 * <p>提供注解元驱动、Fail-Fast 冲突校验、单例去重共享与统一反序列化能力。</p>
 *
 * @author cloud
 */
public final class HandlerRegistry {

    private static final Logger logger = LoggerFactory.getLogger(HandlerRegistry.class);

    public static final String DEFAULT_HANDLE_PACKAGE = "tools.handle";
    private static final String[] DEFAULT_MESSAGE_PACKAGES = {"msg.registor.message", "msg.message"};

    private static final Map<Integer, Class<?>> TRANS_MAP = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Integer> MSG_TRANS_MAP = new ConcurrentHashMap<>();
    private static final Map<Class<? extends Annotation>, KeyResolver<Annotation>> RESOLVERS = new ConcurrentHashMap<>();
    private static final AtomicBoolean MESSAGE_INITIALIZED = new AtomicBoolean(false);

    static {
        ensureMessageInitialized();
    }

    private HandlerRegistry() {
    }

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

    public static Class<?> getProtoClass(int messageId) {
        ensureMessageInitialized();
        return TRANS_MAP.get(messageId);
    }

    public static Integer getMessageId(Class<?> protoClass) {
        ensureMessageInitialized();
        return MSG_TRANS_MAP.get(protoClass);
    }

    public static Message parseMessage(int messageId, byte[] bytes) {
        ensureMessageInitialized();
        return MsgRouter.getInstance().parseMessage(messageId, bytes);
    }

    // ==================== 便捷处理器装配 API ====================

    public static <T> void bind(Map<Integer, T> handles) {
        bind(DEFAULT_HANDLE_PACKAGE, handles);
    }

    public static <T> void bind(Class<?> packageClass, Map<Integer, T> handles) {
        bind(packageClass.getPackage().getName(), handles);
    }

    public static <T> void bind(String packageName, Map<Integer, T> handles) {
        handles.putAll(buildSingle(packageName, null, ProcessType.class, Integer.class));
    }

    public static <T> void bindEnum(Class<?> packageClass, Map<TableState, T> handles) {
        bindEnum(packageClass.getPackage().getName(), handles);
    }

    public static <T> void bindEnum(String packageName, Map<TableState, T> handles) {
        handles.putAll(buildSingle(packageName, null, ProcessEnum.class, TableState.class));
    }

    public static <T> void bindClass(Class<?> factoryClass, Map<Class<?>, T> handles) {
        bindClass(factoryClass.getPackage().getName(), handles);
    }

    @SuppressWarnings("unchecked")
    public static <T> void bindClass(String packageName, Map<Class<?>, T> handles) {
        Map<Class<?>, T> mapped = buildSingle(
                packageName, null, ProcessClass.class, (Class<Class<?>>) (Class<?>) Class.class);
        handles.putAll(mapped);
    }

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

    public static <K, T> Map<K, T> buildSingle(String packageName, Class<T> baseType,
                                               Class<? extends Annotation> annotationType, Class<K> keyType) {
        return buildSingle(Collections.singletonList(packageName), baseType, annotationType, keyType);
    }

    public static <K, T> Map<K, T> buildSingle(Iterable<String> packageNames, Class<T> baseType,
                                               Class<? extends Annotation> annotationType, Class<K> keyType) {
        Map<K, Set<T>> multiMap = build(packageNames, baseType, annotationType, keyType, false);
        Map<K, T> result = new LinkedHashMap<>();
        for (Map.Entry<K, Set<T>> entry : multiMap.entrySet()) {
            result.put(entry.getKey(), entry.getValue().iterator().next());
        }
        return Collections.unmodifiableMap(result);
    }

    private static <K, T> Map<K, Set<T>> build(Iterable<String> packageNames, Class<T> baseType,
                                               Class<? extends Annotation> annotationType,
                                               Class<K> keyType, boolean multiple) {
        long start = System.currentTimeMillis();
        KeyResolver<Annotation> resolver = getResolver(annotationType);
        Set<Class<? extends T>> candidateClasses = collectCandidates(packageNames, baseType, annotationType);
        Map<K, Set<Class<? extends T>>> bindings = resolveBindings(candidateClasses, annotationType, resolver, keyType, multiple);
        Map<K, Set<T>> resultMap = instantiateHandlers(bindings);

        logger.info("HandlerRegistry 注册完成! 包: {}, 注解: @{}, Key类型: {}, 映射条数: {}, 耗时: {}ms",
                packageNames, annotationType.getSimpleName(), keyType.getSimpleName(),
                resultMap.size(), (System.currentTimeMillis() - start));
        return resultMap;
    }

    private static <T> Set<Class<? extends T>> collectCandidates(Iterable<String> packageNames, Class<T> baseType,
                                                                 Class<? extends Annotation> annotationType) {
        Set<Class<? extends T>> candidateClasses = new LinkedHashSet<>();
        for (String pkg : packageNames) {
            candidateClasses.addAll(ClassScanner.scan(pkg, baseType, annotationType));
            Class<? extends Annotation> legacyAnno = findLegacyAnnotation(annotationType.getSimpleName());
            if (legacyAnno != null && !legacyAnno.equals(annotationType)) {
                candidateClasses.addAll(ClassScanner.scan(pkg, baseType, legacyAnno));
            }
        }
        return candidateClasses;
    }

    private static <K, T> Map<K, Set<Class<? extends T>>> resolveBindings(
            Set<Class<? extends T>> candidateClasses, Class<? extends Annotation> annotationType,
            KeyResolver<Annotation> resolver, Class<K> keyType, boolean multiple) {
        Map<K, Set<Class<? extends T>>> bindings = new LinkedHashMap<>();
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
            if (annotation == null) continue;
            bindClassKeys(type, annotation, currentResolver, keyType, multiple, bindings);
        }
        return bindings;
    }

    private static <K, T> void bindClassKeys(
            Class<? extends T> type, Annotation annotation, KeyResolver<Annotation> resolver,
            Class<K> keyType, boolean multiple, Map<K, Set<Class<? extends T>>> bindings) {
        List<?> keys = resolver.keys(annotation, type);
        if (keys == null || keys.isEmpty()) {
            throw new IllegalStateException("处理器未声明有效的 Key: " + type.getName());
        }
        for (Object rawKey : keys) {
            if (rawKey == null) throw new IllegalStateException("处理器声明了 null Key: " + type.getName());
            Object convertedKey = convertKeyIfEnum(rawKey, keyType);
            if (!keyType.isInstance(convertedKey)) {
                throw new IllegalStateException(String.format("处理器 Key 类型不匹配! 类: %s, 期望: %s", type.getName(), keyType.getName()));
            }
            K key = keyType.cast(convertedKey);
            Set<Class<? extends T>> owners = bindings.computeIfAbsent(key, k -> new LinkedHashSet<>());
            if (!multiple && !owners.isEmpty() && !owners.contains(type)) {
                Class<? extends T> existing = owners.iterator().next();
                throw new IllegalStateException(String.format("处理器 Key 冲突! Key: [%s], 已注册: [%s], 冲突: [%s]",
                        key, existing.getName(), type.getName()));
            }
            owners.add(type);
        }
    }

    @SuppressWarnings("unchecked")
    private static <K> Object convertKeyIfEnum(Object rawKey, Class<K> keyType) {
        if (!keyType.isInstance(rawKey) && keyType.isEnum() && rawKey instanceof Enum) {
            try {
                return Enum.valueOf((Class<Enum>) (Class<?>) keyType, ((Enum<?>) rawKey).name());
            } catch (Exception ignored) {
            }
        }
        return rawKey;
    }

    private static <K, T> Map<K, Set<T>> instantiateHandlers(Map<K, Set<Class<? extends T>>> bindings) {
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

    private static <T> T newInstance(Class<T> clazz) {
        try {
            return clazz.getConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("实例化处理器失败，必须提供公共无参构造函数: " + clazz.getName(), e);
        }
    }
}

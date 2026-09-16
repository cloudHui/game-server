package utils.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用处理器注册表构建引擎。
 * <p>
 * 通过 {@link KeyBy} 与 {@link KeyResolver} 实现注解与注册引擎的完全解耦。
 * 支持单处理器映射（单 Key 唯一绑定，冲突熔断）与多监听器集合映射，并在同一次构建中实现同类实例去重共享。
 */
public final class HandlerRegistry {

    private static final Logger logger = LoggerFactory.getLogger(HandlerRegistry.class);

    /** 注解类型到解析器实例缓存（解析器无状态，全局共享） */
    private static final Map<Class<? extends Annotation>, KeyResolver<Annotation>> RESOLVERS = new ConcurrentHashMap<>();

    private HandlerRegistry() {
    }

    /**
     * 根据基类锚点构建单处理器映射表。若不同实现类冲突占用同一 Key 则立即抛异常阻断启动。
     *
     * @param baseType       处理器基类型，同时作为扫描包锚点
     * @param annotationType 标注在处理器上的注册注解（须标注 {@link KeyBy}）
     * @param keyType        Key 类型 Class，用于类型安全校验
     * @param <K>            Key 类型
     * @param <T>            处理器基类类型
     * @return 不可变的 Key 到处理器实例的映射表
     */
    public static <K, T> Map<K, T> buildSingle(Class<T> baseType, Class<? extends Annotation> annotationType, Class<K> keyType) {
        if (baseType == null) {
            throw new IllegalArgumentException("baseType 不能为空");
        }
        return buildSingle(baseType.getPackage().getName(), baseType, annotationType, keyType);
    }

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
    public static <K, T> Map<K, T> buildSingle(String packageName, Class<T> baseType, Class<? extends Annotation> annotationType, Class<K> keyType) {
        Map<K, Set<T>> multiMap = build(packageName, baseType, annotationType, keyType, false);
        Map<K, T> result = new LinkedHashMap<>();
        for (Map.Entry<K, Set<T>> entry : multiMap.entrySet()) {
            result.put(entry.getKey(), entry.getValue().iterator().next());
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 根据基类锚点构建多监听器映射表。允许不同类共享同一个 Key。
     *
     * @param baseType       监听器基类型
     * @param annotationType 注册注解（须标注 {@link KeyBy}）
     * @param keyType        Key 类型 Class
     * @param <K>            Key 类型
     * @param <T>            监听器类型
     * @return Key 到监听器集合的映射表
     */
    public static <K, T> Map<K, Set<T>> buildMultiple(Class<T> baseType, Class<? extends Annotation> annotationType, Class<K> keyType) {
        if (baseType == null) {
            throw new IllegalArgumentException("baseType 不能为空");
        }
        return buildMultiple(baseType.getPackage().getName(), baseType, annotationType, keyType);
    }

    /**
     * 根据指定包名构建多监听器映射表。
     *
     * @param packageName    扫描包名
     * @param baseType       监听器基类型
     * @param annotationType 注册注解
     * @param keyType        Key 类型 Class
     * @param <K>            Key 类型
     * @param <T>            监听器类型
     * @return Key 到监听器集合的映射表
     */
    public static <K, T> Map<K, Set<T>> buildMultiple(String packageName, Class<T> baseType, Class<? extends Annotation> annotationType, Class<K> keyType) {
        return Collections.unmodifiableMap(build(packageName, baseType, annotationType, keyType, true));
    }

    /**
     * 核心扫描、校验与实例化流程。
     */
    private static <K, T> Map<K, Set<T>> build(String packageName, Class<T> baseType,
                                               Class<? extends Annotation> annotationType,
                                               Class<K> keyType, boolean multiple) {
        long start = System.currentTimeMillis();
        KeyResolver<Annotation> resolver = getResolver(annotationType);
        Map<K, Set<Class<? extends T>>> bindings = new LinkedHashMap<>();

        List<Class<? extends T>> candidateClasses = ClassScanner.scan(packageName, baseType, annotationType);

        // 1. 扫描所有类并严格校验 Key，若有冲突立即快速失败（Fail-Fast）
        for (Class<? extends T> type : candidateClasses) {
            Annotation annotation = type.getAnnotation(annotationType);
            List<?> keys = resolver.keys(annotation, type);
            if (keys == null || keys.isEmpty()) {
                throw new IllegalStateException("处理器未声明有效的 Key: " + type.getName());
            }

            for (Object rawKey : keys) {
                if (rawKey == null) {
                    throw new IllegalStateException("处理器声明了 null Key: " + type.getName());
                }
                if (!keyType.isInstance(rawKey)) {
                    throw new IllegalStateException(String.format("处理器 Key 类型不匹配! 类: %s, 实际 Key: %s (%s), 期望类型: %s",
                            type.getName(), rawKey, rawKey.getClass().getName(), keyType.getName()));
                }
                K key = keyType.cast(rawKey);
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

        logger.info("HandlerRegistry 注册完成! 包: [{}], 注解: @{}, Key类型: {}, 映射条数: {}, 实例数: {}, 耗时: {}ms",
                packageName, annotationType.getSimpleName(), keyType.getSimpleName(),
                resultMap.size(), instanceCache.size(), (System.currentTimeMillis() - start));
        return resultMap;
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

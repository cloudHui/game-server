package utils.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.other.ClazzUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * 处理器类扫描器。
 * <p>
 * 支持根据包名或基类类型锚点扫描所有带指定注解的具体实现类。
 */
public final class ClassScanner {

    private static final Logger logger = LoggerFactory.getLogger(ClassScanner.class);

    private ClassScanner() {
    }

    /**
     * 根据基类所在的包及其子包扫描实现类。
     *
     * @param baseType       处理器基类或接口，同时作为扫描包名和类加载器的锚点
     * @param annotationType 处理器标注的注解
     * @param <T>            基类类型
     * @return 匹配的具体实现类列表
     */
    public static <T> List<Class<? extends T>> scan(Class<T> baseType, Class<? extends Annotation> annotationType) {
        if (baseType == null) {
            throw new IllegalArgumentException("baseType 不能为空");
        }
        return scan(baseType.getPackage().getName(), baseType, annotationType);
    }

    /**
     * 根据指定包名扫描匹配基类的实现类（无需注解过滤）。
     *
     * @param packageName 扫描包路径
     * @param baseType    处理器基类或接口
     * @param <T>         基类类型
     * @return 匹配的具体实现类列表
     */
    public static <T> List<Class<? extends T>> scan(String packageName, Class<T> baseType) {
        return scan(packageName, baseType, null);
    }

    /**
     * 根据指定包名扫描匹配基类与注解的实现类。
     *
     * @param packageName    扫描包路径
     * @param baseType       处理器基类或接口（为 null 或 Object.class 时不做类型过滤）
     * @param annotationType 处理器标注的注解（为 null 时不做注解过滤）
     * @param <T>            基类类型
     * @return 匹配的具体实现类列表
     */
    @SuppressWarnings("unchecked")
    public static <T> List<Class<? extends T>> scan(String packageName, Class<T> baseType, Class<? extends Annotation> annotationType) {
        List<Class<? extends T>> result = new ArrayList<>();
        if (packageName == null || packageName.isEmpty()) {
            return result;
        }

        try {
            List<Class<?>> classes = ClazzUtil.getClasses(packageName);
            for (Class<?> clazz : classes) {
                if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                    continue;
                }
                if (clazz.getName().contains("$")) {
                    continue;
                }
                if (baseType != null && baseType != Object.class && !baseType.isAssignableFrom(clazz)) {
                    continue;
                }
                if (annotationType == null || clazz.isAnnotationPresent(annotationType)) {
                    result.add((Class<? extends T>) clazz);
                }
            }
        } catch (Exception e) {
            logger.error("扫描包 [{}] 处理器类失败, baseType: {}, annotation: {}",
                    packageName, baseType != null ? baseType.getName() : "null",
                    annotationType != null ? annotationType.getName() : "none", e);
            throw new IllegalStateException("扫描处理器类失败: " + packageName, e);
        }
        return result;
    }
}

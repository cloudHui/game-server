package utils.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 处理器类扫描器。
 * <p>
 * 基于 Spring ResourcePatternResolver 与 ASM SimpleMetadataReaderFactory，
 * 在字节码元数据层面进行无侵入筛选，不触发目标类的静态初始化（static 块），
 * 严格支持目录与 JAR 包（classpath*: 模式）。
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
     * @return 匹配的具体实现类列表（去重且按类名排序）
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
     * 根据指定包名扫描匹配基类与注解的具体实现类。
     * <p>
     * 先通过 ASM 读取字节码元数据过滤接口、抽象类、内部类与无关注解，
     * 只有真正匹配的类才通过 Class.forName(name, false, loader) 加载。
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

        ClassLoader loader = (baseType != null && baseType.getClassLoader() != null)
                ? baseType.getClassLoader()
                : Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = ClassScanner.class.getClassLoader();
        }

        String pattern = "classpath*:" + packageName.replace('.', '/') + "/**/*.class";
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(loader);
        SimpleMetadataReaderFactory readers = new SimpleMetadataReaderFactory(loader);
        Set<String> matchedNames = new TreeSet<>();
        String currentSource = pattern;

        try {
            Resource[] resources = resolver.getResources(pattern);
            for (Resource resource : resources) {
                currentSource = resource.getDescription();
                MetadataReader reader = readers.getMetadataReader(resource);
                String className = reader.getClassMetadata().getClassName();

                // 排除内部类、接口、抽象类
                if (className.contains("$") || reader.getClassMetadata().isInterface() || reader.getClassMetadata().isAbstract()) {
                    continue;
                }

                // 若指定了注解，按注解全限定名快速过滤
                if (annotationType != null && !reader.getAnnotationMetadata().hasAnnotation(annotationType.getName())) {
                    continue;
                }

                matchedNames.add(className);
            }

            // 对通过字节码元数据预筛选的候选类，进行类型校验（不触发类静态初始化）
            for (String className : matchedNames) {
                currentSource = className;
                Class<?> candidate = Class.forName(className, false, loader);
                if (baseType == null || baseType == Object.class) {
                    result.add((Class<? extends T>) candidate);
                } else if (candidate != baseType && baseType.isAssignableFrom(candidate)) {
                    result.add(candidate.asSubclass(baseType));
                }
            }
        } catch (IOException | ClassNotFoundException | LinkageError e) {
            logger.error("扫描包 [{}] 处理器类失败, baseType: {}, annotation: {}, source: {}",
                    packageName, baseType != null ? baseType.getName() : "null",
                    annotationType != null ? annotationType.getName() : "none", currentSource, e);
            throw new IllegalStateException("处理器扫描失败: package=" + packageName + ", source=" + currentSource, e);
        }

        return result;
    }
}

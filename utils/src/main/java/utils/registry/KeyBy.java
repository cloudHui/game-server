package utils.registry;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标在注册注解上，声明该注解对应的 Key 解析器。
 * <p>
 * 使得注册中心无需硬编码感知具体业务注解，任何新注解只需内嵌解析器并加注此元注解即可。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface KeyBy {

    /**
     * Key 解析器类，必须提供公共无参构造函数。
     *
     * @return 解析器 Class
     */
    Class<? extends KeyResolver<?>> value();
}

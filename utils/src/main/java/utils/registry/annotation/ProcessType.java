package utils.registry.annotation;

import utils.registry.KeyBy;
import utils.registry.KeyResolver;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

/**
 * 消息 ID 处理器注册注解。
 * <p>
 * 支持绑定单个消息 ID 或多个消息 ID 数组，自带 {@link Resolver} 解析器。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@KeyBy(ProcessType.Resolver.class)
public @interface ProcessType {

    /**
     * 单个消息 ID（与 values 互斥，默认属性）
     *
     * @return 消息 ID
     */
    int value() default 0;

    /**
     * 多个消息 ID 数组（支持一个处理器类绑定多个消息）
     *
     * @return 消息 ID 数组
     */
    int[] values() default {};

    /**
     * Key 解析器实现
     */
    final class Resolver implements KeyResolver<ProcessType> {
        @Override
        public List<?> keys(ProcessType annotation, Class<?> handlerType) {
            List<Integer> keys = new ArrayList<>();
            if (annotation.values().length > 0) {
                for (int val : annotation.values()) {
                    keys.add(val);
                }
            } else if (annotation.value() != 0) {
                keys.add(annotation.value());
            }
            return keys;
        }
    }
}

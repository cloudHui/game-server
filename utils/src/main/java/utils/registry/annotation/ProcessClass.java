package utils.registry.annotation;

import utils.registry.KeyBy;
import utils.registry.KeyResolver;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.List;

/**
 * 消息类型 Class 处理器注册注解。
 * <p>
 * 绑定特定的 Protobuf 消息类型 Class，自带 {@link Resolver} 解析器。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@KeyBy(ProcessClass.Resolver.class)
public @interface ProcessClass {

    /**
     * 绑定的消息 Class 类型
     *
     * @return 消息 Class
     */
    Class<?> value() default Void.class;

    /**
     * Key 解析器实现
     */
    final class Resolver implements KeyResolver<ProcessClass> {
        @Override
        public List<?> keys(ProcessClass annotation, Class<?> handlerType) {
            return Collections.singletonList(annotation.value());
        }
    }
}

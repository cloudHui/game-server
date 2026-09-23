package utils.registry.annotation;

import utils.registry.KeyBy;
import utils.registry.KeyResolver;
import utils.registry.enums.TableState;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;

/**
 * 桌子状态机处理器注册注解。
 * <p>
 * 绑定一个或多个 {@link TableState} 状态，自带 {@link Resolver} 解析器。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@KeyBy(ProcessEnum.Resolver.class)
public @interface ProcessEnum {

    /**
     * 处理的桌子状态数组
     *
     * @return 状态数组
     */
    TableState[] value();

    /**
     * Key 解析器实现
     */
    final class Resolver implements KeyResolver<ProcessEnum> {
        @Override
        public List<?> keys(ProcessEnum annotation, Class<?> handlerType) {
            return Arrays.asList(annotation.value());
        }
    }
}

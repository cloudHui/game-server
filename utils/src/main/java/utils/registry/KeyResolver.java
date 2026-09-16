package utils.registry;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * 注解 Key 解析器接口。
 * <p>
 * 从处理器类上的注解中提取绑定的 Key 列表。实现类通常定义在对应注解文件内，并通过 {@link KeyBy} 关联。
 *
 * @param <A> 注解类型
 */
public interface KeyResolver<A extends Annotation> {

    /**
     * 解析处理器声明的 Key 列表。
     *
     * @param annotation  处理器类上的注解实例
     * @param handlerType 被标注的处理器类
     * @return 非空的 Key 列表，元素类型须与构建入口的 keyType 一致
     */
    List<?> keys(A annotation, Class<?> handlerType);
}

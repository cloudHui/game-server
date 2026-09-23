package utils.registry.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 消息常量类中消息 ID 与 Protobuf 消息类的绑定注解
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ClassField {

	/**
	 * 绑定的消息类信息
	 */
	Class<?> value();

	/**
	 * 描述
	 */
	String des() default "";
}

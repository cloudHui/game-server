package net.msg;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 统一消息处理注解
 * <p>
 * 可标注于 Controller/Handler 方法上，也可标注于类上。
 * 支持声明请求消息 ID、关联的应答消息 ID 以及批量透传/转发。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Msg {

    /**
     * 消息 ID
     */
    int value() default 0;

    /**
     * 消息 ID（与 value 等价）
     */
    int id() default 0;

    /**
     * 对应的响应消息 ID（可选）。
     * 若配置且处理方法直接返回 Message 实例，框架自动组装此 ack ID 回发给请求方。
     */
    int ack() default 0;

    /**
     * 描述信息，便于排查与日志审计
     */
    String desc() default "";

    /**
     * 批量透传/转发的消息 ID 列表（用于网关等透传场景）
     */
    int[] forward() default {};
}

package com.cloud.hub.web.handler;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * WebSocket 推送消息格式化方法映射注解。
 * <p>
 * <b>使用场景与调用关系：</b>
 * 标注在 {@link GameWsPushFormatter} 的各个 {@code formatXxx} 方法上，
 * 在类加载阶段被自动反射扫描并绑定，供 {@link GameWebSocketHandler} 收到 Gate 推送时
 * 根据 {@code msgId} 查找对应的 WebSocket action 与执行数据格式化。
 *
 * <p>新增推送类型时，仅需新增格式化方法并打上此注解，彻底免去手动维护注册表代码的冗余。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PushMapping {

    /**
     * 单个协议消息 ID（例如 GMsg.NOT_CARD）
     *
     * @return 协议消息 ID
     */
    int value() default 0;

    /**
     * 多个协议消息 ID（适用于多个消息共用同一格式化逻辑）
     *
     * @return 协议消息 ID 数组
     */
    int[] values() default {};

    /**
     * 前端 WebSocket action 字符串名称（例如 "notCard"）
     *
     * @return action 名称
     */
    String action();
}

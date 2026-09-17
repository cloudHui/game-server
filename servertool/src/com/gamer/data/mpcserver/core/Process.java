package com.gamer.data.mpcserver.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * MCP 命令定义：把命令名、说明、读写提示和参数名单与 Handler 放在同一处。
 *
 * 工具标题由命令名按 '_' 转 Title Case 生成，不再单独声明。
 * 数组参数写入 required / optional，由 Handler 自行解析字符串或数组。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Process {

    /**
     * 命令名，须与注册名一致。使用 value 可省略属性名。
     *
     * @return 命令名
     */
    String value();

    /**
     * 工具说明，展示给 MCP 客户端；CommandHandler 必填。
     *
     * @return 说明文本
     */
    String description() default "";

    /**
     * 是否只读。写命令须显式设为 false，以生成 destructiveHint。
     *
     * @return true 表示只读
     */
    boolean readOnly() default true;

    /**
     * 必填参数名。数组参数也写在这里。
     *
     * @return 必填参数名
     */
    String[] required() default {};

    /**
     * 可选参数名。
     *
     * @return 可选参数名
     */
    String[] optional() default {};
}

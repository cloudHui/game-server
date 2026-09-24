package com.cloud.hub.common.annotation;

import com.cloud.hub.common.enums.BusinessType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义操作日志记录注解。
 * <p>
 * 用于标记 Controller 接口或业务方法，由 AOP 切面拦截并自动将操作人、IP、请求参数、返回结果等
 * 持久化记录到数据库操作日志表中。
 * </p>
 *
 * @author cloud
 */
@Target({ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {

    /** 模块名称，例如 "用户管理"、"房间配置" */
    String title() default "";

    /** 功能业务类型，默认为 OTHER */
    BusinessType businessType() default BusinessType.OTHER;

    /** 是否保存请求的参数，默认保存 */
    boolean isSaveRequestData() default true;

    /** 是否保存响应的数据结果，默认不保存以减少日志冗余 */
    boolean isSaveResponseData() default false;
}


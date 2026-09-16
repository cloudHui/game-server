package com.cloud.hub.common.annotation;

import com.cloud.hub.common.enums.BusinessType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义操作日志记录注解（参照 RuoYi 设计）
 */
@Target({ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {
    /** 模块名称 */
    String title() default "";

    /** 功能类型 */
    BusinessType businessType() default BusinessType.OTHER;

    /** 是否保存请求的参数 */
    boolean isSaveRequestData() default true;

    /** 是否保存响应的参数 */
    boolean isSaveResponseData() default false;
}

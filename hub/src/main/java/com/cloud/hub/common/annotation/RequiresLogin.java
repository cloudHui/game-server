package com.cloud.hub.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用户登录认证注解。
 * <p>
 * 标注在类或方法上，要求当前请求上下文必须持有有效的用户凭证或会话 Session。
 * 若用户未登录，将直接拦截并提示重新登录。
 * </p>
 *
 * @author cloud
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresLogin {
}


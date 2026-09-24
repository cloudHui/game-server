package com.cloud.hub.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理员权限认证注解。
 * <p>
 * 标注在类或方法上，拦截器会检验当前会话用户是否具备管理员角色。
 * 若非管理员访问，将抛出无权限异常拒绝请求。
 * </p>
 *
 * @author cloud
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresAdmin {
}


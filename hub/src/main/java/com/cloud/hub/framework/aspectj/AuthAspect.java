package com.cloud.hub.framework.aspectj;

import com.cloud.hub.common.exception.ServiceException;
import com.cloud.hub.framework.security.SecurityUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 权限与登录状态校验切面（参照 RuoYi 设计）
 */
@Aspect
@Component
@Order(1)
public class AuthAspect {

    @Pointcut("@annotation(com.cloud.hub.common.annotation.RequiresLogin) || @within(com.cloud.hub.common.annotation.RequiresLogin)")
    public void requiresLoginPointcut() {
    }

    @Pointcut("@annotation(com.cloud.hub.common.annotation.RequiresAdmin) || @within(com.cloud.hub.common.annotation.RequiresAdmin)")
    public void requiresAdminPointcut() {
    }

    @Before("requiresLoginPointcut()")
    public void checkLogin(JoinPoint joinPoint) {
        if (SecurityUtils.getLoginUser() == null) {
            throw new ServiceException(401, "请先登录");
        }
    }

    @Before("requiresAdminPointcut()")
    public void checkAdmin(JoinPoint joinPoint) {
        if (SecurityUtils.getLoginUser() == null) {
            throw new ServiceException(401, "请先登录");
        }
        if (!SecurityUtils.isAdmin()) {
            throw new ServiceException(403, "需要管理员账号");
        }
    }
}

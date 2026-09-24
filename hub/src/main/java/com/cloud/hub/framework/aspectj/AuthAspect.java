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
 * 权限与登录状态统一切面处理器。
 * <p>
 * 拦截标注了 {@link com.cloud.hub.common.annotation.RequiresLogin} 或
 * {@link com.cloud.hub.common.annotation.RequiresAdmin} 注解的类与方法，
 * 在业务代码执行前检验当前线程会话是否合法，非法请求直接阻断抛出业务异常。
 * </p>
 *
 * @author cloud
 */
@Aspect
@Component
@Order(1)
public class AuthAspect {

    /**
     * 匹配类或方法上标注有 @RequiresLogin 注解的连接点。
     */
    @Pointcut("@annotation(com.cloud.hub.common.annotation.RequiresLogin) || @within(com.cloud.hub.common.annotation.RequiresLogin)")
    public void requiresLoginPointcut() {
    }

    /**
     * 匹配类或方法上标注有 @RequiresAdmin 注解的连接点。
     */
    @Pointcut("@annotation(com.cloud.hub.common.annotation.RequiresAdmin) || @within(com.cloud.hub.common.annotation.RequiresAdmin)")
    public void requiresAdminPointcut() {
    }

    /**
     * 校验登录态前置通知。
     *
     * @param joinPoint 切入点对象
     */
    @Before("requiresLoginPointcut()")
    public void checkLogin(JoinPoint joinPoint) {
        if (SecurityUtils.getLoginUser() == null) {
            throw new ServiceException(401, "请先登录");
        }
    }

    /**
     * 校验管理员权限前置通知。
     *
     * @param joinPoint 切入点对象
     */
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


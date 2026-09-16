package com.cloud.hub.framework.aspectj;

import com.cloud.hub.common.annotation.Log;
import com.cloud.hub.framework.security.LoginUser;
import com.cloud.hub.framework.security.SecurityUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 操作日志切面处理（参照 RuoYi 设计）
 */
@Aspect
@Component
@Order(2)
public class LogAspect {
    private static final Logger logger = LoggerFactory.getLogger(LogAspect.class);

    @Around("@annotation(logAnnotation)")
    public Object doAround(ProceedingJoinPoint joinPoint, Log logAnnotation) throws Throwable {
        long startTime = System.currentTimeMillis();
        HttpServletRequest request = getRequest();
        String uri = request != null ? request.getRequestURI() : "";
        String httpMethod = request != null ? request.getMethod() : "";
        String ip = request != null ? getClientIp(request) : "127.0.0.1";

        LoginUser user = SecurityUtils.getLoginUser();
        String username = user != null ? user.getUsername() : "anonymous";

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();

        Object[] args = joinPoint.getArgs();
        String paramStr = logAnnotation.isSaveRequestData() ? formatParams(args) : "[IGNORED]";

        Object result = null;
        Throwable exception = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            exception = t;
            throw t;
        } finally {
            long cost = System.currentTimeMillis() - startTime;
            if (exception != null) {
                logger.error("[操作日志 - 失败] 模块: '{}', 类型: {}, 用户: '{}', IP: {}, URI: [{}]{}, 方法: {}, 耗时: {}ms, 异常: {}",
                        logAnnotation.title(), logAnnotation.businessType(), username, ip, httpMethod, uri, methodName, cost, exception.getMessage());
            } else {
                logger.info("[操作日志 - 成功] 模块: '{}', 类型: {}, 用户: '{}', IP: {}, URI: [{}]{}, 方法: {}, 耗时: {}ms, 参数: {}",
                        logAnnotation.title(), logAnnotation.businessType(), username, ip, httpMethod, uri, methodName, cost, paramStr);
            }
        }
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "127.0.0.1";
    }

    private String formatParams(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        try {
            return Arrays.toString(args);
        } catch (Exception e) {
            return "[PARAMS_UNPRINTABLE]";
        }
    }
}

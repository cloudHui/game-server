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
 * 操作日志切面处理器。
 * <p>
 * 拦截标注了 {@link Log} 注解的方法，环绕统计执行耗时，提取客户端 IP、请求 URI、操作用户
 * 及入参参数，记录标准化结构化日志。
 * </p>
 *
 * @author cloud
 */
@Aspect
@Component
@Order(2)
public class LogAspect {

    private static final Logger logger = LoggerFactory.getLogger(LogAspect.class);

    /**
     * 环绕通知：统计执行时间并记录操作日志。
     *
     * @param joinPoint 切点对象
     * @param logAnnotation 日志注解元数据
     * @return 目标方法返回值
     * @throws Throwable 目标方法可能抛出的任意异常
     */
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

        Throwable exception = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            exception = t;
            throw t;
        } finally {
            long cost = System.currentTimeMillis() - startTime;
            if (exception != null) {
                printErrorLog(logAnnotation, username, ip, httpMethod, uri, methodName, cost, exception);
            } else {
                printSuccessLog(logAnnotation, username, ip, httpMethod, uri, methodName, cost, paramStr);
            }
        }
    }

    /**
     * 打印操作成功的结构化日志。
     */
    private void printSuccessLog(Log log, String user, String ip, String httpMethod,
                                 String uri, String method, long cost, String paramStr) {
        logger.info("[操作日志 - 成功] 模块: '{}', 类型: {}, 用户: '{}', IP: {}, URI: [{}]{}, 方法: {}, 耗时: {}ms, 参数: {}",
                log.title(), log.businessType(), user, ip, httpMethod, uri, method, cost, paramStr);
    }

    /**
     * 打印操作异常的结构化日志。
     */
    private void printErrorLog(Log log, String user, String ip, String httpMethod,
                               String uri, String method, long cost, Throwable ex) {
        logger.error("[操作日志 - 失败] 模块: '{}', 类型: {}, 用户: '{}', IP: {}, URI: [{}]{}, 方法: {}, 耗时: {}ms, 异常: {}",
                log.title(), log.businessType(), user, ip, httpMethod, uri, method, cost, ex.getMessage());
    }

    /**
     * 获取当前上下文绑定的 HTTP 请求对象。
     *
     * @return 当前 HttpServletRequest，非 Web 请求下返回 null
     */
    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    /**
     * 解析请求来源客户端真实 IP。
     *
     * @param request HTTP 请求
     * @return 客户端 IP 地址
     */
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

    /**
     * 格式化参数数组为安全字符串。
     *
     * @param args 参数列表
     * @return 格式化后的参数串
     */
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


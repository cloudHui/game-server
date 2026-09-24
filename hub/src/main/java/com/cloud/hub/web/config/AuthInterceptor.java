package com.cloud.hub.web.config;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.cloud.hub.framework.security.LoginUser;
import com.cloud.hub.framework.security.SecurityUtils;
import com.cloud.hub.web.identity.SessionResolver;
import com.cloud.hub.web.service.UserService;

/**
 * 访问控制与身份鉴权拦截器。
 * <p>
 * 保护 Web 页面与接口访问，并自动将登录态信息注入到当前线程的 {@link SecurityUtils} 上下文中。
 *
 * @author cloud
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final UserService userService;
    private final SessionResolver sessions;

    /**
     * 构造鉴权拦截器。
     *
     * @param userService 用户服务
     * @param sessions    会话解析器
     */
    public AuthInterceptor(UserService userService, SessionResolver sessions) {
        this.userService = userService;
        this.sessions = sessions;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String sessionId = sessions.resolve(request);
        UserService.UserInfo userInfo = findUserInfo(sessionId);

        if (userInfo != null) {
            bindLoginUser(userInfo);
            String uri = request.getRequestURI().substring(request.getContextPath().length());
            if (uri.startsWith("/actuator") && !userInfo.isAdmin()) {
                return rejectForbidden(response);
            }
            return true;
        }
        return rejectUnauthorized(request, response);
    }

    /**
     * 解析并获取用户信息。
     */
    private UserService.UserInfo findUserInfo(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        UserService.UserInfo userInfo = userService.getSession(sessionId);
        if (userInfo == null) {
            userInfo = userService.validateToken(sessionId);
        }
        return userInfo;
    }

    /**
     * 绑定登录上下文到当前线程。
     */
    private void bindLoginUser(UserService.UserInfo userInfo) {
        LoginUser loginUser = new LoginUser(
                userInfo.getUserId(),
                userInfo.getUsername(),
                userInfo.getNickname(),
                userInfo.getToken(),
                userInfo.getSessionId(),
                userInfo.isAdmin());
        SecurityUtils.setLoginUser(loginUser);
    }

    /**
     * 未登录请求拦截处理。
     */
    private boolean rejectUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        if (uri.startsWith("/api/") || uri.startsWith("/actuator")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"未授权访问，需要管理员权限\"}");
        } else {
            response.sendRedirect(request.getContextPath() + "/");
        }
        return false;
    }

    /**
     * 无权限请求拦截处理。
     */
    private boolean rejectForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"msg\":\"权限不足，仅管理员可访问\"}");
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        SecurityUtils.clear();
    }
}

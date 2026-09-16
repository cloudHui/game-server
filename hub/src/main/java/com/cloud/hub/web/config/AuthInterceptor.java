package com.cloud.hub.web.config;

import org.springframework.web.servlet.HandlerInterceptor;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.cloud.hub.web.identity.SessionResolver;
import com.cloud.hub.web.service.UserService;

import com.cloud.hub.framework.security.LoginUser;
import com.cloud.hub.framework.security.SecurityUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 保护页面与接口访问，并自动将登录态绑定到当前线程 SecurityUtils（参照 RuoYi 设计）。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final UserService userService;
    private final SessionResolver sessions;

    public AuthInterceptor(UserService userService, SessionResolver sessions) {
        this.userService = userService;
        this.sessions = sessions;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String sessionId = sessions.resolve(request);
        UserService.UserInfo userInfo = null;
        if (sessionId != null && !sessionId.isEmpty()) {
            userInfo = userService.getSession(sessionId);
            if (userInfo == null) {
                userInfo = userService.validateToken(sessionId);
            }
        }

        if (userInfo != null) {
            LoginUser loginUser = new LoginUser(
                    userInfo.getUserId(),
                    userInfo.getUsername(),
                    userInfo.getNickname(),
                    userInfo.getToken(),
                    userInfo.getSessionId(),
                    userInfo.isAdmin()
            );
            SecurityUtils.setLoginUser(loginUser);
            return true;
        }

        String uri = request.getRequestURI().substring(request.getContextPath().length());
        if (uri.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"请先登录\"}");
        } else {
            response.sendRedirect(request.getContextPath() + "/");
        }
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        SecurityUtils.clear();
    }
}

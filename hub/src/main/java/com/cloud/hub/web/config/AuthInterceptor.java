package com.cloud.hub.web.config;

import org.springframework.web.servlet.HandlerInterceptor;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.cloud.hub.web.identity.SessionResolver;
import com.cloud.hub.web.service.UserService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Protects browser pages and API endpoints before they reach controllers.
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
        if (sessionId != null && userService.getSession(sessionId) != null) {
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
}

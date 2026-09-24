package com.cloud.hub.web.identity;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * HTTP 请求会话凭据（Session Token）解析器。
 * <p>
 * 按优先级依序从 Authorization Bearer 头、X-Session-Token 头、URL 查询参数、Cookie 中提取会话标识。
 *
 * @author cloud
 */
@Component
public class SessionResolver {

    /**
     * 解析当前线程的会话 Token。
     *
     * @param explicitToken 显式传入的 Token（若有效则直接返回）
     * @return 解析出的会话 Token，未找到则返回 null
     */
    public String resolveCurrent(String explicitToken) {
        if (explicitToken != null && !explicitToken.trim().isEmpty()) {
            return explicitToken.trim();
        }
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : resolve(attributes.getRequest());
    }

    /**
     * 从指定 HTTP 请求中提取会话标识。
     *
     * @param request HTTP 请求
     * @return 会话标识字符串，未提取到返回 null
     */
    public String resolve(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        String header = request.getHeader("X-Session-Token");
        if (header != null && !header.trim().isEmpty()) {
            return header.trim();
        }
        String parameter = request.getParameter("sessionId");
        if (parameter != null && !parameter.trim().isEmpty()) {
            return parameter.trim();
        }
        return resolveFromCookies(request.getCookies());
    }

    /**
     * 从 Cookie 数组中提取名为 sessionId 的值。
     */
    private String resolveFromCookies(Cookie[] cookies) {
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if ("sessionId".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}

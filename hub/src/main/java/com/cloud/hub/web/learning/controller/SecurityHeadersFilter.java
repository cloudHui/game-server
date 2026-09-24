package com.cloud.hub.web.learning.controller;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 学习中心页面与 API 专属安全响应头注入过滤器。
 * <p>
 * 设置严格的 CSP、防嗅探、防点击劫持及权限策略，隔离并防止跨域风险。
 *
 * @author cloud
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.startsWith("/pages/learning") || path.startsWith("/api/learning"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; "
                        + "img-src 'self' data: blob:; media-src 'self' blob:; connect-src 'self'; "
                        + "object-src 'none'; base-uri 'self'; frame-ancestors 'none'");
        chain.doFilter(request, response);
    }
}

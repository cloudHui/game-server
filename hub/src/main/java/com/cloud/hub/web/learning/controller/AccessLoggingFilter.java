package com.cloud.hub.web.learning.controller;

import com.cloud.hub.web.learning.service.ClientIp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 学习中心 HTTP 访问日志记录过滤器。
 * <p>
 * 仅拦截以 /pages/learning 或 /api/learning 开头的路由，统计耗时并记录真实客户端 IP 与响应码。
 *
 * @author cloud
 */
@Component
public class AccessLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AccessLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.startsWith("/pages/learning") || path.startsWith("/api/learning"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            log.info("学习访问 method={}, path={}, status={}, durationMs={}, ip={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(),
                    System.currentTimeMillis() - started, ClientIp.from(request));
        }
    }
}

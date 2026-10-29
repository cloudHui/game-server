package com.cloud.hub.web.config;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 前端容错路径转发过滤器。
 * <p>
 * 兼容处理因浏览器旧缓存将 /pages/api/** 错当成完整路径的请求，
 * 自动重写并内部转发至正确的 /api/**，保证前后端平滑过渡。
 *
 * @author cloud
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PathCompatibilityFilter implements Filter {

    /** /pages 前缀长度，用于快速路径截取 */
    private static final int PAGES_PREFIX_LEN = "/pages".length();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String uri = req.getRequestURI();

        // 兼容处理：若浏览器由于旧缓存向 /pages/api/** 发送请求，内部转发到 /api/**，避免 404 或鉴权拦截
        if (uri.startsWith("/pages/api/")) {
            String forwardUri = uri.substring(PAGES_PREFIX_LEN);
            req.getRequestDispatcher(forwardUri).forward(request, response);
            return;
        }

        chain.doFilter(request, response);
    }
}


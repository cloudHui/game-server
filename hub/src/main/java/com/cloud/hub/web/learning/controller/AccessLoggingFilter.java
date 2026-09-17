package com.cloud.hub.web.learning.controller;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.cloud.hub.web.learning.service.ClientIp;

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

package com.cloud.weball.config;

import com.cloud.weball.web.config.AuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 应用配置
 */
@Configuration
public class AppConfig implements WebMvcConfigurer {

    @Value("${gate.host:127.0.0.1}")
    private String gateHost;

    @Value("${gate.port:5600}")
    private int gatePort;

    private final AuthInterceptor authInterceptor;

    public AppConfig(@Lazy AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/", "/index.html", "/api/auth/**", "/api/login",
                        "/api/capabilities",
                        "/app-base.js", "/favicon.ico", "/css/**", "/js/**", "/img/**",
                        "/shared/**", "/pages/learning/css/**", "/pages/learning/js/**",
                        "/ws/**");
    }
}

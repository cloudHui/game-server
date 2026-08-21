package com.cloud.hub.config;

import com.cloud.hub.web.config.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 应用配置
 */
@Configuration
public class AppConfig implements WebMvcConfigurer {

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

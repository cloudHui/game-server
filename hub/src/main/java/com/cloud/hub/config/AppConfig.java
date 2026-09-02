package com.cloud.hub.config;

import com.cloud.hub.web.config.AuthInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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

    /** 外部 ARPU 查询独立执行，避免上游超时占住 Web 请求线程。 */
    @Bean(name = "arpuExecutor")
    public ThreadPoolTaskExecutor arpuExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("arpu-query-");
        executor.initialize();
        return executor;
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

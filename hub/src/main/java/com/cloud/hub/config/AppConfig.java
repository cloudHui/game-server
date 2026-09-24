package com.cloud.hub.config;

import com.cloud.hub.web.config.AuthInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 应用核心配置类。
 * <p>
 * 实现 {@link WebMvcConfigurer} 接口，注册系统权限鉴权拦截器，并配置专用于外部数据（如 ARPU）
 * 查询的独立线程池，防止慢查询占满常规 Web 请求线程。
 * </p>
 *
 * @author cloud
 */
@Configuration
public class AppConfig implements WebMvcConfigurer {

    /** 权限认证拦截器（通过 @Lazy 延迟注入避免循环依赖） */
    private final AuthInterceptor authInterceptor;

    /**
     * 构造应用配置。
     *
     * @param authInterceptor 权限校验拦截器
     */
    public AppConfig(@Lazy AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    /**
     * 声明外部 ARPU 异步查询专用线程池。
     * <p>
     * 核心线程数 2，最大 8，队列 20，避免下游慢响应拖垮主业务线程。
     * </p>
     *
     * @return 线程池执行器
     */
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

    /**
     * 注册 MVC 拦截器并配置黑白名单路径规则。
     *
     * @param registry 拦截器注册中心
     */
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


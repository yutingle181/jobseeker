package com.jobseeker.config;

import com.jobseeker.ratelimit.RateLimitInterceptor;
import com.jobseeker.security.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // /agent/** 同样需要登录态：薄网关要按当前登录用户装配「岗位 + 简历」上下文，
        // 没有 UserContext 就无法判断数据归属（会退化为不注入，甚至越权）。
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**", "/agent/**")
                .excludePathPatterns(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/job-sites"
                );

        // 限流放在 auth 之后：已登录请求按 userId 限流，未登录按 IP。
        // 注意排除项只保留登录/注册（防爆破另有方案），
        // **不排除 /api/job-sites** —— 它免登录，但公开端点恰恰最该被保护。
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**", "/agent/**")
                .excludePathPatterns(
                        "/api/auth/register",
                        "/api/auth/login"
                );
    }
}

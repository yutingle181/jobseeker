package com.jobseeker.config;

import com.jobseeker.security.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

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
    }
}

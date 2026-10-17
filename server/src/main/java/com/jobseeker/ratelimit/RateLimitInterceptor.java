package com.jobseeker.ratelimit;

import com.jobseeker.common.UserContext;
import com.jobseeker.config.AgentProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 限流拦截器：超限返回 429 + Retry-After，让前端能提示「稍后再试」。
 *
 * <p>挂在 {@code AuthInterceptor} 之后（WebMvcConfig 的注册顺序即执行顺序），
 * 因此已登录请求能按 userId 维度限流；未登录（无 UserContext）按来源 IP。
 *
 * <p>两个刻意的选择：
 * <ul>
 *   <li><b>不把 /api/job-sites 排除在限流外</b>：它免登录（公开列表），
 *       但公开端点恰恰是最该被保护的——匿名 flood 打的都是它。
 *       排除的只有登录/注册（那是另一套防爆破策略，不在本组件范围）。</li>
 *   <li><b>限流命中记 INFO 不记 WARN</b>：限流是设计的正常工作路径，
 *       用 WARN 会在高峰期把日志刷满，真正的异常反而被埋掉。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter limiter;
    private final AgentProperties props;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        AgentProperties.RateLimit cfg = props.getRateLimit();
        if (!cfg.isEnabled()) {
            return true;
        }

        String key = cfg.isPerUser() && UserContext.get() != null
                ? "u:" + UserContext.get()
                : "ip:" + clientIp(request);
        boolean allowed = limiter.tryAcquire(key, cfg.getMaxRequests(), Duration.ofSeconds(cfg.getWindowSeconds()));
        if (allowed) {
            return true;
        }

        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(Math.max(1, cfg.getRetryAfterSeconds())));
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
        log.info("触发限流 | key={} | backend={} | {}/{}s", key, limiter.backend(),
                cfg.getMaxRequests(), cfg.getWindowSeconds());
        return false;
    }

    /** 取客户端 IP；经过反向代理时优先取 X-Forwarded-For 的第一个地址。 */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
            if (!first.isBlank()) {
                return first.trim();
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }
}

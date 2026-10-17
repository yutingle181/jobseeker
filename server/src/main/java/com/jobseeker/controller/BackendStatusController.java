package com.jobseeker.controller;

import com.jobseeker.cache.ChatCacheRouter;
import com.jobseeker.lock.LockRouter;
import com.jobseeker.ratelimit.RateLimitRouter;
import com.jobseeker.redis.RedisHealthProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 只读状态端点：当前三个能力实际跑在哪个后端上。
 *
 * <p>为什么需要它：缓存 / 锁 / 限流都会在 Redis 不可用时**自动降级**，
 * 而降级是静默的（请求照常成功）。不提供一个观察口，就会出现
 * 「以为在用分布式锁，其实早就降级成进程内锁」的情况 —— 而多实例部署下
 * 这两者的语义差别很大（进程内锁只保护本进程）。
 *
 * <p>与 /agent/gateway-metrics 的分工：那个看**流量与延迟**，这个看**能力形态**。
 */
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class BackendStatusController {

    private final ChatCacheRouter cache;
    private final LockRouter lock;
    private final RateLimitRouter rateLimit;
    private final RedisHealthProbe health;

    @GetMapping("/backends")
    public Map<String, Object> backends() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("redisEnabled", health.enabled());
        out.put("redisHealthy", health.isHealthy());
        out.put("cache", cache.activeBackend());
        out.put("lock", lock.backend());
        out.put("rateLimit", rateLimit.backend());
        return out;
    }
}

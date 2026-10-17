package com.jobseeker.ratelimit;

import com.jobseeker.config.AgentProperties;
import com.jobseeker.redis.RedisHealthProbe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 限流路由器：Redis 可用时跨实例统一计数，不可用时降级为进程内计数。
 *
 * <p>与缓存/锁的差别：限流失败不能「这次不限」——那正好发生在最需要保护的瞬间。
 * 所以降级方向是「改用进程内计数继续限」，只是多实例下总量会放宽（见 {@link LocalRateLimiter}）。
 */
@Slf4j
@Component
public class RateLimitRouter implements RateLimiter {

    private final LocalRateLimiter local = new LocalRateLimiter();
    private final RateLimiter redis;
    private final RedisHealthProbe health;

    private volatile RateLimiter active;

    public RateLimitRouter(AgentProperties props,
                           RedisHealthProbe health,
                           ObjectProvider<StringRedisTemplate> templateProvider) {
        this.health = health;
        StringRedisTemplate template = props.getRedis().isEnabled() ? templateProvider.getIfAvailable() : null;
        this.redis = template != null ? new RedisRateLimiter(template, props.getRateLimit().getKeyPrefix()) : null;

        if (this.redis == null) {
            this.active = local;
            log.info("限流后端 = 进程内（agent.redis.enabled=false，未建立 Redis 连接）");
        } else if (health.isHealthy()) {
            this.active = this.redis;
            log.info("限流后端 = Redis（跨实例统一计数）");
        } else {
            this.active = local;
            log.warn("限流后端 = 进程内（Redis 已启用但探活失败，已降级）");
        }
    }

    @Override
    public String backend() {
        return active == redis ? "redis" : "local";
    }

    @Override
    public boolean tryAcquire(String key, int maxRequests, Duration window) {
        health.tryRecoverIfNeeded();
        try {
            boolean allowed = active.tryAcquire(key, maxRequests, window);
            recoverIfBack();
            return allowed;
        } catch (RuntimeException e) {
            health.markUnavailable("限流");
            if (active == redis) {
                active = local;
                log.warn("限流已降级为进程内计数（本次仍限流，多实例下总量放宽）");
            }
            return local.tryAcquire(key, maxRequests, window);
        }
    }

    private void recoverIfBack() {
        if (active == local && redis != null && health.isHealthy()) {
            active = redis;
            log.info("限流已切回 Redis 后端");
        }
    }
}

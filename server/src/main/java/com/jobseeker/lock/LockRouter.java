package com.jobseeker.lock;

import com.jobseeker.config.AgentProperties;
import com.jobseeker.redis.RedisHealthProbe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 锁路由器：Redis 可用时用分布式锁，不可用时降级为进程内锁（**不会变成没有锁**）。
 *
 * <p>与缓存路由器的差别在于失败时的处理：缓存故障可以「这次不用缓存」，
 * 而锁故障不能「这次不加锁」就完了 —— 那会让击穿防护在最需要的时候失效。
 * 所以这里的降级是「改用进程内锁继续互斥」，并把降级事实告警出来。
 * 多实例部署下进程内锁只保护本进程，这一点在 {@link LocalLock} 的注释里写明，
 * 不假装它等于分布式锁。
 */
@Slf4j
@Component
public class LockRouter implements DistributedLock {

    private final LocalLock local = new LocalLock();
    private final DistributedLock redis;
    private final RedisHealthProbe health;
    private final String keyPrefix;

    private volatile DistributedLock active;

    public LockRouter(AgentProperties props,
                      RedisHealthProbe health,
                      ObjectProvider<StringRedisTemplate> templateProvider) {
        this.health = health;
        this.keyPrefix = props.getRedis().getKeyPrefix();
        StringRedisTemplate template = props.getRedis().isEnabled() ? templateProvider.getIfAvailable() : null;
        this.redis = template != null ? new RedisLock(template, keyPrefix) : null;

        if (this.redis == null) {
            this.active = local;
            log.info("分布式锁后端 = 进程内（agent.redis.enabled=false，未建立 Redis 连接）");
        } else if (health.isHealthy()) {
            this.active = this.redis;
            log.info("分布式锁后端 = Redis（跨实例互斥）");
        } else {
            this.active = local;
            log.warn("分布式锁后端 = 进程内（Redis 已启用但探活失败，已降级）");
        }
    }

    @Override
    public String backend() {
        return active == redis ? "redis" : "local";
    }

    @Override
    public <T> T executeWithLock(String key, Duration ttl, Duration wait, Supplier<T> action) {
        health.tryRecoverIfNeeded();
        try {
            return active.executeWithLock(key, ttl, wait, action);
        } catch (LockUnavailableException e) {
            // 锁基础设施坏了：降级到进程内锁继续互斥，不让请求失败
            health.markUnavailable("锁");
            if (active == redis) {
                active = local;
                log.warn("分布式锁已降级为进程内实现（本次仍保持互斥，多实例下仅本进程有效）");
            }
            return local.executeWithLock(key, ttl, wait, action);
        } finally {
            // 恢复后把后端切回去（放在 finally：无论这次成功与否，都该给下次一个机会）
            if (active == local && redis != null && health.isHealthy()) {
                active = redis;
                log.info("分布式锁已切回 Redis 实现");
            }
        }
    }
}

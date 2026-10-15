package com.jobseeker.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis 可用性探测：缓存 / 锁 / 限流三件套共用同一份「连不上就知道、恢复了知道」。
 *
 * <p>为什么单独一个组件：三个降级路由器都需要探活，如果各写一份，
 * 就会出现「缓存认为 Redis 活着、锁认为它死了」这种自相矛盾的状态，
 * 排查时先怀疑半天业务代码。共用一份，状态只有一处来源。
 *
 * <p>探活方式：PING + 写 + 读 + 删一个带 5 秒 TTL 的探针 key。
 * 不止 PING 的原因是：连得上但没写权限（只读副本 / ACL 配错）时 PING 会骗人，
 * 而缓存与锁都是写密集路径，必须确认「能写能读能删」才算可用。
 */
@Slf4j
@Component
public class RedisHealthProbe {

    private final StringRedisTemplate template;
    private final String keyPrefix;
    private final Duration interval;

    private final AtomicBoolean healthy = new AtomicBoolean(false);
    private final AtomicBoolean warned = new AtomicBoolean(false);
    private final AtomicLong lastProbeAt = new AtomicLong(0);

    public RedisHealthProbe(com.jobseeker.config.AgentProperties props,
                            ObjectProvider<StringRedisTemplate> templateProvider) {
        this.template = props.getRedis().isEnabled() ? templateProvider.getIfAvailable() : null;
        this.keyPrefix = props.getRedis().getKeyPrefix();
        this.interval = Duration.ofSeconds(Math.max(1, props.getRedis().getHealthCheckSeconds()));
        if (this.template != null) {
            this.healthy.set(ping());
        }
    }

    /** Redis 是否被启用（enabled=true 且 Bean 存在）。未启用时各路由器直接用进程内实现。 */
    public boolean enabled() {
        return template != null;
    }

    /** 当前是否健康。未启用时返回 false（语义：没有 Redis 可用）。 */
    public boolean isHealthy() {
        return template != null && healthy.get();
    }

    /** 当前后端名（redis / local），供日志与 /agent/gateway-metrics 展示。 */
    public String backend() {
        return isHealthy() ? "redis" : "local";
    }

    /** 立即探活一次（启动时与恢复判断用）。 */
    public boolean ping() {
        if (template == null) {
            return false;
        }
        try {
            // 注意：RedisCallback 在 org.springframework.data.redis.core 包，
            // 不是 .connection（那个包里是 RedisConnection）——两者名字太像，实测编译报错后才分清。
            if (template.execute((RedisCallback<String>) conn -> conn.ping()) == null) {
                return false;
            }
            String probeKey = keyPrefix + "health-probe";
            template.opsForValue().set(probeKey, "1", Duration.ofSeconds(5));
            String back = template.opsForValue().get(probeKey);
            template.delete(probeKey);
            return "1".equals(back);
        } catch (RuntimeException e) {
            log.debug("Redis 探活失败：{}: {}", e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    /**
     * 标记一次故障：同一次故障只告警一次（避免每个请求刷一条 WARN），
     * 恢复时自动复位，下次故障会再报。
     */
    public void markUnavailable(String what) {
        healthy.set(false);
        if (warned.compareAndSet(false, true)) {
            log.warn("Redis {} 失败，相关能力已降级为进程内实现（稍后自动尝试恢复）", what);
        }
    }

    /** 标记恢复：复位告警位并明确告知，避免「恢复了但没人知道」。 */
    public void markAvailable() {
        boolean was = healthy.getAndSet(true);
        warned.set(false);
        if (!was) {
            log.info("Redis 已恢复，相关能力切回 Redis 实现");
        }
    }

    /**
     * 按配置间隔尝试恢复；并发调用只放一个真探活（CAS 抢占）。
     * 未启用 / 已健康时立即返回，零开销。
     */
    public void tryRecoverIfNeeded() {
        if (template == null || healthy.get()) {
            return;
        }
        long now = System.nanoTime();
        long last = lastProbeAt.get();
        if (now - last < interval.toNanos() || !lastProbeAt.compareAndSet(last, now)) {
            return;
        }
        if (ping()) {
            markAvailable();
        }
    }
}

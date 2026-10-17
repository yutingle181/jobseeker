package com.jobseeker.cache;

import com.jobseeker.config.AgentProperties;
import com.jobseeker.redis.RedisHealthProbe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 缓存后端路由器：按配置与连通性在「Redis / 进程内」之间选择，并在 Redis 故障时自动降级。
 *
 * <p>这是「可选依赖不拖垮服务」的关键一层：
 * <ul>
 *   <li>Redis 未启用 → 直接用进程内缓存，**不尝试连接**（无连接失败噪音）；</li>
 *   <li>Redis 启用但连不上 → 启动即降级为进程内缓存，并明确告警，
 *       <b>不会</b>静默地「看起来有 Redis 其实没有」；</li>
 *   <li>运行中 Redis 挂掉 → 当次请求转交进程内缓存并告警（同一故障只告警一次）；</li>
 *   <li>Redis 恢复 → 探测成功后自动切回，并明确告知「已恢复」。</li>
 * </ul>
 *
 * <p>探活与降级状态统一由 {@link RedisHealthProbe} 提供（缓存 / 锁 / 限流共用一份），
 * 避免三个组件各判一次导致「缓存说 Redis 活着、锁说它死了」这种自相矛盾的状态。
 *
 * <p>降级的代价要说清楚：Redis 里的缓存条目不会搬到内存，所以切回之前那段时间命中率会偏低
 * （相当于冷启动）。这是**可用性优先**的取舍 —— 缓存 miss 只是多花一次 Agent 调用，
 * 而缓存故障拖垮整个 chat 接口是不可接受的。
 */
@Slf4j
@Component
public class ChatCacheRouter implements ChatCache {

    private final ChatCache memory;
    private final ChatCache redis;
    private final RedisHealthProbe health;

    private volatile ChatCache active;

    public ChatCacheRouter(AgentProperties props,
                           RedisHealthProbe health,
                           ObjectProvider<StringRedisTemplate> redisProvider) {
        // 进程内缓存**自己 new**，不再由 Spring 注入成 Bean。
        // 原因（2026-10-17 真机验证时暴露）：ChatResponseCache 原先挂着 @Component，
        // 而它也 implements ChatCache —— 于是容器里有两个 ChatCache bean，
        // AgentGatewayController 按接口注入时直接报 "required a single bean, but 2 were found" 起不来。
        // 进程内缓存是路由器的**内部实现细节**，暴露成 bean 只会带来歧义：
        // 以后每加一个 ChatCache 实现，都会再撞一次同样的错。
        this.memory = new ChatResponseCache(
                props.isChatCacheEnabled(), props.getChatCacheMaxSize(), props.getChatCacheTtlSeconds());
        this.health = health;
        StringRedisTemplate template = props.getRedis().isEnabled() ? redisProvider.getIfAvailable() : null;
        this.redis = template != null ? new RedisChatCache(template, props) : null;

        if (this.redis == null) {
            this.active = memory;
            log.info("响应缓存后端 = 进程内（agent.redis.enabled=false，未建立任何 Redis 连接）");
        } else if (health.isHealthy()) {
            this.active = this.redis;
            log.info("响应缓存后端 = Redis（跨实例共享，ttl={}s）", props.getChatCacheTtlSeconds());
        } else {
            this.active = memory;
            log.warn("响应缓存后端 = 进程内（Redis 已启用但探活失败，已降级；{}s 后自动重试恢复）",
                    props.getRedis().getHealthCheckSeconds());
        }
    }

    /** 当前生效后端名（redis / memory），供 /agent/gateway-metrics 与健康检查展示。 */
    public String activeBackend() {
        return active == redis ? "redis" : "memory";
    }

    @Override
    public boolean isEnabled() {
        return active.isEnabled();
    }

    @Override
    public String buildKey(String payload) {
        return ChatCacheSupport.buildKey(payload);
    }

    @Override
    public String get(String key) {
        health.tryRecoverIfNeeded();
        try {
            String value = active.get(key);
            recoverIfBack();
            return value;
        } catch (RuntimeException e) {
            health.markUnavailable("缓存读");
            return degradeAndGet(key);
        }
    }

    @Override
    public void put(String key, String value) {
        health.tryRecoverIfNeeded();
        try {
            active.put(key, value);
            recoverIfBack();
        } catch (RuntimeException e) {
            health.markUnavailable("缓存写");
            degradeAndPut(key, value);
        }
    }

    /** Redis 出故障：本次请求转交进程内缓存（值不会丢，只是不走 Redis）。 */
    private String degradeAndGet(String key) {
        if (active == redis) {
            active = memory;
        }
        return memory.get(key);
    }

    private void degradeAndPut(String key, String value) {
        if (active == redis) {
            active = memory;
        }
        memory.put(key, value);
    }

    /** Redis 恢复后把后端切回去（放在成功路径上，失败路径不增加负担）。 */
    private void recoverIfBack() {
        if (active == memory && redis != null && health.isHealthy()) {
            active = redis;
            log.info("响应缓存已切回 Redis 后端");
        }
    }
}

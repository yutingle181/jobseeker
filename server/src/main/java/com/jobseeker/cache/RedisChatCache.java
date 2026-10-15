package com.jobseeker.cache;

import com.jobseeker.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis 版响应缓存：跨实例共享，解决进程内缓存的根本局限（多实例各一份、重启即丢）。
 *
 * <p>设计取舍：
 * <ul>
 *   <li><b>不做空值缓存（防穿透）</b>：缓存值是「一次完整的 LLM 回答」而不是数据库行，
 *       未命中时真正的开销在 Agent 侧，而它由 {@code lock/} 的防击穿锁兜底
 *       （同一 key 并发只放一个去问 Agent）。再叠一层空值标记只会让语义复杂，
 *       收益却接近零 —— 这里选择不做，并把理由写清楚。</li>
 *   <li><b>TTL 沿用 chat-cache-ttl-seconds</b>：与内存版同一口径，切换后端后
 *       「缓存活多久」不变，监控数据可比。</li>
 *   <li><b>失败不吞</b>：Redis 读写异常直接抛出，由 {@link ChatCacheRouter} 捕获并降级，
 *       不在这里静默返回 null —— 静默会把「缓存坏了」伪装成「没命中」。</li>
 * </ul>
 */
@Slf4j
public class RedisChatCache implements ChatCache {

    private final StringRedisTemplate redis;
    private final String keyPrefix;
    private final Duration ttl;

    public RedisChatCache(StringRedisTemplate redis, AgentProperties props) {
        this.redis = redis;
        this.keyPrefix = props.getRedis().getKeyPrefix();
        this.ttl = Duration.ofSeconds(Math.max(1, props.getChatCacheTtlSeconds()));
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String buildKey(String payload) {
        return ChatCacheSupport.buildKey(payload);
    }

    @Override
    public String get(String key) {
        return redis.opsForValue().get(keyPrefix + key);
    }

    @Override
    public void put(String key, String value) {
        // 用 Duration 重载而不是 (millis, TimeUnit)：少一次单位换算，也不给调用方留"传错单位"的口子
        redis.opsForValue().set(keyPrefix + key, value, ttl);
    }
}

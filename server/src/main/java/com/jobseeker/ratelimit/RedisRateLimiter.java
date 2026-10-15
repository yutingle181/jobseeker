package com.jobseeker.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collections;

/**
 * Redis 版限流：一段 Lua 同时完成「计数 + 首次设 TTL + 超限判断」。
 *
 * <p>为什么必须用 Lua 而不是「INCR 后再 EXPIRE」两步：
 * 两步之间进程崩掉（或 Redis 主从切换）时，key 会**没有 TTL**——
 * 于是这个 key 永远停留在超限状态，该用户被永久限流，只能等人工删 key。
 * Lua 把三件事变成一个原子操作，从根上消掉这个坑。
 *
 * <p>脚本返回 1=放行，0=超限；调用方据此决定是否返回 429。
 */
@Slf4j
public class RedisRateLimiter implements RateLimiter {

    private static final RedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "if c > tonumber(ARGV[2]) then return 0 else return 1 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final String keyPrefix;

    public RedisRateLimiter(StringRedisTemplate redis, String keyPrefix) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public String backend() {
        return "redis";
    }

    @Override
    public boolean tryAcquire(String key, int maxRequests, Duration window) {
        long windowSeconds = Math.max(1, window.toSeconds());
        Long allowed = redis.execute(
                ACQUIRE_SCRIPT,
                Collections.singletonList(keyPrefix + key),
                String.valueOf(windowSeconds),
                String.valueOf(Math.max(1, maxRequests)));
        // 脚本返回 null 视为异常（连接失败等）：抛出让上层降级，不当作"放行"
        if (allowed == null) {
            throw new IllegalStateException("限流脚本未返回结果");
        }
        return allowed == 1L;
    }
}

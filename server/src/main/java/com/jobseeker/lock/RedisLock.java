package com.jobseeker.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Redis 版分布式锁：{@code SET key value NX EX ttl} 取锁，Lua 比较 value 后 DEL 释放。
 *
 * <p>为什么释放必须走 Lua：`GET` 比对 + `DEL` 分两步执行时，若锁在两步之间自然过期，
 * 而另一个请求刚好拿到同一把锁，我们的 DEL 就会**删掉别人的锁**（经典误删）。
 * Lua 脚本把「比对 + 删除」变成一个原子操作，从根上避免这个坑。
 *
 * <p>为什么 value 用 UUID：标识「这把锁是谁拿的」，是上面那个比对的前提。
 * 没有 owner 标识的锁（`DEL key` 一把梭）在过期瞬间就会误删。
 */
@Slf4j
public class RedisLock implements DistributedLock {

    /** 比对 owner 并删除：返回 1 表示确实删掉了自己加的锁。 */
    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final String keyPrefix;

    public RedisLock(StringRedisTemplate redis, String keyPrefix) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public String backend() {
        return "redis";
    }

    @Override
    public <T> T executeWithLock(String key, Duration ttl, Duration wait, Supplier<T> action) {
        String fullKey = keyPrefix + "lock:" + key;
        String owner = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + wait.toNanos();
        Duration ttlDuration = Duration.ofSeconds(Math.max(1, ttl.toSeconds()));

        boolean acquired = false;
        while (!acquired) {
            try {
                acquired = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(fullKey, owner, ttlDuration));
            } catch (RuntimeException e) {
                // 取锁阶段就失败：不猜「到底拿没拿到」，直接交给上层降级处理
                throw new LockUnavailableException("Redis 取锁失败", e);
            }
            if (acquired) {
                break;
            }
            if (System.nanoTime() >= deadline) {
                return null; // 等不到：按语义返回 null，由调用方决定
            }
            sleep(20);
        }

        try {
            return action.get();
        } finally {
            release(fullKey, owner);
        }
    }

    /** Lua 原子释放；失败只记日志（锁会随 TTL 自然过期，不会永久卡住后续请求）。 */
    private void release(String fullKey, String owner) {
        try {
            redis.execute(UNLOCK_SCRIPT, Collections.singletonList(fullKey), owner);
        } catch (RuntimeException e) {
            log.warn("Redis 释放锁失败（将依赖 TTL 过期）：{}", e.getMessage());
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockUnavailableException("等待锁期间被中断", e);
        }
    }
}

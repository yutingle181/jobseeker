package com.jobseeker.lock;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 进程内锁：Redis 不可用时的降级实现。
 *
 * <p>诚实的边界：它只保证**同一进程内**互斥。多实例部署时，两个实例的进程内锁互不可见，
 * 因此这时的「互斥」是退化的。选择保留它而不是「没有锁」，是因为：
 * 单实例（本机开发 / 演示）下它确实解决击穿；多实例下 Redis 恢复后自动切回真分布式锁。
 * 若明确是多实例且 Redis 长期不可用，应在监控上看到告警并修 Redis，而不是假装锁还有效。
 *
 * <p>TTL 在这里没有意义：进程内锁靠 {@code try/finally} 保证释放，不存在「持锁进程崩掉
 * 导致死锁」的问题（进程没了锁也就没了）。这与 Redis 版必须靠 TTL 兜底是本质差异。
 */
public class LocalLock implements DistributedLock {

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public String backend() {
        return "local";
    }

    @Override
    public <T> T executeWithLock(String key, Duration ttl, Duration wait, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        boolean acquired;
        try {
            acquired = lock.tryLock(Math.max(0, wait.toMillis()), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockUnavailableException("等待锁期间被中断", e);
        }
        if (!acquired) {
            return null;
        }
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}

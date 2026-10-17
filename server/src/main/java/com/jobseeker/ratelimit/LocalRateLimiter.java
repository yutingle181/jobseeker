package com.jobseeker.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内限流：Redis 不可用时的降级实现（固定窗口）。
 *
 * <p>诚实的边界：多实例部署时每个实例各自计数，实际总放行约为 maxRequests × 实例数。
 * 选择保留它而不是「没有限流」，是因为单实例（本机开发 / 演示）下它确实起作用；
 * 多实例下 Redis 恢复后会自动切回。若明确多实例且 Redis 长期不可用，
 * 应当从监控上看到 backend()=local 并去修 Redis，而不是假装限流还严格。
 *
 * <p>实现要点：用 {@code compute} 做「读-改-写」原子化，避免并发下丢计数
 * （两个线程同时读到 count=59 各加一，实际应该是 61 但会写成 60）。
 */
public class LocalRateLimiter implements RateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public String backend() {
        return "local";
    }

    @Override
    public boolean tryAcquire(String key, int maxRequests, Duration window) {
        long windowMs = Math.max(1, window.toMillis());
        long now = System.currentTimeMillis();
        long[] count = new long[1];
        windows.compute(key, (k, w) -> {
            if (w == null || now - w.startedAt >= windowMs) {
                count[0] = 1;
                return new Window(now, 1);
            }
            w.count += 1;
            count[0] = w.count;
            return w;
        });
        return count[0] <= Math.max(1, maxRequests);
    }

    private static final class Window {
        final long startedAt;
        int count;

        Window(long startedAt, int count) {
            this.startedAt = startedAt;
            this.count = count;
        }
    }
}

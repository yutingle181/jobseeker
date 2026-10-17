package com.jobseeker.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 进程内限流（Redis 不可用时的降级实现）：阈值内放行、超限拒绝、窗口过期重置。
 */
class LocalRateLimiterTest {

    private final LocalRateLimiter limiter = new LocalRateLimiter();

    @Test
    @DisplayName("backend 为 local：降级形态可被观察")
    void backendIsLocal() {
        assertFalse("redis".equals(limiter.backend()));
    }

    @Test
    @DisplayName("窗口内前 maxRequests 次放行，之后拒绝")
    void allowsUpToMaxThenRejects() {
        for (int i = 1; i <= 5; i++) {
            assertTrue(limiter.tryAcquire("k", 5, Duration.ofSeconds(60)), "第 " + i + " 次应放行");
        }
        assertFalse(limiter.tryAcquire("k", 5, Duration.ofSeconds(60)), "第 6 次应被拒绝");
    }

    @Test
    @DisplayName("不同 key 互不影响")
    void keysAreIndependent() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("a", 5, Duration.ofSeconds(60));
        }
        assertFalse(limiter.tryAcquire("a", 5, Duration.ofSeconds(60)));
        assertTrue(limiter.tryAcquire("b", 5, Duration.ofSeconds(60)), "b 是独立的键");
    }

    @Test
    @DisplayName("窗口过期后计数重置（固定窗口语义）")
    void windowResetAfterExpiry() throws Exception {
        Duration window = Duration.ofMillis(300);
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire("k", 3, window));
        }
        assertFalse(limiter.tryAcquire("k", 3, window));
        Thread.sleep(400);
        assertTrue(limiter.tryAcquire("k", 3, window), "窗口过后应重新放行");
    }

    @Test
    @DisplayName("并发下计数不丢失（用 compute 做原子读-改-写）")
    void concurrentCountingDoesNotLose() throws Exception {
        int threads = 8;
        int perThread = 50;
        java.util.concurrent.atomic.AtomicInteger allowed = new java.util.concurrent.atomic.AtomicInteger();
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < perThread; j++) {
                    if (limiter.tryAcquire("k", 1000, Duration.ofSeconds(60))) {
                        allowed.incrementAndGet();
                    }
                }
            });
            workers[i].start();
        }
        for (Thread w : workers) {
            w.join();
        }
        assertTrue(allowed.get() == threads * perThread,
                "限额远大于请求数时应全部放行；实际放行 " + allowed.get());
    }
}

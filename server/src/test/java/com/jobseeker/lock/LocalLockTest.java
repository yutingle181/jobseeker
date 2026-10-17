package com.jobseeker.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 进程内锁（Redis 不可用时的降级实现）：互斥、等待超时、finally 释放。
 */
class LocalLockTest {

    private final LocalLock lock = new LocalLock();

    @Test
    @DisplayName("backend 为 local：降级形态可被观察")
    void backendIsLocal() {
        assertEquals("local", lock.backend());
    }

    @Test
    @DisplayName("拿到锁就执行并返回结果；执行完立即释放（可再次获取）")
    void acquiresAndReleases() {
        String out = lock.executeWithLock("k", Duration.ofSeconds(5), Duration.ofMillis(200), () -> "ok");
        assertEquals("ok", out);
        // 能再次拿到 → 上一次确实释放了
        assertEquals("ok2", lock.executeWithLock("k", Duration.ofSeconds(5), Duration.ofMillis(200), () -> "ok2"));
    }

    @Test
    @DisplayName("动作抛异常时锁仍然释放（不会把后续请求永久挡住）")
    void releasesOnException() {
        assertThrows(RuntimeException.class, () ->
                lock.executeWithLock("k", Duration.ofSeconds(5), Duration.ofMillis(200), () -> {
                    throw new RuntimeException("boom");
                }));
        assertEquals("after", lock.executeWithLock("k", Duration.ofSeconds(5), Duration.ofMillis(200), () -> "after"));
    }

    @Test
    @DisplayName("等不到锁返回 null（调用方自行决定无锁直走或放弃）")
    void returnsNullWhenCannotAcquire() throws Exception {
        AtomicInteger inside = new AtomicInteger();
        Thread holder = new Thread(() -> lock.runWithLock("k", Duration.ofSeconds(5), Duration.ofSeconds(2), () -> {
            inside.set(1);
            try {
                Thread.sleep(600);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }));
        holder.start();
        while (inside.get() == 0) {
            Thread.sleep(10);
        }
        // 持锁期间另一个线程短等不到 → null
        assertNull(lock.executeWithLock("k", Duration.ofSeconds(5), Duration.ofMillis(50), () -> "x"));
        holder.join();
        assertTrue(inside.get() == 1);
    }

    @Test
    @DisplayName("并发下同一把锁真正互斥（临界区计数自增不丢失）")
    void mutualExclusionUnderConcurrency() throws Exception {
        int threads = 8;
        int perThread = 200;
        AtomicInteger counter = new AtomicInteger();
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < perThread; j++) {
                    lock.runWithLock("counter", Duration.ofSeconds(5), Duration.ofSeconds(5), counter::incrementAndGet);
                }
            });
            workers[i].start();
        }
        for (Thread w : workers) {
            w.join();
        }
        assertEquals(threads * perThread, counter.get(), "有锁保护时计数不应丢失（无锁则几乎必然小于该值）");
    }
}

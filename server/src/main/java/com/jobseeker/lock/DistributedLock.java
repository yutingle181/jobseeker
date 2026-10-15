package com.jobseeker.lock;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 分布式锁抽象：跨实例的「同一时刻只有一个请求在执行某段临界区」。
 *
 * <p>第一个真实用途：网关 chat 的**缓存击穿（stampede）**防护 ——
 * 相同 query 的并发请求同时 miss 时会一起打到 Agent，加锁 + 双重检查后
 * 只有一个去问 Agent，其余等它回写缓存后直接命中。
 *
 * <p>语义边界（诚实说明，别让使用者误解）：
 * <ul>
 *   <li>提供的是「互斥」，**不是** exactly-once，也不是红锁（Redlock）：
 *       单机 Redis + 演示规模下，红锁的代价（多实例、时钟漂移）大于收益；</li>
 *   <li>锁**没有看门狗续期**：临界区必须明显短于 ttl。若临界区可能超过 ttl，
 *       要么拆小临界区，要么换带续期的实现 —— 这里选择不实现，并在注释里写明；</li>
 *   <li>Redis 不可用时由 {@link LockRouter} 降级为进程内锁（仍互斥，但只在本进程内有效），
 *       <b>不会</b>静默变成「没有锁」。</li>
 * </ul>
 */
public interface DistributedLock {

    /** 当前后端名（redis / local），供日志与 /agent/gateway-metrics 展示。 */
    String backend();

    /**
     * 在锁保护下执行临界区，返回其结果；**拿不到锁时返回 null**（调用方自行决定
     * 是无锁直走还是放弃）。动作抛出的异常会原样向上传播，锁保证被释放。
     *
     * @param key      锁 key（调用方传业务键，如缓存 key）
     * @param ttl      锁自动过期时间；临界区必须远短于它
     * @param wait     最多等待多久（拿不到就返回 null）
     * @param action   临界区动作
     */
    <T> T executeWithLock(String key, Duration ttl, Duration wait, Supplier<T> action);

    /** {@link #executeWithLock} 的无返回值版本。 */
    default void runWithLock(String key, Duration ttl, Duration wait, Runnable action) {
        executeWithLock(key, ttl, wait, () -> {
            action.run();
            return null;
        });
    }
}

package com.jobseeker.ratelimit;

import java.time.Duration;

/**
 * 接口限流抽象：固定窗口内每个 key 允许 maxRequests 次，超出即拒绝。
 *
 * <p>用途：网关是对 Agent（外部 LLM 配额）的入口，单个用户把并发打满会拖垮所有人，
 * 也很快烧光免费额度。限流是「保护共享资源」而不是「惩罚用户」——
 * 所以拒绝时要给明确的 429 + Retry-After，让前端能提示「稍后再试」。
 *
 * <p>语义边界：
 * <ul>
 *   <li>**固定窗口**，不是滑动窗口/令牌桶：实现在 Redis 侧只需一次 INCR，成本最低；
 *       代价是窗口边界可能出现两倍突发（前一半窗口打满 + 后一半窗口立刻再打满）。
 *       演示规模可接受，若要严格可用令牌桶替换实现，接口不变。</li>
 *   <li>**近似限流**：Redis 故障降级为进程内计数时，多实例下每个实例各自计数，
 *       实际总放行可能是 N 倍。这一点在实现里写明，监控上看 backend() 可知当前形态。</li>
 * </ul>
 */
public interface RateLimiter {

    /** 当前后端名（redis / local），供 /agent/gateway-metrics 展示。 */
    String backend();

    /**
     * 尝试放行一次请求。
     *
     * @param key         限流键（如 "u:123" 或 "ip:10.0.0.1"）
     * @param maxRequests 窗口内最大次数
     * @param window      窗口长度
     * @return true=放行；false=已超限（调用方返回 429）
     */
    boolean tryAcquire(String key, int maxRequests, Duration window);
}

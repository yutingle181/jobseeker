package com.jobseeker.metrics;

import com.jobseeker.config.AgentProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网关端到端监控基线（9.2.D）。
 *
 * 设计取舍：
 * - **零依赖**：仅用 JDK 的 ConcurrentLinkedDeque + AtomicLong，不引入 actuator/micrometer，
 *   与响应缓存、user_context 裁剪任务保持一致的「薄网关、零新增依赖」原则。
 * - **只记真实 Agent 时延**：缓存 HIT 分支仅累加 cacheHit，不记 chat/stream 耗时，
 *   避免本地 replay / 缓存直返污染 P95，使基线真实反映 Agent 侧开销（与 9.1 结论一致）。
 * - **聚合在读取时发生**：每条记录 O(1) 入队（有界窗口超出淘汰最旧），snapshot() 才排序算分位，
 *   调试/压测读取低频，对热点请求路径零影响。
 * - **近似基线而非长期存储**：默认滑动窗口 maxSamples 条，内存恒定；文档如实说明这是基线而非持久指标。
 * - **开关可控**：metricsEnabled=false 时埋点跳过记录，且 /agent/gateway-metrics 返回 enabled=false。
 */
@Component
public class GatewayMetrics {

    private final boolean enabled;
    private final int maxSamples;

    private final ConcurrentLinkedDeque<Long> chatTotal = new ConcurrentLinkedDeque<>();
    private final AtomicInteger chatTotalCount = new AtomicInteger();

    private final ConcurrentLinkedDeque<Long> streamTtft = new ConcurrentLinkedDeque<>();
    private final AtomicInteger streamTtftCount = new AtomicInteger();

    private final ConcurrentLinkedDeque<Long> streamTotal = new ConcurrentLinkedDeque<>();
    private final AtomicInteger streamTotalCount = new AtomicInteger();

    private final AtomicLong cacheHit = new AtomicLong();
    private final AtomicLong cacheMiss = new AtomicLong();

    /** 工具调用次数（累计，跨轮）与每次工具耗时（用于算分布）。 */
    private final AtomicLong toolCallCount = new AtomicLong();
    private final AtomicLong toolTurnCount = new AtomicLong();
    private final ConcurrentLinkedDeque<Long> toolDurations = new ConcurrentLinkedDeque<>();
    private final AtomicInteger toolDurationCount = new AtomicInteger();

    public GatewayMetrics(AgentProperties props) {
        this.enabled = props.isMetricsEnabled();
        this.maxSamples = props.getMetricsMaxSamples();
    }

    /** 记录非流式总耗时（仅缓存 miss 调用）。 */
    public void recordChat(long totalMs) {
        if (!enabled) {
            return;
        }
        boundOffer(chatTotal, chatTotalCount, totalMs);
    }

    /** 记录流式首 token 延迟与总耗时（仅缓存 miss 调用）。 */
    public void recordStream(long ttftMs, long totalMs) {
        if (!enabled) {
            return;
        }
        boundOffer(streamTtft, streamTtftCount, ttftMs);
        boundOffer(streamTotal, streamTotalCount, totalMs);
    }

    /** 记录缓存命中情况（HIT 不记耗时，MISS 记 Agent 时延）。 */
    public void recordCache(boolean hit) {
        if (!enabled) {
            return;
        }
        if (hit) {
            cacheHit.incrementAndGet();
        } else {
            cacheMiss.incrementAndGet();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 记录一次流式回答中的工具调用（Function Calling 自治程度）。
     *
     * 只记录**次数与耗时**，不记录工具名、查询原文与返回内容——前者用于观察
     * 「模型是否真的在用工具、用了多久」，后者属于用户数据，不入指标。
     * 与 {@link #recordCache(boolean)} 一致：缓存 HIT 的 replay 也照常计数，
     * 因为它确实调用过工具，不算延迟但算次数。
     *
     * @param durationsMs 本轮每个工具调用的耗时；空列表表示本轮未调用工具
     */
    public void recordToolCalls(List<Long> durationsMs) {
        if (!enabled) {
            return;
        }
        toolTurnCount.incrementAndGet();
        if (durationsMs == null || durationsMs.isEmpty()) {
            return;
        }
        toolCallCount.addAndGet(durationsMs.size());
        for (Long d : durationsMs) {
            if (d != null) {
                boundOffer(toolDurations, toolDurationCount, Math.max(0, d));
            }
        }
    }

    /** 读时排序算分位，返回不可变快照；调用低频，不影响主流程。 */
    public MetricsSnapshot snapshot() {
        Stat chat = compute(chatTotal, chatTotalCount);
        StreamStat stream = new StreamStat(
                streamTtftCount.get(),
                avg(streamTtft),
                percentile(copySorted(streamTtft), 0.50),
                percentile(copySorted(streamTtft), 0.95),
                avg(streamTotal),
                percentile(copySorted(streamTotal), 0.50),
                percentile(copySorted(streamTotal), 0.95));
        long hit = cacheHit.get();
        long miss = cacheMiss.get();
        long total = hit + miss;
        CacheStat cache = new CacheStat(hit, miss, total == 0 ? 0.0 : (double) hit / total);

        List<Long> sortedTools = copySorted(toolDurations);
        long turns = toolTurnCount.get();
        long calls = toolCallCount.get();
        ToolStat tools = new ToolStat(
                calls,
                toolDurationCount.get(),
                avg(toolDurations),
                percentile(sortedTools, 0.50),
                percentile(sortedTools, 0.95),
                turns == 0 ? 0.0 : (double) calls / turns);
        return new MetricsSnapshot(enabled, chat, stream, cache, tools);
    }

    private void boundOffer(ConcurrentLinkedDeque<Long> d, AtomicInteger c, long v) {
        d.offer(v);
        int n = c.incrementAndGet();
        while (n > maxSamples) {
            d.pollFirst();
            n = c.decrementAndGet();
        }
    }

    private static List<Long> copySorted(ConcurrentLinkedDeque<Long> d) {
        List<Long> list = new ArrayList<>(d);
        Collections.sort(list);
        return list;
    }

    private static double avg(ConcurrentLinkedDeque<Long> d) {
        if (d.isEmpty()) {
            return 0.0;
        }
        long sum = 0;
        for (long v : d) {
            sum += v;
        }
        return (double) sum / d.size();
    }

    private static Stat compute(ConcurrentLinkedDeque<Long> d, AtomicInteger count) {
        List<Long> sorted = copySorted(d);
        return new Stat(count.get(), avg(d), percentile(sorted, 0.50), percentile(sorted, 0.95));
    }

    private static long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.ceil(p * sorted.size()) - 1;
        if (idx < 0) {
            idx = 0;
        }
        if (idx >= sorted.size()) {
            idx = sorted.size() - 1;
        }
        return sorted.get(idx);
    }

    /** 单端点统计快照（非流式：总耗时）。 */
    public record Stat(long count, double avgMs, long p50Ms, long p95Ms) {
    }

    /** 流式端点统计快照（首 token 延迟 + 总耗时 两套分位）。 */
    public record StreamStat(long count, double ttftAvgMs, long ttftP50Ms, long ttftP95Ms,
                            double totalAvgMs, long totalP50Ms, long totalP95Ms) {
    }

    /** 缓存命中统计。 */
    public record CacheStat(long hit, long miss, double rate) {
    }

    /**
     * 工具调用统计（新增字段，不改动既有 TTFT / P95 / 缓存命中率语义）。
     *
     * @param callCount 累计工具调用次数
     * @param samples   参与耗时统计的样本数
     * @param avgMs     单次工具调用平均耗时
     * @param p50Ms     单次工具调用耗时 P50
     * @param p95Ms     单次工具调用耗时 P95
     * @param callsPerTurn 平均每轮回答的工具调用次数（0 表示模型选择不调用工具）
     */
    public record ToolStat(long callCount, long samples, double avgMs, long p50Ms, long p95Ms,
                           double callsPerTurn) {
    }

    /** 整体快照（tools 追加在末尾，既有字段与顺序保持不变，向后兼容）。 */
    public record MetricsSnapshot(boolean enabled, Stat chat, StreamStat chatStream, CacheStat cache,
                                 ToolStat tools) {
    }
}

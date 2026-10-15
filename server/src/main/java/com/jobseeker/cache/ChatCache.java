package com.jobseeker.cache;

/**
 * 网关层响应缓存抽象（相同 query + user_context 命中即返回，省掉整轮 Agent 调用）。
 *
 * <p>存在的理由：后端要在「有 Redis」与「没装 Redis」两种部署形态下都能工作，
 * 而两者的实现差异（进程内 LRU / Redis TTL）不该漏到 Controller。Controller 只依赖这个接口，
 * 具体用哪个实现由 {@link ChatCacheRouter} 在启动时按配置与连通性决定，运行中失败时自动切换。
 *
 * <p>方法集合刻意与原先的 {@link ChatResponseCache} 完全一致（{@code isEnabled / buildKey / get / put}），
 * 因此网关控制器可以零改动地换依赖类型。
 */
public interface ChatCache {

    /** 当前生效的后端是否启用。禁用时 Controller 直接跳过缓存逻辑（不走 get/put）。 */
    boolean isEnabled();

    /**
     * 由请求报文生成稳定 key。
     *
     * <p>两个实现必须产生**完全相同**的 key：否则从 Redis 切到内存（或反过来）时，
     * 同一请求的命中率会凭空变化，监控数据不可比。因此实现都委托 {@link ChatCacheSupport}。
     */
    String buildKey(String payload);

    /** 命中且未过期返回值，否则返回 null（调用方随后走真实 Agent 调用）。 */
    String get(String key);

    /** 回写成功响应；实现自行决定存储与淘汰策略。 */
    void put(String key, String value);
}

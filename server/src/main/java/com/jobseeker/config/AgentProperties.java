package com.jobseeker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Python Agent 对接配置。
 *
 * 注意：Agent 的接口没有 /api 前缀（如 /chat、/knowledge、/sessions），
 * 这里拼接时不要多加前缀。
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /** Agent 服务地址，如 http://127.0.0.1:8000 */
    private String baseUrl = "http://127.0.0.1:8000";

    /** Agent 侧 API_KEY 非空时需透传 X-API-Key */
    private String apiKey = "";

    private int connectTimeoutMs = 5000;

    private int readTimeoutMs = 120000;

    /** AI 产物目录（Agent 的 Agent_output），只读 */
    private String outputDir = "../Agent实习项目/Agent_output";

    /** 网关层响应缓存是否开启（相同 query+user_context 命中即返回，省掉整轮 Agent 调用） */
    private boolean chatCacheEnabled = true;

    /** 缓存最大条目数（超出按 LRU 淘汰最旧） */
    private int chatCacheMaxSize = 1000;

    /** 缓存存活时间（秒），到期惰性失效 */
    private long chatCacheTtlSeconds = 300;

    /** user_context 按需裁剪是否开启（按 query 相关性动态压缩 JD/简历，降 token 与 prefill） */
    private boolean contextCompressEnabled = true;

    /** JD 硬上限（字符），压缩只在更短预算内去掉无关句，不会超过此值 */
    private int contextJdLimit = 1200;

    /** 简历正文硬上限（字符） */
    private int contextResumeLimit = 4000;

    /** 端到端监控基线是否采集并暴露（首 token 延迟 / P95 + 缓存命中率，9.2.D） */
    private boolean metricsEnabled = true;

    /** 指标滑动窗口容量（保留最近 N 条样本用于算分位，超界淘汰最旧） */
    private int metricsMaxSamples = 2000;

    /**
     * Redis 接入配置（缓存 / 分布式锁 / 限流三件套的载体）。
     *
     * **默认不启用**：{@code enabled=false} 时 RedisConfig 不装配、不建连接池，
     * 服务在没装 Redis 的机器上照常启动，并自动降级为进程内实现
     * （缓存→InMemoryChatCache，锁→进程内 ReentrantLock，限流→进程内计数）。
     * 这是「可选能力不拖垮启动」的落点：装不装 Redis 由部署方决定，代码两条路都验证过。
     */
    private Redis redis = new Redis();

    /** 接口限流配置（按登录用户或来源 IP 计窗口请求数）。 */
    private RateLimit rateLimit = new RateLimit();

    public Redis getRedis() {
        return redis;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    /** Redis 连接与行为参数。 */
    @Data
    public static class Redis {
        /** 总开关。false 时不建任何 Redis 连接，也不注册相关 Bean。 */
        private boolean enabled = false;

        private String host = "127.0.0.1";

        private int port = 6379;

        /** 空表示无密码（本机开发常见）。 */
        private String password = "";

        private int database = 0;

        /** 单次命令超时（毫秒）。宁短不宁长：限流/锁这类热路径不能陪 Redis 一起卡。 */
        private int timeoutMs = 1000;

        /** 连接超时（毫秒）。 */
        private int connectTimeoutMs = 1000;

        private int poolMaxTotal = 32;

        private int poolMaxIdle = 8;

        private int poolMinIdle = 2;

        /** key 前缀：与其他应用共用同一 Redis 时避免串数据。 */
        private String keyPrefix = "jobseeker:";

        /**
         * 健康探测间隔（秒）；0 表示只在启动时探一次。
         * 探测失败会把 Redis 侧实现降级为进程内实现并告警（恢复后自动切回）。
         */
        private int healthCheckSeconds = 30;
    }

    /** 限流参数。固定窗口：窗口内超过 maxRequests 即返回 429。 */
    @Data
    public static class RateLimit {
        /** 是否启用。Redis 不可用时自动降级为进程内计数（不会变成"无限流"）。 */
        private boolean enabled = true;

        /** 统计窗口长度（秒）。 */
        private int windowSeconds = 60;

        /** 单用户（或单 IP）在窗口内的最大请求数。 */
        private int maxRequests = 60;

        /** true 按登录用户维度；false 按来源 IP（未登录场景）。 */
        private boolean perUser = true;

        /** 429 响应里的 Retry-After 秒数。 */
        private int retryAfterSeconds = 60;

        /** 限流统计 key 前缀。 */
        private String keyPrefix = "jobseeker:rl:";
    }
}

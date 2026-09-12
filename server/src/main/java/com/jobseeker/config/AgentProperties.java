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
}

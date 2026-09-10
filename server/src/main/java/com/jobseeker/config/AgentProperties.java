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
}

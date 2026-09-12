package com.jobseeker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobseeker.cache.ChatResponseCache;
import com.jobseeker.client.AgentClient;
import com.jobseeker.common.BizException;
import com.jobseeker.metrics.GatewayMetrics;
import com.jobseeker.service.AgentContextService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 薄网关：把前端的 /agent/** 原样转发给 Python Agent。
 *
 * 目的只有一个 —— 让前端不直接暴露 Agent 地址与 API Key，同时规避跨域。
 * 这里**不做业务编排**：不判断模式、不改提示词、不维护会话状态，
 * Agent 返回什么就转发什么。
 *
 * 唯一的加工是把前端传的 position_id / resume_id 换成 Agent 认识的 user_context
 * （查库拼装「关联岗位 + 关联简历」），解决大模型看不到用户已关联数据的问题。
 */
@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentGatewayController {

    private final AgentClient agentClient;
    private final AgentContextService contextService;
    private final ObjectMapper objectMapper;
    private final ChatResponseCache cache;
    private final GatewayMetrics metrics;

    /** SSE 里工具调用事件的类型值，用于在转发热路径上做「零解析」快速过滤。 */
    private static final String TOOL_CALL_MARKER = "\"tool_call\"";

    @PostMapping("/chat")
    public String chat(@RequestBody String body, HttpServletResponse response) {
        // 先算好待转发报文：注入异常（如未登录）必须在此抛出，不进入缓存逻辑
        String payload = withUserContext(body);
        if (cache.isEnabled()) {
            String key = cache.buildKey(payload);
            String hit = cache.get(key);
            if (hit != null) {
                response.setHeader("X-Cache", "HIT");
                metrics.recordCache(true);
                log.info("chat 命中缓存，直接返回");
                return hit;
            }
            response.setHeader("X-Cache", "MISS");
        }
        long start = System.nanoTime();
        String result = agentClient.post("/chat", payload);
        metrics.recordChat(ms(start));
        metrics.recordCache(false);
        if (cache.isEnabled()) {
            cache.put(cache.buildKey(payload), result);
        }
        return result;
    }

    /**
     * SSE 流式透传。必须边收边写并 flush，否则前端看不到逐字效果。
     * 命中缓存时按 SSE 格式 replay，仍是合法的逐行事件流。
     */
    @PostMapping("/chat/stream")
    public void chatStream(@RequestBody String body, HttpServletResponse response) throws IOException {
        // 先算好待转发报文：注入异常（如未登录）必须在写响应头/取 writer 之前抛出，
        // 否则响应已进入 SSE 状态，异常只能变成 500 且拿不到错误信息。
        String payload = withUserContext(body);

        response.setContentType("text/event-stream; charset=utf-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        // 反向代理（nginx 等）不要缓冲，否则流式会变成一次性返回
        response.setHeader("X-Accel-Buffering", "no");

        if (cache.isEnabled()) {
            String key = cache.buildKey(payload);
            String hit = cache.get(key);
            if (hit != null) {
                response.setHeader("X-Cache", "HIT");
                metrics.recordCache(true);
                log.info("chat/stream 命中缓存，replay 返回");
                java.io.PrintWriter w = response.getWriter();
                List<Long> replayedTools = new ArrayList<>();
                for (String line : hit.split("\n", -1)) {
                    w.write(line);
                    w.write("\n");
                    w.flush();
                    // replay 的文本里同样带着工具调用事件：照常计入「模型用没用工具」
                    Long toolMs = toolElapsedMs(line);
                    if (toolMs != null) {
                        replayedTools.add(toolMs);
                    }
                }
                metrics.recordToolCalls(replayedTools);
                return;
            }
            response.setHeader("X-Cache", "MISS");
        }

        java.io.PrintWriter writer = response.getWriter();
        if (cache.isEnabled()) {
            String key = cache.buildKey(payload);
            List<String> buffer = new ArrayList<>();
            List<Long> toolDurations = new ArrayList<>();
            long start = System.nanoTime();
            AtomicBoolean firstLine = new AtomicBoolean(true);
            long[] ttft = {-1};
            agentClient.stream("/chat/stream", payload, line -> {
                buffer.add(line);
                writer.write(line);
                writer.write("\n");
                writer.flush();
                Long toolMs = toolElapsedMs(line);
                if (toolMs != null) {
                    toolDurations.add(toolMs);
                }
                if (firstLine.compareAndSet(true, false)) {
                    ttft[0] = ms(start);
                }
            });
            metrics.recordStream(ttft[0], ms(start));
            metrics.recordCache(false);
            metrics.recordToolCalls(toolDurations);
            cache.put(key, String.join("\n", buffer));
        } else {
            List<Long> toolDurations = new ArrayList<>();
            long start = System.nanoTime();
            AtomicBoolean firstLine = new AtomicBoolean(true);
            long[] ttft = {-1};
            agentClient.stream("/chat/stream", payload, line -> {
                writer.write(line);
                writer.write("\n");
                writer.flush();
                Long toolMs = toolElapsedMs(line);
                if (toolMs != null) {
                    toolDurations.add(toolMs);
                }
                if (firstLine.compareAndSet(true, false)) {
                    ttft[0] = ms(start);
                }
            });
            metrics.recordStream(ttft[0], ms(start));
            metrics.recordCache(false);
            metrics.recordToolCalls(toolDurations);
        }
    }

    /**
     * 从一行 SSE 数据里取工具调用耗时（Function Calling 自治程度的观测点）。
     *
     * 只有含 "tool_call" 标记的行才做 JSON 解析，普通 delta 行仅一次子串判断，
     * 不给逐字流式加开销。只取耗时，不落任何工具名与查询原文。
     *
     * @return 工具调用耗时毫秒；该行不是工具调用事件时返回 null
     */
    private Long toolElapsedMs(String line) {
        if (line == null || !line.contains(TOOL_CALL_MARKER)) {
            return null;
        }
        try {
            String json = line.startsWith("data:") ? line.substring(5).trim() : line.trim();
            JsonNode node = objectMapper.readTree(json);
            if (!"tool_call".equals(node.path("type").asText())) {
                return null;
            }
            return Math.max(0L, node.path("elapsed_ms").asLong(0L));
        } catch (Exception e) {
            log.debug("工具调用事件解析失败，已跳过该指标：{}", e.getMessage());
            return null;
        }
    }

    @PostMapping("/chat/finish")
    public String finish(@RequestBody String body) {
        return agentClient.post("/chat/finish", body);
    }

    @PostMapping("/chat/confirm")
    public String confirm(@RequestBody String body) {
        return agentClient.post("/chat/confirm", body);
    }

    @GetMapping("/health")
    public String health() {
        return agentClient.get("/health");
    }

    @GetMapping("/knowledge")
    public String knowledgeList() {
        return agentClient.get("/knowledge");
    }

    @DeleteMapping("/knowledge/{kb}")
    public String deleteKnowledge(@PathVariable("kb") String kb) {
        return agentClient.delete("/knowledge/" + kb);
    }

    /** 知识库建库：multipart 原样转发给 Agent。 */
    @PostMapping("/knowledge/{kb}/ingest")
    public String ingest(@PathVariable("kb") String kb,
                         @RequestParam("files") MultipartFile[] files) throws IOException {
        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) {
                continue;
            }
            MediaType mt = f.getContentType() == null
                    ? MediaType.parse("application/octet-stream")
                    : MediaType.parse(f.getContentType());
            builder.addFormDataPart("files", f.getOriginalFilename(),
                    okhttp3.RequestBody.create(f.getBytes(), mt));
        }
        return agentClient.postMultipart("/knowledge/" + kb + "/ingest", builder.build());
    }

    /**
     * 注入用户档案：把前端传的 position_id / resume_id 换成 Agent 认识的 user_context。
     *
     * 用 ObjectNode 增删而非重建 DTO，原样保留 top_k / reranker / use_bm25 / use_vector
     * 等既有参数；解析失败或未关联任何信息时原样透传，保证向后兼容、不阻断对话。
     */
    private String withUserContext(String body) {
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(body);
            Long positionId = readLong(node, "position_id");
            Long resumeId = readLong(node, "resume_id");
            if (positionId == null && resumeId == null) {
                return body;
            }
            node.remove("position_id");
            node.remove("resume_id");
            // query 保留在报文中（Agent 需要它），同时用于 user_context 按需裁剪
            String query = readText(node, "query");
            String context = contextService.build(positionId, resumeId, query);
            if (context != null && !context.isBlank()) {
                node.put("user_context", context);
            }
            return objectMapper.writeValueAsString(node);
        } catch (BizException e) {
            throw e; // 401 等鉴权异常照常上抛，不静默吞掉
        } catch (Exception e) {
            log.warn("用户档案注入失败，已降级为原样转发：{}", e.getMessage());
            return body;
        }
    }

    /** 宽容读取 Long：兼容数字与字符串两种传参，缺失或非法一律返回 null。 */
    private Long readLong(ObjectNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isNumber()) {
            return v.asLong();
        }
        if (v.isTextual() && !v.asText().isBlank()) {
            try {
                return Long.valueOf(v.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 宽容读取文本字段：非文本或空白一律返回 null。 */
    private String readText(ObjectNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isTextual() || v.asText().isBlank()) {
            return null;
        }
        return v.asText();
    }

    /** 兜底：避免中文在透传时被错误编码。 */
    @GetMapping(value = "/ping", produces = "text/plain;charset=UTF-8")
    public String ping() {
        return new String("agent gateway ok".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    /** 只读调试端点：返回端到端监控基线快照（首 token 延迟 / P95 + 缓存命中率，9.2.D）。 */
    @GetMapping("/gateway-metrics")
    public GatewayMetrics.MetricsSnapshot gatewayMetrics() {
        return metrics.snapshot();
    }

    /** 纳秒起点到当前毫秒数，非负，用于请求时延埋点。 */
    private static long ms(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
    }
}

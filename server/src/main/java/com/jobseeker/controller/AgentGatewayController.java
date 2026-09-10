package com.jobseeker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobseeker.client.AgentClient;
import com.jobseeker.common.BizException;
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

    @PostMapping("/chat")
    public String chat(@RequestBody String body) {
        return agentClient.post("/chat", withUserContext(body));
    }

    /**
     * SSE 流式透传。必须边收边写并 flush，否则前端看不到逐字效果。
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

        java.io.PrintWriter writer = response.getWriter();
        agentClient.stream("/chat/stream", payload, line -> {
            writer.write(line);
            writer.write("\n");
            writer.flush();
        });
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
            String context = contextService.build(positionId, resumeId);
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

    /** 兜底：避免中文在透传时被错误编码。 */
    @GetMapping(value = "/ping", produces = "text/plain;charset=UTF-8")
    public String ping() {
        return new String("agent gateway ok".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }
}

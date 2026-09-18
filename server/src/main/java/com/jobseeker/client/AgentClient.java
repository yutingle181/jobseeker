package com.jobseeker.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseeker.client.dto.AgentSessionDetail;
import com.jobseeker.common.BizException;
import com.jobseeker.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 调用 Python Agent 的客户端。
 *
 * 只做协议转发与数据拉取，不含任何 AI 业务编排 —— Agent 的模式判断、
 * 提示词、会话状态机都在 Python 侧，本服务一律不重写。
 *
 * 注意：Agent 接口没有 /api 前缀（/chat、/knowledge、/sessions）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final AgentProperties props;
    private final ObjectMapper objectMapper;

    private OkHttpClient httpClient;

    private synchronized OkHttpClient http() {
        if (httpClient == null) {
            // OkHttp 自带连接池，进程内复用同一实例以复用 TCP 连接、降低握手开销
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(props.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                    .readTimeout(props.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                    .writeTimeout(props.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                    .build();
        }
        return httpClient;
    }

    private Request.Builder base(String path) {
        Request.Builder b = new Request.Builder().url(props.getBaseUrl() + path);
        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            b.addHeader("X-API-Key", props.getApiKey());
        }
        return b;
    }

    public String get(String path) {
        OkHttpClient client = http();
        try (Response resp = client.newCall(base(path).get().build()).execute()) {
            return readBody(resp);
        } catch (Exception e) {
            throw new BizException(502, "调用 Agent 失败：" + e.getMessage());
        }
    }

    public String delete(String path) {
        OkHttpClient client = http();
        try (Response resp = client.newCall(base(path).delete().build()).execute()) {
            return readBody(resp);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(502, "调用 Agent 失败：" + e.getMessage());
        }
    }

    public String post(String path, String jsonBody) {
        OkHttpClient client = http();
        RequestBody body = RequestBody.create(jsonBody, JSON);
        try (Response resp = client.newCall(base(path).post(body).build()).execute()) {
            return readBody(resp);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(502, "调用 Agent 失败：" + e.getMessage());
        }
    }

    /** multipart 透传（知识库建库的文件上传）。 */
    public String postMultipart(String path, RequestBody multipart) {
        OkHttpClient client = http();
        try (Response resp = client.newCall(base(path).post(multipart).build()).execute()) {
            return readBody(resp);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(502, "调用 Agent 失败：" + e.getMessage());
        }
    }

    /**
     * SSE 流式透传：逐行回调，由网关边收边写给前端，不做聚合（否则前端看不到流式）。
     */
    public void stream(String path, String jsonBody, Consumer<String> onLine) {
        OkHttpClient client = http();
        RequestBody body = RequestBody.create(jsonBody, JSON);
        Call call = client.newCall(base(path).post(body).build());
        try (Response resp = call.execute()) {
            if (!resp.isSuccessful()) {
                throw new BizException(502, "Agent 流式接口返回 " + resp.code());
            }
            ResponseBody rb = resp.body();
            if (rb == null) {
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(rb.byteStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    onLine.accept(line);
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Agent 流式调用中断：{}", e.getMessage());
            throw new BizException(502, "Agent 流式调用失败：" + e.getMessage());
        }
    }

    /** 拉取 Agent 会话详情，用于归档。 */
    public AgentSessionDetail fetchSession(String sessionId) {
        String json = get("/sessions/" + sessionId);
        try {
            return objectMapper.readValue(json, AgentSessionDetail.class);
        } catch (Exception e) {
            throw new BizException(502, "解析 Agent 会话失败：" + e.getMessage());
        }
    }

    /**
     * 把一段已有的面试记录交给 Agent 的 interview_review 模式，生成结构化复盘 Markdown。
     *
     * 复用现有 POST /chat（mode=interview_review），解析返回 JSON 的 message 字段。
     * Agent 不可达 / 超时 / 返回为空都会抛 BizException，由调用方决定回退策略。
     *
     * @param transcript   面试记录文本（面试官与候选人的问答）
     * @param userContext  可选岗位 JD 等上下文，用于让复盘贴合岗位
     * @return 复盘 Markdown（含「面试复盘 / 追问链 / 薄弱点 / 改进动作」小标题）
     */
    public String generateReview(String transcript, String userContext) {
        String body;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("query", transcript);
            payload.put("mode", "interview_review");
            if (userContext != null && !userContext.isBlank()) {
                payload.put("user_context", userContext);
            }
            body = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new BizException(502, "构造复盘请求失败：" + e.getMessage());
        }

        String resp = post("/chat", body);
        try {
            JsonNode node = objectMapper.readTree(resp);
            String message = node.path("message").asText(null);
            if (message == null || message.isBlank()) {
                throw new BizException(502, "Agent 复盘返回为空");
            }
            return message;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(502, "解析 Agent 复盘失败：" + e.getMessage());
        }
    }

    private String readBody(Response resp) throws Exception {
        ResponseBody rb = resp.body();
        String text = rb == null ? "" : rb.string();
        if (!resp.isSuccessful()) {
            throw new BizException(502, "Agent 返回 " + resp.code() + "：" + text);
        }
        return text;
    }
}

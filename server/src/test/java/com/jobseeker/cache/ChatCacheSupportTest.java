package com.jobseeker.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 缓存 key 生成逻辑：两个后端必须产出**完全相同**的 key。
 *
 * <p>这条是监控数据可比的前提：如果 Redis 版与内存版的 key 逻辑有偏差，
 * 切换后端后同一请求的命中率会凭空变化，而这种问题极难定位。
 */
class ChatCacheSupportTest {

    @Test
    @DisplayName("相同报文得到相同 key；仅 session_id 不同也要相同（语义缓存）")
    void samePayloadSameKey() {
        String a = "{\"query\":\"你好\",\"session_id\":\"s1\",\"top_k\":5}";
        String b = "{\"query\":\"你好\",\"session_id\":\"s2\",\"top_k\":5}";
        assertEquals(ChatCacheSupport.buildKey(a), ChatCacheSupport.buildKey(a));
        assertEquals(ChatCacheSupport.buildKey(a), ChatCacheSupport.buildKey(b),
                "去掉 session_id 后应视为同一问题（跨会话命中）");
    }

    @Test
    @DisplayName("易变字段被剔除：session_id / session 不影响 key")
    void volatileFieldsIgnored() {
        String a = "{\"query\":\"你好\",\"session\":\"x\"}";
        String b = "{\"query\":\"你好\"}";
        assertEquals(ChatCacheSupport.buildKey(a), ChatCacheSupport.buildKey(b));
    }

    @Test
    @DisplayName("真正不同的请求必须得到不同 key")
    void differentPayloadDifferentKey() {
        assertNotEquals(
                ChatCacheSupport.buildKey("{\"query\":\"你好\"}"),
                ChatCacheSupport.buildKey("{\"query\":\"在吗\"}"));
    }

    @Test
    @DisplayName("非法 JSON 不抛异常，退化为稳定可用的兜底 key")
    void malformedJsonDoesNotBlowUp() {
        String key = ChatCacheSupport.buildKey("这不是 JSON");
        assertEquals(ChatCacheSupport.buildKey("这不是 JSON"), key, "兜底 key 必须稳定（同输入同输出）");
    }
}

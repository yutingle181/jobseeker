package com.jobseeker.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 进程内缓存的行为基线：TTL、LRU 淘汰、只缓存成功响应。
 *
 * <p>这些用例同时是「降级路径」的验收：Redis 不可用时请求仍然能命中缓存，
 * 语义与 Redis 版一致（同一个 key、同样的 TTL 口径）。
 */
class ChatResponseCacheTest {

    private ChatResponseCache cache() {
        return new ChatResponseCache(true, 3, 300);
    }

    @Test
    @DisplayName("写入后可读回；命中即原样返回")
    void putThenGet() {
        ChatResponseCache cache = cache();
        String key = cache.buildKey("{\"query\":\"你好\"}");
        cache.put(key, "回答");
        assertEquals("回答", cache.get(key));
    }

    @Test
    @DisplayName("未命中返回 null（调用方随后走真实 Agent 调用）")
    void missReturnsNull() {
        assertNull(cache().get("nope"));
    }

    @Test
    @DisplayName("TTL 到期后惰性失效")
    void ttlExpires() throws Exception {
        ChatResponseCache cache = new ChatResponseCache(true, 10, 1);
        String key = cache.buildKey("{\"query\":\"你好\"}");
        cache.put(key, "回答");
        Thread.sleep(1100);
        assertNull(cache.get(key), "到期后必须失效，否则会把过期回答当新答案返回");
    }

    @Test
    @DisplayName("超过容量按 LRU 淘汰最旧")
    void lruEvictsOldest() {
        ChatResponseCache cache = new ChatResponseCache(true, 2, 300);
        String k1 = cache.buildKey("{\"query\":\"1\"}");
        String k2 = cache.buildKey("{\"query\":\"2\"}");
        String k3 = cache.buildKey("{\"query\":\"3\"}");
        cache.put(k1, "a");
        cache.put(k2, "b");
        cache.put(k3, "c");
        assertNull(cache.get(k1), "最旧的 k1 应被淘汰");
        assertNotNull(cache.get(k2));
        assertNotNull(cache.get(k3));
    }

    @Test
    @DisplayName("disabled 时不读不写（降级开关关掉后零副作用）")
    void disabledIsNoop() {
        ChatResponseCache cache = new ChatResponseCache(false, 10, 300);
        assertNull(cache.get("k"));
        cache.put("k", "v");
        assertNull(cache.get("k"));
    }

    @Test
    @DisplayName("实现了 ChatCache 接口：与 Redis 版同一套契约")
    void implementsChatCache() {
        ChatResponseCache cache = cache();
        ChatCache asInterface = cache;
        assertEquals(cache.buildKey("{\"query\":\"x\"}"), asInterface.buildKey("{\"query\":\"x\"}"));
        assertEquals(cache.isEnabled(), asInterface.isEnabled());
    }
}

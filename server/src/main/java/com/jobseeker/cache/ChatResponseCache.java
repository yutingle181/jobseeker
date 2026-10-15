package com.jobseeker.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 进程内响应缓存：对「相同 query + 相同 user_context」的重复请求直接命中，
 * 省掉整轮 Agent / LLM 调用。零新增依赖（JDK MessageDigest + ConcurrentHashMap）。
 *
 * 设计取舍：
 * - key 由「规范化 JSON 去掉 session_id 后 SHA-256」生成，使不同会话的相同问答也能命中（语义缓存）；
 *   代价仅是缓存体里的 session_id 文本来自历史会话，仅用于展示，不影响逻辑。
 * - 仅缓存成功响应：错误/异常不回写，避免污染缓存；与 401/降级语义完全兼容。
 * - **仅本进程可见**：多实例部署时每个实例各有一份，重启即丢。需要跨实例共享时把
 *   `agent.redis.enabled` 打开，由 {@link ChatCacheRouter} 切到 {@link RedisChatCache}，
 *   本实现自动退居为「Redis 不可用」时的兜底。
 * - 并发同一 key 的缓存击穿（stampede）由 {@code lock/} 的分布式锁兜底（加锁 + 双重检查），
 *   本实现自身仍保持无锁，避免进程内缓存引入锁竞争。
 */
@Slf4j
@Component
public class ChatResponseCache implements ChatCache {

    private final boolean enabled;
    private final int maxSize;
    private final long ttlMs;

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    /** 维护 LRU 顺序，队首最旧，用于超容量淘汰。 */
    private final ConcurrentLinkedDeque<String> lru = new ConcurrentLinkedDeque<>();

    public ChatResponseCache(
            @org.springframework.beans.factory.annotation.Value("${agent.chat-cache-enabled:true}") boolean enabled,
            @org.springframework.beans.factory.annotation.Value("${agent.chat-cache-max-size:1000}") int maxSize,
            @org.springframework.beans.factory.annotation.Value("${agent.chat-cache-ttl-seconds:300}") long ttlSeconds) {
        this.enabled = enabled;
        this.maxSize = Math.max(1, maxSize);
        this.ttlMs = Math.max(1, ttlSeconds) * 1000L;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 由请求报文生成稳定 key：解析 JSON → 去除易变字段（session_id / session）→ 规范化后 SHA-256。
     * 解析失败则回退为字符串 hashCode，保证主流程不被缓存逻辑阻断。
     *
     * <p>逻辑与 {@link RedisChatCache} 共享同一份实现（{@link ChatCacheSupport}）：
     * 两个后端必须产出完全相同的 key，否则切换后端会让命中率凭空变化、监控数据不可比。
     */
    @Override
    public String buildKey(String payload) {
        return ChatCacheSupport.buildKey(payload);
    }

    /** 命中且未过期返回值，否则惰性删除过期项并返回 null。 */
    @Override
    public String get(String key) {
        if (!enabled || key == null) {
            return null;
        }
        Entry e = store.get(key);
        if (e == null) {
            return null;
        }
        if (System.currentTimeMillis() > e.expireAt) {
            store.remove(key);
            lru.remove(key);
            return null;
        }
        // 命中后移到 LRU 队尾（最近使用）
        lru.remove(key);
        lru.addLast(key);
        return e.value;
    }

    /** 成功响应回写；维护 LRU 顺序与容量上限。 */
    @Override
    public void put(String key, String value) {
        if (!enabled || key == null || value == null) {
            return;
        }
        store.put(key, new Entry(value, System.currentTimeMillis() + ttlMs));
        lru.remove(key);
        lru.addLast(key);
        while (lru.size() > maxSize) {
            String oldest = lru.pollFirst();
            if (oldest == null) {
                break;
            }
            store.remove(oldest);
        }
    }

    private static class Entry {
        final String value;
        final long expireAt;

        Entry(String value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }
}

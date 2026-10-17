package com.jobseeker.cache;

import com.jobseeker.config.AgentProperties;
import com.jobseeker.redis.RedisHealthProbe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 缓存路由器：三种形态 + 降级/恢复。
 *
 * <p>重点验的是**降级不静默**：Redis 挂了之后，请求必须照常完成（走内存缓存），
 * 且 activeBackend() 变成 memory —— 后者是 /agent/backends 能观察到的事实。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatCacheRouterTest {

    @Mock
    private StringRedisTemplate template;
    @Mock
    private ValueOperations<String, String> ops;
    @Mock
    private ObjectProvider<StringRedisTemplate> provider;

    private AgentProperties props(boolean redisEnabled) {
        AgentProperties props = new AgentProperties();
        props.getRedis().setEnabled(redisEnabled);
        props.getRedis().setKeyPrefix("jobseeker:");
        props.getRedis().setHealthCheckSeconds(30);
        props.setChatCacheTtlSeconds(300);
        return props;
    }

    private RedisHealthProbe probe(AgentProperties props) {
        return new RedisHealthProbe(props, provider);
    }

    private ChatResponseCache memory() {
        return new ChatResponseCache(true, 100, 300);
    }

    @Test
    @DisplayName("Redis 未启用：直接用进程内缓存，且根本不问 Redis 要 Template")
    void disabledUsesMemoryWithoutTouchingRedis() {
        AgentProperties props = props(false);
        ChatCacheRouter router = new ChatCacheRouter(props, probe(props), provider);

        assertEquals("memory", router.activeBackend());
        // 关键：未启用时不得向 Spring 索取 Template（否则等于建了连接）
        org.mockito.Mockito.verifyNoInteractions(provider);
    }

    @Test
    @DisplayName("Redis 启用且探活成功：用 Redis 后端")
    void healthyUsesRedis() {
        AgentProperties props = props(true);
        when(provider.getIfAvailable()).thenReturn(template);
        when(template.execute(any(org.springframework.data.redis.core.RedisCallback.class))).thenReturn("PONG");
        when(template.opsForValue()).thenReturn(ops);
        // ValueOperations.set 是 void 方法：必须用 doNothing().when(...)，
        // 用 when(...) 包 void 调用会直接编译失败（"此处不允许使用 void 类型"）
        org.mockito.Mockito.doNothing().when(ops).set(anyString(), anyString(), any(Duration.class));
        when(ops.get(anyString())).thenReturn("1");
        when(template.delete(anyString())).thenReturn(true);

        ChatCacheRouter router = new ChatCacheRouter(props, probe(props), provider);

        assertEquals("redis", router.activeBackend());
    }

    @Test
    @DisplayName("Redis 启用但探活失败：启动即降级，且明确告知")
    void unhealthyFallsBackAtStartup() {
        AgentProperties props = props(true);
        when(provider.getIfAvailable()).thenReturn(template);
        when(template.execute(any(org.springframework.data.redis.core.RedisCallback.class)))
                .thenThrow(new RuntimeException("connection refused"));

        ChatCacheRouter router = new ChatCacheRouter(props, probe(props), provider);

        assertEquals("memory", router.activeBackend());
    }

    @Test
    @DisplayName("运行中 Redis 读失败：本次请求降级到内存缓存并照常返回值")
    void readFailureDegradesAndStillReturnsValue() {
        AgentProperties props = props(true);
        when(provider.getIfAvailable()).thenReturn(template);
        when(template.execute(any(org.springframework.data.redis.core.RedisCallback.class))).thenReturn("PONG");
        when(template.opsForValue()).thenReturn(ops);
        // ValueOperations.set 是 void 方法：必须用 doNothing().when(...)
        org.mockito.Mockito.lenient().doNothing().when(ops).set(anyString(), anyString(), any(Duration.class));
        when(ops.get(anyString())).thenReturn("1");
        when(template.delete(anyString())).thenReturn(true);

        ChatCacheRouter router = new ChatCacheRouter(props, probe(props), provider);
        String key = router.buildKey("{\"query\":\"你好\"}");

        // 先让 Redis 读失败
        when(ops.get("jobseeker:" + key)).thenThrow(new RuntimeException("boom"));
        assertNull(router.get(key), "Redis 读失败时应降级（内存也没有 → null，由调用方去问 Agent）");
        assertEquals("memory", router.activeBackend(), "降级后后端必须是内存，且可被 /agent/backends 观察到");

        // 降级后内存缓存仍然可用：写入 → 读回
        router.put(key, "回答");
        assertEquals("回答", router.get(key));
    }

    @Test
    @DisplayName("Redis 写失败：降级且不把异常抛给调用方")
    void writeFailureDoesNotPropagate() {
        AgentProperties props = props(true);
        when(provider.getIfAvailable()).thenReturn(template);
        when(template.execute(any(org.springframework.data.redis.core.RedisCallback.class))).thenReturn("PONG");
        when(template.opsForValue()).thenReturn(ops);
        // ValueOperations.set 是 void 方法：必须用 doNothing().when(...)
        org.mockito.Mockito.lenient().doNothing().when(ops).set(anyString(), anyString(), any(Duration.class));
        when(ops.get(anyString())).thenReturn("1");
        when(template.delete(anyString())).thenReturn(true);

        ChatCacheRouter router = new ChatCacheRouter(props, probe(props), provider);
        String key = router.buildKey("{\"query\":\"你好\"}");

        // Redis 写失败：不得把异常抛给调用方（缓存写失败不该让用户的提问失败）
        // void 方法要用 doThrow().when(...)（when(...).thenThrow 对 void 同样不成立）
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(ops).set(anyString(), anyString(), any(Duration.class));

        router.put(key, "回答");
        assertEquals("memory", router.activeBackend());
        assertEquals("回答", router.get(key), "降级后内存侧应有值");
    }

    @Test
    @DisplayName("isEnabled 跟随当前后端：内存缓存关闭时整体关闭")
    void isEnabledFollowsActiveBackend() {
        // 2026-10-17：这里原来是「new 一个 enabled=false 的 ChatResponseCache 塞进路由」，
        // 但进程内缓存已改成由路由自己按 props 创建（否则容器里两个 ChatCache bean 会二义 ✓✗），
        // 于是注入式写法失效。改成直接关 props —— **语义等价且更贴近真实**：
        // 真实场景里关缓存就是改 agent.chat-cache-enabled ✓✓
        AgentProperties props = props(true);
        props.setChatCacheEnabled(false);
        ChatCacheRouter router = new ChatCacheRouter(props, probe(props), provider);
        assertTrue(!router.isEnabled(), "内存缓存关闭时，路由也应报告未启用（控制器据此跳过缓存）");
    }
}

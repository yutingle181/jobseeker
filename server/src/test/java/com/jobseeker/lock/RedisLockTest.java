package com.jobseeker.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 锁：取锁、等待、释放，以及「释放必须走 Lua 比对 owner」这条核心保证。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisLockTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> ops;

    private RedisLock lock() {
        return new RedisLock(redis, "jobseeker:");
    }

    @Test
    @DisplayName("取到锁就执行，并用 Lua 脚本按 owner 释放")
    void acquiresExecutesAndReleases() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);

        String out = lock().executeWithLock("k", Duration.ofSeconds(5), Duration.ofMillis(200), () -> "ok");

        assertEquals("ok", out);
        // 释放必须走 Lua（比对 owner 后删除），而不是裸 DEL
        verify(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    @DisplayName("抢不到锁时轮询到超时返回 null，且不执行动作")
    void returnsNullWhenLockHeldByOthers() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        String out = lock().executeWithLock("k", Duration.ofSeconds(5), Duration.ofMillis(120), () -> "should-not-run");

        assertNull(out);
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    @DisplayName("动作抛异常时也必须释放锁（否则持锁进程出错会卡住后续请求）")
    void releasesEvenWhenActionThrows() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);

        assertThrows(RuntimeException.class, () ->
                lock().executeWithLock("k", Duration.ofSeconds(5), Duration.ofMillis(200), () -> {
                    throw new RuntimeException("boom");
                }));

        verify(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    @DisplayName("取锁阶段 Redis 就失败：抛 LockUnavailableException 让上层降级，而不是假装拿到锁")
    void acquireFailureThrowsForDegradation() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("connection refused"));

        assertThrows(LockUnavailableException.class, () ->
                lock().executeWithLock("k", Duration.ofSeconds(5), Duration.ofMillis(200), () -> "x"));
    }

    @Test
    @DisplayName("释放脚本失败只记日志，不向上抛（锁有 TTL 兜底）")
    void releaseFailureIsSwallowed() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("boom"));

        String out = lock().executeWithLock("k", Duration.ofSeconds(5), Duration.ofMillis(200), () -> "ok");

        assertEquals("ok", out, "释放失败不影响本次业务结果（靠 TTL 自然过期兜底）");
    }

    @Test
    @DisplayName("key 带上前缀与 lock: 命名空间，避免与其他业务的锁串味")
    void keyIsNamespaced() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);

        lock().executeWithLock("chat:abc", Duration.ofSeconds(5), Duration.ofMillis(200), () -> "ok");

        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(ops).setIfAbsent(keyCaptor.capture(), anyString(), any(Duration.class));
        assertEquals("jobseeker:lock:chat:abc", keyCaptor.getValue());
        // 释放用的 key 与取锁一致（同一个 Lua 调用的第一个参数）
        verify(redis).execute(any(RedisScript.class), org.mockito.ArgumentMatchers.argThat(
                keys -> keys != null && keys.size() == 1 && "jobseeker:lock:chat:abc".equals(keys.get(0))),
                any(Object[].class));
    }
}

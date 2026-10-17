package com.jobseeker.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 限流：一次 Lua 完成「计数 + 首次设 TTL + 超限判断」。
 *
 * <p>最关键的一条是 {@link #nullResultThrowsInsteadOfAllowing()}：
 * 脚本返回 null（连接失败等）时**必须抛异常让上层降级**，
 * 而不能当成"放行"——那会让限流在最需要保护的时刻静默失效。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisRateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    private RedisRateLimiter limiter() {
        return new RedisRateLimiter(redis, "jobseeker:rl:");
    }

    @Test
    @DisplayName("脚本返回 1 → 放行")
    void returnsTrueWhenScriptSaysOne() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        assertTrue(limiter().tryAcquire("u:1", 5, Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("脚本返回 0 → 拒绝")
    void returnsFalseWhenScriptSaysZero() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);
        assertFalse(limiter().tryAcquire("u:1", 5, Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("脚本返回 null → 抛异常（绝不能当成放行）")
    void nullResultThrowsInsteadOfAllowing() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> limiter().tryAcquire("u:1", 5, Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("key 带前缀；窗口秒数与限额都透传给脚本")
    void keyIsNamespacedAndArgsPassed() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);

        limiter().tryAcquire("u:42", 7, Duration.ofSeconds(30));

        org.mockito.ArgumentCaptor<List> keys = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.ArgumentCaptor<Object[]> args = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(redis).execute(any(RedisScript.class), keys.capture(), args.capture());
        assertEquals("jobseeker:rl:u:42", keys.getValue().get(0));
        assertArrayEquals(new Object[]{"30", "7"}, args.getValue());
    }
}

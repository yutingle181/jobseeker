package com.jobseeker.ratelimit;

import com.jobseeker.common.UserContext;
import com.jobseeker.config.AgentProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 限流拦截器：超限返回 429 + Retry-After，并按 userId / IP 维度取键。
 */
class RateLimitInterceptorTest {

    private RateLimiter limiter;
    private AgentProperties props;
    private RateLimitInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        limiter = mock(RateLimiter.class);
        props = new AgentProperties();
        props.getRateLimit().setEnabled(true);
        props.getRateLimit().setMaxRequests(60);
        props.getRateLimit().setWindowSeconds(60);
        props.getRateLimit().setPerUser(true);
        props.getRateLimit().setRetryAfterSeconds(30);
        interceptor = new RateLimitInterceptor(limiter, props);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("放行时返回 true 且不改响应")
    void allowedPassesThrough() throws Exception {
        when(limiter.tryAcquire(anyString(), anyInt(), org.mockito.ArgumentMatchers.any(Duration.class))).thenReturn(true);
        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("超限返回 429 + Retry-After + 中文提示，且不继续处理")
    void blockedReturns429WithRetryAfter() throws Exception {
        when(limiter.tryAcquire(anyString(), anyInt(), org.mockito.ArgumentMatchers.any(Duration.class))).thenReturn(false);

        assertFalse(interceptor.preHandle(request, response, new Object()));

        assertEquals(429, response.getStatus());
        assertEquals("30", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("请求过于频繁"), "应给用户可读的中文提示");
    }

    @Test
    @DisplayName("已登录按 userId 维度限流")
    void perUserKeyWhenLoggedIn() throws Exception {
        UserContext.set(7L);
        when(limiter.tryAcquire(anyString(), anyInt(), org.mockito.ArgumentMatchers.any(Duration.class))).thenReturn(true);

        interceptor.preHandle(request, response, new Object());

        verify(limiter).tryAcquire(eq("u:7"), eq(60), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    @DisplayName("未登录按来源 IP 维度限流；有 X-Forwarded-For 时取第一个地址")
    void perIpKeyWhenAnonymous() throws Exception {
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8");
        when(limiter.tryAcquire(anyString(), anyInt(), org.mockito.ArgumentMatchers.any(Duration.class))).thenReturn(true);

        interceptor.preHandle(request, response, new Object());

        verify(limiter).tryAcquire(eq("ip:1.2.3.4"), eq(60), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    @DisplayName("开关关闭时完全放行（不触达限流器）")
    void disabledSkipsLimiter() throws Exception {
        props.getRateLimit().setEnabled(false);
        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(limiter, org.mockito.Mockito.never()).tryAcquire(anyString(), anyInt(),
                org.mockito.ArgumentMatchers.any(Duration.class));
    }
}

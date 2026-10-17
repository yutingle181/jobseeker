package com.jobseeker.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import lombok.RequiredArgsConstructor;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis 连接配置：**仅在 {@code agent.redis.enabled=true} 时装配**。
 *
 * <p>为什么用条件装配而不是「始终建 Template、用时再判」：连接池在 Bean 创建时就会
 * 尝试建连，本机开发/CI 上没装 Redis 时会在启动日志里刷一片连接失败，
 * 既污染日志也让人误判「服务有问题」。条件装配让「没装 Redis」成为一条干净路径：
 * 相关 Bean 根本不存在，降级组件（{@code cache/}、{@code lock/}）自然接管。
 *
 * <p>超时口径：命令超时与连接超时都取配置（默认 1s）。缓存/锁/限流都在请求热路径上，
 * 宁可快速失败走降级，也不能陪 Redis 一起卡住用户请求。
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.redis.enabled", havingValue = "true")
public class RedisConfig {

    private final AgentProperties props;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        AgentProperties.Redis cfg = props.getRedis();
        int timeoutMs = Math.max(100, cfg.getTimeoutMs());
        int connectMs = Math.max(100, cfg.getConnectTimeoutMs());

        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration();
        standalone.setHostName(cfg.getHost());
        standalone.setPort(cfg.getPort());
        standalone.setDatabase(cfg.getDatabase());
        if (cfg.getPassword() != null && !cfg.getPassword().isBlank()) {
            standalone.setPassword(RedisPassword.of(cfg.getPassword()));
        }

        // 命令超时走 TimeoutOptions（ClientOptions.Builder 与 LettuceClientConfiguration.Builder
        // 在当前版本都没有 commandTimeout 方法，实测编译报错后才改到这条正路上）。
        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(Duration.ofMillis(connectMs))
                        .build())
                .timeoutOptions(TimeoutOptions.builder()
                        .fixedTimeout(Duration.ofMillis(timeoutMs))
                        .build())
                .build();

        // commons-pool2：连接池参数显式化，避免默认值在高并发下把 Redis 打满。
        // 注意：池配置要走 LettucePoolingClientConfiguration.poolConfig(...)，
        // 而不是 LettuceConnectionFactory.setPoolConfig（该方法在当前版本不存在）；
        // commandTimeout 已在 ClientOptions 上设置，这里不再重复（该 Builder 也没有这个方法）。
        GenericObjectPoolConfig<Object> pool = new GenericObjectPoolConfig<>();
        pool.setMaxTotal(Math.max(1, cfg.getPoolMaxTotal()));
        pool.setMaxIdle(Math.max(1, cfg.getPoolMaxIdle()));
        pool.setMinIdle(Math.max(0, cfg.getPoolMinIdle()));
        pool.setTestOnBorrow(true);
        pool.setTestWhileIdle(true);

        LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .clientOptions(clientOptions)
                .clientName("jobseeker-server")
                .poolConfig(pool)
                .build();

        return new LettuceConnectionFactory(standalone, clientConfig);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate(factory);
        // 网关侧只用单 key 读写与 Lua 原子脚本，不需要事务支持
        template.setEnableTransactionSupport(false);
        return template;
    }
}

package com.jobseeker.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局序列化约定：Long 一律按字符串下发。
 *
 * 背景：主键使用雪花算法（{@code IdType.ASSIGN_ID}），是 19 位 Long；
 * 而 JavaScript 的 Number 只能精确表示 2^53 以内的整数，直接按数字下发会在浏览器侧被
 * 四舍五入（例如 ...587778 变成 ...587800）。前端再把该值原样回传时就已经是错的，
 * 表现为「明明关联了岗位/简历，AI 却回答没有关联」这类极难排查的问题——
 * 本项目的 user_context 注入（网关 → Agent）正是受害者。
 *
 * 收敛做法：把 Long 统一序列化为字符串，前端只把它当作**不透明 ID** 透传与比较，
 * 不再参与任何数值运算，因此语义不变；网关侧 {@code readLong} 已兼容字符串入参。
 * int / Integer 不受影响（如 {@code Result.code} 仍是数字），既有接口字段名与结构不变。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longAsStringCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance);
    }
}

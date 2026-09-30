package com.campus.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置：只补一件事——给需要**缓存对象**的场景提供一个 JSON 序列化的
 * {@code RedisTemplate<String, Object>}。
 *
 * <p>为什么需要它：Spring Boot 自动配置给的是 `RedisTemplate<Object, Object>`，
 * 默认 JDK 序列化，存下去在 redis-cli 里是一串二进制乱码，`GET` 出来没法看、也没法定点排查。
 * 本 Bean 把 key 固定成字符串、value 固定成 JSON，`GET cm:xxx` 能直接读懂。
 *
 * <p>**`StringRedisTemplate` 仍然继续用**（不加包装、不改默认）：验证码、发信节流这类
 * 纯字符串缓存走它更直接，没必要为了统一而绕一层 JSON。
 *
 * <p>key 的拼装不在这里，见 {@link com.campus.core.redis.RedisKeys} 及其同包的各模块常量类。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // key 用字符串：`redis-cli> GET cm:item:detail:1001` 这样才敲得出来
        StringRedisSerializer keySerializer = new StringRedisSerializer();

        ObjectMapper mapper = new ObjectMapper();
        // ★ 必须注册：实体里到处是 LocalDateTime，不注册取出来直接抛 InvalidDefinitionException
        mapper.registerModule(new JavaTimeModule());
        // 时间写成 "2026-09-30T14:23:48" 而不是秒数数组，缓存内容要能人工核对
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // JSON 里带 @class，反序列化才能还原成原来的类型——
        // 代价是只能读本应用写的数据，不要拿它解析外部来源的 JSON
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(mapper);

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }
}

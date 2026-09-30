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
 * 给「缓存对象」的场景补一个 JSON 序列化的 `RedisTemplate<String, Object>`。
 *
 * <p>Spring Boot 自动配置给的是 `RedisTemplate<Object, Object>` + JDK 序列化，
 * 存下去在 redis-cli 里是二进制乱码，没法看也没法定点排查；这里固定 key 为字符串、value 为 JSON。
 *
 * <p>`StringRedisTemplate` 照旧使用：验证码、节流这类纯字符串缓存走它更直接，不必绕一层 JSON。
 * key 的拼装见 {@link com.campus.core.redis.RedisKeys} 及同包各模块常量类。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // key 用字符串，redis-cli 里才敲得出 `GET cm:xxx`
        StringRedisSerializer keySerializer = new StringRedisSerializer();

        ObjectMapper mapper = new ObjectMapper();
        // 必须注册：实体里到处是 LocalDateTime，不注册取出来直接抛 InvalidDefinitionException
        mapper.registerModule(new JavaTimeModule());
        // 时间写成人可读的字符串而不是秒数数组
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // JSON 带 @class 才能还原类型；代价是只能读本应用写的数据
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

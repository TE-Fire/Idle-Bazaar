package com.campus.core.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
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
 *
 * <p><b>类型信息必须显式打开</b>：读取侧只声明了 `Object`，没有 `@class` 就无法还原成实体，
 * 反序列化会得到 `LinkedHashMap`——调用方写 `cached instanceof XxxVO` 拿到 false、写
 * `cached instanceof List&lt;实体&gt;` 拿到 `List&lt;LinkedHashMap&gt;`，
 * 表现为**缓存永远是 miss（每次都查库），而且不报错**。
 *
 * <p>这个坑来自 `GenericJackson2JsonRedisSerializer(ObjectMapper)` 这个构造器：
 * 它**只**把传进来的 mapper 原样用上，不会像无参构造器那样自动开启 default typing。
 * 传入自定义 mapper（我们需要它注册 `JavaTimeModule`）就等于把类型信息关掉了。
 */
@Configuration
public class RedisConfig {

    /**
     * 允许还原的类型范围。开了 default typing 后，JSON 里的 `@class` 会被当成真实类名去实例化，
     * 因此不能放开成任意类（反序列化 gadget 的经典入口）。
     * 这里只放行本项目自己的类与 JDK 的容器/值类型，缓存里出现的类型就这些。
     */
    private static final PolymorphicTypeValidator TYPE_VALIDATOR = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("com.campus.")
            .allowIfSubType("java.")
            .build();

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
        // 写入 @class 才能还原类型（详见类注释）；代价是只能读本应用写的数据
        mapper.activateDefaultTyping(TYPE_VALIDATOR, ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY);

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

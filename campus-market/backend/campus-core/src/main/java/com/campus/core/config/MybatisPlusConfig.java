package com.campus.core.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件：仅分页。
 *
 * <p>不注册乐观锁插件——并发占位用手写条件更新（`tech/01` §9.1），
 * 语句里显式带 `version = ?` 与 `version = version + 1`，不依赖注解改写 SQL。
 * 装了插件却没有任何 `@Version` 字段时它什么也不做，留着只会让人误以为有兜底。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}

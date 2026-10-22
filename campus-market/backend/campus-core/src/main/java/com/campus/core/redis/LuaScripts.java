package com.campus.core.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * classpath Lua 脚本加载。
 *
 * <p>约定：所有 .lua 放 core 模块 {@code resources/lua/}，新增脚本只加文件。
 * 脚本在类初始化时读入内存一次，文件缺失立刻失败，不留到请求期才暴露。
 */
public final class LuaScripts {

    private LuaScripts() {
    }

    public static <T> RedisScript<T> load(String classpath, Class<T> resultType) {
        try {
            String script = StreamUtils.copyToString(
                    new ClassPathResource(classpath).getInputStream(), StandardCharsets.UTF_8);
            return RedisScript.of(script, resultType);
        } catch (IOException e) {
            throw new UncheckedIOException("Lua 脚本读取失败: " + classpath, e);
        }
    }
}

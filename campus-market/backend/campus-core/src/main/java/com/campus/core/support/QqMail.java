package com.campus.core.support;

import java.util.List;
import java.util.Locale;

/**
 * QQ 系邮箱的规范化与判定（学生端注册 / 登录 / 找回，管理端注册 / 登录共用）。
 *
 * <p>只在这里认域名：数据库只保证唯一，放开域名不用改表，改这一个列表即可。
 */
public final class QqMail {

    private static final List<String> SUFFIXES = List.of("@qq.com", "@foxmail.com");

    /** 入库与查库一律小写：大小写不同的同一邮箱不能注册出两个账号 */
    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isQqMail(String email) {
        String e = normalize(email);
        return SUFFIXES.stream().anyMatch(e::endsWith);
    }

    private QqMail() {
    }
}

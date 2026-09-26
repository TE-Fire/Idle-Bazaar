package com.campus.core.satoken;

import cn.dev33.satoken.stp.StpLogic;

/**
 * 学生端 StpLogic（Web + 小程序共用）。
 *
 * <p>Sa-Token 不引入 JWT，会话存 Redis，因此支持「封禁后立即踢下线」这类即时失效。
 * 见 tech/01 8.4。
 */
public class StpUserUtil {

    private StpUserUtil() {
    }

    public static final String TYPE = "user";

    public static StpLogic stpLogic = new StpLogic(TYPE);

    public static void setStpLogic(StpLogic logic) {
        stpLogic = logic;
    }

    public static void login(Object id) {
        stpLogic.login(id);
    }

    public static void logout() {
        stpLogic.logout();
    }

    public static boolean isLogin() {
        return stpLogic.isLogin();
    }

    public static void checkLogin() {
        stpLogic.checkLogin();
    }

    /** 用户 id，未登录抛 NotLoginException */
    public static long getLoginIdAsLong() {
        return stpLogic.getLoginIdAsLong();
    }

    public static String getTokenValue() {
        return stpLogic.getTokenValue();
    }

    /** 封禁时用：按账号 id 踢下线 */
    public static void kickout(Object loginId) {
        stpLogic.kickout(loginId);
    }
}

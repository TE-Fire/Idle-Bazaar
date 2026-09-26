package com.campus.core.satoken;

import cn.dev33.satoken.stp.StpLogic;

/**
 * 管理端 StpLogic。
 *
 * <p>权限校验一律走 {@link #checkPermission(String)}，字符串取自
 * {@link com.campus.common.constant.PermCodes}，与前端 data-perm、数据库 perm_code 三方同名。
 * 前端按钮隐藏只是体验层，这里的校验才是安全边界。
 */
public class StpAdminUtil {

    private StpAdminUtil() {
    }

    public static final String TYPE = "admin";

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

    public static long getLoginIdAsLong() {
        return stpLogic.getLoginIdAsLong();
    }

    public static String getTokenValue() {
        return stpLogic.getTokenValue();
    }

    /** 无权限抛 NotPermissionException，由 GlobalExceptionHandler 转成 1002 */
    public static void checkPermission(String permCode) {
        stpLogic.checkPermission(permCode);
    }

    public static boolean hasPermission(String permCode) {
        return stpLogic.hasPermission(permCode);
    }
}

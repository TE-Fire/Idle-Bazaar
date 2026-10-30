package com.campus.core.context;

import com.campus.common.web.RequestContext;
import com.campus.common.web.RequestContextHolder;
import com.campus.core.satoken.StpUserUtil;

/**
 * 当前登录学生（`app_user`）的取数口，与管理端 {@link AdminContext} 同构。
 *
 * <p>学生端所有写操作都以「这个 id」为前提：发布商品写 `seller_id`、下单写 `buyer_id`、
 * 举报与申诉写申请人。它们**不接受客户端传来的用户 id**——那种写法等于允许替他人下单。
 *
 * <p>与 {@link AdminContext} 一样是**懒加载 + 请求内缓存**：第一次取时调 `StpUserUtil`，
 * 之后同一请求内零成本。异步线程没有请求上下文，取不到值时也不写回，避免上下文泄漏。
 *
 * <p>未登录时抛 `NotLoginException`，由 GlobalExceptionHandler 统一转成 1001，这里刻意不兜底。
 */
public final class UserContext {

    private static final String KEY_USER_ID = "userId";

    private UserContext() {
    }

    /** 当前登录学生 id；未登录抛 NotLoginException（→ 1001） */
    public static long getUserId() {
        RequestContext ctx = RequestContextHolder.get();
        if (ctx != null) {
            Object cached = ctx.getAttribute(KEY_USER_ID);
            if (cached instanceof Long id) {
                return id;
            }
        }
        long id = StpUserUtil.getLoginIdAsLong();
        if (ctx != null) {
            ctx.setAttribute(KEY_USER_ID, id);
        }
        return id;
    }

    /** 当前登录学生 id；未登录返回 null（免登录接口里问「是不是我收藏的」用） */
    public static Long getUserIdOrNull() {
        return StpUserUtil.isLogin() ? getUserId() : null;
    }
}

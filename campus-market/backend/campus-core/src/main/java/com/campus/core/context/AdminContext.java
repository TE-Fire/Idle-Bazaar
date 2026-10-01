package com.campus.core.context;

import com.campus.common.web.RequestContext;
import com.campus.common.web.RequestContextHolder;
import com.campus.core.satoken.StpAdminUtil;

/**
 * 当前登录管理员的取数口。
 *
 * <p>预期消费方有两个，都是**业务层显式取值**，不是框架自动填充：
 * <ul>
 *   <li>{@code item_audit.handler_id}——审核处置时由 service 写入（F-A9「采集处置人」，
 *       该表还带 {@code first_handle_at} / {@code handle_cost_ms}，SLA 靠它算分人明细）；</li>
 *   <li>{@code sys_operation_log.admin_id/admin_name/role_code}——{@code @OpLog} AOP 落库。</li>
 * </ul>
 *
 * <p><b>注意：本项目的表没有通用 {@code create_by}/{@code update_by} 列</b>（22 张表全都没有，
 * 见 db/schema.sql），所以 MyBatis-Plus 的 MetaObjectHandler **不需要**知道操作人，
 * 也不要为了用上本类去给表加审计列——审核域用 item_audit.handler_id 这种**业务字段**记录，
 * 语义更准（它还要配 first_handle_at 一起算首审时长）。
 *
 * <p><b>为什么不在 Filter 里 set 登录人</b>：Filter 早于 Sa-Token 的拦截器执行，
 * 那时 token 还没解析，拿不到 loginId。所以这里改成<b>懒加载</b>——
 * 第一次取时调 {@link StpAdminUtil#getLoginIdAsLong()}，然后把结果挂到请求上下文上，
 * 同一请求后续再取就是零成本（不再读 Redis）。
 *
 * <p><b>只放 adminId，不放用户对象 / 角色</b>：超级管理员改了某人角色后，
 * 上下文里的旧对象是脏的；ID 是唯一不会过期的值。姓名与角色由业务层按 id 查，
 * 别放进上下文。
 *
 * <p>未登录时 {@code StpAdminUtil.getLoginIdAsLong()} 抛 NotLoginException，
 * 这里<b>刻意不 catch</b>——它由 GlobalExceptionHandler 统一转成 1001，
 * 是已有的正确行为；自己兜底返回 null 反而会把「没登录」这个错误吞掉，
 * 退化成后面的 NPE。
 *
 * <p>异步场景（@Async / 线程池 / MQ 消费者）没有请求上下文，getAdminId() 会因
 * sa-token 拿不到 request 而失败。当前项目没有这类入口，约定「异步任务不用本类」；
 * 定时任务要记操作人时，等真有需求再引入 TransmittableThreadLocal，不提前设计。
 */
public final class AdminContext {

    private static final String KEY_ADMIN_ID = "adminId";

    private AdminContext() {
    }

    /** 当前登录管理员 id；未登录抛 NotLoginException（→ 1001） */
    public static long getAdminId() {
        RequestContext ctx = RequestContextHolder.get();
        if (ctx != null) {
            Object cached = ctx.getAttribute(KEY_ADMIN_ID);
            if (cached instanceof Long id) {
                return id;
            }
        }
        long id = StpAdminUtil.getLoginIdAsLong();
        // 只在有上下文时写回：异步线程里没有清理点，写进去就等于泄漏
        if (ctx != null) {
            ctx.setAttribute(KEY_ADMIN_ID, id);
        }
        return id;
    }
}

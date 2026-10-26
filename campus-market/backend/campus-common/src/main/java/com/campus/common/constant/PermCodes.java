package com.campus.common.constant;

/**
 * 管理端权限点常量（18 个）。
 *
 * <p>关键约定：这里的字符串必须与前端 data-perm、数据库 admin_role_perm.perm_code
 * 三方完全一致，任何一处拼错都会造成「前端隐藏了但后端没拦」的安全裂缝。
 * 后端注解一律引用常量，不要手写字符串。
 *
 * <p>矩阵见 business/03 F-A1。
 */
public final class PermCodes {

    private PermCodes() {
    }

    /* ── 看板与报表 ── */
    public static final String DASHBOARD_VIEW = "dashboard.view";
    public static final String REPORT_VIEW = "report.view";

    /* ── 商品 ── */
    public static final String ITEM_VIEW = "item.view";
    public static final String ITEM_OFFLINE = "item.offline";
    public static final String ITEM_DELETE = "item.delete";

    /* ── 用户 ── */
    public static final String USER_VIEW = "user.view";
    public static final String USER_VERIFY = "user.verify";
    public static final String USER_BAN = "user.ban";

    /* ── 审核 ── */
    /** 进审核队列页、看队列与详情的只读权限点；四个角色都有（与 item.view 同一模式） */
    public static final String REVIEW_VIEW = "review.view";
    public static final String REVIEW_JUDGE = "review.judge";
    public static final String REVIEW_FORCE_OFF = "review.forceoff";
    public static final String REVIEW_APPEAL = "review.appeal";

    /* ── 订单 ── */
    /** 进订单页、看列表与详情的只读权限点；四个角色都有（只读角色巡盘也要能看订单） */
    public static final String ORDER_VIEW = "order.view";
    public static final String ORDER_REFUND = "order.refund";
    public static final String ORDER_ARBITRATE = "order.arbitrate";

    /* ── 内容与系统 ── */
    public static final String CONTENT_EDIT = "content.edit";
    public static final String SETTINGS_EDIT = "settings.edit";
    public static final String LOG_VIEW = "log.view";

    /** 全部权限点，用于启动时校验数据库种子是否齐全 */
    public static final String[] ALL = {
            DASHBOARD_VIEW, REPORT_VIEW,
            ITEM_VIEW, ITEM_OFFLINE, ITEM_DELETE,
            USER_VIEW, USER_VERIFY, USER_BAN,
            REVIEW_VIEW, REVIEW_JUDGE, REVIEW_FORCE_OFF, REVIEW_APPEAL,
            ORDER_VIEW, ORDER_REFUND, ORDER_ARBITRATE,
            CONTENT_EDIT, SETTINGS_EDIT, LOG_VIEW
    };
}

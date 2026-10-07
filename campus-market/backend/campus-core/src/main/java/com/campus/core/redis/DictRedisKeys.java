package com.campus.core.redis;

/**
 * 字典缓存 key。本模块两套缓存，各一个 key，返回内容不同所以不共用：
 *
 * <ul>
 *   <li>{@link #ALL}：五张字典的<b>启用项</b>聚合（`DictAllVO`），学生端 `/api/dict/all` 用。</li>
 *   <li>{@link #ADMIN_FULL}：分类/校区/面交/学院的<b>全量（含停用项）</b>聚合（`DictAdminFullVO`），
 *       管理端 `/admin/dict/**` 与商品/审核列表回填用。管理端要看到停用项，启用项缓存缺了它们会解析成 null。</li>
 * </ul>
 *
 * <p>写操作（create / update / 启停）提交后统一 {@code evictCache()}，两个 key 一并 del。
 * TTL 60s 不是主要失效手段，只在 del 失败或 Redis 短暂不可用时，限制脏数据的存活时间。
 */
public final class DictRedisKeys {

    private static final String MODULE = RedisKeys.PREFIX + "dict:";

    /** 五张字典的启用项聚合，值是 `DictAllVO` 的 JSON */
    public static final String ALL = MODULE + "all";

    /** 管理端全量字典（含停用项），值是 `DictAdminFullVO` 的 JSON */
    public static final String ADMIN_FULL = MODULE + "admin:full";

    private DictRedisKeys() {
    }
}

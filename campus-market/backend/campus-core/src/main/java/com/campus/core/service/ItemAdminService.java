package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.BizException;
import com.campus.common.exception.CommonErrorCode;
import com.campus.common.result.PageResult;
import com.campus.core.context.AdminContext;
import com.campus.core.dto.req.ItemListReq;
import com.campus.core.dto.req.ItemOfflineReq;
import com.campus.core.dto.resp.ItemDetailVO;
import com.campus.core.dto.resp.DictCategoryVO;
import com.campus.core.dto.resp.DictCampusVO;
import com.campus.core.dto.resp.DictMeetPointVO;
import com.campus.core.dto.resp.ItemImageVO;
import com.campus.core.dto.resp.ItemListVO;
import com.campus.core.dto.resp.UserPubVO;
import com.campus.core.entity.AppUser;
import com.campus.core.entity.Item;
import com.campus.core.entity.ItemAudit;
import com.campus.core.entity.ItemImage;
import com.campus.core.enums.ItemStatusEnum;
import com.campus.core.errcode.ItemErrorCode;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.mapper.ItemAuditMapper;
import com.campus.core.mapper.ItemImageMapper;
import com.campus.core.mapper.ItemMapper;
import com.campus.core.mapper.ReportMapper;
import com.campus.core.service.cache.CategoryAvgPriceCache;
import com.campus.core.service.cache.UserPubCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端商品管理（business/03 A-B4，流程见 notes/08）。
 *
 * <p><b>权限</b>：由 Controller 的 `@SaCheckPermission` 收口，本类不重复判断——
 * 下架与删除是两个权限点（`item.offline` / `item.delete`），放在 Controller 上一眼能看清
 * 哪个接口要哪个权限，写在 Service 里要翻代码。
 *
 * <p><b>只能下架不能编辑</b>：没有 `updateItem` 方法。business/03 §一明确「不做替用户改价改描述」，
 * 只能强制下架，不能代编辑，避免责任不清。这不是功能缺失，页面上也要把这句话写出来。
 *
 * <p><b>与审核队列（A-B2）的分界</b>：{@link #forceOffline} 是两处共用的处置方法——
 * 商品页（巡盘发现）与审核页（判读后处置）都调它，权限点各挂各的。
 * 结果必须完全一致（status → 7 + 一条审核记录），两套实现必然漂移。
 *
 * <p><b>列表查询用「分页主表 + 批量回填」</b>：先按条件分页查 `item`，
 * 再用这一页的 id 集合分别查卖家、举报数、审核记录，Java 里组装。
 * 不用一条大联表 SQL：LEFT JOIN + GROUP BY 会让 MP 分页的 count 查询把总数算错，
 * 而一页最多几十条，两次查询的开销可以忽略。**这个模式订单 / 用户 / 审核列表都会复用。**
 *
 * <p><b>均价查询不在本类里</b>：分类成交均价是全量 GROUP BY、实测 380–1000ms，
 * 由 {@link CategoryAvgPriceCache} 统一缓存，本类与 `ItemService`（进队的
 * `price_abnormal` 快照）、`ReviewAdminService`（队列回填）三方共用。
 * 本类其余 SQL 都带分页条件、结果不重复，缓存没有收益。加了 `idx_admin_list`
 * 索引后，列表耗时已回到亚毫秒级。
 */
@Service
@RequiredArgsConstructor
public class ItemAdminService {

    /** `item_audit.status`：1 待审核 / 4 已强制下架 */
    private static final int AUDIT_PENDING = 1;
    private static final int AUDIT_FORCED_OFFLINE = 4;

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    /** 上限：不限制的话 pageSize=100000 一次就能把表拖出来 */
    private static final int MAX_PAGE_SIZE = 100;

    private final ItemMapper itemMapper;
    private final ItemImageMapper itemImageMapper;
    private final ItemAuditMapper itemAuditMapper;
    private final AppUserMapper appUserMapper;
    private final ReportMapper reportMapper;
    private final DictService dictService;
    private final CategoryAvgPriceCache categoryAvgPriceCache;
    private final UserPubCache userPubCache;
    private final ItemImageJanitor itemImageJanitor;

    /* ══ 一、列表 ═══════════════════════════════════════════ */

    /**
     * 分页查询。
     *
     * <p>排序固定按创建时间倒序（最新发布在前）：管理端的商品列表是「巡盘」视角，
     * 要看的是刚发生的事；筛选条件里没有排序控件，不给选择比给一堆用不上的选项好。
     */
    public PageResult<ItemListVO> list(ItemListReq req) {
        long pageNum = normalizePageNum(req.getPageNum());
        long pageSize = normalizePageSize(req.getPageSize());

        LambdaQueryWrapper<Item> q = new LambdaQueryWrapper<Item>()
                .eq(Item::getDeleted, 0);

        applyKeyword(q, req.getKeyword());
        applyStatus(q, req.getStatus());
        applyCampus(q, req.getCampusId());
        applyCategory(q, req.getCategoryId());

        q.orderByDesc(Item::getCreateTime).orderByDesc(Item::getId);

        Page<Item> page = itemMapper.selectPage(new Page<>(pageNum, pageSize), q);
        List<Item> rows = page.getRecords();
        if (rows.isEmpty()) {
            return PageResult.of(List.of(), page.getTotal(), pageNum, pageSize);
        }

        Fill fill = buildFill(rows);
        List<ItemListVO> vos = rows.stream().map(r -> toListVO(r, fill)).toList();
        return PageResult.of(vos, page.getTotal(), pageNum, pageSize);
    }

    /* ══ 二、详情 ═══════════════════════════════════════════ */

    /**
     * 详情（只读）。图片按 `sort` 升序，`sort = 0` 是主图。
     *
     * <p>这里不返回「同类目在售商品」之类的推荐数据——管理端详情是取证与处置用的，
     * 不是导购页。
     */
    public ItemDetailVO detail(Long id) {
        Item row = requireItem(id);
        Fill fill = buildFill(List.of(row));

        ItemDetailVO vo = new ItemDetailVO();
        copyTo(row, fill, vo);
        vo.setImages(itemImageMapper.selectList(new LambdaQueryWrapper<ItemImage>()
                        .eq(ItemImage::getItemId, id)
                        .eq(ItemImage::getDeleted, 0)
                        .orderByAsc(ItemImage::getSort)
                        .orderByAsc(ItemImage::getId))
                .stream()
                .map(img -> {
                    ItemImageVO v = new ItemImageVO();
                    v.setId(img.getId());
                    v.setUrl(img.getUrl());
                    v.setSort(img.getSort());
                    return v;
                })
                .toList());
        return vo;
    }

    /* ══ 三、强制下架 ═══════════════════════════════════════ */

    /**
     * 强制下架：商品转「已强制下架」，并留一条审核记录。
     *
     * <p><b>为什么一定要写 `item_audit`</b>：强制下架是可申诉的处置动作，
     * 申诉复审要能拉出「谁、何时、依据什么」（business/03 F-A11 判据）。
     * 这条业务留痕与 `@OpLog` 不是一回事——后者记的是「谁点了哪个按钮」，
     * 属审计层；两者互补，不能互相替代。
     *
     * <p><b>已有待审核记录时更新它，而不是新增一条</b>：那条记录的 `enter_queue_at` 是
     * 真正的进队时间，`first_handle_at − enter_queue_at` 才是首审时长（A9 SLA）。
     * 新增一条会把这个差值算成 0，SLA 数据直接失真。
     *
     * <p>商品已通过审核后再被巡盘下架（没有待审核单据）时才新增一条，
     * 此时 `enter_queue_at` 取当前时间——它不是一次"进队"，而是这条时间线的起点。
     */
    @Transactional(rollbackFor = Exception.class)
    public void forceOffline(Long id, ItemOfflineReq req) {
        Item row = requireItem(id);
        BizAssert.isTrue(ItemStatusEnum.fromCode(row.getStatus()).canForceOffline(),
                ItemErrorCode.ITEM_CANNOT_OFFLINE);

        String reason = req.getReason().trim();
        String note = req.getNote() == null || req.getNote().isBlank() ? null : req.getNote().trim();
        LocalDateTime now = LocalDateTime.now();
        long adminId = AdminContext.getAdminId();

        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, id)
                .eq(Item::getDeleted, 0)
                .set(Item::getStatus, ItemStatusEnum.FORCED_OFFLINE.getCode())
                .set(Item::getRejectReason, reason));

        ItemAudit pending = itemAuditMapper.latestOf(id);
        if (pending != null && pending.getStatus() == AUDIT_PENDING) {
            itemAuditMapper.update(null, new LambdaUpdateWrapper<ItemAudit>()
                    .eq(ItemAudit::getId, pending.getId())
                    .set(ItemAudit::getStatus, AUDIT_FORCED_OFFLINE)
                    .set(ItemAudit::getHandlerId, adminId)
                    .set(ItemAudit::getFirstHandleAt, now)
                    .set(ItemAudit::getRejectReason, reason)
                    .set(ItemAudit::getRemark, note));
            return;
        }
        itemAuditMapper.insert(new ItemAudit()
                .setItemId(id)
                .setStatus(AUDIT_FORCED_OFFLINE)
                .setHandlerId(adminId)
                .setEnterQueueAt(now)
                .setFirstHandleAt(now)
                .setRejectReason(reason)
                .setRemark(note)
                .setPriorityScore(0)
                .setReportCount(0)
                .setImageRisk(0)
                .setPriceAbnormal(0)
                .setCreditDeduct(0));
    }

    /* ══ 四、删除 ═══════════════════════════════════════════ */

    /**
     * 删除商品（软删）。`item_image` 一并软删，不留孤儿图。
     *
     * <p>用同一个时间戳而不是各自取 `System.currentTimeMillis()`：
     * 两行数据的时间不一致会让「按时间清理」这类运维操作对不齐。
     *
     * <p>只删非在售态，校验在 {@link ItemStatusEnum#canDelete()} 里——
     * 已售出商品被订单引用，删了纠纷介入时看不到商品快照（business/03 F-A6）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long id) {
        Item row = requireItem(id);
        BizAssert.isTrue(ItemStatusEnum.fromCode(row.getStatus()).canDelete(),
                ItemErrorCode.ITEM_CANNOT_DELETE);

        long ts = System.currentTimeMillis();
        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, id)
                .eq(Item::getDeleted, 0)
                .set(Item::getDeleted, ts));
        itemImageMapper.update(null, new LambdaUpdateWrapper<ItemImage>()
                .eq(ItemImage::getItemId, id)
                .eq(ItemImage::getDeleted, 0)
                .set(ItemImage::getDeleted, ts));
        // MinIO 对象在提交后清理（回滚则图必须还在）
        itemImageJanitor.cleanupAfterCommit(id);
    }

    /* ══ 五、查询条件 ═══════════════════════════════════════ */

    /**
     * 关键词：匹配标题**或**卖家昵称。
     *
     * <p>昵称在 `app_user` 上，所以先查出命中的卖家 id 再拼 `IN`。
     * 昵称模糊匹配的基数远小于商品数，这个 IN 列表不会长到影响 SQL；
     * 没命中时不拼 `OR`，空 IN 会生成语法错误。
     */
    private void applyKeyword(LambdaQueryWrapper<Item> q, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String kw = keyword.trim();
        List<Long> sellerIds = appUserMapper.selectList(new LambdaQueryWrapper<AppUser>()
                        .like(AppUser::getNickname, kw)
                        .eq(AppUser::getDeleted, 0))
                .stream()
                .map(AppUser::getId)
                .toList();

        q.and(w -> {
            w.like(Item::getTitle, kw);
            if (!sellerIds.isEmpty()) {
                w.or().in(Item::getSellerId, sellerIds);
            }
        });
    }

    private void applyStatus(LambdaQueryWrapper<Item> q, String status) {
        ItemStatusEnum s = parseStatus(status);
        if (s != null) {
            q.eq(Item::getStatus, s.getCode());
        }
    }

    private void applyCampus(LambdaQueryWrapper<Item> q, String campusId) {
        Long id = parseId(campusId, "校区 id");
        if (id != null) {
            q.eq(Item::getCampusId, id);
        }
    }

    /**
     * 分类筛选：传**一级分类** id 时命中它下面所有二级分类。
     *
     * <p>不能沿用前端 mock 的 `categoryId.startsWith(...)`——那是 mock 的 id 编码规则
     * （`ca101` ⊂ `ca1`），雪花 ID 下恒不成立（notes/08 问题 8）。
     *
     * <p>传的是二级 id 时（该 id 下没有子分类）退化成精确匹配，不用调用方区分层级。
     */
    private void applyCategory(LambdaQueryWrapper<Item> q, String categoryId) {
        Long id = parseId(categoryId, "分类 id");
        if (id == null) {
            return;
        }
        List<Long> children = dictService.categories().stream()
                .filter(c -> id.equals(c.getParentId()))
                .map(DictCategoryVO::getId)
                .toList();
        if (children.isEmpty()) {
            q.eq(Item::getCategoryId, id);
        } else {
            q.in(Item::getCategoryId, children);
        }
    }

    /* ══ 六、批量回填 ═══════════════════════════════════════ */

    /**
     * 把一页商品需要的关联数据一次查齐，避免逐行回查。
     *
     * <p>审核记录按 `item_id` 归并时取 `create_time` 最大的那条——
     * 一个商品可以有多条（重提会再进队），要的是时间线上最后一条。
     */
    private Fill buildFill(Collection<Item> rows) {
        Fill fill = new Fill();
        fill.itemIds = rows.stream().map(Item::getId).toList();
        List<Long> sellerIds = rows.stream().map(Item::getSellerId).distinct().toList();

        // 卖家只取昵称与信用分，走公开资料缓存：全字段查 app_user 会把手机号一并select出来，
        // 与「对外载体不含敏感字段」的口径相悖，也白白多查一列
        fill.users = userPubCache.getOrLoad(sellerIds);

        fill.reportCounts = reportMapper.countByItem(fill.itemIds).stream()
                .collect(Collectors.toMap(ReportMapper.ItemReportCountRow::getItemId,
                        ReportMapper.ItemReportCountRow::getCnt, (a, b) -> a));

        fill.audits = itemAuditMapper.selectList(new LambdaQueryWrapper<ItemAudit>()
                        .in(ItemAudit::getItemId, fill.itemIds)
                        .eq(ItemAudit::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(ItemAudit::getItemId, Function.identity(),
                        (a, b) -> a.getCreateTime().compareTo(b.getCreateTime()) >= 0 ? a : b));

        // 分类均价与字典名称是全量小表，一次查完比按 id 过滤更简单，结果形状也稳定
        fill.avgPrices = categoryAvgPriceCache.categoryAvgPrices();

        fill.categoryNames = nameMap(dictService.categories(), DictCategoryVO::getId, DictCategoryVO::getName);
        fill.campusNames = nameMap(dictService.campuses(), DictCampusVO::getId, DictCampusVO::getName);
        fill.meetPointNames = nameMap(dictService.meetPoints(), DictMeetPointVO::getId, DictMeetPointVO::getName);
        return fill;
    }

    private <T> Map<Long, String> nameMap(List<T> rows,
                                          Function<T, Long> idFn,
                                          Function<T, String> nameFn) {
        return rows.stream().collect(Collectors.toMap(idFn, nameFn, (a, b) -> a));
    }

    private ItemListVO toListVO(Item row, Fill fill) {
        ItemListVO vo = new ItemListVO();
        copyTo(row, fill, vo);
        return vo;
    }

    /** 列表与详情共用的字段填充。详情在 {@link #detail} 里补 `images` */
    private void copyTo(Item row, Fill fill, ItemListVO vo) {
        vo.setId(row.getId());
        vo.setTitle(row.getTitle());
        vo.setMainImage(row.getMainImage());
        vo.setCategoryId(row.getCategoryId());
        vo.setCategoryName(fill.categoryNames.get(row.getCategoryId()));
        vo.setPrice(row.getPrice());
        vo.setOriginalPrice(row.getOriginPrice());
        vo.setCondition(row.getGrade());
        vo.setCampusId(row.getCampusId());
        vo.setCampusName(fill.campusNames.get(row.getCampusId()));
        vo.setMeetPointId(row.getMeetPointId());
        vo.setMeetPointName(row.getMeetPointId() == null ? null : fill.meetPointNames.get(row.getMeetPointId()));
        vo.setSellerId(row.getSellerId());

        UserPubVO seller = fill.users.get(row.getSellerId());
        vo.setSellerNick(seller == null ? null : seller.getNickname());
        vo.setSellerCredit(seller == null ? null : seller.getCreditScore());

        vo.setStatus(ItemStatusEnum.fromCode(row.getStatus()));
        vo.setPublishAt(row.getCreateTime());
        vo.setAuditAt(row.getPublishedAt());
        vo.setViewCount(row.getViewCount());
        vo.setWantCount(row.getFavCount());
        vo.setReportCount(fill.reportCounts.getOrDefault(row.getId(), 0L).intValue());

        ItemAudit audit = fill.audits.get(row.getId());
        vo.setRiskScore(audit == null ? 0 : nullSafe(audit.getImageRisk()));

        // 没有成交记录时为 0：前端 isPriceAbnormal 里 avg <= 0 直接返回 false，不会误标红
        BigDecimal avg = fill.avgPrices.get(row.getCategoryId());
        vo.setCategoryAvgPrice(avg == null ? BigDecimal.ZERO : avg.setScale(2, RoundingMode.HALF_UP));

        vo.setDescription(row.getDescription());
    }

    /* ══ 八、内部工具 ════════════════════════════════════════ */

    private Item requireItem(Long id) {
        return BizAssert.notNull(itemMapper.selectOne(new LambdaQueryWrapper<Item>()
                .eq(Item::getId, id)
                .eq(Item::getDeleted, 0)), ItemErrorCode.ITEM_NOT_FOUND);
    }

    private long normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /** 空 = 不筛选；非法值报 1000 而不是静默忽略——静默忽略会让人以为"真的没有数据" */
    private ItemStatusEnum parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ItemStatusEnum.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "商品状态不合法：" + status);
        }
    }

    private Long parseId(String id, String label) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, label + "不合法：" + id);
        }
    }

    private int nullSafe(Integer v) {
        return Objects.requireNonNullElse(v, 0);
    }

    /** 一页商品的关联数据。字段可变，组装完即丢弃，不做成不可变对象 */
    private static class Fill {
        private List<Long> itemIds;
        private Map<Long, UserPubVO> users;
        private Map<Long, ItemAudit> audits;
        private Map<Long, Long> reportCounts;
        private Map<Long, BigDecimal> avgPrices;
        private Map<Long, String> categoryNames;
        private Map<Long, String> campusNames;
        private Map<Long, String> meetPointNames;
    }
}

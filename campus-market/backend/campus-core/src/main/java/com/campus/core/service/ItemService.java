package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.CommonErrorCode;
import com.campus.common.result.PageResult;
import com.campus.core.dto.req.ItemListReq;
import com.campus.core.dto.req.ItemPublishReq;
import com.campus.core.dto.resp.ApiItemVO;
import com.campus.core.dto.resp.DictCampusVO;
import com.campus.core.dto.resp.DictCategoryVO;
import com.campus.core.dto.resp.DictMeetPointVO;
import com.campus.core.dto.resp.ItemImageVO;
import com.campus.core.entity.AppUser;
import com.campus.core.entity.Item;
import com.campus.core.entity.ItemAudit;
import com.campus.core.entity.ItemImage;
import com.campus.core.enums.AuditStatusEnum;
import com.campus.core.enums.ItemStatusEnum;
import com.campus.core.errcode.ItemErrorCode;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.mapper.ItemAuditMapper;
import com.campus.core.mapper.ItemImageMapper;
import com.campus.core.mapper.ItemMapper;
import com.campus.core.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学生端商品维护（发布 / 浏览 / 编辑 / 下架），与管理端 {@link ItemAdminService} 分工。
 *
 * <p>两个易错点：**发布必须同时插入 `item_audit`**（队列取件条件是 `status = 1`，
 * 漏插不报错、商品却永不进审核队列）；本类一切操作以 `seller_id = 当前用户` 为前提，
 * 非本人商品返回 1108 而不是「不存在」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemService {

    /** 列表分页上限：C 端能被脚本翻页，不设限的话深翻页会把列表查询拖垮 */
    private static final long MAX_PAGE_SIZE = 50;

    /** 价格异常判定：标价超过同类目成交均价 3 倍（`item_audit.price_abnormal` 的口径） */
    private static final BigDecimal PRICE_ABNORMAL_TIMES = BigDecimal.valueOf(3);

    private final ItemMapper itemMapper;

    private final ItemImageMapper itemImageMapper;

    private final ItemAuditMapper itemAuditMapper;

    private final AppUserMapper appUserMapper;

    private final DictService dictService;

    private final CategoryAvgPriceCache categoryAvgPriceCache;

    private final ReportMapper reportMapper;

    private final ItemImageJanitor itemImageJanitor;

    /**
     * 发布商品。提交即送审：落 PENDING 并插 `item_audit` 进队（先审后发，business/01 F-W3）；
     * `draft = true` 暂存草稿：落 DRAFT、不进队、豁免图片与描述（含 D 档描述必填），
     * 必填字段仍要齐全——`item` 表这些列 NOT NULL，草稿同样要落库。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long publish(Long sellerId, ItemPublishReq req) {
        AppUser seller = requireUser(sellerId);
        // 发布：字典项必须存在且启用（草稿同样落库，引用也必须有效）
        dictService.validateItemRefs(req.getCategoryId(), req.getGrade(), req.getCampusId(),
                req.getMeetPointId(), true);
        boolean draft = Boolean.TRUE.equals(req.getDraft());
        if (!draft) {
            requireGradeFlawDescribed(req);
        }

        List<String> images = req.getImages() == null ? List.of() : req.getImages();
        Item item = new Item()
                .setSellerId(sellerId)
                .setTitle(req.getTitle())
                .setCategoryId(req.getCategoryId())
                .setGrade(req.getGrade().toUpperCase(Locale.ROOT))
                .setPrice(req.getPrice())
                .setOriginPrice(req.getOriginPrice())
                .setCampusId(req.getCampusId())
                .setMeetPointId(req.getMeetPointId())
                .setDescription(req.getDescription())
                .setNegotiable(nvl(req.getNegotiable()))
                .setDeliveryIncluded(nvl(req.getDeliveryIncluded()))
                .setStatus((draft ? ItemStatusEnum.DRAFT : ItemStatusEnum.PENDING).getCode());
        if (!images.isEmpty()) {
            // 首张即主图，列表页免查图表
            item.setMainImage(images.get(0));
        }
        itemMapper.insert(item);
        saveImages(item.getId(), images);
        if (!draft) {
            enqueueAudit(item, seller);
        }
        return item.getId();
    }

    /** 卖家主动下架：仅限在售品，落 OFFLINE（可重新上架），区别于平台处置的 7 */
    @Transactional(rollbackFor = Exception.class)
    public void offline(Long sellerId, Long itemId) {
        Item item = itemMapper.selectById(itemId);
        BizAssert.notNull(item, ItemErrorCode.ITEM_NOT_FOUND);
        BizAssert.isTrue(sellerId.equals(item.getSellerId()), ItemErrorCode.ITEM_NOT_OWNER);
        BizAssert.isTrue(ItemStatusEnum.fromCode(item.getStatus()) == ItemStatusEnum.ON_SALE,
                ItemErrorCode.ITEM_CANNOT_OFFLINE);
        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, itemId)
                        .eq(Item::getStatus, ItemStatusEnum.ON_SALE.getCode())
                        .set(Item::getStatus, ItemStatusEnum.OFFLINE.getCode()));
    }

    /**
     * 卖家视角的「我的商品」：含草稿等全部状态（business/01 第四节：草稿可继续编辑 / 删除），
     * 支持按状态 / 关键词 / 分类 / 校区筛选。按最近更新倒序——草稿要能最快找到接着改。
     *
     * <p>不回填卖家信息（`fillSellers`）：卖家就是当前用户，昵称与信用分前端本地已有。
     */
    public PageResult<ApiItemVO> mine(Long sellerId, ItemListReq req) {
        long pageNum = req.getPageNum() == null || req.getPageNum() < 1 ? 1 : req.getPageNum();
        long size = req.getPageSize() == null || req.getPageSize() < 1 ? 10 : req.getPageSize();
        long pageSize = Math.min(size, MAX_PAGE_SIZE);

        LambdaQueryWrapper<Item> q = new LambdaQueryWrapper<Item>()
                .eq(Item::getSellerId, sellerId)
                .eq(Item::getDeleted, 0);
        String keyword = trim(req.getKeyword());
        if (keyword != null) {
            q.like(Item::getTitle, keyword);
        }
        Integer status = parseStatus(req.getStatus());
        if (status != null) {
            q.eq(Item::getStatus, status);
        }
        List<Long> categoryIds = expandCategory(req.getCategoryId());
        if (!categoryIds.isEmpty()) {
            q.in(Item::getCategoryId, categoryIds);
        }
        Long campusId = toId(req.getCampusId());
        if (campusId != null) {
            q.eq(Item::getCampusId, campusId);
        }
        q.orderByDesc(Item::getUpdateTime).orderByDesc(Item::getId);

        Page<Item> page = itemMapper.selectPage(new Page<>(pageNum, pageSize), q);
        List<ApiItemVO> list = page.getRecords().stream().map(this::toApiVO).toList();
        return PageResult.of(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 卖家删除自己的商品（软删，与管理端同一写法）：仅草稿 / 已下架 / 审核未过。
     *
     * <p>不含已强制下架——申诉撤销要把商品恢复在售（notes/09 R5），商品没了恢复就失去落点，
     * 那条路只能走申诉等平台处置；也不含在售 / 待审核 / 已售出（有订单引用或正在交易）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long sellerId, Long itemId) {
        Item item = requireOwned(sellerId, itemId);
        ItemStatusEnum status = ItemStatusEnum.fromCode(item.getStatus());
        BizAssert.isTrue(status == ItemStatusEnum.DRAFT || status == ItemStatusEnum.OFFLINE
                || status == ItemStatusEnum.REJECTED, ItemErrorCode.ITEM_CANNOT_DELETE);

        long ts = System.currentTimeMillis();
        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, itemId)
                .eq(Item::getDeleted, 0)
                .set(Item::getDeleted, ts));
        itemImageMapper.update(null, new LambdaUpdateWrapper<ItemImage>()
                .eq(ItemImage::getItemId, itemId)
                .eq(ItemImage::getDeleted, 0)
                .set(ItemImage::getDeleted, ts));
        // MinIO 对象在提交后清理（回滚则图必须还在）
        itemImageJanitor.cleanupAfterCommit(itemId);
    }

    /* ══ 二、浏览 ═════════════════════════════════════════════ */

    /**
     * 搜索在售商品（关键词 / 分类 / 校区），分页。
     *
     * <p>只出 ON_SALE：详情页承载交易入口，未过审商品挂「立即购买」自相矛盾。
     * 关键词走 LIKE（ngram 索引在，但 MATCH...AGAINST 对短关键词不友好，暂不引入）。
     * `categoryId` 传一级时展开其下所有二级（首页金刚区入口传的是一级 id）。
     */
    public PageResult<ApiItemVO> search(ItemListReq req) {
        long pageNum = req.getPageNum() == null || req.getPageNum() < 1 ? 1 : req.getPageNum();
        long size = req.getPageSize() == null || req.getPageSize() < 1 ? 10 : req.getPageSize();
        long pageSize = Math.min(size, MAX_PAGE_SIZE);

        LambdaQueryWrapper<Item> q = new LambdaQueryWrapper<Item>()
                .eq(Item::getStatus, ItemStatusEnum.ON_SALE.getCode())
                .eq(Item::getDeleted, 0);

        String keyword = trim(req.getKeyword());
        if (keyword != null) {
            q.and(w -> w.like(Item::getTitle, keyword).or().like(Item::getDescription, keyword));
        }
        List<Long> categoryIds = expandCategory(req.getCategoryId());
        if (!categoryIds.isEmpty()) {
            q.in(Item::getCategoryId, categoryIds);
        }
        Long campusId = toId(req.getCampusId());
        if (campusId != null) {
            q.eq(Item::getCampusId, campusId);
        }
        q.orderByDesc(Item::getPublishedAt).orderByDesc(Item::getId);

        Page<Item> page = itemMapper.selectPage(new Page<>(pageNum, pageSize), q);
        List<ApiItemVO> list = page.getRecords().stream().map(this::toApiVO).toList();
        fillSellers(list);
        return PageResult.of(list, page.getTotal(), pageNum, pageSize);
    }

    /** 商品详情并累加浏览量；不去重（热度粗略信号，做到 UV 精确要另建去重表，不划算） */
    @Transactional(rollbackFor = Exception.class)
    public ApiItemVO detail(Long itemId) {
        Item item = itemMapper.selectById(itemId);
        BizAssert.notNull(item, ItemErrorCode.ITEM_NOT_FOUND);
        BizAssert.isTrue(ItemStatusEnum.fromCode(item.getStatus()) != ItemStatusEnum.FORCED_OFFLINE,
                ItemErrorCode.ITEM_NOT_FOUND);

        int viewCount = nvl(item.getViewCount()) + 1;
        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, itemId)
                .set(Item::getViewCount, viewCount));

        ApiItemVO vo = toApiVO(item);
        vo.setViewCount(viewCount);
        vo.setImages(itemImageMapper.selectList(new LambdaQueryWrapper<ItemImage>()
                        .eq(ItemImage::getItemId, itemId)
                        .eq(ItemImage::getDeleted, 0)
                        .orderByAsc(ItemImage::getSort)
                        .orderByAsc(ItemImage::getId))
                .stream()
                .map(this::toImageVO)
                .toList());
        fillSellers(List.of(vo));
        return vo;
    }

    /* ══ 三、编辑 ═════════════════════════════════════════════ */

    /**
     * 编辑商品。草稿态商品也可编辑（继续编辑 / 送审）；审核未过可修改后重提；已发布商品改价格或改成色回待审核
     * 重新进队（审核认定的正是这两项，notes/13 第七节），只改描述免审。
     * 图片按入参整体替换，不传差量——差量能从库里算出来。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long sellerId, Long itemId, ItemPublishReq req) {
        Item item = requireOwned(sellerId, itemId);
        ItemStatusEnum status = ItemStatusEnum.fromCode(item.getStatus());
        BizAssert.isTrue(status == ItemStatusEnum.DRAFT || status == ItemStatusEnum.ON_SALE
                || status == ItemStatusEnum.OFFLINE || status == ItemStatusEnum.REJECTED,
                ItemErrorCode.ITEM_CANNOT_EDIT);
        // 编辑：只查存在不查启用——历史商品可引用已停用项，查启用会把它们锁死
        dictService.validateItemRefs(req.getCategoryId(), req.getGrade(), req.getCampusId(),
                req.getMeetPointId(), false);

        // 草稿暂存：草稿态 + 勾选「存草稿」——先存着回头再补，不进队、豁免 D 档描述必填
        boolean draftSave = status == ItemStatusEnum.DRAFT && Boolean.TRUE.equals(req.getDraft());
        if (!draftSave) {
            requireGradeFlawDescribed(req);
        }

        // 草稿送审：草稿态 + 未勾「存草稿」——首次离开草稿态，此前从没进过队，必须进
        boolean submitDraft = status == ItemStatusEnum.DRAFT && !draftSave;
        // 审核未过：修改后重提——不论改什么，保存即回待审核重新进队（business/01 商品状态机）
        boolean resubmit = status == ItemStatusEnum.REJECTED;
        // 核心字段变更：价格或成色变化——已发布商品只改这两项才回审，改其余字段照常免审
        boolean coreChanged = !Objects.equals(item.getPrice(), req.getPrice())
                || !item.getGrade().equalsIgnoreCase(req.getGrade());
        // 回待审核判定：草稿送审、驳回重提必进队；已发布商品（在售 / 已下架）看核心字段是否变更
        boolean reaudit = submitDraft || resubmit || (status != ItemStatusEnum.DRAFT && coreChanged);
        List<String> images = req.getImages() == null ? List.of() : req.getImages();

        LambdaUpdateWrapper<Item> u = new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, itemId)
                .eq(Item::getDeleted, 0)
                .set(Item::getTitle, req.getTitle())
                .set(Item::getCategoryId, req.getCategoryId())
                .set(Item::getGrade, req.getGrade().toUpperCase(Locale.ROOT))
                .set(Item::getPrice, req.getPrice())
                .set(Item::getOriginPrice, req.getOriginPrice())
                .set(Item::getCampusId, req.getCampusId())
                .set(Item::getMeetPointId, req.getMeetPointId())
                .set(Item::getDescription, req.getDescription())
                .set(Item::getNegotiable, nvl(req.getNegotiable()))
                .set(Item::getDeliveryIncluded, nvl(req.getDeliveryIncluded()))
                .set(Item::getMainImage, images.isEmpty() ? null : images.get(0));
        if (reaudit) {
            u.set(Item::getStatus, ItemStatusEnum.PENDING.getCode());
        }
        itemMapper.update(null, u);
        replaceImages(itemId, images);

        if (reaudit) {
            enqueueAudit(itemMapper.selectById(itemId), requireUser(sellerId));
        }
    }

    private void saveImages(Long itemId, List<String> images) {
        for (int i = 0; i < images.size(); i++) {
            itemImageMapper.insert(new ItemImage()
                    .setItemId(itemId)
                    .setUrl(images.get(i))
                    .setSort(i));
        }
    }

    /**
     * 插审核单据并算好四项权重快照。快照而非实时值：实时算会让队列中的单子因后续
     * 数据变化跳位置；等待补偿是时间函数，由 ReviewAdminService 查询时实算。
     */
    private void enqueueAudit(Item item, AppUser seller) {
        // 预留分量：本期没有图片检测源，恒 0（db/01 §5.6 同口径：一期占位，二期接识别服务）
        int imageRisk = 0;
        int priceAbnormal = isPriceAbnormal(item) ? 1 : 0;
        int creditDeduct = (100 - nvl(seller.getCreditScore(), 100)) / 10;
        int reportCount = currentReportCount(item.getId());

        itemAuditMapper.insert(new ItemAudit()
                .setItemId(item.getId())
                .setStatus(AuditStatusEnum.PENDING.getCode())
                .setEnterQueueAt(LocalDateTime.now())
                .setReportCount(reportCount)
                .setImageRisk(imageRisk)
                .setCreditDeduct(creditDeduct)
                .setPriceAbnormal(priceAbnormal)
                .setPriorityScore(reportCount * 3 + Math.round(imageRisk / 10.0f)
                        + creditDeduct + priceAbnormal * 5));
    }

    /** 进队时的实时未结举报数（notes/13 O1 规则 3.1）；没被举报的商品查不到行，按 0 计 */
    private int currentReportCount(Long itemId) {
        List<ReportMapper.ItemReportCountRow> rows = reportMapper.countByItem(List.of(itemId));
        return rows.isEmpty() ? 0 : Math.toIntExact(rows.get(0).getCnt());
    }

    /**
     * 价格异常：标价高于同类目成交均价 3 倍。均价走共享缓存（CategoryAvgPriceCache），
     * 滞后 ≤5 分钟不改变判定。新品无成交时没有均价可比，判为不异常。
     */
    private boolean isPriceAbnormal(Item item) {
        if (item.getCategoryId() == null || item.getPrice() == null) {
            return false;
        }
        BigDecimal avg = categoryAvgPriceCache.categoryAvgPrices().get(item.getCategoryId());
        if (avg == null || avg.signum() <= 0) {
            return false;
        }
        return item.getPrice().compareTo(avg.multiply(PRICE_ABNORMAL_TIMES)) > 0;
    }

    private Item requireOwned(Long sellerId, Long itemId) {
        Item item = itemMapper.selectById(itemId);
        BizAssert.notNull(item, ItemErrorCode.ITEM_NOT_FOUND);
        BizAssert.isTrue(sellerId.equals(item.getSellerId()), ItemErrorCode.ITEM_NOT_OWNER);
        return item;
    }

    /** 状态筛选用枚举名接（与对外输出的形态一致）；空 = 全部，非法名 1000 */
    private Integer parseStatus(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return ItemStatusEnum.valueOf(name.trim().toUpperCase(Locale.ROOT)).getCode();
        } catch (IllegalArgumentException e) {
            BizAssert.isTrue(false, CommonErrorCode.PARAM_ERROR, "未知商品状态：" + name);
            return null;
        }
    }

    private AppUser requireUser(Long userId) {
        return BizAssert.notNull(appUserMapper.selectById(userId), CommonErrorCode.NOT_FOUND);
    }

    /** 图片整体替换：旧行软删，新行按入参顺序重写 sort */
    private void replaceImages(Long itemId, List<String> images) {
        itemImageMapper.update(null, new LambdaUpdateWrapper<ItemImage>()
                .eq(ItemImage::getItemId, itemId)
                .eq(ItemImage::getDeleted, 0)
                .set(ItemImage::getDeleted, 1));
        saveImages(itemId, images);
    }

    /** 分类名与面交点名：字典是小表且已缓存（见 DictService 的管理端全量缓存），可直接全量取值 */
    private ApiItemVO toApiVO(Item item) {
        Map<Long, String> categoryNames = dictService.categories().stream()
                .collect(Collectors.toMap(DictCategoryVO::getId, DictCategoryVO::getName, (a, b) -> a));
        Map<Long, String> meetPointNames = dictService.meetPoints().stream()
                .collect(Collectors.toMap(DictMeetPointVO::getId, DictMeetPointVO::getName, (a, b) -> a));
        Map<Long, String> campusNames = dictService.campuses().stream()
                .collect(Collectors.toMap(DictCampusVO::getId, DictCampusVO::getName, (a, b) -> a));

        ApiItemVO vo = new ApiItemVO();
        vo.setId(item.getId());
        vo.setTitle(item.getTitle());
        vo.setMainImage(item.getMainImage());
        vo.setCategoryId(item.getCategoryId());
        vo.setCategoryName(categoryNames.get(item.getCategoryId()));
        vo.setPrice(item.getPrice());
        vo.setOriginPrice(item.getOriginPrice());
        vo.setGrade(item.getGrade());
        vo.setCampusId(item.getCampusId());
        vo.setCampusName(campusNames.get(item.getCampusId()));
        vo.setMeetPointId(item.getMeetPointId());
        vo.setMeetPointName(meetPointNames.get(item.getMeetPointId()));
        vo.setSellerId(item.getSellerId());
        vo.setNegotiable(item.getNegotiable());
        vo.setDeliveryIncluded(item.getDeliveryIncluded());
        vo.setDescription(item.getDescription());
        vo.setStatus(ItemStatusEnum.fromCode(item.getStatus()));
        vo.setPublishAt(item.getPublishedAt());
        vo.setViewCount(item.getViewCount());
        return vo;
    }

    /** 卖家昵称与信用分批量回填；人均一行，避免列表逐条查库 */
    private void fillSellers(List<ApiItemVO> list) {
        List<Long> ids = list.stream().map(ApiItemVO::getSellerId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, AppUser> users = appUserMapper.selectList(new LambdaQueryWrapper<AppUser>()
                        .in(AppUser::getId, ids)
                        .eq(AppUser::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity(), (a, b) -> a));
        list.forEach(vo -> {
            AppUser u = users.get(vo.getSellerId());
            if (u != null) {
                vo.setSellerNick(u.getNickname());
                vo.setSellerCredit(u.getCreditScore());
                vo.setSellerAuthStatus(u.getAuthStatus());
            }
        });
    }

    /** 一级分类展开为其下所有二级；传二级则退化成精确匹配 */
    private List<Long> expandCategory(String categoryId) {
        Long id = toId(categoryId);
        if (id == null) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        ids.add(id);
        dictService.categories().forEach(c -> {
            if (id.equals(c.getParentId())) {
                ids.add(c.getId());
            }
        });
        return ids;
    }

    private ItemImageVO toImageVO(ItemImage img) {
        ItemImageVO v = new ItemImageVO();
        v.setId(img.getId());
        v.setUrl(img.getUrl());
        v.setSort(img.getSort());
        return v;
    }

    private Long toId(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(v.trim());
        } catch (NumberFormatException e) {
            BizAssert.isTrue(false, CommonErrorCode.PARAM_ERROR, "id 不合法：" + v);
            return null;
        }
    }

    private String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static int nvl(Integer v, int def) {
        return v == null ? def : v;
    }

    private static int nvl(Integer v) {
        return nvl(v, 0);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 成色 D 必须写描述（A/B/C 不要求），发布与编辑共用。
     *
     * <p>D 档「明显使用痕迹」具体显现在哪只能由描述承载，「D + 空描述」是管理端
     * 「成色描述不符」的标准形态，纠纷又靠下单快照取证（business/03 F-A6）。
     * 跨字段规则，单字段注解表达不了，故放 Service；编辑同样要查，否则留两条绕过路径。
     */
    private void requireGradeFlawDescribed(ItemPublishReq req) {
        BizAssert.isTrue(!("D".equalsIgnoreCase(req.getGrade()) && isBlank(req.getDescription())),
                CommonErrorCode.PARAM_ERROR);
    }
}

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
 * 学生端商品维护（发布 / 下架），与 `ItemAdminService` 的分工见 notes/13 第八节。
 *
 * <p><b>发布必须同时插入 `item_audit`</b>，这是本类最容易漏的一步：
 * 只写 `item` 而状态设为待审核，商品会出现在管理端的商品列表里、却**永远不会出现在审核队列**，
 * 因为队列的取件条件是 `item_audit.status = 1`。漏掉这一步，前端发布功能在用户看来就是
 * 「发布成功但管理端找不到」，而后端没有任何报错。
 *
 * <p>校验上与 {@link ItemAdminService} 的另一处差异是所有权：这里的一切操作都以
 * `seller_id = 当前用户` 为前提，不属于自己的商品返回 1108 而不是「不存在」。
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

    /**
     * 发布商品，提交即送审。
     *
     * <p><b>不直接上架</b>（`business/01` F-W3 判据）：校园实名制下先审后发是平台信任机制的一部分，
     * 因此这里落到 {@link ItemStatusEnum#PENDING} 而不是 `ON_SALE`。
     *
     * @return 新商品 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long publish(Long sellerId, ItemPublishReq req) {
        AppUser seller = appUserMapper.selectById(sellerId);
        BizAssert.notNull(seller, CommonErrorCode.NOT_FOUND);
        requireGradeFlawDescribed(req);

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
                .setStatus(ItemStatusEnum.PENDING.getCode());
        if (!images.isEmpty()) {
            // 首张即主图，列表页免查图表
            item.setMainImage(images.get(0));
        }
        itemMapper.insert(item);
        saveImages(item.getId(), images);
        enqueueAudit(item, seller);
        return item.getId();
    }

    /**
     * 卖家主动下架。与管理端强制下架是两件事：这里只允许在售品自用下架，
     * 落到 {@link ItemStatusEnum#OFFLINE}（可重新上架）而不是平台处置的 7。
     */
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

    /* ══ 二、浏览 ═════════════════════════════════════════════ */

    /**
     * 搜索在售商品（关键词 / 分类 / 校区），分页。
     *
     * <p><b>只出 ON_SALE</b>：待审核、已下架、被处置的商品一律不出。这不是为了保密，
     * 而是详情页承载着交易入口——让一件尚未通过审核的商品挂上了「立即购买」是自相矛盾的。
     *
     * <p>关键词走 `LIKE`：`item.title` 上有 ngram 全文索引可用，但全文检索要写 `MATCH...AGAINST`
     * 且对短关键词不友好，早引入只会让查询从这里开始分叉。真出现性能瓶颈再换不迟。
     *
     * <p>`categoryId` 兼容一级与二级：传一级时展开其下所有二级——首页金刚区点进来传的正是一级 id。
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

    /**
     * 商品详情，并累加浏览量。
     *
     * <p>浏览数直接自增而不做去重：它只是商品热度的粗略信号。要做到 UV 级别的精确就得再引入一张去重表，
     * 为一个展示性质的数字付出一整套写入与清理成本并不划算。
     */
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
     * 编辑商品。**改价格或改成色必须重新送审**（`notes/13` 第七节）：
     * 审核通过那次认定的正是价格与成色，二者一变，原先那条「已通过」的单据就不再能代表当前商品；
     * 只改描述不重排队，因为那属于补充说明，不构成新的可售信息。
     *
     * <p>图片整体替换而不是增量追加：`ItemPublishReq.images` 是编辑后的完整清单，
     * 按「新增/删除哪些」传列表会让前端必须维护差量，而这个差量我们能从库里算出来。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long sellerId, Long itemId, ItemPublishReq req) {
        Item item = requireOwned(sellerId, itemId);
        ItemStatusEnum status = ItemStatusEnum.fromCode(item.getStatus());
        BizAssert.isTrue(status == ItemStatusEnum.ON_SALE || status == ItemStatusEnum.OFFLINE,
                ItemErrorCode.ITEM_CANNOT_EDIT);

        // 与 publish 同一规则：编辑同样可能把商品改成「D 档 + 空描述」，见 requireGradeFlawDescribed
        requireGradeFlawDescribed(req);

        boolean coreChanged = !Objects.equals(item.getPrice(), req.getPrice())
                || !item.getGrade().equalsIgnoreCase(req.getGrade());
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
        if (coreChanged) {
            u.set(Item::getStatus, ItemStatusEnum.PENDING.getCode());
        }
        itemMapper.update(null, u);
        replaceImages(itemId, images);

        if (coreChanged) {
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
     * 插入审核的单据并算好四项权重快照。
     *
     * <p>四项是「进队那一刻的风险画像」，必须存下来而不是每次重算——
     * 审核队列要靠它们排序，实时算会让正在处理的单子因后续数据变化而跳位置。
     * 等待补偿是时间的函数，不入库，由 `ReviewAdminService` 查询时按 `enter_queue_at` 实时算。
     */
    private void enqueueAudit(Item item, AppUser seller) {
        int imageRisk = 0;
        int priceAbnormal = isPriceAbnormal(item) ? 1 : 0;
        int creditDeduct = (100 - nvl(seller.getCreditScore(), 100)) / 10;
        int reportCount = 0;

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

    /**
     * 价格异常：标价高于同类目成交均价 3 倍。
     *
     * <p>新品尚无成交时没有均价可比，判为不异常；冒把这个前提写成「异常」会让每个新品都 +5 分。
     */
    private boolean isPriceAbnormal(Item item) {
        if (item.getCategoryId() == null || item.getPrice() == null) {
            return false;
        }
        BigDecimal avg = itemMapper.avgPriceByCategory().stream()
                .filter(r -> item.getCategoryId().equals(r.getCategoryId()))
                .map(ItemMapper.CategoryAvgRow::getAvgPrice)
                .findFirst()
                .orElse(null);
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
     * **成色 D 必须写描述**（A/B/C 不要求），发布与编辑两个入口共用这一条。
     *
     * <p>为什么单盯 D：A 全新未拆、B 几乎全新、C 轻微使用痕迹，买家看到档位就有稳定预期；
     * D「明显使用痕迹」的「明显」具体显现在哪（划痕 / 掉漆 / 屏幕老化……），档位本身给不出
     * 任何信息，唯一载体是描述。允许「D + 空描述」等于挂出一件「有毛病但不告诉你是啥」的商品，
     * 正是管理端驳回原因里「成色描述不符」的标准形态；而订单快照制度要求纠纷介入时按下单时的
     * 成色与描述取证（`business/03` F-A6），发布时堵住这个缺口，审核与纠纷两边都省事。
     *
     * <p>为什么放 Service 而不是 {@code @Valid}：这是跨字段规则——「当 grade = D 时 description
     * 必填」，单字段注解表达不了。
     *
     * <p>为什么编辑也要查：改描述免审、改成色虽回待审核但重审不查这条，只堵发布一个入口，
     * 等于留下「先发低档再改成 D」「D 档清空描述」两条绕过路径。
     */
    private void requireGradeFlawDescribed(ItemPublishReq req) {
        BizAssert.isTrue(!("D".equalsIgnoreCase(req.getGrade()) && isBlank(req.getDescription())),
                CommonErrorCode.PARAM_ERROR);
    }
}

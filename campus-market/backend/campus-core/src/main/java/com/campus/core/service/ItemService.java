package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.CommonErrorCode;
import com.campus.core.dto.req.ItemPublishReq;
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
import java.util.List;
import java.util.Locale;

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

    /** 价格异常判定：标价超过同类目成交均价 3 倍（`item_audit.price_abnormal` 的口径） */
    private static final BigDecimal PRICE_ABNORMAL_TIMES = BigDecimal.valueOf(3);

    private final ItemMapper itemMapper;

    private final ItemImageMapper itemImageMapper;

    private final ItemAuditMapper itemAuditMapper;

    private final AppUserMapper appUserMapper;

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
        BizAssert.isTrue(!("D".equalsIgnoreCase(req.getGrade()) && isBlank(req.getDescription())),
                CommonErrorCode.PARAM_ERROR);

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

    private static int nvl(Integer v, int def) {
        return v == null ? def : v;
    }

    private static int nvl(Integer v) {
        return nvl(v, 0);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.asserts.BizAssert;
import com.campus.common.result.PageResult;
import com.campus.core.dto.req.OfferCreateReq;
import com.campus.core.dto.resp.OfferVO;
import com.campus.core.entity.AppUser;
import com.campus.core.entity.Item;
import com.campus.core.entity.ItemOffer;
import com.campus.core.enums.ItemStatusEnum;
import com.campus.core.enums.OfferStatusEnum;
import com.campus.core.errcode.ItemErrorCode;
import com.campus.core.errcode.OfferErrorCode;
import com.campus.core.errcode.OrderErrorCode;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.mapper.ItemMapper;
import com.campus.core.mapper.ItemOfferMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 议价：买家对「可议价」商品出价，卖家同意后买家按该价付款（状态机见 notes/28）。
 *
 * <p><b>不变量：议价价永不写回 {@code item.price}</b>。价格只落在议价单与被它成交的那笔订单上，
 * 商品详情、列表、分类均价缓存都不受影响——议价成功而未付款时，其他买家看到的仍是标价。
 * 这条不是靠「记得别改」维持的，而是没有第二个写入方：本类全程不碰商品表。
 *
 * <p><b>议价单不锁货</b>：卖家可以同意多个买家的出价，谁先付款谁成交，由
 * {@code OrderService#pay} 的条件更新裁决；成交后同商品其余进行中的议价统一转「已失效」。
 *
 * <p><b>两个时限</b>：卖家同意后 10 分钟内可付款（过时转已失效），买家出价后 7 天无人答复
 * 也转已失效——不设这条，一条沉默的单子会永久占住「同买家同商品仅一条进行中」的名额。
 *
 * <p>所有状态变更都用「带当前状态作条件」的更新并校验影响行数，
 * 卖家同时点同意与拒绝、定时任务与人工操作相撞时，只有一个能生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfferService {

    /** 卖家同意后的付款窗口（分钟）。与订单 30 分钟待付款分开算：那是订单时限，这是价格承诺时限 */
    public static final int PAY_WINDOW_MINUTES = 10;

    /** 待答复保鲜期（天）：超时视为卖家不想谈，释放出价名额 */
    private static final int PENDING_ALIVE_DAYS = 7;

    /** 出价下限比例：拦「1 元试探」这类骚扰性出价，同时给卖家留出还价空间 */
    private static final BigDecimal MIN_RATIO = new BigDecimal("0.10");

    private static final int PAGE_MAX = 20;

    private static final ObjectMapper EXTRA = new ObjectMapper();

    private final ItemOfferMapper offerMapper;
    private final ItemMapper itemMapper;
    private final AppUserMapper appUserMapper;
    private final ChatService chatService;

    /* ══ 一、买家发起 ═══════════════════════════════════════ */

    /**
     * 出价并落一条聊天卡片。
     *
     * <p>标价基准取商品当前价并快照进 `origin_price`：卖家事后改价不影响已发出的议价，
     * 否则同一张单据会随着改价反复变得合法或非法。
     *
     * <p>唯一索引 `uk_alive` 兜住连点：同一买家对同一商品只能有一条进行中议价，
     * 撞索引时转成「已有进行中议价」的可读错误，而不是先查后插（那中间有窗口）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(long buyerId, OfferCreateReq req) {
        Item item = itemMapper.selectById(req.getItemId());
        BizAssert.notNull(item, ItemErrorCode.ITEM_NOT_FOUND);
        BizAssert.isTrue(Objects.equals(item.getNegotiable(), 1), OfferErrorCode.OFFER_NOT_NEGOTIABLE);
        BizAssert.isTrue(ItemStatusEnum.fromCode(item.getStatus()) == ItemStatusEnum.ON_SALE,
                OrderErrorCode.ITEM_NOT_ON_SALE);
        BizAssert.isTrue(!buyerIdEquals(item.getSellerId(), buyerId), OrderErrorCode.BUY_SELF);

        BigDecimal origin = item.getPrice();
        BigDecimal price = req.getOfferPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal floor = origin.multiply(MIN_RATIO).setScale(2, RoundingMode.HALF_UP);
        BizAssert.isTrue(price.compareTo(origin) < 0 && price.compareTo(floor) >= 0,
                OfferErrorCode.OFFER_PRICE_INVALID);

        ItemOffer row = new ItemOffer()
                .setItemId(item.getId())
                .setBuyerId(buyerId)
                .setSellerId(item.getSellerId())
                .setOriginPrice(origin)
                .setOfferPrice(price)
                .setStatus(OfferStatusEnum.PENDING.getCode());
        try {
            offerMapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw OfferErrorCode.OFFER_DUPLICATE.ex();
        }

        chatService.sendCard(buyerId, item.getId(), item.getSellerId(), ChatService.TYPE_OFFER,
                "买家出价 ¥" + price.toPlainString() + "（标价 ¥" + origin.toPlainString() + "）",
                cardJson(row));
        return row.getId();
    }

    /* ══ 二、卖家答复 ═══════════════════════════════════════ */

    /**
     * 同意议价：写 10 分钟付款截止。
     *
     * <p>同意前再确认商品仍在售：这件货可能已被别人付款买走，此时同意等于给出一个
     * 兑现不了的承诺，不如直接告诉卖家「已经卖掉了」。
     */
    @Transactional(rollbackFor = Exception.class)
    public void agree(long sellerId, Long offerId) {
        ItemOffer offer = requireAliveOffer(offerId);
        BizAssert.isTrue(sellerId == offer.getSellerId(), OfferErrorCode.OFFER_ONLY_SELLER);

        Item item = itemMapper.selectById(offer.getItemId());
        BizAssert.notNull(item, ItemErrorCode.ITEM_NOT_FOUND);
        BizAssert.isTrue(ItemStatusEnum.fromCode(item.getStatus()) == ItemStatusEnum.ON_SALE,
                OrderErrorCode.ITEM_NOT_ON_SALE);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusMinutes(PAY_WINDOW_MINUTES);
        int rows = offerMapper.update(null, new LambdaUpdateWrapper<ItemOffer>()
                .eq(ItemOffer::getId, offerId)
                .eq(ItemOffer::getStatus, OfferStatusEnum.PENDING.getCode())
                .set(ItemOffer::getStatus, OfferStatusEnum.AGREED.getCode())
                .set(ItemOffer::getExpireTime, deadline)
                .set(ItemOffer::getHandleTime, now));
        BizAssert.isTrue(rows == 1, OfferErrorCode.OFFER_STATUS_INVALID);

        offer.setStatus(OfferStatusEnum.AGREED.getCode()).setExpireTime(deadline);
        chatService.sendCard(sellerId, offer.getItemId(), offer.getBuyerId(), ChatService.TYPE_OFFER,
                "卖家同意了你的议价，¥" + offer.getOfferPrice().toPlainString()
                        + "，请在 " + PAY_WINDOW_MINUTES + " 分钟内付款",
                cardJson(offer));
    }

    @Transactional(rollbackFor = Exception.class)
    public void reject(long sellerId, Long offerId, String reason) {
        ItemOffer offer = requireAliveOffer(offerId);
        BizAssert.isTrue(sellerId == offer.getSellerId(), OfferErrorCode.OFFER_ONLY_SELLER);

        String text = trim(reason);
        int rows = offerMapper.update(null, new LambdaUpdateWrapper<ItemOffer>()
                .eq(ItemOffer::getId, offerId)
                .eq(ItemOffer::getStatus, OfferStatusEnum.PENDING.getCode())
                .set(ItemOffer::getStatus, OfferStatusEnum.REJECTED.getCode())
                .set(ItemOffer::getHandleTime, LocalDateTime.now())
                .set(ItemOffer::getRejectReason, text));
        BizAssert.isTrue(rows == 1, OfferErrorCode.OFFER_STATUS_INVALID);

        offer.setStatus(OfferStatusEnum.REJECTED.getCode()).setExpireTime(null);
        chatService.sendCard(sellerId, offer.getItemId(), offer.getBuyerId(), ChatService.TYPE_OFFER,
                text == null ? "卖家拒绝了你的议价" : "卖家拒绝了你的议价：" + text,
                cardJson(offer));
    }

    /** 买家撤回：只对进行中的单据有效，撤回后不占出价名额，可立刻重新出价 */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(long buyerId, Long offerId) {
        ItemOffer offer = requireAliveOffer(offerId);
        BizAssert.isTrue(buyerId == offer.getBuyerId(), OfferErrorCode.OFFER_ONLY_BUYER);

        int rows = offerMapper.update(null, new LambdaUpdateWrapper<ItemOffer>()
                .eq(ItemOffer::getId, offerId)
                .in(ItemOffer::getStatus, OfferStatusEnum.PENDING.getCode(), OfferStatusEnum.AGREED.getCode())
                .set(ItemOffer::getStatus, OfferStatusEnum.EXPIRED.getCode()));
        BizAssert.isTrue(rows == 1, OfferErrorCode.OFFER_STATUS_INVALID);

        offer.setStatus(OfferStatusEnum.EXPIRED.getCode()).setExpireTime(null);
        chatService.sendCard(buyerId, offer.getItemId(), offer.getSellerId(), ChatService.TYPE_OFFER,
                "买家撤回了议价", cardJson(offer));
    }

    /* ══ 三、供订单调用 ═════════════════════════════════════ */

    /**
     * 下单取价：返回这笔议价约定的成交金额。
     *
     * <p>校验顺序按「能不能付」排：先确认单据归属（防止拿别人的议价单下单），
     * 再看状态与时限。只在「已同意且未过付款截止」时放行——
     * 过了截止还按议价价成交，等于把 10 分钟时限做成摆设。
     */
    public BigDecimal priceForOrder(Long offerId, long buyerId, long itemId) {
        ItemOffer offer = requireAliveOffer(offerId);
        BizAssert.isTrue(buyerId == offer.getBuyerId(), OfferErrorCode.OFFER_NOT_OWNER);
        BizAssert.isTrue(Objects.equals(offer.getItemId(), itemId),
                OfferErrorCode.OFFER_STATUS_INVALID, "议价单与商品不匹配");
        BizAssert.isTrue(OfferStatusEnum.fromCode(offer.getStatus()) == OfferStatusEnum.AGREED,
                OfferErrorCode.OFFER_STATUS_INVALID, "议价尚未同意或已失效");
        BizAssert.isTrue(offer.getExpireTime() != null && LocalDateTime.now().isBefore(offer.getExpireTime()),
                OfferErrorCode.OFFER_EXPIRED);
        return offer.getOfferPrice();
    }

    /** 付款前复检时限：下单与付款之间可能已过 10 分钟 */
    public void assertPayable(Long offerId) {
        ItemOffer offer = requireAliveOffer(offerId);
        BizAssert.isTrue(OfferStatusEnum.fromCode(offer.getStatus()) == OfferStatusEnum.AGREED,
                OfferErrorCode.OFFER_STATUS_INVALID, "议价已失效");
        BizAssert.isTrue(offer.getExpireTime() != null && LocalDateTime.now().isBefore(offer.getExpireTime()),
                OfferErrorCode.OFFER_EXPIRED);
    }

    /**
     * 成交：议价单转已下单，同商品其余进行中的议价统一失效。
     *
     * <p>由付款成功后调用（与订单状态更新同事务）。其余议价一并作废是必须的——
     * 货已卖出，留着的「已同意」只会让买家点付款时收到一句「商品已被买走」，
     * 不如提前显示「已失效」，让人知道该换一件了。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markOrdered(Long offerId, Long itemId) {
        if (offerId == null) {
            return;
        }
        offerMapper.update(null, new LambdaUpdateWrapper<ItemOffer>()
                .eq(ItemOffer::getId, offerId)
                .set(ItemOffer::getStatus, OfferStatusEnum.ORDERED.getCode()));

        int others = offerMapper.update(null, new LambdaUpdateWrapper<ItemOffer>()
                .eq(ItemOffer::getItemId, itemId)
                .ne(ItemOffer::getId, offerId)
                .in(ItemOffer::getStatus, OfferStatusEnum.PENDING.getCode(), OfferStatusEnum.AGREED.getCode())
                .set(ItemOffer::getStatus, OfferStatusEnum.EXPIRED.getCode()));
        log.info("[offer] 成交 offerId={} itemId={}，同时失效同商品议价 {} 条", offerId, itemId, others);
    }

    /* ══ 四、查询 ═══════════════════════════════════════════ */

    /** 我的议价：asSeller 决定查「我收到的出价」还是「我发出的出价」 */
    public PageResult<OfferVO> list(long userId, boolean asSeller, Integer pageNum, Integer pageSize) {
        long page = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = Math.min(pageSize == null || pageSize < 1 ? 10 : pageSize, PAGE_MAX);

        Page<ItemOffer> p = offerMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ItemOffer>()
                        .eq(asSeller ? ItemOffer::getSellerId : ItemOffer::getBuyerId, userId)
                        .eq(ItemOffer::getDeleted, 0)
                        .orderByDesc(ItemOffer::getCreateTime)
                        .orderByDesc(ItemOffer::getId));
        if (p.getRecords().isEmpty()) {
            return PageResult.of(List.of(), p.getTotal(), page, size);
        }

        Map<Long, Item> items = itemMapper.selectBatchIds(p.getRecords().stream()
                        .map(ItemOffer::getItemId).distinct().toList())
                .stream().collect(Collectors.toMap(Item::getId, Function.identity(), (a, b) -> a));
        Map<Long, AppUser> users = appUserMapper.selectBatchIds(p.getRecords().stream()
                        .map(o -> asSeller ? o.getBuyerId() : o.getSellerId()).distinct().toList())
                .stream().collect(Collectors.toMap(AppUser::getId, Function.identity(), (a, b) -> a));

        List<OfferVO> list = p.getRecords().stream()
                .map(o -> toVO(o, items.get(o.getItemId()), users.get(asSeller ? o.getBuyerId() : o.getSellerId()), asSeller))
                .toList();
        return PageResult.of(list, p.getTotal(), page, size);
    }

    /** 单条详情：聊天卡片点开后用它拿最新状态（卡片消息里的状态是发送时的快照） */
    public OfferVO detail(long userId, Long offerId) {
        ItemOffer offer = offerMapper.selectOne(new LambdaQueryWrapper<ItemOffer>()
                .eq(ItemOffer::getId, offerId)
                .eq(ItemOffer::getDeleted, 0));
        BizAssert.notNull(offer, OfferErrorCode.OFFER_NOT_FOUND);
        boolean asSeller = userId == offer.getSellerId();
        BizAssert.isTrue(asSeller || userId == offer.getBuyerId(), OfferErrorCode.OFFER_NOT_OWNER);

        Item item = itemMapper.selectById(offer.getItemId());
        AppUser peer = appUserMapper.selectById(asSeller ? offer.getBuyerId() : offer.getSellerId());
        return toVO(offer, item, peer, asSeller);
    }

    /* ══ 五、超时失效 ═══════════════════════════════════════ */

    /**
     * 每分钟扫一次过期单据。两条更新都是单表条件更新，不需要逐笔事务：
     * 影响 0 行就代表已被人工处理，本身就是幂等的。
     */
    @Scheduled(fixedDelay = 60 * 1000L)
    public void expireOverdue() {
        LocalDateTime now = LocalDateTime.now();
        int agreed = offerMapper.update(null, new LambdaUpdateWrapper<ItemOffer>()
                .eq(ItemOffer::getStatus, OfferStatusEnum.AGREED.getCode())
                .lt(ItemOffer::getExpireTime, now)
                .set(ItemOffer::getStatus, OfferStatusEnum.EXPIRED.getCode()));
        int pending = offerMapper.update(null, new LambdaUpdateWrapper<ItemOffer>()
                .eq(ItemOffer::getStatus, OfferStatusEnum.PENDING.getCode())
                .lt(ItemOffer::getCreateTime, now.minusDays(PENDING_ALIVE_DAYS))
                .set(ItemOffer::getStatus, OfferStatusEnum.EXPIRED.getCode()));
        if (agreed + pending > 0) {
            log.info("[offer] 超时失效 {} 条（已同意 {} / 待答复 {}）", agreed + pending, agreed, pending);
        }
    }

    /* ══ 六、内部 ═══════════════════════════════════════════ */

    private ItemOffer requireAliveOffer(Long offerId) {
        ItemOffer offer = offerMapper.selectOne(new LambdaQueryWrapper<ItemOffer>()
                .eq(ItemOffer::getId, offerId)
                .eq(ItemOffer::getDeleted, 0));
        BizAssert.notNull(offer, OfferErrorCode.OFFER_NOT_FOUND);
        return offer;
    }

    private OfferVO toVO(ItemOffer offer, Item item, AppUser peer, boolean asSeller) {
        OfferVO vo = new OfferVO();
        vo.setId(offer.getId());
        vo.setItemId(offer.getItemId());
        vo.setItemTitle(item == null ? null : item.getTitle());
        vo.setItemMainImage(item == null ? null : item.getMainImage());
        vo.setItemPrice(item == null ? null : item.getPrice());
        vo.setOriginPrice(offer.getOriginPrice());
        vo.setOfferPrice(offer.getOfferPrice());
        vo.setStatus(OfferStatusEnum.fromCode(offer.getStatus()));
        vo.setExpireTime(OfferStatusEnum.fromCode(offer.getStatus()) == OfferStatusEnum.AGREED
                ? offer.getExpireTime() : null);
        vo.setBuyerId(offer.getBuyerId());
        vo.setSellerId(offer.getSellerId());
        vo.setPeerNick(peer == null ? null : peer.getNickname());
        vo.setRejectReason(offer.getRejectReason());
        vo.setCreateTime(offer.getCreateTime());
        vo.setAsSeller(asSeller);
        return vo;
    }

    /** 卡片载荷：金额一律字符串（对外口径），状态取枚举名。</p>
     *
     * <p>公开给 {@code ChatService} 复用：消息列表要用同一份结构重建最新状态，
     * 两处各写一遍必然在加字段时漏掉一边。
     */
    public static String cardJson(ItemOffer offer) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("offerId", offer.getId());
        map.put("itemId", offer.getItemId());
        map.put("originPrice", offer.getOriginPrice().toPlainString());
        map.put("offerPrice", offer.getOfferPrice().toPlainString());
        map.put("status", OfferStatusEnum.fromCode(offer.getStatus()).name());
        if (offer.getExpireTime() != null) {
            map.put("expireTime", offer.getExpireTime().toString());
        }
        try {
            return EXTRA.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("[offer] 卡片载荷组装失败 offerId={}", offer.getId(), e);
            return null;
        }
    }

    private static boolean buyerIdEquals(Long sellerId, Long buyerId) {
        return Objects.equals(sellerId, buyerId);
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

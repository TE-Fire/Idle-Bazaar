package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.BizException;
import com.campus.common.exception.CommonErrorCode;
import com.campus.common.result.PageResult;
import com.campus.core.dto.resp.OrderVO;
import com.campus.core.entity.Appeal;
import com.campus.core.entity.AppUser;
import com.campus.core.entity.DictCampus;
import com.campus.core.entity.DictMeetPoint;
import com.campus.core.entity.Item;
import com.campus.core.entity.TradeOrder;
import com.campus.core.entity.UserAddress;
import com.campus.core.enums.AddressKindEnum;
import com.campus.core.enums.AppealBizTypeEnum;
import com.campus.core.enums.AppealStatusEnum;
import com.campus.core.enums.CancelTypeEnum;
import com.campus.core.enums.DeliveryTypeEnum;
import com.campus.core.enums.ItemStatusEnum;
import com.campus.core.enums.OrderStatusEnum;
import com.campus.core.errcode.OrderErrorCode;
import com.campus.core.mapper.AppealMapper;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.mapper.DictCampusMapper;
import com.campus.core.mapper.DictMeetPointMapper;
import com.campus.core.mapper.ItemMapper;
import com.campus.core.mapper.TradeOrderMapper;
import com.campus.core.mapper.UserAddressMapper;
import com.campus.core.redis.ItemRedisKeys;
import com.campus.core.redis.LuaScripts;
import com.campus.core.service.cache.CategoryAvgPriceCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 学生端交易全链路（下单 / 付款占位 / 核销 / 收货 / 取消与退款 / 纠纷裁决），口径见 notes/13，
 *
 * <p><b>无预定机制是本类的立足点</b>：下单只写一条待付款订单，商品保持在售、可被他人同时下单。
 * 所有权判定的唯一时刻是付款成功的那一刻，因此 {@link #pay} 是唯一有并发竞争的方法，
 * 其余方法要么只读、要么只改自己的订单。
 *
 * <p><b>占位用两级而不是一级</b>：Redis Lua 前置拦截做削峰与提前失败，MySQL 条件更新做最终裁决。
 * 二者不是替代关系——Lua 无法访问 DB，也保证不了持久一致；正确性始终由 DB 的条件更新提供。
 * Redis 不可用时的最坏结果是多放行一次到 DB（被 DB 拦住）或拒绝一个已无货的请求（TTL 后自愈），
 * **不会出现超卖**。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    /** 待付款超时时长，与 `idx_expire(status, expire_time)` 的扫描口径一致 */
    private static final int PAY_TIMEOUT_MINUTES = 30;

    /** 卖家处理退款申请的时限，超时由 {@link #autoRefund} 自动同意（notes/13 第四节） */
    private static final int AUTO_REFUND_HOURS = 24;

    private static final String AUTO_REFUND_REASON = "卖家超时未处理，系统自动同意退款";

    /** 裁决留痕统一文案：让 `cancel_reason` 在同为终态的订单里能区分出「被裁决」这一来源 */
    private static final String DISPUTE_SETTLED_REASON = "管理端裁决结案";

    /** 强制退款的留痕前缀，与裁决结案区分开：一个是代卖家退款，一个是判输赢 */
    private static final String FORCE_REFUND_REASON = "管理端强制退款";

    /** 纠纷默认理由：前端允许只发起不填理由，申诉单 reason 不能空着进队列 */
    private static final String DISPUTE_DEFAULT_REASON = "订单纠纷，申请平台裁决";

    /** Lua 返回值：抢到标记 */
    private static final int OCCUPY_WON = 1;

    /** Lua 返回值：同一订单重入，按幂等处理 */
    private static final int OCCUPY_REENTRY = 2;

    /** Lua 返回值：冲突（他人已锁定或已售出） */
    private static final int OCCUPY_CONFLICT = 0;

    /** Lua 返回值：缓存缺失，放行交给 DB 裁决 */
    private static final int OCCUPY_MISS = -1;

    /**
     * 抢占商品占位标记（{@code lua/occupy.lua}，`tech/01` §9.1；holder hash 由 KEYS[2] 传入以遵守 key 常量规范）。
     *
     * <p>返回 -1 表示缓存缺失，**必须放行**：缓存未预热或已被淘汰时 Lua 无从判定，
     * 若按「缺失即拒绝」设计，缓存一失效商品就永远卖不掉。放行是安全的，DB 会拦住。
     */
    private static final RedisScript<Long> OCCUPY_SCRIPT = LuaScripts.load("lua/occupy.lua", Long.class);

    /**
     * 回滚占位标记，**必须先校验 holder 是自己的订单号**（{@code lua/release.lua}）。
     *
     * <p>少了这层校验，DB 走得慢的那个请求会在自己的更新失败后，把别人刚抢到的锁释放掉，
     * 于是出现两个赢家。这是本方案里最隐蔽的一处正确性依赖。
     */
    private static final RedisScript<Long> RELEASE_SCRIPT = LuaScripts.load("lua/release.lua", Long.class);

    /** 超时扫描每轮处理上限：积压时分多轮消化，单轮耗时与连接持有时间有上界 */
    private static final int EXPIRE_SCAN_BATCH = 200;

    private final TradeOrderMapper orderMapper;

    private final ItemMapper itemMapper;

    private final AppealMapper appealMapper;

    /** 订单详情补交易对方昵称 / 头像、面交点名称与校区时用 */
    private final AppUserMapper appUserMapper;

    private final DictMeetPointMapper meetPointMapper;

    private final DictCampusMapper campusMapper;

    private final UserAddressMapper userAddressMapper;

    private final CategoryAvgPriceCache categoryAvgPriceCache;

    /** 批量逐笔取消用：单笔独立事务，失败不牵连同批已提交的（见 cancelOne） */
    private final PlatformTransactionManager txManager;

    /** 占位标记是纯字符串状态机，用 StringRedisTemplate 即可，不必绕一层 JSON 序列化 */
    private final StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper;

    /** 下单取价与成交后失效议价（notes/28）：议价价只进这一笔订单，不写回商品 */
    private final OfferService offerService;

    /**
     * 下单：**只插订单，不锁商品**。
     *
     * <p>商品快照在下单这一刻固化进订单，之后即便卖家改价改描述，
     * 历史订单里留存的原样仍是纠纷取证的可信数据源（notes/13 第六节）。
     *
     * <p><b>金额有两条来源</b>：不传 `offerId` 时按商品标价；传了则按那笔议价的出价，
     * 且必须是「已同意且未过 10 分钟付款截止」的单据。无论走哪条，金额都只固化进本订单，
     * 商品标价不动——这是「议价成功未付款不影响其他买家看到的价格」的实现方式。
     *
     * @return 新订单 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long buyerId, Long itemId, Long meetPointId, Long addressId, LocalDateTime meetTime,
                       Long offerId) {
        Item item = itemMapper.selectById(itemId);
        BizAssert.notNull(item, CommonErrorCode.NOT_FOUND);
        BizAssert.isTrue(ItemStatusEnum.fromCode(item.getStatus()) == ItemStatusEnum.ON_SALE,
                OrderErrorCode.ITEM_NOT_ON_SALE);
        BizAssert.isTrue(!buyerId.equals(item.getSellerId()), OrderErrorCode.BUY_SELF);

        // 收货方式决定后续的地址交互与付款后状态：面交走面交点、自提用卖家提货地址、送货上门用买家收货地址。
        // deliveryType 在创建时从商品抄入订单并固化，之后不再随商品改动。
        DeliveryTypeEnum deliveryType = DeliveryTypeEnum.fromCode(item.getDeliveryType());
        Long orderAddressId = null;
        String contact = null, phone = null, detail = null;

        if (deliveryType == DeliveryTypeEnum.DELIVERY) {
            // 送货上门：收货地址必须由买家自己选，且是其 SHIP 地址，不得代选他人地址
            UserAddress addr = requireAddress(buyerId, addressId, AddressKindEnum.SHIP.getCode(),
                    "送货上门订单必须选择收货地址");
            orderAddressId = addr.getId();
            contact = addr.getContactName();
            phone = addr.getContactPhone();
            detail = addressDetailOf(addr);
        } else if (deliveryType == DeliveryTypeEnum.PICKUP) {
            // 自提：提货地址由卖家在商品上预设，买家不可选；抄入订单并快照
            BizAssert.notNull(item.getPickupAddressId(), OrderErrorCode.ORDER_STATUS_INVALID,
                    "该自提商品未设置提货地址");
            UserAddress addr = requireAddress(item.getSellerId(), item.getPickupAddressId(),
                    AddressKindEnum.PICKUP.getCode(), "提货地址不存在或不属于当前卖家");
            orderAddressId = addr.getId();
            contact = addr.getContactName();
            phone = addr.getContactPhone();
            detail = addressDetailOf(addr);
        }

        LocalDateTime now = LocalDateTime.now();
        // 成交金额两条来源：议价单（须已同意且未过 10 分钟截止）或商品标价。
        // itemPrice 快照始终留标价，这样订单详情能同时看到「原价多少、议价成交多少」。
        BigDecimal amount = offerId == null
                ? item.getPrice()
                : offerService.priceForOrder(offerId, buyerId, itemId);
        TradeOrder order = new TradeOrder()
                .setOrderNo(newOrderNo())
                .setItemId(itemId)
                .setBuyerId(buyerId)
                .setSellerId(item.getSellerId())
                .setAmount(amount)
                .setOfferId(offerId)
                .setStatus(OrderStatusEnum.WAIT_PAY.getCode())
                .setDeliveryType(deliveryType.getCode())
                .setAddressId(orderAddressId)
                .setAddressContact(contact)
                .setAddressPhone(phone)
                .setAddressDetail(detail)
                .setMeetPointId(deliveryType == DeliveryTypeEnum.MEET ? meetPointId : null)
                .setMeetTime(meetTime)
                .setVerifyCode(newVerifyCode())
                .setExpireTime(now.plusMinutes(PAY_TIMEOUT_MINUTES))
                .setItemTitle(item.getTitle())
                .setItemPrice(item.getPrice())
                .setItemGrade(item.getGrade())
                .setItemMainImage(item.getMainImage())
                .setSnapshot(toSnapshot(item));
        orderMapper.insert(order);
        return order.getId();
    }

    /**
     * 模拟支付并抢占商品。这是并发下「恰好一个赢家」的唯一判定点。
     *
     * <p>幂等是这里的一等公民：同一个订单重复回调只会进一次 Lua，
     * 第二次由 {@code OCCUPY_REENTRY} 识别，后续 DB 条件更新因状态已变而自动失效，安全返回。
     */
    @Transactional(rollbackFor = Exception.class)
    public void pay(Long orderId, Long buyerId) {
        TradeOrder order = orderMapper.selectById(orderId);
        BizAssert.notNull(order, OrderErrorCode.ORDER_NOT_FOUND);
        BizAssert.isTrue(buyerId.equals(order.getBuyerId()), OrderErrorCode.ORDER_NOT_OWNER);

        OrderStatusEnum status = OrderStatusEnum.fromCode(order.getStatus());
        if (status != OrderStatusEnum.WAIT_PAY) {
            log.info("[order] 重复支付回调，放行：orderId={}, status={}", orderId, status.getDesc());
            return;
        }
        BizAssert.isTrue(LocalDateTime.now().isBefore(order.getExpireTime()),
                OrderErrorCode.ORDER_TIMEOUT);

        String stateKey = ItemRedisKeys.buildStateKey(order.getItemId());
        String holder = String.valueOf(order.getId());

        // 议价单的 10 分钟付款截止要在这里再验一次：下单时校验过，但下单与付款之间可能已经超时。
        // 放在占位之前——过了时限就该拒绝，不该先占住商品再回滚。
        if (order.getOfferId() != null) {
            offerService.assertPayable(order.getOfferId());
        }

        long result = occupy(stateKey, holder);
        BizAssert.isTrue(result != OCCUPY_CONFLICT, OrderErrorCode.ITEM_SOLD_OUT);

        boolean done = false;
        try {
            claimItem(order.getItemId());
            LocalDateTime now = LocalDateTime.now();
            // 付款后状态由收货方式决定：面交→待面交、自提→待自提、送货上门→待发货
            OrderStatusEnum afterPay = switch (DeliveryTypeEnum.fromCode(order.getDeliveryType())) {
                case MEET -> OrderStatusEnum.WAIT_MEET;
                case PICKUP -> OrderStatusEnum.WAIT_PICKUP;
                case DELIVERY -> OrderStatusEnum.WAIT_SHIP;
            };
            int rows = orderMapper.update(null, new LambdaUpdateWrapper<TradeOrder>()
                    .eq(TradeOrder::getId, orderId)
                    .eq(TradeOrder::getStatus, OrderStatusEnum.WAIT_PAY.getCode())
                    .set(TradeOrder::getStatus, afterPay.getCode())
                    .set(TradeOrder::getPayTime, now));
            BizAssert.isTrue(rows == 1, OrderErrorCode.ORDER_STATUS_INVALID);

            cancelSiblings(order.getItemId(), orderId, CancelTypeEnum.SYSTEM_TIMEOUT,
                    "商品已被其他买家付款");
            // 议价成交：本单议价转已下单，同商品其余进行中的议价一并失效（货已卖出）
            offerService.markOrdered(order.getOfferId(), order.getItemId());
            // 终态：不再设 TTL，商品既已售出，标记就该一直挡住后来的请求
            stringRedisTemplate.opsForValue().set(stateKey, ItemRedisKeys.STATE_SOLD);
            stringRedisTemplate.opsForHash().delete(ItemRedisKeys.HOLDER, stateKey);
            done = true;
        } finally {
            if (!done) {
                release(stateKey, holder);
            }
        }
    }

    /**
     * MySQL 侧的条件更新：**真正的裁决者**。
     *
     * <p>以影响行数 = 1 判定抢到。`version` 参与条件是为了让同一时刻读取到相同版本号的多个请求
     * 只有一个能改写成功，其余落回 0 行。
     */
    private void claimItem(Long itemId) {
        Item item = itemMapper.selectById(itemId);
        BizAssert.notNull(item, CommonErrorCode.NOT_FOUND);
        int rows = itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, itemId)
                .eq(Item::getStatus, ItemStatusEnum.ON_SALE.getCode())
                .eq(Item::getVersion, item.getVersion())
                .set(Item::getStatus, ItemStatusEnum.SOLD.getCode())
                .setSql("version = version + 1"));
        BizAssert.isTrue(rows == 1, OrderErrorCode.ITEM_SOLD_OUT);
    }

    /**
     * 赢家占位后，同商品其余待付款订单必须立刻取消。
     *
     * <p>不做这一步，那些订单会一直悬到各自的超时才消失，
     * 而这期间买家看到的是「已付款待面交」的假象。
     */
    private void cancelSiblings(Long itemId, Long winnerId, CancelTypeEnum type, String reason) {
        List<TradeOrder> siblings = orderMapper.selectList(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getItemId, itemId)
                .eq(TradeOrder::getStatus, OrderStatusEnum.WAIT_PAY.getCode())
                .ne(TradeOrder::getId, winnerId));
        siblings.forEach(o -> orderMapper.update(null, new LambdaUpdateWrapper<TradeOrder>()
                .eq(TradeOrder::getId, o.getId())
                .eq(TradeOrder::getStatus, OrderStatusEnum.WAIT_PAY.getCode())
                .set(TradeOrder::getStatus, OrderStatusEnum.CANCELED.getCode())
                .set(TradeOrder::getCancelBy, type.getCode())
                .set(TradeOrder::getCancelReason, reason)));
    }

    /**
     * 超时未付款订单的补偿扫描（`tech/01` §9.2 的兜底环节，决策 1 选定本期就用它承担超时取消）。
     *
     * <p>每 10 分钟一次，取消 `expire_time` 已过、仍是待付款的订单。取消本身带条件更新：
     * 并发真实付款成功时状态已不是待付款，这里的更新自然落空，
     * 不会出现「付款成功后被超时任务取消」。
     *
     * <p><b>方法本身不开事务</b>：整批一个大事务，任何一笔失败都全批回滚，且行锁从第一笔
     * 握到最后一笔，阻塞的恰是这批订单的真实付款。逐笔独立事务由 {@link #cancelOne} 负责，
     * 失败的订单状态未变，下一轮扫描会再次捞到——重试由幂等性免费提供，无需补偿机制。
     * 每轮限量并按过期时间排序：积压时分多轮消化，fixedDelay 保证轮次不重叠。
     */
    @Scheduled(fixedDelay = 10 * 60 * 1000L)
    public void cancelExpired() {
        List<TradeOrder> expired = orderMapper.selectList(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getStatus, OrderStatusEnum.WAIT_PAY.getCode())
                .le(TradeOrder::getExpireTime, LocalDateTime.now())
                .orderByAsc(TradeOrder::getExpireTime)
                .last("LIMIT " + EXPIRE_SCAN_BATCH));
        int count = 0;
        for (TradeOrder order : expired) {
            try {
                count += cancelOne(order, CancelTypeEnum.SYSTEM_TIMEOUT, "30 分钟未付款，系统自动取消");
            } catch (Exception e) {
                // 单笔失败只丢这一笔：状态未变，下轮扫描自动重试，不能让它拖垮已成功的
                log.error("[order] 单笔超时取消失败，留待下次扫描重试：orderId={}", order.getId(), e);
            }
        }
        if (count > 0) {
            log.info("[order] 超时取消待付款订单 {} 笔", count);
        }
    }

    /**
     * 单笔取消。**状态校验写在 WHERE 里而不是先查后判**：
     * 两个入口同时操作同一笔订单时，先查后判的两个请求都会通过，后一个会覆盖前一个的结果。
     * 影响行数 0 即表示状态已被别人改走，本次作废。
     *
     * <p>单笔一个独立事务（REQUIRES_NEW）：批量调用方（{@link #cancelExpired}）失败只回滚
     * 这一笔，不影响同批已提交的。异常照实抛给调用方，由它决定吞掉重试还是让外层回滚——
     * 「取消失败该怎么办」两个入口语义不同，不能在这里统一处理。
     */
    private int cancelOne(TradeOrder order, CancelTypeEnum type, String reason) {
        TransactionTemplate template = new TransactionTemplate(txManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> orderMapper.update(null, new LambdaUpdateWrapper<TradeOrder>()
                .eq(TradeOrder::getId, order.getId())
                .eq(TradeOrder::getStatus, order.getStatus())
                .set(TradeOrder::getStatus, OrderStatusEnum.CANCELED.getCode())
                .set(TradeOrder::getCancelBy, type.getCode())
                .set(TradeOrder::getCancelReason, reason)));
    }

    /* ══ 四、履约：核销与确认收货 ══════════════════════════════ */

    /**
     * 扫码核销：待面交 → 已验货。
     *
     * <p><b>防单方核销</b>：核销码由卖家持有（仅卖家侧的 {@link OrderVO} 携带，买家侧永远拿不到），
     * 执行核销的却只能是买家——`verify_by` 恒为买家。于是任一方都无法单独完成：
     * 卖家握着码但本接口拒绝卖家调用，买家能调本接口却看不到码，必须双方见面、卖家出示码、买家输入。
     *
     * <p>核销码一单一张（`uk_verify_code` 唯一约束），码对得上才放行。
     */
    @Transactional(rollbackFor = Exception.class)
    public void verify(Long orderId, String code, Long userId) {
        TradeOrder order = requireOrder(orderId);
        // 硬不变量：只有买家能核销。卖家是当事人但不能核销，故不能用 isParty 代替。
        BizAssert.isTrue(userId.equals(order.getBuyerId()), OrderErrorCode.VERIFY_ONLY_BUYER);
        BizAssert.isTrue(order.getVerifyCode() != null && order.getVerifyCode().equals(code),
                OrderErrorCode.VERIFY_CODE_INVALID);

        int rows = orderMapper.update(null, new LambdaUpdateWrapper<TradeOrder>()
                .eq(TradeOrder::getId, orderId)
                .eq(TradeOrder::getStatus, OrderStatusEnum.WAIT_MEET.getCode())
                .set(TradeOrder::getStatus, OrderStatusEnum.VERIFIED.getCode())
                .set(TradeOrder::getVerifyTime, LocalDateTime.now())
                .set(TradeOrder::getVerifyBy, userId));
        BizAssert.isTrue(rows == 1, OrderErrorCode.ORDER_STATUS_INVALID, "只有待面交的订单可以核销");
    }

    /**
     * 卖家发货（仅送货上门）：待发货 → 已发货。
     *
     * <p>其它收货方式没有发货动作——面交靠线下核销、自提靠买家到店自取，
     * 都不经过「卖家发货」这一态，故 {@code delivery_type} 非送货上门时直接拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public void ship(Long orderId, Long sellerId) {
        TradeOrder order = requireOrder(orderId);
        BizAssert.isTrue(sellerId.equals(order.getSellerId()), OrderErrorCode.ORDER_NOT_OWNER);
        BizAssert.isTrue(DeliveryTypeEnum.fromCode(order.getDeliveryType()) == DeliveryTypeEnum.DELIVERY,
                OrderErrorCode.ORDER_STATUS_INVALID, "只有送货上门订单需要发货");
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<TradeOrder>()
                .eq(TradeOrder::getId, orderId)
                .eq(TradeOrder::getStatus, OrderStatusEnum.WAIT_SHIP.getCode())
                .set(TradeOrder::getStatus, OrderStatusEnum.SHIPPED.getCode()));
        BizAssert.isTrue(rows == 1, OrderErrorCode.ORDER_STATUS_INVALID, "只有待发货订单可以标记发货");
    }

    /**
     * 买家确认收货：已验货 / 已发货 / 待自提 → 已完成。
     *
     * <p>三种收货方式共用同一个「确认收货」终态入口，区别只是进入它的前置态不同：
     * 面交要先验货（VERIFIED）、送货上门要先发货（SHIPPED）、自提则付款后直接待自提（WAIT_PICKUP）。
     * 无论哪种，确认收货都统一置 {@code verify_time}（评价窗口起算点）并让均价缓存失效。
     */
    @Transactional(rollbackFor = Exception.class)
    public void receive(Long orderId, Long buyerId) {
        TradeOrder order = requireOrder(orderId);
        BizAssert.isTrue(buyerId.equals(order.getBuyerId()), OrderErrorCode.ORDER_NOT_OWNER);

        OrderStatusEnum status = OrderStatusEnum.fromCode(order.getStatus());
        BizAssert.isTrue(status == OrderStatusEnum.VERIFIED || status == OrderStatusEnum.SHIPPED
                || status == OrderStatusEnum.WAIT_PICKUP, OrderErrorCode.ORDER_STATUS_INVALID,
                "当前状态不能确认收货");

        int rows = orderMapper.update(null, new LambdaUpdateWrapper<TradeOrder>()
                .eq(TradeOrder::getId, orderId)
                .eq(TradeOrder::getStatus, order.getStatus())
                .set(TradeOrder::getStatus, OrderStatusEnum.COMPLETED.getCode())
                .set(TradeOrder::getVerifyTime, LocalDateTime.now()));
        BizAssert.isTrue(rows == 1, OrderErrorCode.ORDER_STATUS_INVALID, "当前状态不能确认收货");
        // 成交成立：status=4 正是 avgPriceByCategory 的统计口径，均价缓存立即失效
        categoryAvgPriceCache.evict();
    }

    /* ══ 五、逆向：取消 / 退款 / 纠纷 ══════════════════════════ */

    /**
     * 取消订单。**付款前后语义不同**（notes/13 第四节）：待付款时直接作废，因为钱没进过这道流程；
     * 已付款后再取消，实质是发起退款申请，必须落到退款中等卖家处理，而不能直接作废——
     * 否则货款已经「付」出、订单却显示为已取消，卖家无从应对。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long orderId, Long userId, String reason) {
        TradeOrder order = requireOrder(orderId);
        BizAssert.isTrue(isParty(order, userId), OrderErrorCode.ORDER_NOT_OWNER);

        OrderStatusEnum status = OrderStatusEnum.fromCode(order.getStatus());
        BizAssert.isTrue(!status.isFrozen(), OrderErrorCode.ORDER_STATUS_INVALID,
                "纠纷中订单已冻结，请等待管理端裁决");
        BizAssert.isTrue(!status.isTerminal(), OrderErrorCode.ORDER_STATUS_INVALID);

        CancelTypeEnum by = userId.equals(order.getBuyerId()) ? CancelTypeEnum.BUYER : CancelTypeEnum.SELLER;
        String text = trim(reason);

        if (status == OrderStatusEnum.WAIT_PAY) {
            BizAssert.isTrue(cancelOne(order, by, text) == 1, OrderErrorCode.ORDER_STATUS_INVALID);
            return;
        }
        // 已付款但未完成的任一态（待面交/已验货/待发货/已发货/待自提）取消，都视作发起退款申请，
        // 落到退款中等卖家处理——逻辑与既有「付款后取消即退款申请」一致，仅把新态并入同一分支。
        if (status == OrderStatusEnum.WAIT_MEET || status == OrderStatusEnum.VERIFIED
                || status == OrderStatusEnum.WAIT_SHIP || status == OrderStatusEnum.SHIPPED
                || status == OrderStatusEnum.WAIT_PICKUP) {
            int rows = orderMapper.update(null, new LambdaUpdateWrapper<TradeOrder>()
                    .eq(TradeOrder::getId, orderId)
                    .eq(TradeOrder::getStatus, order.getStatus())
                    .set(TradeOrder::getStatus, OrderStatusEnum.REFUNDING.getCode())
                    .set(TradeOrder::getCancelBy, by.getCode())
                    .set(TradeOrder::getCancelReason, text));
            BizAssert.isTrue(rows == 1, OrderErrorCode.ORDER_STATUS_INVALID);
            return;
        }
        BizAssert.isTrue(false, OrderErrorCode.ORDER_STATUS_INVALID, "退款申请已在等待卖家处理");
    }

    /**
     * 卖家处理退款申请：同意 → 已退款，拒绝 → 纠纷中（冻结，转管理端裁决）。
     *
     * <p>**退款是一次状态迁移而不是资金退回**——本平台不走真实资金流，界面文案也应避免
     * 「款项已退回账户」这类暗示存在结算的表述（notes/13 第四节）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleRefund(Long orderId, Long sellerId, boolean agree, String note) {
        TradeOrder order = requireOrder(orderId);
        BizAssert.isTrue(sellerId.equals(order.getSellerId()), OrderErrorCode.ORDER_NOT_OWNER);
        // 本接口只受理「退款中」的申请：少了这道校验，对待面交订单误调拒绝分支会把它直接转成纠纷
        BizAssert.isTrue(OrderStatusEnum.fromCode(order.getStatus()) == OrderStatusEnum.REFUNDING,
                OrderErrorCode.ORDER_STATUS_INVALID, "没有待处理的退款申请");

        if (agree) {
            int rows = orderMapper.update(null, new LambdaUpdateWrapper<TradeOrder>()
                    .eq(TradeOrder::getId, orderId)
                    .eq(TradeOrder::getStatus, OrderStatusEnum.REFUNDING.getCode())
                    .set(TradeOrder::getStatus, OrderStatusEnum.REFUNDED.getCode())
                    .set(TradeOrder::getCancelReason, trim(note)));
            BizAssert.isTrue(rows == 1, OrderErrorCode.ORDER_STATUS_INVALID, "没有待处理的退款申请");
            // 退款成立：商品从已售出恢复在售（notes/13 O9 数据变化），占位标记同步重置
            restoreItemOnRefund(order);
            return;
        }
        // 拒绝即纠纷起点：冻结订单并同事务写申诉单，管理端才有件可裁（notes/13 O12）。
        // 申请人取买家——退款诉求本由买家发起，被拒后要主张权利的也是他
        String reason = trim(note);
        openDispute(order, order.getBuyerId(), reason == null ? "卖家拒绝退款" : "卖家拒绝退款：" + reason);
    }

    /**
     * 发起订单纠纷：订单转纠纷中(8) 冻结，同事务写一条 biz_type=3 的申诉单。
     *
     * <p>申诉单是管理端取件的唯一入口——只冻结订单不写单，纠纷在审核队列里不存在，
     * 谁也裁不了，订单将永久停在纠纷中。因此状态迁移与写单必须在同一事务里。
     */
    @Transactional(rollbackFor = Exception.class)
    public void openDispute(Long orderId, Long userId, String reason) {
        TradeOrder order = requireOrder(orderId);
        BizAssert.isTrue(isParty(order, userId), OrderErrorCode.ORDER_NOT_OWNER);
        openDispute(order, userId, reason);
    }

    /** 受理范围与 notes/13 O12 前置一致：待面交 / 已验货 / 退款中，另含三种收货方式的履约态；终态与已冻结不可再起 */
    private void openDispute(TradeOrder order, Long applicantId, String reason) {
        OrderStatusEnum status = OrderStatusEnum.fromCode(order.getStatus());
        BizAssert.isTrue(status == OrderStatusEnum.WAIT_MEET || status == OrderStatusEnum.VERIFIED
                || status == OrderStatusEnum.REFUNDING || status == OrderStatusEnum.WAIT_SHIP
                || status == OrderStatusEnum.SHIPPED || status == OrderStatusEnum.WAIT_PICKUP,
                OrderErrorCode.ORDER_STATUS_INVALID, "当前状态不能发起纠纷");

        String text = trim(reason);
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<TradeOrder>()
                .eq(TradeOrder::getId, order.getId())
                .eq(TradeOrder::getStatus, order.getStatus())
                .set(TradeOrder::getStatus, OrderStatusEnum.DISPUTING.getCode())
                .set(TradeOrder::getCancelReason, text));
        BizAssert.isTrue(rows == 1, OrderErrorCode.ORDER_STATUS_INVALID);

        appealMapper.insert(new Appeal()
                .setApplicantId(applicantId)
                .setBizType(AppealBizTypeEnum.ORDER.getCode())
                .setBizId(order.getId())
                .setReason(text == null ? DISPUTE_DEFAULT_REASON : text)
                .setStatus(AppealStatusEnum.WAITING.getCode()));
    }

    /**
     * 卖家 {@value #AUTO_REFUND_HOURS} 小时未处理退款申请，系统自动同意。
     *
     * <p>起算时间取 `update_time`：表里没有专门的退款申请时间列，而订单停留在退款中期间不会有别处改动它，
     * 此刻 `update_time` 就等于那次状态迁移的时刻。这是借用的语义，将来若这条分支上再长出别的写操作，
     * 应当补一列而不是继续借用它。
     *
     * <p><b>方法本身不开事务</b>：整批一个大事务，任何一笔失败都全批回滚，且行锁从第一笔握到
     * 最后一笔，阻塞的恰是这批订单的真实受理。逐笔独立事务由 {@link #autoRefundOne} 负责，
     * 失败的订单状态未变，下一轮扫描会再次捞到——重试由幂等性免费提供，无需补偿机制
     * （与 {@link #cancelExpired} 同一模式，notes/18 问题一）。
     * 每轮限量并按进入退款中的时刻排序：先被冷处理的更早解决，积压时分多轮消化。
     */
    @Scheduled(fixedDelay = 30 * 60 * 1000L)
    public void autoRefund() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(AUTO_REFUND_HOURS);
        List<TradeOrder> pending = orderMapper.selectList(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getStatus, OrderStatusEnum.REFUNDING.getCode())
                .le(TradeOrder::getUpdateTime, deadline)
                .orderByAsc(TradeOrder::getUpdateTime)
                .last("LIMIT " + EXPIRE_SCAN_BATCH));
        int count = 0;
        for (TradeOrder order : pending) {
            try {
                count += autoRefundOne(order);
            } catch (Exception e) {
                // 单笔失败只丢这一笔：状态未变，下轮扫描自动重试，不能让它拖垮已成功的
                log.error("[order] 单笔自动同意退款失败，留待下次扫描重试：orderId={}", order.getId(), e);
            }
        }
        if (count > 0) {
            log.info("[order] 卖家超时未处理，系统自动同意退款 {} 笔", count);
        }
    }

    /**
     * 单笔自动同意：转已退款并恢复商品。**状态校验写在 WHERE 里而不是先查后判**：
     * 并发下卖家可能刚好自己点了同意，行数 0 即表示订单已被改走，本次作废。
     *
     * <p>单笔一个独立事务（REQUIRES_NEW）：批量调用方（{@link #autoRefund}）失败只回滚
     * 这一笔，不影响同批已提交的。
     */
    private int autoRefundOne(TradeOrder order) {
        TransactionTemplate template = new TransactionTemplate(txManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> {
            int rows = orderMapper.update(null, new LambdaUpdateWrapper<TradeOrder>()
                    .eq(TradeOrder::getId, order.getId())
                    .eq(TradeOrder::getStatus, OrderStatusEnum.REFUNDING.getCode())
                    .set(TradeOrder::getStatus, OrderStatusEnum.REFUNDED.getCode())
                    .set(TradeOrder::getCancelReason, AUTO_REFUND_REASON));
            if (rows == 1) {
                restoreItemOnRefund(order);
            }
            return rows;
        });
    }

    /**
     * 把管理端的纠纷裁决结果落到订单上（由 {@link ReviewAdminService#judgeAppeal} 调用）。
     *
     * <p>**库里没有「纠纷已裁决」这个状态码**，结果必须映射到已有终态：买家赢→已退款，卖家赢→已完成。
     * 单独留一个「已裁决」会让订单既不完成也不退款，钱货去向反而说不清（notes/13 第二节）。
     *
     * @param target 裁决后的目标状态，只允许 {@link OrderStatusEnum#REFUNDED} 与 {@link OrderStatusEnum#COMPLETED}
     */
    @Transactional(rollbackFor = Exception.class)
    public void settleDispute(Long orderId, OrderStatusEnum target) {
        BizAssert.isTrue(target == OrderStatusEnum.REFUNDED || target == OrderStatusEnum.COMPLETED,
                CommonErrorCode.PARAM_ERROR, "纠纷裁决结果只能是已退款或已完成");

        TradeOrder order = requireOrder(orderId);
        BizAssert.isTrue(OrderStatusEnum.fromCode(order.getStatus()) == OrderStatusEnum.DISPUTING,
                OrderErrorCode.ORDER_STATUS_INVALID, "只有纠纷中的订单需要裁决");

        int rows = orderMapper.update(null, new LambdaUpdateWrapper<TradeOrder>()
                .eq(TradeOrder::getId, orderId)
                .eq(TradeOrder::getStatus, OrderStatusEnum.DISPUTING.getCode())
                .set(TradeOrder::getStatus, target.getCode())
                .set(TradeOrder::getCancelReason, DISPUTE_SETTLED_REASON));
        BizAssert.isTrue(rows == 1, OrderErrorCode.ORDER_STATUS_INVALID);
        if (target == OrderStatusEnum.REFUNDED) {
            // 买家赢：退款成立，商品恢复在售，与卖家同意退款同一后果（notes/13 O9）
            restoreItemOnRefund(order);
        } else {
            // 卖家赢：成交成立，订单进入已成交统计，均价缓存随之失效
            categoryAvgPriceCache.evict();
        }
    }

    /**
     * 管理端强制退款（A6）：把「退款中 / 纠纷中」直接转已退款并恢复商品。
     *
     * <p>**只受理这两个悬置态**。已付款 / 待面交的交易还在正常进行，卖家未必违约，
     * 管理端替卖家退款属于越权处置；已完成、已取消、已退款是终态，重复处置没有意义。
     * 状态校验同时写在 Java 与 WHERE 里：Java 侧给可读报错，WHERE 侧保证并发下只有一方生效。
     *
     * <p>不走 `handleRefund`：那个方法要求操作人是卖家，且只认退款中。
     */
    @Transactional(rollbackFor = Exception.class)
    public void forceRefund(Long orderId, String note) {
        TradeOrder order = requireOrder(orderId);
        String reason = trim(note);
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<TradeOrder>()
                .eq(TradeOrder::getId, orderId)
                .in(TradeOrder::getStatus, OrderStatusEnum.REFUNDING.getCode(),
                        OrderStatusEnum.DISPUTING.getCode())
                .set(TradeOrder::getStatus, OrderStatusEnum.REFUNDED.getCode())
                .set(TradeOrder::getCancelReason,
                        reason == null ? FORCE_REFUND_REASON : FORCE_REFUND_REASON + "：" + reason));
        BizAssert.isTrue(rows == 1, OrderErrorCode.ORDER_STATUS_INVALID, "只能对退款中 / 纠纷中订单强制退款");
        restoreItemOnRefund(order);
    }

    /* ══ 六、查询 ═════════════════════════════════════════════ */

    /** 我买到的 / 我卖出的，走 `idx_buyer` / `idx_seller` */
    public PageResult<OrderVO> mine(Long userId, boolean asSeller, OrderStatusEnum status,
                                    long pageNum, long pageSize) {
        LambdaQueryWrapper<TradeOrder> q = new LambdaQueryWrapper<>();
        if (asSeller) {
            q.eq(TradeOrder::getSellerId, userId);
        } else {
            q.eq(TradeOrder::getBuyerId, userId);
        }
        q.eq(TradeOrder::getDeleted, 0);
        if (status != null) {
            q.eq(TradeOrder::getStatus, status.getCode());
        }
        q.orderByDesc(TradeOrder::getCreateTime).orderByDesc(TradeOrder::getId);

        Page<TradeOrder> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), q);
        List<OrderVO> list = page.getRecords().stream().map(o -> toVO(o, userId)).toList();
        return PageResult.of(list, page.getTotal(), pageNum, pageSize);
    }

    public OrderVO detail(Long orderId, Long userId) {
        TradeOrder order = requireOrder(orderId);
        BizAssert.isTrue(isParty(order, userId), OrderErrorCode.ORDER_NOT_OWNER);
        OrderVO vo = toVO(order, userId);
        fillPartyAndMeetPoint(vo);
        return vo;
    }

    /**
     * 核销码只向卖家暴露：买家侧永远拿不到码，也就无法独自核销（配合 {@link #verify} 只允许买家调用，
     * 形成「卖家持码、买家核销、任一方都做不完」的闭环）。viewer 为卖家时才回填 verifyCode。
     */
    private OrderVO toVO(TradeOrder o, Long viewerId) {
        OrderVO vo = new OrderVO();
        vo.setId(o.getId());
        vo.setOrderNo(o.getOrderNo());
        vo.setItemId(o.getItemId());
        vo.setBuyerId(o.getBuyerId());
        vo.setSellerId(o.getSellerId());
        vo.setAmount(o.getAmount());
        vo.setOfferId(o.getOfferId());
        vo.setStatus(OrderStatusEnum.fromCode(o.getStatus()));
        vo.setDeliveryType(DeliveryTypeEnum.fromCode(o.getDeliveryType()));
        vo.setAddressId(o.getAddressId());
        vo.setAddressContact(o.getAddressContact());
        vo.setAddressPhone(o.getAddressPhone());
        vo.setAddressDetail(o.getAddressDetail());
        vo.setMeetPointId(o.getMeetPointId());
        vo.setMeetTime(o.getMeetTime());
        // 仅卖家可见核销码；买家侧留空，前端据此隐藏并只展示输入框
        if (viewerId != null && viewerId.equals(o.getSellerId())) {
            vo.setVerifyCode(o.getVerifyCode());
        }
        vo.setVerifyTime(o.getVerifyTime());
        vo.setExpireTime(o.getExpireTime());
        vo.setPayTime(o.getPayTime());
        vo.setCancelBy(o.getCancelBy());
        vo.setCancelReason(o.getCancelReason());
        vo.setItemTitle(o.getItemTitle());
        vo.setItemPrice(o.getItemPrice());
        vo.setItemGrade(o.getItemGrade());
        vo.setItemMainImage(o.getItemMainImage());
        vo.setCreateTime(o.getCreateTime());
        return vo;
    }

    /**
     * 详情页专属的冗余字段：交易对方的昵称 / 头像、面交点名称与校区。
     *
     * <p>故意只挂在 {@link #detail} 上、不进 {@link #mine}（列表）：列表一页多条，
     * 逐单查用户与面交点会变成 N+1，而列表本就用商品快照展示，不需要对方信息。
     * 头像为空时前端会按 id 派生占位图，所以这里只负责把库里真实值透传出来。
     */
    private void fillPartyAndMeetPoint(OrderVO vo) {
        AppUser seller = appUserMapper.selectById(vo.getSellerId());
        if (seller != null) {
            vo.setSellerNick(seller.getNickname());
            vo.setSellerAvatar(seller.getAvatarUrl());
        }
        AppUser buyer = appUserMapper.selectById(vo.getBuyerId());
        if (buyer != null) {
            vo.setBuyerNick(buyer.getNickname());
            vo.setBuyerAvatar(buyer.getAvatarUrl());
        }
        if (vo.getMeetPointId() != null) {
            DictMeetPoint mp = meetPointMapper.selectById(vo.getMeetPointId());
            if (mp != null) {
                vo.setMeetPointName(mp.getName());
                if (mp.getCampusId() != null) {
                    DictCampus campus = campusMapper.selectById(mp.getCampusId());
                    if (campus != null) {
                        vo.setMeetPointCampus(campus.getName());
                    }
                }
            }
        }
    }

    /* ══ 七、内部工具 ═════════════════════════════════════════ */

    private TradeOrder requireOrder(Long orderId) {
        return BizAssert.notNull(orderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getId, orderId)
                .eq(TradeOrder::getDeleted, 0)), OrderErrorCode.ORDER_NOT_FOUND);
    }

    /** 买卖双方任一方都算当事人：面交是双方行为，不能只认买家 */
    private boolean isParty(TradeOrder order, Long userId) {
        return userId.equals(order.getBuyerId()) || userId.equals(order.getSellerId());
    }

    /**
     * 取用下单时的地址并校验归属（买家收货 / 卖家自提）。
     *
     * <p>校验「地址属于本人 + 类型对得上」是硬不变量：少了它，买家可以传卖家的自提地址当收货地址，
     * 或把收货地址塞给自提订单，订单上的地址快照就成了别人的信息。
     */
    private UserAddress requireAddress(Long ownerId, Long addressId, Integer kind, String message) {
        BizAssert.notNull(addressId, CommonErrorCode.PARAM_ERROR, message);
        UserAddress addr = userAddressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, addressId)
                .eq(UserAddress::getUserId, ownerId)
                .eq(UserAddress::getKind, kind)
                .eq(UserAddress::getDeleted, 0));
        return BizAssert.notNull(addr, CommonErrorCode.PARAM_ERROR, message);
    }

    /** 地址快照文本：省市区 + 详细地址拼接，缺哪段补哪段 */
    private String addressDetailOf(UserAddress addr) {
        StringBuilder sb = new StringBuilder();
        appendIfNotBlank(sb, addr.getProvince());
        appendIfNotBlank(sb, addr.getCity());
        appendIfNotBlank(sb, addr.getDistrict());
        appendIfNotBlank(sb, addr.getDetail());
        return sb.toString();
    }

    private void appendIfNotBlank(StringBuilder sb, String part) {
        if (part != null && !part.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(part.trim());
        }
    }

    /**
     * 退款成立后把商品从已售出恢复在售，并重置占位标记。
     *
     * <p>占位标记在付款成功时被置为**永久**的 SOLD（货既售出就该一直挡住后来者，故不设 TTL），
     * 不重置的话恢复在售的商品会被 Lua 一直当已售挡下——货回来了却永远不能再卖。
     * 条件更新限定 SOLD：这期间商品若已被平台强制下架，以平台处置为准，不抢回来。
     */
    private void restoreItemOnRefund(TradeOrder order) {
        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, order.getItemId())
                .eq(Item::getStatus, ItemStatusEnum.SOLD.getCode())
                .set(Item::getStatus, ItemStatusEnum.ON_SALE.getCode()));
        String stateKey = ItemRedisKeys.buildStateKey(order.getItemId());
        try {
            stringRedisTemplate.opsForValue().set(stateKey, ItemRedisKeys.STATE_ON_SALE,
                    Duration.ofSeconds(ItemRedisKeys.STATE_TTL_SECONDS));
        } catch (Exception e) {
            // 标记没重置成功商品就暂时卖不掉，必须留 error 让运维看得见（TTL 救不了永久值）
            log.error("[order] 退款后占位标记重置失败，商品可能暂时无法购买，key={}", stateKey, e);
        }
    }

    private String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Redis 前置拦截；不可用时降级放行（功能不受影响，只是 DB 压力上升） */
    private long occupy(String stateKey, String holder) {
        try {
            Long r = stringRedisTemplate.execute(OCCUPY_SCRIPT,
                    List.of(stateKey, ItemRedisKeys.HOLDER),
                    ItemRedisKeys.STATE_ON_SALE,
                    ItemRedisKeys.STATE_LOCKED,
                    String.valueOf(ItemRedisKeys.STATE_TTL_SECONDS),
                    holder);
            return r == null ? OCCUPY_MISS : r;
        } catch (Exception e) {
            log.warn("[order] Redis 占位前置不可用，降级放行交给 DB，key={}", stateKey, e);
            return OCCUPY_MISS;
        }
    }

    /** 释放占位标记；校验失败返回 0（不是自己的锁，不碰） */
    private long release(String stateKey, String holder) {
        try {
            Long r = stringRedisTemplate.execute(RELEASE_SCRIPT,
                    List.of(stateKey, ItemRedisKeys.HOLDER), holder);
            return r == null ? 0 : r;
        } catch (Exception e) {
            log.warn("[order] 释放占位标记失败，等待 TTL 自愈，key={}", stateKey, e);
            return 0;
        }
    }

    /** 商品完整信息快照。序列化失败不该阻断下单，取证只是辅助字段 */
    private String toSnapshot(Item item) {
        try {
            return objectMapper.writeValueAsString(item);
        } catch (Exception e) {
            log.warn("[order] 商品快照序列化失败，跳过：itemId={}", item.getId(), e);
            return null;
        }
    }

    private String newOrderNo() {
        return "OD" + System.currentTimeMillis()
                + ThreadLocalRandom.current().nextInt(100000, 999999);
    }

    private String newVerifyCode() {
        return UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase(Locale.ROOT);
    }
}

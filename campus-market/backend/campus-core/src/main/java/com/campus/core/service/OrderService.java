package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.BizException;
import com.campus.common.exception.CommonErrorCode;
import com.campus.core.entity.Item;
import com.campus.core.entity.TradeOrder;
import com.campus.core.enums.CancelTypeEnum;
import com.campus.core.enums.ItemStatusEnum;
import com.campus.core.enums.OrderStatusEnum;
import com.campus.core.errcode.OrderErrorCode;
import com.campus.core.mapper.ItemMapper;
import com.campus.core.mapper.TradeOrderMapper;
import com.campus.core.redis.ItemRedisKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 学生端交易（下单 / 付款占位 / 取消），口径见 notes/13，并发方案见 `tech/01` §9.1。
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

    /** Lua 返回值：抢到标记 */
    private static final int OCCUPY_WON = 1;

    /** Lua 返回值：同一订单重入，按幂等处理 */
    private static final int OCCUPY_REENTRY = 2;

    /** Lua 返回值：冲突（他人已锁定或已售出） */
    private static final int OCCUPY_CONFLICT = 0;

    /** Lua 返回值：缓存缺失，放行交给 DB 裁决 */
    private static final int OCCUPY_MISS = -1;

    /**
     * 抢占商品占位标记（`tech/01` §9.1 的脚本，holder hash 改为由 KEYS[2] 传入以遵守 key 常量规范）。
     *
     * <p>返回 -1 表示缓存缺失，**必须放行**：缓存未预热或已被淘汰时 Lua 无从判定，
     * 若按「缺失即拒绝」设计，缓存一失效商品就永远卖不掉。放行是安全的，DB 会拦住。
     */
    private static final RedisScript<Long> OCCUPY_SCRIPT = RedisScript.of("""
            local cur = redis.call('GET', KEYS[1])
            if cur == false then
              return -1
            end
            if cur == ARGV[2] then
              if redis.call('HGET', KEYS[2], KEYS[1]) == ARGV[4] then
                return 2
              end
              return 0
            end
            if cur ~= ARGV[1] then
              return 0
            end
            redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
            redis.call('HSET', KEYS[2], KEYS[1], ARGV[4])
            return 1
            """, Long.class);

    /**
     * 回滚占位标记，**必须先校验 holder 是自己的订单号**。
     *
     * <p>少了这层校验，DB 走得慢的那个请求会在自己的更新失败后，把别人刚抢到的锁释放掉，
     * 于是出现两个赢家。这是本方案里最隐蔽的一处正确性依赖。
     */
    private static final RedisScript<Long> RELEASE_SCRIPT = RedisScript.of("""
            if redis.call('HGET', KEYS[2], KEYS[1]) == ARGV[1] then
              redis.call('DEL', KEYS[1])
              redis.call('HDEL', KEYS[2], KEYS[1])
              return 1
            end
            return 0
            """, Long.class);

    private final TradeOrderMapper orderMapper;

    private final ItemMapper itemMapper;

    /** 占位标记是纯字符串状态机，用 StringRedisTemplate 即可，不必绕一层 JSON 序列化 */
    private final StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper;

    /**
     * 下单：**只插订单，不锁商品**。
     *
     * <p>商品快照在下单这一刻固化进订单，之后即便卖家改价改描述，
     * 历史订单里留存的原样仍是纠纷取证的可信数据源（notes/13 第六节）。
     *
     * @return 新订单 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long buyerId, Long itemId, Long meetPointId, LocalDateTime meetTime) {
        Item item = itemMapper.selectById(itemId);
        BizAssert.notNull(item, CommonErrorCode.NOT_FOUND);
        BizAssert.isTrue(ItemStatusEnum.fromCode(item.getStatus()) == ItemStatusEnum.ON_SALE,
                OrderErrorCode.ITEM_NOT_ON_SALE);
        BizAssert.isTrue(!buyerId.equals(item.getSellerId()), OrderErrorCode.BUY_SELF);

        LocalDateTime now = LocalDateTime.now();
        TradeOrder order = new TradeOrder()
                .setOrderNo(newOrderNo())
                .setItemId(itemId)
                .setBuyerId(buyerId)
                .setSellerId(item.getSellerId())
                .setAmount(item.getPrice())
                .setStatus(OrderStatusEnum.WAIT_PAY.getCode())
                .setMeetPointId(meetPointId)
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

        long result = occupy(stateKey, holder);
        BizAssert.isTrue(result != OCCUPY_CONFLICT, OrderErrorCode.ITEM_SOLD_OUT);

        boolean done = false;
        try {
            claimItem(order.getItemId());
            LocalDateTime now = LocalDateTime.now();
            int rows = orderMapper.update(null, new LambdaUpdateWrapper<TradeOrder>()
                    .eq(TradeOrder::getId, orderId)
                    .eq(TradeOrder::getStatus, OrderStatusEnum.WAIT_PAY.getCode())
                    .set(TradeOrder::getStatus, OrderStatusEnum.WAIT_MEET.getCode())
                    .set(TradeOrder::getPayTime, now));
            BizAssert.isTrue(rows == 1, OrderErrorCode.ORDER_STATUS_INVALID);

            cancelSiblings(order.getItemId(), orderId, CancelTypeEnum.SYSTEM_TIMEOUT,
                    "商品已被其他买家付款");
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

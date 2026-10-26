package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.CommonErrorCode;
import com.campus.common.result.PageResult;
import com.campus.core.dto.req.AdminOrderQueryReq;
import com.campus.core.dto.req.AdminOrderRefundReq;
import com.campus.core.dto.req.AppealJudgeReq;
import com.campus.core.dto.req.OrderArbitrateReq;
import com.campus.core.dto.resp.AdminOrderVO;
import com.campus.core.dto.resp.DictMeetPointVO;
import com.campus.core.dto.resp.UserPubVO;
import com.campus.core.entity.Appeal;
import com.campus.core.entity.AppUser;
import com.campus.core.entity.Item;
import com.campus.core.entity.TradeOrder;
import com.campus.core.enums.AppealBizTypeEnum;
import com.campus.core.enums.AppealStatusEnum;
import com.campus.core.enums.OrderStatusEnum;
import com.campus.core.errcode.OrderErrorCode;
import com.campus.core.errcode.ReviewErrorCode;
import com.campus.core.mapper.AppealMapper;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.mapper.ItemMapper;
import com.campus.core.mapper.TradeOrderMapper;
import com.campus.core.service.cache.UserPubCache;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 管理端订单管理（A6 `/orders`，流程见 business/03 A-B7 / F-A6）。
 *
 * <p><b>本类的存在意义是「取证」，不是「查订单」</b>：纠纷裁决要看下单那一刻的商品是什么样，
 * 而卖家随时可能改价改描述。因此这里把订单表上的四个快照列（标题 / 价格 / 成色 / 主图）
 * 与**当前商品**并排给出来，再由服务端判定 `snapshotDrifted`——规则集中在后端，
 * Web 与小程序不会各写一套。
 *
 * <p><b>快照描述不在订单表上</b>，只在 `trade_order.snapshot` 的整份商品 JSON 里，
 * 所以只有详情才解析它（列表多一层 JSON 解析只为一段不展示的文本，不划算）。
 *
 * <p><b>处置全部委托既有链路</b>：强制退款走 {@link OrderService#forceRefund}，
 * 裁决走 {@link ReviewAdminService#judgeAppeal}。管理端与学生端若各写一套状态迁移，
 * 商品恢复与占位重置必然漂移。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private static final int KEYWORD_USER_LIMIT = 200;

    private static final int NOTE_MAX = 200;

    private final TradeOrderMapper orderMapper;

    private final ItemMapper itemMapper;

    private final AppUserMapper appUserMapper;

    private final AppealMapper appealMapper;

    private final UserPubCache userPubCache;

    private final DictService dictService;

    private final OrderService orderService;

    private final ReviewAdminService reviewService;

    private final ObjectMapper objectMapper;

    /**
     * 订单列表：分页 + 状态 / 只看向纠纷 / 关键词（订单号、商品标题、买卖双方昵称）。
     *
     * <p>关键词跨表靠「先查昵称命中的用户 id，再拼 IN」，不写 JOIN——
     * LEFT JOIN 后分页 count 的行数语义会变（notes/08 列表查询模式）。
     */
    public PageResult<AdminOrderVO> list(AdminOrderQueryReq req) {
        long pageNum = req.getPageNum() == null || req.getPageNum() < 1 ? 1 : req.getPageNum();
        long pageSize = req.getPageSize() == null || req.getPageSize() < 1 ? 10 : req.getPageSize();

        LambdaQueryWrapper<TradeOrder> q = new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getDeleted, 0);
        OrderStatusEnum status = parseStatus(req.getStatus());
        if (status != null) {
            q.eq(TradeOrder::getStatus, status.getCode());
        }
        if (Boolean.TRUE.equals(req.getDisputeOnly())) {
            q.eq(TradeOrder::getStatus, OrderStatusEnum.DISPUTING.getCode());
        }
        applyKeyword(q, req.getKeyword());
        q.orderByDesc(TradeOrder::getCreateTime).orderByDesc(TradeOrder::getId);

        Page<TradeOrder> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), q);
        return PageResult.of(assemble(page.getRecords(), false), page.getTotal(), pageNum, pageSize);
    }

    /** 订单详情：多出快照描述与完整的漂移判定（列表只比价格与成色） */
    public AdminOrderVO detail(Long orderId) {
        TradeOrder order = orderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getId, orderId)
                .eq(TradeOrder::getDeleted, 0));
        BizAssert.notNull(order, OrderErrorCode.ORDER_NOT_FOUND);
        return assemble(List.of(order), true).get(0);
    }

    /**
     * 强制退款：仅「退款中 / 纠纷中」，转已退款、商品恢复在售（与卖家同意退款同一后果）。
     *
     * <p>若该订单还挂着待复审的纠纷申诉单，一并结案——订单已退款，队列里那条申诉再无裁决对象，
     * 留着会让审核员对一张已终态的订单反复操作（第二次裁决会因订单状态不符直接报错）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void forceRefund(Long orderId, AdminOrderRefundReq req) {
        orderService.forceRefund(orderId, cut(req == null ? null : req.getNote()));
        closePendingAppeal(orderId, "管理端强制退款，申诉随之结案");
    }

    /**
     * 纠纷裁决：退款买家 → 已退款，放款卖家 → 已完成。两种结果都落到已有终态。
     *
     * <p>委托 {@link ReviewAdminService#judgeAppeal}：它负责申诉单结案、商品/订单恢复与换人复审校验，
     * 这里只把「退款买家 / 放款卖家」翻译成它认的 3 撤销 / 2 维持。
     */
    @Transactional(rollbackFor = Exception.class)
    public void arbitrate(Long orderId, OrderArbitrateReq req) {
        String result = req == null || req.getResult() == null ? "" : req.getResult().trim();
        BizAssert.isTrue("REFUND_BUYER".equals(result) || "PAY_SELLER".equals(result),
                CommonErrorCode.PARAM_ERROR, "裁决结果只能是退款买家或放款卖家");

        TradeOrder order = orderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getId, orderId)
                .eq(TradeOrder::getDeleted, 0));
        BizAssert.notNull(order, OrderErrorCode.ORDER_NOT_FOUND);
        BizAssert.isTrue(OrderStatusEnum.fromCode(order.getStatus()) == OrderStatusEnum.DISPUTING,
                OrderErrorCode.ORDER_STATUS_INVALID, "只有纠纷中的订单需要裁决");

        Appeal appeal = waitingOrderAppeal(orderId);
        // 提示要说清是"没有可裁的单"，而不是让管理员去猜申诉单是不是被删了
        BizAssert.isTrue(appeal != null, ReviewErrorCode.REVIEW_TASK_NOT_FOUND,
                "该订单没有待复审的纠纷申诉单，无法裁决");

        AppealJudgeReq judge = new AppealJudgeReq();
        // 撤销 = 支持申诉人（买家退款）；维持 = 驳回申诉（卖家拿钱）
        judge.setResult("REFUND_BUYER".equals(result)
                ? AppealStatusEnum.REVOKED.getCode()
                : AppealStatusEnum.UPHELD.getCode());
        judge.setNote(cut(req.getNote()));
        reviewService.judgeAppeal(appeal.getId(), judge);
    }

    /* ══ 内部 ═════════════════════════════════════════════ */

    /** 仅关闭仍待处理的申诉单：已裁决过的保持原结论，不覆盖人工判断 */
    private void closePendingAppeal(Long orderId, String note) {
        appealMapper.update(null, new LambdaUpdateWrapper<Appeal>()
                .eq(Appeal::getBizType, AppealBizTypeEnum.ORDER.getCode())
                .eq(Appeal::getBizId, orderId)
                .eq(Appeal::getStatus, AppealStatusEnum.WAITING.getCode())
                .set(Appeal::getStatus, AppealStatusEnum.REVOKED.getCode())
                .set(Appeal::getHandleNote, note));
    }

    private Appeal waitingOrderAppeal(Long orderId) {
        return appealMapper.selectOne(new LambdaQueryWrapper<Appeal>()
                .eq(Appeal::getBizType, AppealBizTypeEnum.ORDER.getCode())
                .eq(Appeal::getBizId, orderId)
                .eq(Appeal::getStatus, AppealStatusEnum.WAITING.getCode())
                .eq(Appeal::getDeleted, 0)
                .orderByDesc(Appeal::getId)
                .last("LIMIT 1"));
    }

    private OrderStatusEnum parseStatus(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return OrderStatusEnum.valueOf(name.trim());
        } catch (IllegalArgumentException e) {
            throw CommonErrorCode.PARAM_ERROR.ex("未知的订单状态：" + name);
        }
    }

    private void applyKeyword(LambdaQueryWrapper<TradeOrder> q, String keyword) {
        String kw = cut(keyword);
        if (kw == null) {
            return;
        }
        List<Long> userIds = appUserMapper.selectList(new LambdaQueryWrapper<AppUser>()
                        .select(AppUser::getId)
                        .like(AppUser::getNickname, kw)
                        .eq(AppUser::getDeleted, 0)
                        .last("LIMIT " + KEYWORD_USER_LIMIT))
                .stream().map(AppUser::getId).toList();
        q.and(w -> {
            w.like(TradeOrder::getOrderNo, kw).or().like(TradeOrder::getItemTitle, kw);
            if (!userIds.isEmpty()) {
                w.or().in(TradeOrder::getBuyerId, userIds).or().in(TradeOrder::getSellerId, userIds);
            }
        });
    }

    /**
     * 批量组装：当前商品、买卖双方公开资料、纠纷申诉单、面交点名称各一次批量查询。
     *
     * <p>当前商品**不过滤软删**：卖家删了商品，恰恰要让管理员看到「快照还在、商品没了」。
     */
    private List<AdminOrderVO> assemble(List<TradeOrder> orders, boolean withSnapshotDetail) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(TradeOrder::getId).toList();
        List<Long> itemIds = orders.stream().map(TradeOrder::getItemId).distinct().toList();
        List<Long> userIds = orders.stream()
                .flatMap(o -> Stream.of(o.getBuyerId(), o.getSellerId()))
                .filter(Objects::nonNull).distinct().toList();

        Map<Long, Item> items = itemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity(), (a, b) -> a));
        Map<Long, UserPubVO> users = userPubCache.getOrLoad(userIds);
        Map<Long, String> meetPointNames = dictService.meetPoints().stream()
                .collect(Collectors.toMap(DictMeetPointVO::getId, DictMeetPointVO::getName, (a, b) -> a));

        // 一个订单可能前后有过多条申诉（曾被拒过一次又重新发起），只取最新一条
        Map<Long, Appeal> appeals = new HashMap<>();
        for (Appeal a : appealMapper.selectList(new LambdaQueryWrapper<Appeal>()
                .eq(Appeal::getBizType, AppealBizTypeEnum.ORDER.getCode())
                .in(Appeal::getBizId, orderIds)
                .eq(Appeal::getDeleted, 0)
                .orderByDesc(Appeal::getId))) {
            appeals.putIfAbsent(a.getBizId(), a);
        }

        return orders.stream().map(o -> build(o, items.get(o.getItemId()), users, appeals.get(o.getId()),
                meetPointNames, withSnapshotDetail)).toList();
    }

    private AdminOrderVO build(TradeOrder o, Item item, Map<Long, UserPubVO> users, Appeal appeal,
                               Map<Long, String> meetPointNames, boolean withSnapshotDetail) {
        AdminOrderVO vo = new AdminOrderVO();
        vo.setId(o.getId());
        vo.setOrderNo(o.getOrderNo());
        vo.setItemId(o.getItemId());
        vo.setItemTitle(o.getItemTitle());
        vo.setItemPrice(o.getItemPrice());
        vo.setItemGrade(o.getItemGrade());
        vo.setItemMainImage(o.getItemMainImage());
        vo.setAmount(o.getAmount());
        vo.setStatus(OrderStatusEnum.fromCode(o.getStatus()));
        vo.setBuyerId(o.getBuyerId());
        vo.setSellerId(o.getSellerId());
        vo.setMeetPointId(o.getMeetPointId());
        vo.setMeetPointName(o.getMeetPointId() == null ? null : meetPointNames.get(o.getMeetPointId()));
        vo.setMeetTime(o.getMeetTime());
        vo.setPayTime(o.getPayTime());
        vo.setExpireTime(o.getExpireTime());
        vo.setCreateTime(o.getCreateTime());
        vo.setCancelBy(o.getCancelBy());
        vo.setCancelReason(o.getCancelReason());

        UserPubVO buyer = users.get(o.getBuyerId());
        UserPubVO seller = users.get(o.getSellerId());
        vo.setBuyerNick(buyer == null ? null : buyer.getNickname());
        vo.setSellerNick(seller == null ? null : seller.getNickname());

        // 当前商品：价格 / 成色取实体列，描述只有详情才从快照 JSON 与实体对比
        String snapDesc = withSnapshotDetail ? snapshotField(o.getSnapshot(), "description") : null;
        if (withSnapshotDetail) {
            vo.setItemDescription(snapDesc);
        }
        if (item == null) {
            // 商品已物理不存在：快照仍在，标成漂移让管理员知道"当前值不可信"
            vo.setSnapshotDrifted(true);
            return vo;
        }
        vo.setCurrentPrice(item.getPrice());
        vo.setCurrentGrade(item.getGrade());
        if (withSnapshotDetail) {
            vo.setCurrentDescription(item.getDescription());
        }
        vo.setSnapshotDrifted(drifted(o, item, snapDesc));

        if (appeal != null) {
            vo.setAppealId(appeal.getId());
            vo.setDisputeReason(appeal.getReason());
            vo.setDisputeEvidence(appeal.getEvidence());
            vo.setDisputeAt(appeal.getCreateTime());
            // 申诉表只存申请人 id，页面要一眼看出谁在主张权利
            vo.setDisputeBy(Objects.equals(appeal.getApplicantId(), o.getBuyerId()) ? 1 : 2);
            vo.setDisputeNote(appeal.getHandleNote());
        }
        return vo;
    }

    /** 价格 / 成色必比；描述只在详情比（列表没解析快照 JSON） */
    private boolean drifted(TradeOrder o, Item item, String snapDesc) {
        if (!sameMoney(o.getItemPrice(), item.getPrice())) {
            return true;
        }
        if (!Objects.equals(o.getItemGrade(), item.getGrade())) {
            return true;
        }
        return snapDesc != null && !Objects.equals(snapDesc, item.getDescription());
    }

    /** BigDecimal 比较必须用 compareTo：899 与 899.00 的 equals 是 false，但业务上是同一个价 */
    private boolean sameMoney(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }

    /** 从下单时的整份商品 JSON 里取单个字段；解析失败不阻断列表，只当没有 */
    private String snapshotField(String snapshot, String field) {
        if (snapshot == null || snapshot.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(snapshot).path(field);
            return node.isMissingNode() || node.isNull() ? null : node.asText();
        } catch (Exception e) {
            log.warn("[order] 快照解析失败：field={}", field, e);
            return null;
        }
    }

    private String cut(String s) {
        String t = s == null || s.isBlank() ? null : s.trim();
        return t != null && t.length() > NOTE_MAX ? t.substring(0, NOTE_MAX) : t;
    }
}

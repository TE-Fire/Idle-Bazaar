package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.BizException;
import com.campus.common.exception.CommonErrorCode;
import com.campus.core.context.AdminContext;
import com.campus.core.dto.req.AppealJudgeReq;
import com.campus.core.dto.req.ItemOfflineReq;
import com.campus.core.dto.req.ReportHandleReq;
import com.campus.core.dto.req.ReviewRejectReq;
import com.campus.core.dto.resp.DictCategoryVO;
import com.campus.core.dto.resp.ItemImageVO;
import com.campus.core.dto.resp.ReviewQueueVO;
import com.campus.core.dto.resp.SlaConfigVO;
import com.campus.core.dto.resp.ReviewSlaVO;
import com.campus.core.dto.resp.ReviewTaskVO;
import com.campus.core.dto.resp.WeightPartsVO;
import com.campus.core.dto.resp.UserPubVO;
import com.campus.core.entity.AdminUser;
import com.campus.core.entity.Appeal;
import com.campus.core.entity.Item;
import com.campus.core.entity.ItemAudit;
import com.campus.core.entity.ItemImage;
import com.campus.core.entity.Report;
import com.campus.core.entity.TradeOrder;
import com.campus.core.enums.AppealBizTypeEnum;
import com.campus.core.enums.AppealStatusEnum;
import com.campus.core.enums.AuditStatusEnum;
import com.campus.core.enums.CreditReasonEnum;
import com.campus.core.enums.ItemStatusEnum;
import com.campus.core.enums.OrderStatusEnum;
import com.campus.core.enums.QueueKindEnum;
import com.campus.core.enums.ReportStatusEnum;
import com.campus.core.enums.ReportTargetTypeEnum;
import com.campus.core.errcode.ReviewErrorCode;
import com.campus.core.mapper.AdminUserMapper;
import com.campus.core.mapper.AppealMapper;
import com.campus.core.mapper.ItemAuditMapper;
import com.campus.core.mapper.ItemImageMapper;
import com.campus.core.mapper.ItemMapper;
import com.campus.core.mapper.ReportMapper;
import com.campus.core.mapper.TradeOrderMapper;
import com.campus.core.service.cache.CategoryAvgPriceCache;
import com.campus.core.service.cache.UserPubCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端审核队列：将商品审核、举报、申诉三类待处置单据合并为一条排序队列（流程见 notes/09）。
 *
 * <p>三类单据共用「权重排序 + 连续处置」交互，在后端合成以保证排序口径唯一，避免前端二次排序导致口径漂移。
 * 排序规则为类型优先（申诉 > 举报 > 商品审核，见 {@link QueueKindEnum}）+ 组内权重降序；
 * 权重含实时等待补偿，排序不使用库内 {@code priority_score}（其数值为前四项之和的冗余快照）。
 *
 * <p>强制下架复用 {@link ItemAdminService#forceOffline}，与商品管理页为同一实现，保证两入口处置结果一致。
 * 所有处置以条件更新（{@code WHERE id = ? AND status = 待处置}）执行，影响行数为 0 即视为已被其他管理员处置，
 * 避免并发重复处置。{@code handle_cost_ms} 记录服务端处置动作耗时，非审核员阅读时长。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewAdminService {

    /** 权重公式里的两个乘数（business/03 F-A2） */
    private static final int REPORT_WEIGHT = 3;
    private static final int PRICE_ABNORMAL_WEIGHT = 5;
    /** 等待每满 12 小时 +1 */
    private static final int WAIT_COMPENSATE_HOURS = 12;

    /** 申诉裁决结论：`appeal.status` 直接复用这两个值 */
    private static final int APPEAL_UPHOLD = 2;
    private static final int APPEAL_REVOKE = 3;

    private final ItemMapper itemMapper;
    private final ItemImageMapper itemImageMapper;
    private final ItemAuditMapper itemAuditMapper;
    private final ReportMapper reportMapper;
    private final AppealMapper appealMapper;
    private final AdminUserMapper adminUserMapper;
    private final TradeOrderMapper tradeOrderMapper;
    private final ItemAdminService itemAdminService;
    private final OrderService orderService;
    private final DictService dictService;
    private final CategoryAvgPriceCache categoryAvgPriceCache;
    private final UserPubCache userPubCache;
    private final CreditService creditService;
    private final SysConfigService sysConfigService;

    /* ══ 一、队列 ═══════════════════════════════════════════ */

    /**
     * 合并队列（不分页）。流程：三表分别取待处置单据 → 汇聚商品 id → 批量回填关联数据
     * → 三类工厂映射为统一任务 → 丢弃无法解析单据 → 三级排序 → 组装响应。
     *
     * <p>关联数据采用「一次批量回填」而非逐行查询（见 notes/08 附录 A.2）：队列规模虽小，
     * 逐行回查会放大为上千次库往返。
     *
     * @param kind 过滤类型 {@code item} / {@code report} / {@code appeal}；为空或非法值按全部处理
     */
    public ReviewQueueVO queue(String kind) {
        // ① 解析类型过滤参数（空 / 非法值 → 全部）
        QueueKindEnum filter = parseKind(kind);

        // ② 三表分别按进队条件取单据
        List<ItemAudit> audits = (filter == null || filter == QueueKindEnum.ITEM)
                ? itemAuditMapper.selectList(new LambdaQueryWrapper<ItemAudit>()
                .eq(ItemAudit::getStatus, AuditStatusEnum.PENDING.getCode())
                .eq(ItemAudit::getDeleted, 0))
                : List.of();

        List<Report> reports = (filter == null || filter == QueueKindEnum.REPORT)
                ? reportMapper.selectList(new LambdaQueryWrapper<Report>()
                        .in(Report::getStatus, List.of(ReportStatusEnum.WAITING.getCode(),
                                ReportStatusEnum.PROCESSING.getCode()))
                        .eq(Report::getTargetType, ReportTargetTypeEnum.ITEM.getCode())
                .eq(Report::getDeleted, 0))
                : List.of();

        List<Appeal> appeals = (filter == null || filter == QueueKindEnum.APPEAL)
                ? appealMapper.selectList(new LambdaQueryWrapper<Appeal>()
                .eq(Appeal::getStatus, AppealStatusEnum.WAITING.getCode())
                .eq(Appeal::getDeleted, 0))
                : List.of();

        // ③ 申诉的 biz_id 是被申诉对象的 id，按 biz_type 分两条路一次查齐，避免逐条回库
        Map<Long, Report> appealReports = reportsById(appeals);
        Map<Long, TradeOrder> appealOrders = ordersById(appeals);

        // ④ 汇聚所有单据指向的商品 id：审核取 item_id，举报取 target_id，
        //    申诉要绕一层——商品审核直接就是商品 id，举报与订单纠纷须转一次（订单 → item_id）
        List<Long> itemIds = new ArrayList<>();
        audits.forEach(a -> itemIds.add(a.getItemId()));
        reports.forEach(r -> itemIds.add(r.getTargetId()));
        appeals.forEach(a -> {
            Long itemId = appealItemId(a, appealReports, appealOrders);
            if (itemId != null) {
                itemIds.add(itemId);
            }
        });

        // ⑤ 一次性批量回填关联数据（商品 / 卖家 / 举报数 / 权重快照 / 图片 / 均价 / 分类名 / 申诉原处置）
        Fill fill = buildFill(itemIds, appeals, appealReports);

        // ⑥ 三类工厂统一映射为 ReviewTaskVO（共性字段走 baseTask，各自补差异字段）
        List<ReviewTaskVO> tasks = new ArrayList<>();
        audits.forEach(a -> tasks.add(itemTask(a, fill)));
        reports.forEach(r -> tasks.add(reportTask(r, fill)));
        appeals.forEach(a -> tasks.add(appealTask(a, appealReports, appealOrders, fill)));

        // ⑦ 丢弃解析不出商品的单据（引用对象已被物理清理时才会发生），避免单条异常拖垮整列
        tasks.removeIf(Objects::isNull);

        // ⑧ 三级排序：类型顺序 → 权重降序 → 进队时间升序（稳定序）
        tasks.sort(Comparator
                .comparingInt((ReviewTaskVO t) -> t.getKind().getOrder())
                .thenComparing(Comparator.comparingInt(ReviewTaskVO::getWeight).reversed())
                .thenComparing(Comparator.nullsLast(Comparator.comparing(ReviewTaskVO::getSubmitAt))));

        // ⑨ 组装响应（含 SLA 概览）
        ReviewQueueVO vo = new ReviewQueueVO();
        vo.setList(tasks);
        vo.setSla(sla(tasks));
        return vo;
    }

    /* ══ 二、商品审核处置 ═══════════════════════════════════ */

    /**
     * 通过：商品 2 → 3（上架），审核单据 → 2。
     * 商品须处于待审核，否则状态已被他处改动，拦截至 REVIEW_ITEM_NOT_PENDING（3004），避免覆盖。
     */
    @Transactional(rollbackFor = Exception.class)
    public void pass(Long auditId) {
        long begin = System.currentTimeMillis();
        ItemAudit audit = requirePendingAudit(auditId);
        Item item = requireItem(audit.getItemId());
        BizAssert.isTrue(ItemStatusEnum.PENDING.getCode().equals(item.getStatus()),
                ReviewErrorCode.REVIEW_ITEM_NOT_PENDING);

        LocalDateTime now = LocalDateTime.now();
        long adminId = AdminContext.getAdminId();

        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, audit.getItemId())
                .eq(Item::getDeleted, 0)
                .set(Item::getStatus, ItemStatusEnum.ON_SALE.getCode())
                .set(Item::getPublishedAt, now));

        updateAudit(auditId, AuditStatusEnum.PASSED, adminId, now, begin, null, null);
    }

    /**
     * 驳回：商品 2 → 6（审核未过），审核单据 → 3 并记录原因。原因必填由 Controller 的 @Valid 校验。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long auditId, ReviewRejectReq req) {
        long begin = System.currentTimeMillis();
        ItemAudit audit = requirePendingAudit(auditId);
        Item item = requireItem(audit.getItemId());
        BizAssert.isTrue(ItemStatusEnum.PENDING.getCode().equals(item.getStatus()),
                ReviewErrorCode.REVIEW_ITEM_NOT_PENDING);

        LocalDateTime now = LocalDateTime.now();
        long adminId = AdminContext.getAdminId();
        String reason = req.getReason().trim();

        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, audit.getItemId())
                .eq(Item::getDeleted, 0)
                .set(Item::getStatus, ItemStatusEnum.REJECTED.getCode())
                .set(Item::getRejectReason, reason));

        updateAudit(auditId, AuditStatusEnum.REJECTED, adminId, now, begin, reason, trim(req.getNote()));
    }

    /**
     * 强制下架：转调 {@link ItemAdminService#forceOffline}（商品页与审核页共用实现）。
     * 仅前置校验单据仍为待审核（重复处置 → REVIEW_HANDLED）。该路不记 handle_cost_ms，
     * 因上游还承载商品页入口，耗时口径须两入口一致。
     */
    @Transactional(rollbackFor = Exception.class)
    public void forceOffline(Long auditId, ItemOfflineReq req) {
        ItemAudit audit = requirePendingAudit(auditId);
        itemAdminService.forceOffline(audit.getItemId(), req);
    }

    /* ══ 三、举报处置 ═══════════════════════════════════════ */

    /**
     * 举报裁决：result=1 成立 → 商品强制下架；result=2 不成立 → 商品不动。
     * 一步到位写 status=3，无独立受理步骤。成立一律强制下架（→7），不区分轻重，
     * 以免与卖家自行下架（4）语义混淆。商品不可下架时上游抛 1106，整笔回滚，举报单保持待处理。
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleReport(Long reportId, ReportHandleReq req) {
        long begin = System.currentTimeMillis();
        Integer result = req.getResult();
        BizAssert.isTrue(result != null && (result == 1 || result == 2),
                CommonErrorCode.PARAM_ERROR, "裁决结论只能是 1 成立 / 2 不成立");

        Report report = reportMapper.selectOne(new LambdaQueryWrapper<Report>()
                .eq(Report::getId, reportId)
                .eq(Report::getDeleted, 0));
        BizAssert.notNull(report, ReviewErrorCode.REVIEW_TASK_NOT_FOUND);
        BizAssert.isTrue(!ReportStatusEnum.HANDLED.getCode().equals(report.getStatus()),
                ReviewErrorCode.REVIEW_HANDLED);

        long adminId = AdminContext.getAdminId();
        LocalDateTime now = LocalDateTime.now();
        String note = trim(req.getNote());

        if (result == 1) {
            ItemOfflineReq offline = new ItemOfflineReq();
            offline.setReason(BizAssert.notBlank(req.getReason(),
                    CommonErrorCode.PARAM_ERROR, "举报成立时处置依据必填").trim());
            offline.setNote(note);
            itemAdminService.forceOffline(report.getTargetId(), offline);
            deductForReportedItem(report);
        }

        int n = reportMapper.update(null, new LambdaUpdateWrapper<Report>()
                .eq(Report::getId, reportId)
                .in(Report::getStatus, List.of(ReportStatusEnum.WAITING.getCode(),
                        ReportStatusEnum.PROCESSING.getCode()))
                .set(Report::getStatus, ReportStatusEnum.HANDLED.getCode())
                .set(Report::getResult, result)
                .set(Report::getHandlerId, adminId)
                .set(Report::getHandleTime, now)
                .set(Report::getHandleNote, note));
        BizAssert.isTrue(n == 1, ReviewErrorCode.REVIEW_HANDLED);

        // 举报单没有 handle_cost_ms 字段；处置耗时只在商品审核单据上记（SLA 明细按审核单据统计）
        log.debug("举报 {} 裁决 result={} 耗时 {}ms", reportId, result, System.currentTimeMillis() - begin);
    }

    /**
     * 举报成立给卖家扣 10 分。只有举报裁决这一处扣：强制下架本身不扣，
     * 否则举报成立（内部会调 forceOffline）会被记两次。
     */
    private void deductForReportedItem(Report report) {
        Item item = itemMapper.selectById(report.getTargetId());
        if (item == null || item.getSellerId() == null) {
            return;
        }
        creditService.change(item.getSellerId(), CreditReasonEnum.REPORTED, report.getId(),
                "商品举报成立");
    }

    /* ══ 四、申诉裁决 ═══════════════════════════════════════ */
    /**
     * 申诉裁决：result=2 维持（商品不动）/ 3 撤销（按原处置反向恢复）。
     * 换人复审（F-A4）：复审人不得为原处置人，否则 REVIEW_APPEAL_SAME_HANDLER（3002）。
     * 撤销仅恢复平台处置过的状态（强制下架→在售、审核未过→待审核），其余状态不变，原因写入 handle_note。
     */
    @Transactional(rollbackFor = Exception.class)
    public void judgeAppeal(Long appealId, AppealJudgeReq req) {
        long begin = System.currentTimeMillis();
        Integer result = req.getResult();
        BizAssert.isTrue(result != null && (result == APPEAL_UPHOLD || result == APPEAL_REVOKE),
                CommonErrorCode.PARAM_ERROR, "复审结论只能是 2 维持 / 3 撤销");

        Appeal appeal = appealMapper.selectOne(new LambdaQueryWrapper<Appeal>()
                .eq(Appeal::getId, appealId)
                .eq(Appeal::getDeleted, 0));
        BizAssert.notNull(appeal, ReviewErrorCode.REVIEW_TASK_NOT_FOUND);
        BizAssert.isTrue(AppealStatusEnum.WAITING.getCode().equals(appeal.getStatus()),
                ReviewErrorCode.REVIEW_HANDLED);

        long adminId = AdminContext.getAdminId();
        Long originHandler = originHandlerOf(appeal);
        BizAssert.isTrue(originHandler == null || originHandler != adminId,
                ReviewErrorCode.REVIEW_APPEAL_SAME_HANDLER);

        LocalDateTime now = LocalDateTime.now();
        String note = trim(req.getNote());

        // 订单纠纷与针对商品处置的申诉是两种不同的纠正对象：前者要恢复的是订单，
        // 商品本身可能从未被处置过，交给 revoke 会误改商品状态
        if (isOrderAppeal(appeal)) {
            OrderStatusEnum target = (result == APPEAL_REVOKE)
                    ? OrderStatusEnum.REFUNDED
                    : OrderStatusEnum.COMPLETED;
            orderService.settleDispute(appeal.getBizId(), target);
            note = append(note, target == OrderStatusEnum.REFUNDED
                    ? "裁定买家胜诉，订单转入已退款"
                    : "裁定卖家胜诉，订单转入已完成");
        } else if (result == APPEAL_REVOKE) {
            note = revoke(appeal, note);
        }

        int n = appealMapper.update(null, new LambdaUpdateWrapper<Appeal>()
                .eq(Appeal::getId, appealId)
                .eq(Appeal::getStatus, AppealStatusEnum.WAITING.getCode())
                .set(Appeal::getStatus, result)
                .set(Appeal::getHandlerId, adminId)
                .set(Appeal::getHandleTime, now)
                .set(Appeal::getHandleNote, note));
        BizAssert.isTrue(n == 1, ReviewErrorCode.REVIEW_HANDLED);

        log.debug("申诉 {} 裁决 result={} 耗时 {}ms", appealId, result, System.currentTimeMillis() - begin);
    }

    /* ══ 五、队列组装 ═══════════════════════════════════════ */

    private ReviewTaskVO itemTask(ItemAudit audit, Fill fill) {
        // 商品审核没有 reason / evidence：它是常规上架流程，不是谁提出的指控
        return baseTask(QueueKindEnum.ITEM, audit.getId(), audit.getItemId(), audit.getEnterQueueAt(), fill);
    }

    private ReviewTaskVO reportTask(Report report, Fill fill) {
        ReviewTaskVO vo = baseTask(QueueKindEnum.REPORT, report.getId(), report.getTargetId(), report.getCreateTime(), fill);
        if (vo == null) {
            return null;
        }
        vo.setReason(report.getReason());
        vo.setEvidence(report.getEvidence());
        return vo;
    }

    private ReviewTaskVO appealTask(Appeal appeal, Map<Long, Report> appealReports,
                                    Map<Long, TradeOrder> appealOrders, Fill fill) {
        Long itemId = appealItemId(appeal, appealReports, appealOrders);
        if (itemId == null) {
            return null;
        }
        ReviewTaskVO vo = baseTask(QueueKindEnum.APPEAL, appeal.getId(), itemId, appeal.getCreateTime(), fill);
        if (vo == null) {
            return null;
        }
        vo.setReason(appeal.getReason());
        vo.setEvidence(appeal.getEvidence());

        // 原处置信息：换人复审要用（F-A4）。biz_type=1 取已处置的审核单据，2 取那条举报
        Origin origin = fill.origins.get(appeal.getId());
        if (origin != null) {
            vo.setHandledBy(origin.handlerId);
            vo.setHandledByName(origin.handlerName);
            vo.setHandledResult(origin.resultText);
        }
        return vo;
    }

    /**
     * 三类任务的共性字段组装：商品、卖家、权重。
     * 权重取自该商品最新审核记录快照 + 本单据进队时间（见 {@link #fillWeight}）。
     * 商品不存在时返回 null，交由调用方丢弃，而非中断整列。
     */
    private ReviewTaskVO baseTask(QueueKindEnum kind, Long bizId, Long itemId, LocalDateTime submitAt, Fill fill) {
        Item item = fill.items.get(itemId);
        if (item == null) {
            log.warn("审核队列里有一条指向不存在商品的单据：kind={} bizId={} itemId={}", kind, bizId, itemId);
            return null;
        }
        ItemAudit snapshot = fill.snapshots.get(itemId);

        ReviewTaskVO vo = new ReviewTaskVO();
        vo.setId(bizId);
        vo.setKind(kind);
        vo.setItemId(itemId);
        vo.setItemTitle(item.getTitle());
        vo.setCategoryName(fill.categoryNames.get(item.getCategoryId()));
        vo.setDescription(item.getDescription());
        vo.setMainImage(item.getMainImage());
        vo.setImages(fill.images.getOrDefault(itemId, List.of()));
        vo.setItemPrice(item.getPrice());
        vo.setItemStatus(ItemStatusEnum.fromCode(item.getStatus()));
        vo.setSellerId(item.getSellerId());

        UserPubVO seller = fill.users.get(item.getSellerId());
        vo.setSellerNick(seller == null ? null : seller.getNickname());
        vo.setSellerCredit(seller == null ? null : seller.getCreditScore());

        vo.setReportCount(fill.reportCounts.getOrDefault(itemId, 0L).intValue());
        vo.setRiskScore(snapshot == null ? 0 : nullSafe(snapshot.getImageRisk()));
        BigDecimal avg = fill.avgPrices.get(item.getCategoryId());
        vo.setCategoryAvgPrice(avg == null ? BigDecimal.ZERO : avg.setScale(2, RoundingMode.HALF_UP));

        vo.setSubmitAt(submitAt);
        fillWeight(vo, snapshot, submitAt);
        return vo;
    }

    /**
     * 计算权重五项与总分。前四项取自商品最新审核记录快照，第五项等待补偿按进队时间实算。
     * 总分由五项相加，不读 {@code priority_score}（同一公式的冗余快照），以保证「五项之和 = 总分」不变量。
     */
    private void fillWeight(ReviewTaskVO vo, ItemAudit snapshot, LocalDateTime submitAt) {
        long waitHours = waitHours(submitAt);
        WeightPartsVO p = new WeightPartsVO();
        p.setReport(nullSafe(snapshot == null ? null : snapshot.getReportCount()) * REPORT_WEIGHT);
        p.setRisk(snapshot == null ? 0 : (int) Math.round(nullSafe(snapshot.getImageRisk()) / 10.0));
        p.setCredit(nullSafe(snapshot == null ? null : snapshot.getCreditDeduct()));
        p.setPrice(nullSafe(snapshot == null ? null : snapshot.getPriceAbnormal()) * PRICE_ABNORMAL_WEIGHT);
        p.setWait((int) (waitHours / WAIT_COMPENSATE_HOURS));

        vo.setParts(p);
        vo.setWeight(p.getReport() + p.getRisk() + p.getCredit() + p.getPrice() + p.getWait());
        vo.setWaitHours((int) waitHours);
    }

    /**
     * SLA 概览。due = 等待 ≥ 首审阈值且 < 积压阈值（已超时但未积压）；overdue = 等待 ≥ 积压阈值；
     * avgHandleSeconds 是目标值而非 handle_cost_ms 统计值。三项统计不区分单据类型，
     * 故「申诉 48h 内复审」承诺未在概览体现，本期未落地。
     *
     * <p>阈值读 {@code sys_config}（设置页可调），不再写死常量——写死的话设置页改完刷新即失效。
     */
    private ReviewSlaVO sla(List<ReviewTaskVO> tasks) {
        SlaConfigVO cfg = sysConfigService.sla();
        int firstResponseHours = cfg.getFirstResponseHours();
        int backlogHours = cfg.getBacklogHours();
        ReviewSlaVO s = new ReviewSlaVO();
        s.setPending(tasks.size());
        s.setDue((int) tasks.stream()
                .filter(t -> t.getWaitHours() >= firstResponseHours && t.getWaitHours() < backlogHours)
                .count());
        s.setOverdue((int) tasks.stream().filter(t -> t.getWaitHours() >= backlogHours).count());
        s.setFirstResponseHours(firstResponseHours);
        s.setBacklogHours(backlogHours);
        s.setAvgHandleSeconds(cfg.getAvgHandleSeconds());
        return s;
    }

    /* ══ 六、批量回填 ═══════════════════════════════════════ */

    /** 批量回填：以商品 id 集合为入口，一次查齐队列所需的全部关联数据，供后续组装零回库。 */
    private Fill buildFill(List<Long> itemIds, List<Appeal> appeals, Map<Long, Report> appealReports) {
        Fill fill = new Fill();
        List<Long> ids = itemIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return fill;
        }

        // 商品主体
        fill.items = itemMapper.selectList(new LambdaQueryWrapper<Item>()
                        .in(Item::getId, ids)
                        .eq(Item::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(Item::getId, Function.identity(), (a, b) -> a));

        // 卖家（取商品集合中出现的 seller_id），只回填昵称与信用分，走公开资料缓存
        fill.users = userPubCache.getOrLoad(
                fill.items.values().stream().map(Item::getSellerId).distinct().toList());

        // 商品未结举报数（实时；已处理不计，口径见 ReportMapper#countByItem）
        fill.reportCounts = reportMapper.countByItem(ids).stream()
                .collect(Collectors.toMap(ReportMapper.ItemReportCountRow::getItemId,
                        ReportMapper.ItemReportCountRow::getCnt, (a, b) -> a));

        // 权重快照：取每件商品时间线最后一条审核记录（重提会产生多条）
        List<ItemAudit> all = itemAuditMapper.selectList(new LambdaQueryWrapper<ItemAudit>()
                .in(ItemAudit::getItemId, ids)
                .eq(ItemAudit::getDeleted, 0));
        fill.snapshots = all.stream()
                .collect(Collectors.toMap(ItemAudit::getItemId, Function.identity(),
                        (a, b) -> a.getCreateTime().compareTo(b.getCreateTime()) >= 0 ? a : b));

        // 商品图片（按商品分组）
        Map<Long, List<ItemImage>> rawImages = itemImageMapper.selectList(new LambdaQueryWrapper<ItemImage>()
                        .in(ItemImage::getItemId, ids)
                        .eq(ItemImage::getDeleted, 0)
                        .orderByAsc(ItemImage::getSort)
                        .orderByAsc(ItemImage::getId))
                .stream()
                .collect(Collectors.groupingBy(ItemImage::getItemId));
        fill.images = new HashMap<>();
        rawImages.forEach((itemId, list) ->
                fill.images.put(itemId, list.stream().map(this::toImageVO).toList()));

        // 分类均价走共享缓存：全量 GROUP BY 不随队列规模变化，与商品列表同一口径（TTL 5 分钟）
        fill.avgPrices = categoryAvgPriceCache.categoryAvgPrices();

        // 分类名（全量小表）
        fill.categoryNames = dictService.categories().stream()
                .collect(Collectors.toMap(DictCategoryVO::getId, DictCategoryVO::getName, (a, b) -> a));

        // 申诉原处置信息（换人复审用）
        fill.origins = origins(appeals, appealReports, all);
        return fill;
    }

    /**
     * 申诉原处置信息（换人复审用）。biz_type=1 取该商品最近一条已处置审核记录，
     * biz_type=2 取被引用举报的处置人。
     *
     * <p>**订单纠纷（biz_type=3）没有原处置人**：它是买卖双方谈不拢后由系统转入的，
     * 此前不存在任何管理员处置。`originHandlerOf` 因此返回 null，F-A4 的换人校验对这类申诉自动放宽，
     * 这不是漏掉，而是它本来就没有「自己复审自己」的对象。
     */
    private Map<Long, Origin> origins(List<Appeal> appeals, Map<Long, Report> appealReports, List<ItemAudit> all) {
        if (appeals.isEmpty()) {
            return Map.of();
        }
        Map<Long, Origin> map = new HashMap<>();
        List<Long> adminIds = new ArrayList<>();

        for (Appeal a : appeals) {
            Origin o = new Origin();
            if (AppealBizTypeEnum.ITEM.getCode().equals(a.getBizType())) {
                ItemAudit disposed = all.stream()
                        .filter(x -> a.getBizId().equals(x.getItemId())
                                && (AuditStatusEnum.REJECTED.getCode().equals(x.getStatus())
                                || AuditStatusEnum.FORCED_OFFLINE.getCode().equals(x.getStatus())))
                        .max(Comparator.comparing(ItemAudit::getCreateTime))
                        .orElse(null);
                if (disposed != null) {
                    o.handlerId = disposed.getHandlerId();
                    o.resultText = AuditStatusEnum.fromCode(disposed.getStatus()).getDesc();
                }
            } else if (AppealBizTypeEnum.REPORT.getCode().equals(a.getBizType())) {
                Report r = appealReports.get(a.getBizId());
                if (r != null) {
                    o.handlerId = r.getHandlerId();
                    o.resultText = r.getResult() == null ? null
                            : (r.getResult() == 1 ? "举报成立" : "举报不成立");
                }
            }
            if (o.handlerId != null) {
                adminIds.add(o.handlerId);
            }
            map.put(a.getId(), o);
        }

        Map<Long, String> names = adminIds.isEmpty() ? Map.of()
                : adminUserMapper.selectList(new LambdaQueryWrapper<AdminUser>()
                        .in(AdminUser::getId, adminIds.stream().distinct().toList()))
                .stream()
                .collect(Collectors.toMap(AdminUser::getId, AdminUser::getRealName, (a, b) -> a));
        map.values().forEach(o -> {
            if (o.handlerId != null) {
                o.handlerName = names.get(o.handlerId);
            }
        });
        return map;
    }

    /** 申诉引用的举报（biz_type = 2 时 biz_id 是 report.id），一次查齐避免逐条回库 */
    private Map<Long, Report> reportsById(List<Appeal> appeals) {
        List<Long> ids = appeals.stream()
                .filter(a -> AppealBizTypeEnum.REPORT.getCode().equals(a.getBizType()))
                .map(Appeal::getBizId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return reportMapper.selectList(new LambdaQueryWrapper<Report>()
                        .in(Report::getId, ids)
                        .eq(Report::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(Report::getId, Function.identity(), (a, b) -> a));
    }

    /** 申诉引用的订单（biz_type = 3 时 biz_id 是 trade_order.id），一次查齐避免逐条回库 */
    private Map<Long, TradeOrder> ordersById(List<Appeal> appeals) {
        List<Long> ids = appeals.stream()
                .filter(a -> AppealBizTypeEnum.ORDER.getCode().equals(a.getBizType()))
                .map(Appeal::getBizId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return tradeOrderMapper.selectList(new LambdaQueryWrapper<TradeOrder>()
                        .in(TradeOrder::getId, ids)
                        .eq(TradeOrder::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(TradeOrder::getId, Function.identity(), (a, b) -> a));
    }

    /**
     * 申诉指向的商品 id。biz_type=1 直接就是商品；2 与 3 都要转一层——
     * 举报打到商品上（`report.target_id`），订单纠纷则由 `order.item_id` 反查。
     *
     * <p>**订单纠纷此前被刻意返回 null（不进队列）**，理由是当时没有订单可引用；订单模块落地后这条分支成立，
     * 纠纷单据得以与管理端其余两类申诉共用同一条处置队列（notes/13 第五节）。
     */
    private Long appealItemId(Appeal appeal, Map<Long, Report> appealReports, Map<Long, TradeOrder> appealOrders) {
        if (AppealBizTypeEnum.ITEM.getCode().equals(appeal.getBizType())) {
            return appeal.getBizId();
        }
        if (AppealBizTypeEnum.REPORT.getCode().equals(appeal.getBizType())) {
            Report r = appealReports.get(appeal.getBizId());
            return r == null ? null : r.getTargetId();
        }
        if (AppealBizTypeEnum.ORDER.getCode().equals(appeal.getBizType())) {
            TradeOrder o = appealOrders.get(appeal.getBizId());
            return o == null ? null : o.getItemId();
        }
        return null;
    }

    private Long originHandlerOf(Appeal appeal) {
        if (AppealBizTypeEnum.ITEM.getCode().equals(appeal.getBizType())) {
            ItemAudit disposed = itemAuditMapper.selectList(new LambdaQueryWrapper<ItemAudit>()
                            .eq(ItemAudit::getItemId, appeal.getBizId())
                            .in(ItemAudit::getStatus, List.of(AuditStatusEnum.REJECTED.getCode(),
                                    AuditStatusEnum.FORCED_OFFLINE.getCode()))
                            .eq(ItemAudit::getDeleted, 0))
                    .stream()
                    .max(Comparator.comparing(ItemAudit::getCreateTime))
                    .orElse(null);
            return disposed == null ? null : disposed.getHandlerId();
        }
        if (AppealBizTypeEnum.REPORT.getCode().equals(appeal.getBizType())) {
            Report r = reportMapper.selectOne(new LambdaQueryWrapper<Report>()
                    .eq(Report::getId, appeal.getBizId())
                    .eq(Report::getDeleted, 0));
            return r == null ? null : r.getHandlerId();
        }
        return null;
    }

    /* ══ 七、撤销：恢复商品 ═════════════════════════════════ */

    /**
     * 撤销原判：将平台处置结果反向恢复。仅处理强制下架 / 审核未过两种状态，其余不变，
     * 恢复说明追加至 handle_note，便于复盘。
     *
     * @return 追加了恢复说明的处置备注
     */
    private String revoke(Appeal appeal, String note) {
        Long itemId = appealItemId(appeal, reportsById(List.of(appeal)), ordersById(List.of(appeal)));
        if (itemId == null) {
            return append(note, "申诉成立，但该申诉不是针对商品处置（biz_type=" + appeal.getBizType() + "），商品未变更");
        }
        Item item = itemMapper.selectOne(new LambdaQueryWrapper<Item>()
                .eq(Item::getId, itemId)
                .eq(Item::getDeleted, 0));
        if (item == null) {
            return append(note, "申诉成立，但商品已不存在，未变更");
        }

        ItemStatusEnum status = ItemStatusEnum.fromCode(item.getStatus());
        if (status == ItemStatusEnum.FORCED_OFFLINE) {
            LambdaUpdateWrapper<Item> u = new LambdaUpdateWrapper<Item>()
                    .eq(Item::getId, itemId)
                    .eq(Item::getDeleted, 0)
                    .set(Item::getStatus, ItemStatusEnum.ON_SALE.getCode())
                    .set(Item::getRejectReason, null);
            // 恢复上架：published_at 为空说明它从没真正上架过，补上当前时间
            if (item.getPublishedAt() == null) {
                u.set(Item::getPublishedAt, LocalDateTime.now());
            }
            itemMapper.update(null, u);
            // 举报成立时扣过分，误判纠正要还回去；按报单 id 记流水，便于与扣分对账
            if (AppealBizTypeEnum.REPORT.getCode().equals(appeal.getBizType())
                    && item.getSellerId() != null) {
                creditService.change(item.getSellerId(), CreditReasonEnum.REPORT_REVOKED,
                        appeal.getBizId(), "举报误判撤销");
            }
            return append(note, "申诉成立，商品恢复为在售");
        }
        if (status == ItemStatusEnum.REJECTED) {
            itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                    .eq(Item::getId, itemId)
                    .eq(Item::getDeleted, 0)
                    .set(Item::getStatus, ItemStatusEnum.PENDING.getCode())
                    .set(Item::getRejectReason, null));
            return append(note, "申诉成立，商品恢复为待审核");
        }
        return append(note, "申诉成立，但商品当前为「" + status.getDesc() + "」，未变更");
    }

    /* ══ 八、内部工具 ════════════════════════════════════════ */

    /**
     * 审核单据条件更新：仅当仍为待审核才改得动，影响行数 0 → REVIEW_HANDLED（已被处置）。
     */
    private void updateAudit(Long auditId, AuditStatusEnum target, long adminId, LocalDateTime now,
                             long begin, String reason, String remark) {
        int n = itemAuditMapper.update(null, new LambdaUpdateWrapper<ItemAudit>()
                .eq(ItemAudit::getId, auditId)
                .eq(ItemAudit::getStatus, AuditStatusEnum.PENDING.getCode())
                .set(ItemAudit::getStatus, target.getCode())
                .set(ItemAudit::getHandlerId, adminId)
                .set(ItemAudit::getFirstHandleAt, now)
                .set(ItemAudit::getHandleCostMs, (int) (System.currentTimeMillis() - begin))
                .set(reason != null, ItemAudit::getRejectReason, reason)
                .set(remark != null, ItemAudit::getRemark, remark));
        BizAssert.isTrue(n == 1, ReviewErrorCode.REVIEW_HANDLED);
    }

    private ItemAudit requirePendingAudit(Long auditId) {
        ItemAudit audit = itemAuditMapper.selectOne(new LambdaQueryWrapper<ItemAudit>()
                .eq(ItemAudit::getId, auditId)
                .eq(ItemAudit::getDeleted, 0));
        BizAssert.notNull(audit, ReviewErrorCode.REVIEW_TASK_NOT_FOUND);
        BizAssert.isTrue(AuditStatusEnum.PENDING.getCode().equals(audit.getStatus()),
                ReviewErrorCode.REVIEW_HANDLED);
        return audit;
    }

    private Item requireItem(Long itemId) {
        return BizAssert.notNull(itemMapper.selectOne(new LambdaQueryWrapper<Item>()
                .eq(Item::getId, itemId)
                .eq(Item::getDeleted, 0)), ReviewErrorCode.REVIEW_TASK_NOT_FOUND);
    }

    /**
     * 解析类型过滤参数。空值按全部处理；非空但非法值抛参数异常。
     */
    private QueueKindEnum parseKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return null;
        }
        QueueKindEnum k = QueueKindEnum.fromCode(kind);
        if (k == null) {
            throw new BizException(CommonErrorCode.PARAM_ERROR,
                    "队列类型不合法：" + kind);
        }
        return k;
    }

    private boolean isOrderAppeal(Appeal appeal) {
        return AppealBizTypeEnum.ORDER.getCode().equals(appeal.getBizType());
    }

    private long waitHours(LocalDateTime since) {
        if (since == null) {
            return 0;
        }
        return Math.max(0, Duration.between(since, LocalDateTime.now()).toHours());
    }

    private int nullSafe(Integer v) {
        return Objects.requireNonNullElse(v, 0);
    }

    private String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String append(String base, String extra) {
        return base == null ? extra : base + "；" + extra;
    }

    private ItemImageVO toImageVO(ItemImage img) {
        ItemImageVO v = new ItemImageVO();
        v.setId(img.getId());
        v.setUrl(img.getUrl());
        v.setSort(img.getSort());
        return v;
    }

    /** 队列组装用的单次关联数据容器，组装完即丢弃。 */
    private static class Fill {
        private Map<Long, Item> items = Map.of();
        private Map<Long, UserPubVO> users = Map.of();
        private Map<Long, ItemAudit> snapshots = Map.of();
        private Map<Long, Long> reportCounts = Map.of();
        private Map<Long, List<ItemImageVO>> images = Map.of();
        private Map<Long, BigDecimal> avgPrices = Map.of();
        private Map<Long, String> categoryNames = Map.of();
        private Map<Long, Origin> origins = Map.of();
    }

    /** 申诉的原处置信息（F-A4） */
    private static class Origin {
        private Long handlerId;
        private String handlerName;
        private String resultText;
    }
}

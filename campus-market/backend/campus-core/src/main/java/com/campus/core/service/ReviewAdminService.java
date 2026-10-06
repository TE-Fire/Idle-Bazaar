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
import com.campus.core.dto.resp.ReviewSlaVO;
import com.campus.core.dto.resp.ReviewTaskVO;
import com.campus.core.dto.resp.WeightPartsVO;
import com.campus.core.entity.AdminUser;
import com.campus.core.entity.AppUser;
import com.campus.core.entity.Appeal;
import com.campus.core.entity.Item;
import com.campus.core.entity.ItemAudit;
import com.campus.core.entity.ItemImage;
import com.campus.core.entity.Report;
import com.campus.core.enums.AppealStatusEnum;
import com.campus.core.enums.AuditStatusEnum;
import com.campus.core.enums.ItemStatusEnum;
import com.campus.core.enums.QueueKindEnum;
import com.campus.core.enums.ReportStatusEnum;
import com.campus.core.errcode.ReviewErrorCode;
import com.campus.core.mapper.AdminUserMapper;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.mapper.AppealMapper;
import com.campus.core.mapper.ItemAuditMapper;
import com.campus.core.mapper.ItemImageMapper;
import com.campus.core.mapper.ItemMapper;
import com.campus.core.mapper.ReportMapper;
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
 * 管理端审核队列（A-B2 商品审核 + A-B3 举报与申诉，流程见 notes/09）。
 *
 * <p><b>三种来源压成一条队列</b>：商品审核（`item_audit.status = 1`）、举报（`report.status IN (1,2)`）、
 * 申诉（`appeal.status = 1`）各有各的表，但共用一套「权重排序 + 连续审核」交互，
 * 所以在**这里**合成，不在前端合成——排序口径是最不能漂移的东西，分成三个接口前端就得再排一次。
 *
 * <p><b>排序 = 类型优先 + 组内权重降序</b>：申诉整体先于举报，举报先于商品审核
 * （见 {@link QueueKindEnum} 的 `order`）。组内按 {@link #weight} 降序，
 * 权重含实时的等待补偿，**不能只用库里的 `priority_score`**——那是进队时的基础分，
 * 只用它会让风险最高的老单沉底，正是 F-A2 要避免的。
 *
 * <p><b>强制下架复用 {@link ItemAdminService#forceOffline}</b>：商品页与审核页是同一个动作的两个入口，
 * 结果必须完全一致（status → 7 + 一条审核记录），两套实现必然漂移。
 *
 * <p><b>并发兜底用条件更新，不是先查后改</b>：所有处置都是
 * `WHERE id = ? AND status = 待处置`，影响行数 0 就抛 3001。
 * 先 `SELECT` 再 `UPDATE` 在两个人同时点按钮时两个都会成功，后一个会静默覆盖前一个的处置结果。
 *
 * <p><b>`handle_cost_ms` 的口径</b>：记的是**服务端处理这次处置动作的耗时**，不是审核员阅读单据的时长。
 * 要后者得由前端在打开 Drawer 时上报时间戳（多一个接口 + 时钟对齐），本期不做；
 * 这个数留给 A8 报表做「操作效率」时会被替换成更准的口径，注释在此避免误读。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewAdminService {

    /** SLA 阈值（business/03 F-A9）：与前端 mock 的 DEFAULT_SLA 一致，A9 设置落地后改从配置读 */
    private static final int FIRST_RESPONSE_HOURS = 2;
    private static final int BACKLOG_HOURS = 24;
    private static final int AVG_HANDLE_SECONDS = 30;

    /** 权重公式里的两个乘数（business/03 F-A2） */
    private static final int REPORT_WEIGHT = 3;
    private static final int PRICE_ABNORMAL_WEIGHT = 5;
    /** 等待每满 12 小时 +1 */
    private static final int WAIT_COMPENSATE_HOURS = 12;

    /** 申诉 `biz_type`：1 商品审核 2 举报 3 订单纠纷 */
    private static final int BIZ_ITEM = 1;
    private static final int BIZ_REPORT = 2;

    private final ItemMapper itemMapper;
    private final ItemImageMapper itemImageMapper;
    private final ItemAuditMapper itemAuditMapper;
    private final ReportMapper reportMapper;
    private final AppealMapper appealMapper;
    private final AppUserMapper appUserMapper;
    private final AdminUserMapper adminUserMapper;
    private final ItemAdminService itemAdminService;
    private final DictService dictService;

    /* ══ 一、队列 ═══════════════════════════════════════════ */

    /**
     * 合并队列（不分页）。
     *
     * @param kind 可选过滤：`item` / `report` / `appeal`；空或非法值之外的其它值按「全部」处理
     *
     * <p>关联数据用「批量回填」而不是逐行查：队列最多几百条，
     * 每条再查 5 次库就是几千次往返。见 notes/08 附录 A.2 定的模式。
     */
    public ReviewQueueVO queue(String kind) {
        QueueKindEnum filter = parseKind(kind);

        List<ItemAudit> audits = filter == null || filter == QueueKindEnum.ITEM
                ? itemAuditMapper.selectList(new LambdaQueryWrapper<ItemAudit>()
                .eq(ItemAudit::getStatus, AuditStatusEnum.PENDING.getCode())
                .eq(ItemAudit::getDeleted, 0))
                : List.of();

        List<Report> reports = filter == null || filter == QueueKindEnum.REPORT
                ? reportMapper.selectList(new LambdaQueryWrapper<Report>()
                .in(Report::getStatus, List.of(ReportStatusEnum.WAITING.getCode(),
                        ReportStatusEnum.PROCESSING.getCode()))
                .eq(Report::getTargetType, 1)
                .eq(Report::getDeleted, 0))
                : List.of();

        List<Appeal> appeals = filter == null || filter == QueueKindEnum.APPEAL
                ? appealMapper.selectList(new LambdaQueryWrapper<Appeal>()
                .eq(Appeal::getStatus, AppealStatusEnum.WAITING.getCode())
                .eq(Appeal::getDeleted, 0))
                : List.of();

        // 申诉的 biz_id 要绕一层才拿得到商品：先把它引用到的 report 一次查齐
        Map<Long, Report> appealReports = reportsById(appeals);

        List<Long> itemIds = new ArrayList<>();
        audits.forEach(a -> itemIds.add(a.getItemId()));
        reports.forEach(r -> itemIds.add(r.getTargetId()));
        appeals.forEach(a -> {
            Long itemId = appealItemId(a, appealReports);
            if (itemId != null) {
                itemIds.add(itemId);
            }
        });

        Fill fill = buildFill(itemIds, appeals, appealReports);

        List<ReviewTaskVO> tasks = new ArrayList<>();
        audits.forEach(a -> tasks.add(itemTask(a, fill)));
        reports.forEach(r -> tasks.add(reportTask(r, fill)));
        appeals.forEach(a -> tasks.add(appealTask(a, appealReports, fill)));
        // 队列里可能有解析不出商品的申诉（订单纠纷，本期不做），它们没有 VO
        tasks.removeIf(Objects::isNull);

        tasks.sort(Comparator
                .comparingInt((ReviewTaskVO t) -> t.getKind().getOrder())
                .thenComparing(Comparator.comparingInt(ReviewTaskVO::getWeight).reversed())
                .thenComparing(ReviewTaskVO::getSubmitAt));

        ReviewQueueVO vo = new ReviewQueueVO();
        vo.setList(tasks);
        vo.setSla(sla(tasks));
        return vo;
    }

    /* ══ 二、商品审核处置 ═══════════════════════════════════ */

    /**
     * 通过：商品 2 → 3（上架），审核单据 → 2。
     *
     * <p>商品必须先处于「待审核」才允许通过：单据是待审核、商品却已经在售，
     * 说明有别的地方改过状态，此时照着单据把它改成在售会覆盖掉别人的处置结果，所以拦住（3004）。
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
     * 驳回：商品 2 → 6（审核未过），审核单据 → 3 并记原因。
     *
     * <p>原因必填（`@NotBlank`）由 Controller 的 `@Valid` 拦，这里不再判空。
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
     * 强制下架：转调 {@link ItemAdminService#forceOffline}。
     *
     * <p>这里只做「单据必须还是待审核」这一层前置校验（重复处置 → 3001），
     * 商品侧的状态判断与审核留痕都在上游方法里——它是商品页与审核页共用的唯一实现。
     *
     * <p>`handle_cost_ms` 这一路不写：上游方法还有商品页入口，两边口径要一致，
     * 而「处置动作耗时」这个口径本来就只在本类的三个处置方法里记，不值得为它给上游加参数。
     */
    @Transactional(rollbackFor = Exception.class)
    public void forceOffline(Long auditId, ItemOfflineReq req) {
        ItemAudit audit = requirePendingAudit(auditId);
        itemAdminService.forceOffline(audit.getItemId(), req);
    }

    /* ══ 三、举报处置 ═══════════════════════════════════════ */

    /**
     * 举报裁决：`result = 1` 成立 → 商品走强制下架；`2` 不成立 → 商品不动。
     *
     * <p>不做独立的「受理」步骤（notes/09 问题 4）：裁决时一步到位把 `status` 写成 3。
     *
     * <p>成立 → 一律强制下架（→ 7），不区分轻重（notes/09 问题 9）：
     * 「整改后自行上架」的 4 是卖家自己下架的语义，平台处置混进去就分不清是谁下的架。
     *
     * <p>商品若处于不可下架的状态（已售出等），上游会抛 1106，整笔回滚——
     * 举报单保持「待处理」，不会出现「裁决成立但商品没动」的半截状态。
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

    /* ══ 四、申诉裁决 ═══════════════════════════════════════ */

    /**
     * 申诉裁决：2 维持（商品不动）/ 3 撤销（按原处置反向恢复商品）。
     *
     * <p><b>换人复审（F-A4）</b>：复审人不得是原处置人，命中抛 3002。
     * 原处置人从申诉指向的那次处置上取：`biz_type = 1` → 该商品最近一条已处置的审核单据，
     * `biz_type = 2` → 那条举报的 `handler_id`。
     *
     * <p><b>撤销的恢复规则</b>：商品当前是「已强制下架」才恢复成「在售」、是「审核未过」才恢复成「待审核」。
     * 其它状态（已售出 / 卖家自下）不动——商品已经卖出去了，恢复上架没有意义，
     * 也不该把卖家自己的操作撤掉。恢复结果写进 `handle_note`，复盘时能看出为什么没恢复。
     */
    @Transactional(rollbackFor = Exception.class)
    public void judgeAppeal(Long appealId, AppealJudgeReq req) {
        long begin = System.currentTimeMillis();
        Integer result = req.getResult();
        BizAssert.isTrue(result != null && (result == 2 || result == 3),
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

        if (result == 3) {
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

    private ReviewTaskVO appealTask(Appeal appeal, Map<Long, Report> appealReports, Fill fill) {
        Long itemId = appealItemId(appeal, appealReports);
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
     * 三种任务共用的字段：商品、卖家、权重。
     *
     * <p><b>权重全部取自该商品最新一条审核记录的快照 + 本单据自己的进队时间</b>：
     * 快照不随后续变化，正在处理的单子不会因为又来一条举报就换位置；
     * 等待补偿用本单据的时间，所以同一商品从三条通道进来可以有不同的等待分。
     *
     * @return 商品不存在时为 null（数据不一致时跳过这一条，而不是让整个队列 500）
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

        AppUser seller = fill.users.get(item.getSellerId());
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
     * 算权重五项与总分（business/03 F-A2）。
     *
     * <p>五项之和**恒等于** `weight`：UI 要逐项解释「为什么它排第一」，
     * 总分若另算一处就会出现「排第一的那条解释出来的分不是最高」。
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

    private ReviewSlaVO sla(List<ReviewTaskVO> tasks) {
        ReviewSlaVO s = new ReviewSlaVO();
        s.setPending(tasks.size());
        s.setDue((int) tasks.stream()
                .filter(t -> t.getWaitHours() >= FIRST_RESPONSE_HOURS && t.getWaitHours() < BACKLOG_HOURS)
                .count());
        s.setOverdue((int) tasks.stream().filter(t -> t.getWaitHours() >= BACKLOG_HOURS).count());
        s.setFirstResponseHours(FIRST_RESPONSE_HOURS);
        s.setBacklogHours(BACKLOG_HOURS);
        s.setAvgHandleSeconds(AVG_HANDLE_SECONDS);
        return s;
    }

    /* ══ 六、批量回填 ═══════════════════════════════════════ */

    private Fill buildFill(List<Long> itemIds, List<Appeal> appeals, Map<Long, Report> appealReports) {
        Fill fill = new Fill();
        List<Long> ids = itemIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return fill;
        }

        fill.items = itemMapper.selectList(new LambdaQueryWrapper<Item>()
                        .in(Item::getId, ids)
                        .eq(Item::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(Item::getId, Function.identity(), (a, b) -> a));

        fill.users = appUserMapper.selectList(new LambdaQueryWrapper<AppUser>()
                        .in(AppUser::getId, fill.items.values().stream().map(Item::getSellerId).distinct().toList())
                        .eq(AppUser::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity(), (a, b) -> a));

        fill.reportCounts = reportMapper.countByItem(ids).stream()
                .collect(Collectors.toMap(ReportMapper.ItemReportCountRow::getItemId,
                        ReportMapper.ItemReportCountRow::getCnt, (a, b) -> a));

        // 一个商品可以有多条审核记录（重提会再进队），要的是时间线上最后一条
        List<ItemAudit> all = itemAuditMapper.selectList(new LambdaQueryWrapper<ItemAudit>()
                .in(ItemAudit::getItemId, ids)
                .eq(ItemAudit::getDeleted, 0));
        fill.snapshots = all.stream()
                .collect(Collectors.toMap(ItemAudit::getItemId, Function.identity(),
                        (a, b) -> a.getCreateTime().compareTo(b.getCreateTime()) >= 0 ? a : b));

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

        fill.avgPrices = itemMapper.avgPriceByCategory().stream()
                .collect(Collectors.toMap(ItemMapper.CategoryAvgRow::getCategoryId,
                        ItemMapper.CategoryAvgRow::getAvgPrice, (a, b) -> a));

        // 分类名是全量小表，一次查完比按 id 过滤更简单（与 ItemAdminService 同一口径）
        fill.categoryNames = dictService.categories().stream()
                .collect(Collectors.toMap(DictCategoryVO::getId, DictCategoryVO::getName, (a, b) -> a));

        fill.origins = origins(appeals, appealReports, all);
        return fill;
    }

    /**
     * 申诉的「原处置人 + 原处置结果」，换人复审要用（F-A4）。
     *
     * <p>`biz_type = 3`（订单纠纷）取不到——订单模块还没做，
     * 这类申诉在队列里不带原处置信息，F-A4 的校验对它自动放宽（`originHandlerOf` 返回 null）。
     */
    private Map<Long, Origin> origins(List<Appeal> appeals, Map<Long, Report> appealReports, List<ItemAudit> all) {
        if (appeals.isEmpty()) {
            return Map.of();
        }
        Map<Long, Origin> map = new HashMap<>();
        List<Long> adminIds = new ArrayList<>();

        for (Appeal a : appeals) {
            Origin o = new Origin();
            if (a.getBizType() != null && a.getBizType() == BIZ_ITEM) {
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
            } else if (a.getBizType() != null && a.getBizType() == BIZ_REPORT) {
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
                .filter(a -> a.getBizType() != null && a.getBizType() == BIZ_REPORT)
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

    /** 申诉指向的商品 id；订单纠纷（biz_type = 3）本期返回 null */
    private Long appealItemId(Appeal appeal, Map<Long, Report> appealReports) {
        if (appeal.getBizType() == null) {
            return null;
        }
        if (appeal.getBizType() == BIZ_ITEM) {
            return appeal.getBizId();
        }
        if (appeal.getBizType() == BIZ_REPORT) {
            Report r = appealReports.get(appeal.getBizId());
            return r == null ? null : r.getTargetId();
        }
        return null;
    }

    private Long originHandlerOf(Appeal appeal) {
        if (appeal.getBizType() != null && appeal.getBizType() == BIZ_ITEM) {
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
        if (appeal.getBizType() != null && appeal.getBizType() == BIZ_REPORT) {
            Report r = reportMapper.selectOne(new LambdaQueryWrapper<Report>()
                    .eq(Report::getId, appeal.getBizId())
                    .eq(Report::getDeleted, 0));
            return r == null ? null : r.getHandlerId();
        }
        return null;
    }

    /* ══ 七、撤销：恢复商品 ═════════════════════════════════ */

    /**
     * 撤销原判：把商品从平台处置的结果恢复回去。
     *
     * <p>只处理两种「平台处置过」的状态，其余不动，并把原因写进 `handle_note`
     * ——卖家看到的是「申诉成立但商品未变更」而不是什么都没有，
     * 复盘时也能看出当时商品处于什么状态。
     *
     * @return 追加了恢复说明的处置备注
     */
    private String revoke(Appeal appeal, String note) {
        Long itemId = appealItemId(appeal, reportsById(List.of(appeal)));
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
     * 审核单据的条件更新：只有仍是「待审核」才改得动。
     *
     * <p>影响行数 0 → 3001（已被别人处置）。这比先查后改可靠：
     * 两个管理员同时点按钮时，先查后改两个都会走到这里，后一个会静默覆盖前一个的结果。
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

    /** kind 过滤参数：空 / 无法识别都按「全部」处理——筛选项是前端给的，不给前端报错的机会 */
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

    /** 队列组装用的一次性关联数据。字段可变，组装完即丢弃 */
    private static class Fill {
        private Map<Long, Item> items = Map.of();
        private Map<Long, AppUser> users = Map.of();
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

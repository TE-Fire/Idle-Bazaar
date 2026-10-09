package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.BizException;
import com.campus.common.exception.CommonErrorCode;
import com.campus.core.dto.req.AppealCreateReq;
import com.campus.core.dto.req.ReportCreateReq;
import com.campus.core.entity.Appeal;
import com.campus.core.entity.AppUser;
import com.campus.core.entity.Item;
import com.campus.core.entity.ItemAudit;
import com.campus.core.entity.Report;
import com.campus.core.entity.TradeOrder;
import com.campus.core.enums.AppealStatusEnum;
import com.campus.core.enums.ReportStatusEnum;
import com.campus.core.errcode.ItemErrorCode;
import com.campus.core.errcode.OrderErrorCode;
import com.campus.core.errcode.ReviewErrorCode;
import com.campus.core.mapper.AppealMapper;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.mapper.ItemAuditMapper;
import com.campus.core.mapper.ItemMapper;
import com.campus.core.mapper.ReportMapper;
import com.campus.core.mapper.TradeOrderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 学生端提交举报与申诉。
 *
 * <p>这两个接口体量很小——只是往 `report` / `appeal` 各插一行 `status = 1`——
 * 但它们是**审核队列第一次拥有真实单据来源**的关键：
 * 在此之前队列里只有演示数据，管理的 `*Service` 写得再完整也没有实物可审。
 *
 * <p>真正需要判断的不是插入本身，而是**谁有资格对什么发起单据**。
 * 校验都放在这里而不是管理端：管理端的队列只负责排和处置，不接受"这份申诉合不合法"的判断权下放。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper reportMapper;

    private final AppealMapper appealMapper;

    private final ItemMapper itemMapper;

    private final AppUserMapper appUserMapper;

    private final ItemAuditMapper itemAuditMapper;

    private final TradeOrderMapper orderMapper;

    private final ObjectMapper objectMapper;

    /**
     * 提交举报。
     *
     * <p>重复举报必须拦：同一人对同一对象已存在未处理单据时再插一行，
     * 队列会被同一条投诉刷屏，而队列右侧的权重是按「举报次数」累加的，等于变相操纵排序。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long report(Long userId, ReportCreateReq req) {
        Integer type = req.getTargetType();
        BizAssert.isTrue(type != null && (type == 1 || type == 2), CommonErrorCode.PARAM_ERROR);

        if (type == 1) {
            Item item = itemMapper.selectById(req.getTargetId());
            BizAssert.notNull(item, ItemErrorCode.ITEM_NOT_FOUND);
            BizAssert.isTrue(!userId.equals(item.getSellerId()),
                    ReviewErrorCode.CANNOT_REPORT_SELF);
        } else {
            AppUser target = appUserMapper.selectById(req.getTargetId());
            BizAssert.notNull(target, CommonErrorCode.NOT_FOUND);
        }

        Long dup = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, userId)
                .eq(Report::getTargetType, type)
                .eq(Report::getTargetId, req.getTargetId())
                .in(Report::getStatus,
                        List.of(ReportStatusEnum.WAITING.getCode(), ReportStatusEnum.PROCESSING.getCode())));
        BizAssert.isTrue(dup == null || dup == 0, ReviewErrorCode.REPORT_DUPLICATE);

        Report report = new Report()
                .setReporterId(userId)
                .setTargetType(type)
                .setTargetId(req.getTargetId())
                .setReason(req.getReason())
                .setEvidence(toJson(req.getEvidence()))
                .setStatus(ReportStatusEnum.WAITING.getCode());
        reportMapper.insert(report);
        return report.getId();
    }

    /**
     * 提交申诉。
     *
     * <p>申诉是对「针对自己的处置」发起的，所以判据分两条：<b>单据必须存在</b>，
     * 且<b>申诉人是该单据的承受方</b>。少了后者，任何人都能对任意 id 申诉，
     * 换人复审（F-A4）这条规则就会被绕过。
     *
     * <p>`bizType = 3`（订单纠纷）按 notes/13 决策 2 本期开放，交由任务 6 把它接进队列。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long appeal(Long userId, AppealCreateReq req) {
        Long dup = appealMapper.selectCount(new LambdaQueryWrapper<Appeal>()
                .eq(Appeal::getApplicantId, userId)
                .eq(Appeal::getBizType, req.getBizType())
                .eq(Appeal::getBizId, req.getBizId()));
        BizAssert.isTrue(dup == null || dup == 0, ReviewErrorCode.APPEAL_NOT_ALLOWED);

        int bizType = req.getBizType();
        // 显式初始化：default 分支会抛异常走不到使用点，但这里不依赖 Java 的确定性赋值分析
        boolean owned = false;
        switch (bizType) {
            case 1 -> {
                ItemAudit audit = itemAuditMapper.selectById(req.getBizId());
                BizAssert.notNull(audit, ReviewErrorCode.REVIEW_TASK_NOT_FOUND);
                Item item = itemMapper.selectById(audit.getItemId());
                BizAssert.notNull(item, ItemErrorCode.ITEM_NOT_FOUND);
                owned = userId.equals(item.getSellerId());
            }
            case 2 -> {
                Report target = reportMapper.selectById(req.getBizId());
                BizAssert.notNull(target, CommonErrorCode.NOT_FOUND);
                owned = isReportTarget(target, userId);
            }
            case 3 -> {
                TradeOrder order = orderMapper.selectById(req.getBizId());
                BizAssert.notNull(order, OrderErrorCode.ORDER_NOT_FOUND);
                owned = userId.equals(order.getBuyerId()) || userId.equals(order.getSellerId());
            }
            default -> throw new BizException(CommonErrorCode.PARAM_ERROR);
        }
        BizAssert.isTrue(owned, ReviewErrorCode.APPEAL_NOT_ALLOWED);

        Appeal appeal = new Appeal()
                .setApplicantId(userId)
                .setBizType(bizType)
                .setBizId(req.getBizId())
                .setReason(req.getReason())
                .setEvidence(toJson(req.getEvidence()))
                .setStatus(AppealStatusEnum.WAITING.getCode());
        appealMapper.insert(appeal);
        return appeal.getId();
    }

    /**
     * 被举报方的判定：`target_type = 1` 时是商品卖家，否则是被举报的用户本人。
     *
     * <p>两条通道必须合并判断——商品举报打到**卖家**头上，但单据上记的是商品 id，
     * 只比 `target_id` 会把「举报人」误判成「被举报人」。
     */
    private boolean isReportTarget(Report target, Long userId) {
        if (Integer.valueOf(1).equals(target.getTargetType())) {
            Item item = itemMapper.selectById(target.getTargetId());
            return item != null && userId.equals(item.getSellerId());
        }
        return userId.equals(target.getTargetId());
    }

    /** 凭证序列化失败不该阻断提交——它是辅助材料，理由是必填项 */
    private String toJson(List<String> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (Exception e) {
            log.warn("[report] 凭证序列化失败，忽略：{}", evidence, e);
            return null;
        }
    }
}

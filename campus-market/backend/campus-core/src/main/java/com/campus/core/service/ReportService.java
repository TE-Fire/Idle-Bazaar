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
import com.campus.core.entity.ItemComment;
import com.campus.core.entity.MomentComment;
import com.campus.core.entity.Report;
import com.campus.core.entity.TradeOrder;
import com.campus.core.entity.UserMoment;
import com.campus.core.enums.AppealBizTypeEnum;
import com.campus.core.enums.AppealStatusEnum;
import com.campus.core.enums.OrderStatusEnum;
import com.campus.core.enums.ReportStatusEnum;
import com.campus.core.enums.ReportTargetTypeEnum;
import com.campus.core.errcode.ItemErrorCode;
import com.campus.core.errcode.OrderErrorCode;
import com.campus.core.errcode.ReviewErrorCode;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.mapper.AppealMapper;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.mapper.ItemAuditMapper;
import com.campus.core.mapper.ItemCommentMapper;
import com.campus.core.mapper.ItemMapper;
import com.campus.core.mapper.MomentCommentMapper;
import com.campus.core.mapper.ReportMapper;
import com.campus.core.mapper.TradeOrderMapper;
import com.campus.core.mapper.UserMomentMapper;
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

    private final UserMomentMapper momentMapper;

    private final ItemCommentMapper itemCommentMapper;

    private final MomentCommentMapper momentCommentMapper;

    private final ObjectMapper objectMapper;

    /**
     * 提交举报。
     *
     * <p>重复举报必须拦：同一人对同一对象已存在未处理单据时再插一行，
     * 队列会被同一条投诉刷屏，而队列右侧的权重是按「举报次数」累加的，等于变相操纵排序。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long report(Long userId, ReportCreateReq req) {
        ReportTargetTypeEnum targetType = ReportTargetTypeEnum.fromCodeOrNull(req.getTargetType());
        BizAssert.notNull(targetType, CommonErrorCode.PARAM_ERROR,
                "举报对象类型只能是 1 商品 / 2 用户 / 3 动态 / 4 商品评论 / 5 动态评论");

        switch (targetType) {
            case ITEM -> {
                Item item = itemMapper.selectById(req.getTargetId());
                BizAssert.notNull(item, ItemErrorCode.ITEM_NOT_FOUND);
                BizAssert.isTrue(!userId.equals(item.getSellerId()),
                        ReviewErrorCode.CANNOT_REPORT_SELF);
            }
            case USER -> {
                AppUser target = appUserMapper.selectById(req.getTargetId());
                BizAssert.notNull(target, CommonErrorCode.NOT_FOUND);
            }
            // 社交三类：只允许举报仍在架的内容（已屏蔽 / 已删除的没有处置对象），且不能举报自己
            case MOMENT -> {
                UserMoment m = momentMapper.selectOne(new LambdaQueryWrapper<UserMoment>()
                        .eq(UserMoment::getId, req.getTargetId())
                        .eq(UserMoment::getDeleted, 0L));
                BizAssert.notNull(m, UserErrorCode.MOMENT_NOT_FOUND);
                BizAssert.isTrue(!userId.equals(m.getUserId()), ReviewErrorCode.CANNOT_REPORT_SELF);
            }
            case ITEM_COMMENT -> {
                ItemComment c = itemCommentMapper.selectOne(new LambdaQueryWrapper<ItemComment>()
                        .eq(ItemComment::getId, req.getTargetId())
                        .eq(ItemComment::getDeleted, 0L));
                BizAssert.notNull(c, CommonErrorCode.NOT_FOUND);
                BizAssert.isTrue(!userId.equals(c.getUserId()), ReviewErrorCode.CANNOT_REPORT_SELF);
            }
            case MOMENT_COMMENT -> {
                MomentComment c = momentCommentMapper.selectOne(new LambdaQueryWrapper<MomentComment>()
                        .eq(MomentComment::getId, req.getTargetId())
                        .eq(MomentComment::getDeleted, 0L));
                BizAssert.notNull(c, CommonErrorCode.NOT_FOUND);
                BizAssert.isTrue(!userId.equals(c.getUserId()), ReviewErrorCode.CANNOT_REPORT_SELF);
            }
            default -> throw new BizException(CommonErrorCode.PARAM_ERROR);
        }

        Long dup = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, userId)
                .eq(Report::getTargetType, targetType.getCode())
                .eq(Report::getTargetId, req.getTargetId())
                .in(Report::getStatus,
                        List.of(ReportStatusEnum.WAITING.getCode(), ReportStatusEnum.PROCESSING.getCode())));
        BizAssert.isTrue(dup == null || dup == 0, ReviewErrorCode.REPORT_DUPLICATE);

        Report report = new Report()
                .setReporterId(userId)
                .setTargetType(targetType.getCode())
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
        AppealBizTypeEnum bizType = AppealBizTypeEnum.fromCodeOrNull(req.getBizType());
        BizAssert.notNull(bizType, CommonErrorCode.PARAM_ERROR,
                "申诉对象类型只能是 1 商品审核 / 2 举报 / 3 订单纠纷");

        Long dup = appealMapper.selectCount(new LambdaQueryWrapper<Appeal>()
                .eq(Appeal::getApplicantId, userId)
                .eq(Appeal::getBizType, bizType.getCode())
                .eq(Appeal::getBizId, req.getBizId()));
        BizAssert.isTrue(dup == null || dup == 0, ReviewErrorCode.APPEAL_NOT_ALLOWED);

        // 显式初始化：default 分支会抛异常走不到使用点，但这里不依赖 Java 的确定性赋值分析
        boolean owned = false;
        switch (bizType) {
            case ITEM -> {
                ItemAudit audit = itemAuditMapper.selectById(req.getBizId());
                BizAssert.notNull(audit, ReviewErrorCode.REVIEW_TASK_NOT_FOUND);
                Item item = itemMapper.selectById(audit.getItemId());
                BizAssert.notNull(item, ItemErrorCode.ITEM_NOT_FOUND);
                owned = userId.equals(item.getSellerId());
            }
            case REPORT -> {
                Report target = reportMapper.selectById(req.getBizId());
                BizAssert.notNull(target, CommonErrorCode.NOT_FOUND);
                owned = isReportTarget(target, userId);
            }
            case ORDER -> {
                TradeOrder order = orderMapper.selectById(req.getBizId());
                BizAssert.notNull(order, OrderErrorCode.ORDER_NOT_FOUND);
                // 订单纠纷只能从 /api/order/{id}/dispute 进入状态机：这里只收纠纷中订单的
                // 补充申诉，且一单一条——judgeAppeal 按 biz_id 取件，两条申诉会让同一纠纷裁两遍
                BizAssert.isTrue(OrderStatusEnum.fromCode(order.getStatus()) == OrderStatusEnum.DISPUTING,
                        OrderErrorCode.ORDER_STATUS_INVALID, "订单纠纷须通过纠纷入口发起");
                Long exists = appealMapper.selectCount(new LambdaQueryWrapper<Appeal>()
                        .eq(Appeal::getBizType, bizType.getCode())
                        .eq(Appeal::getBizId, req.getBizId()));
                BizAssert.isTrue(exists == null || exists == 0, ReviewErrorCode.APPEAL_NOT_ALLOWED,
                        "该订单已有纠纷申诉");
                owned = userId.equals(order.getBuyerId()) || userId.equals(order.getSellerId());
            }
            default -> throw new BizException(CommonErrorCode.PARAM_ERROR);
        }
        BizAssert.isTrue(owned, ReviewErrorCode.APPEAL_NOT_ALLOWED);

        Appeal appeal = new Appeal()
                .setApplicantId(userId)
                .setBizType(bizType.getCode())
                .setBizId(req.getBizId())
                .setReason(req.getReason())
                .setEvidence(toJson(req.getEvidence()))
                .setStatus(AppealStatusEnum.WAITING.getCode());
        appealMapper.insert(appeal);
        return appeal.getId();
    }

    /**
     * 被举报方的判定：商品举报打到**卖家**头上、社交三类打到**内容作者**头上，
     * 其余（用户举报）就是 target_id 本人。
     *
     * <p>几条通道必须合并判断——单据上记的是被举报内容的 id，只比 `target_id`
     * 会把「举报人」误判成「被举报人」。
     */
    private boolean isReportTarget(Report target, Long userId) {
        ReportTargetTypeEnum type = ReportTargetTypeEnum.fromCodeOrNull(target.getTargetType());
        if (type == null) {
            return false;
        }
        switch (type) {
            case ITEM -> {
                Item item = itemMapper.selectById(target.getTargetId());
                return item != null && userId.equals(item.getSellerId());
            }
            case MOMENT -> {
                UserMoment m = momentMapper.selectById(target.getTargetId());
                return m != null && userId.equals(m.getUserId());
            }
            case ITEM_COMMENT -> {
                ItemComment c = itemCommentMapper.selectById(target.getTargetId());
                return c != null && userId.equals(c.getUserId());
            }
            case MOMENT_COMMENT -> {
                MomentComment c = momentCommentMapper.selectById(target.getTargetId());
                return c != null && userId.equals(c.getUserId());
            }
            default -> {
                return userId.equals(target.getTargetId());
            }
        }
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

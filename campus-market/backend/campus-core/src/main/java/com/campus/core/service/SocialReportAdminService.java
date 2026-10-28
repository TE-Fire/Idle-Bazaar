package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.BizException;
import com.campus.common.exception.CommonErrorCode;
import com.campus.common.result.PageResult;
import com.campus.core.context.AdminContext;
import com.campus.core.dto.req.ReportHandleReq;
import com.campus.core.dto.resp.SocialReportVO;
import com.campus.core.dto.resp.UserPubVO;
import com.campus.core.entity.ItemComment;
import com.campus.core.entity.MomentComment;
import com.campus.core.entity.Report;
import com.campus.core.entity.UserMoment;
import com.campus.core.enums.ReportStatusEnum;
import com.campus.core.enums.ReportTargetTypeEnum;
import com.campus.core.errcode.ReviewErrorCode;
import com.campus.core.mapper.ItemCommentMapper;
import com.campus.core.mapper.MomentCommentMapper;
import com.campus.core.mapper.ReportMapper;
import com.campus.core.mapper.UserMomentMapper;
import com.campus.core.service.cache.UserPubCache;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 社交举报处置（管理端，notes/22 批次 3）。
 *
 * <p>为什么不并进审核队列：队列的任务模型围绕「商品」——权重按商品举报次数累加、SLA 按审核单统计，
 * 动态与评论既解析不出商品、也没有 SLA 要求（notes/09）。所以社交举报单独一条通道，
 * 处置动作只有一个：把内容的 `audit_status` 置成「举报下架(2)」，随即从广场流与评论列表消失。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialReportAdminService {

    /** audit_status：2 举报下架 */
    private static final int AUDIT_OFFLINE = 2;
    private static final int SUMMARY_MAX = 60;
    private static final List<Integer> SOCIAL_TYPES = List.of(
            ReportTargetTypeEnum.MOMENT.getCode(),
            ReportTargetTypeEnum.ITEM_COMMENT.getCode(),
            ReportTargetTypeEnum.MOMENT_COMMENT.getCode());

    private final ReportMapper reportMapper;
    private final UserMomentMapper momentMapper;
    private final ItemCommentMapper itemCommentMapper;
    private final MomentCommentMapper momentCommentMapper;
    private final UserPubCache userPubCache;
    private final NoticeService noticeService;
    private final ObjectMapper objectMapper;

    /** 举报列表：只出社交三类，按提交时间倒序 */
    public PageResult<SocialReportVO> list(Integer status, Integer targetType, int pageNum, int pageSize) {
        int pn = Math.max(1, pageNum);
        int ps = Math.min(Math.max(1, pageSize), 50);

        LambdaQueryWrapper<Report> w = new LambdaQueryWrapper<Report>()
                .eq(Report::getDeleted, 0L)
                .orderByDesc(Report::getCreateTime)
                .orderByDesc(Report::getId);
        if (targetType != null) {
            ReportTargetTypeEnum t = ReportTargetTypeEnum.fromCodeOrNull(targetType);
            BizAssert.isTrue(t != null && t.isSocial(), CommonErrorCode.PARAM_ERROR,
                    "targetType 只能是 3 动态 / 4 商品评论 / 5 动态评论");
            w.eq(Report::getTargetType, targetType);
        } else {
            w.in(Report::getTargetType, SOCIAL_TYPES);
        }
        if (status != null) {
            w.eq(Report::getStatus, status);
        }

        Page<Report> p = reportMapper.selectPage(new Page<>(pn, ps), w);
        return PageResult.of(toVOList(p.getRecords()), p.getTotal(), p.getCurrent(), p.getSize());
    }

    /**
     * 裁决：result=1 成立 → 内容置「举报下架(2)」并通知作者；result=2 不成立 → 内容不动。
     * 一步写 status=3，与商品举报同一口径（不做独立受理步骤）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void handle(long reportId, ReportHandleReq req) {
        Integer result = req.getResult();
        BizAssert.isTrue(result != null && (result == 1 || result == 2),
                CommonErrorCode.PARAM_ERROR, "裁决结论只能是 1 成立 / 2 不成立");

        Report report = reportMapper.selectOne(new LambdaQueryWrapper<Report>()
                .eq(Report::getId, reportId)
                .eq(Report::getDeleted, 0L));
        BizAssert.notNull(report, ReviewErrorCode.REVIEW_TASK_NOT_FOUND);

        ReportTargetTypeEnum type = ReportTargetTypeEnum.fromCodeOrNull(report.getTargetType());
        BizAssert.isTrue(type != null && type.isSocial(), CommonErrorCode.PARAM_ERROR,
                "该举报不是社交内容举报，请到审核队列处置");
        BizAssert.isTrue(!ReportStatusEnum.HANDLED.getCode().equals(report.getStatus()),
                ReviewErrorCode.REVIEW_HANDLED);

        String note = req.getNote() == null ? null : req.getNote().trim();
        String reason = null;
        Long authorId = null;
        if (result == 1) {
            reason = BizAssert.notBlank(req.getReason(),
                    CommonErrorCode.PARAM_ERROR, "举报成立时处置依据必填").trim();
            authorId = offlineContent(type, report.getTargetId(), reason, note);
        }

        int n = reportMapper.update(null, new LambdaUpdateWrapper<Report>()
                .eq(Report::getId, reportId)
                .in(Report::getStatus, List.of(ReportStatusEnum.WAITING.getCode(),
                        ReportStatusEnum.PROCESSING.getCode()))
                .set(Report::getStatus, ReportStatusEnum.HANDLED.getCode())
                .set(Report::getResult, result)
                .set(Report::getHandlerId, AdminContext.getAdminId())
                .set(Report::getHandleTime, LocalDateTime.now())
                .set(Report::getHandleNote, note));
        BizAssert.isTrue(n == 1, ReviewErrorCode.REVIEW_HANDLED);

        if (authorId != null) {
            noticeService.push(authorId, String.format(
                    "你发布的%s因「%s」被举报成立，已下架。如有异议可在对应详情页申诉。",
                    type.getDesc(), note == null || note.isBlank() ? reason : note));
        }
        log.debug("社交举报 {} 裁决 result={} type={} target={}", reportId, result, type, report.getTargetId());
    }

    /** 置内容为举报下架，返回内容作者 id；内容已被删则返回 null（不阻断单据结案） */
    private Long offlineContent(ReportTargetTypeEnum type, long targetId, String reason, String note) {
        switch (type) {
            case MOMENT -> {
                UserMoment m = momentMapper.selectById(targetId);
                if (m == null || m.getDeleted() != null && m.getDeleted() != 0) {
                    return null;
                }
                momentMapper.update(null, new LambdaUpdateWrapper<UserMoment>()
                        .eq(UserMoment::getId, targetId)
                        .set(UserMoment::getAuditStatus, AUDIT_OFFLINE));
                return m.getUserId();
            }
            case ITEM_COMMENT -> {
                ItemComment c = itemCommentMapper.selectById(targetId);
                if (c == null || c.getDeleted() != null && c.getDeleted() != 0) {
                    return null;
                }
                itemCommentMapper.update(null, new LambdaUpdateWrapper<ItemComment>()
                        .eq(ItemComment::getId, targetId)
                        .set(ItemComment::getAuditStatus, AUDIT_OFFLINE));
                return c.getUserId();
            }
            case MOMENT_COMMENT -> {
                MomentComment c = momentCommentMapper.selectById(targetId);
                if (c == null || c.getDeleted() != null && c.getDeleted() != 0) {
                    return null;
                }
                momentCommentMapper.update(null, new LambdaUpdateWrapper<MomentComment>()
                        .eq(MomentComment::getId, targetId)
                        .set(MomentComment::getAuditStatus, AUDIT_OFFLINE));
                return c.getUserId();
            }
            default -> {
                return null;
            }
        }
    }

    /* ── 组装 ── */

    private List<SocialReportVO> toVOList(List<Report> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> momentIds = new HashSet<>();
        Set<Long> icIds = new HashSet<>();
        Set<Long> mcIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (Report r : rows) {
            userIds.add(r.getReporterId());
            ReportTargetTypeEnum t = ReportTargetTypeEnum.fromCodeOrNull(r.getTargetType());
            if (t == null) {
                continue;
            }
            switch (t) {
                case MOMENT -> momentIds.add(r.getTargetId());
                case ITEM_COMMENT -> icIds.add(r.getTargetId());
                case MOMENT_COMMENT -> mcIds.add(r.getTargetId());
                default -> {
                    // 非社交类型已在查询层过滤，这里不会出现
                }
            }
        }

        Map<Long, UserMoment> moments = new HashMap<>();
        if (!momentIds.isEmpty()) {
            for (UserMoment m : momentMapper.selectBatchIds(momentIds)) {
                moments.put(m.getId(), m);
                userIds.add(m.getUserId());
            }
        }
        Map<Long, ItemComment> itemComments = new HashMap<>();
        if (!icIds.isEmpty()) {
            for (ItemComment c : itemCommentMapper.selectBatchIds(icIds)) {
                itemComments.put(c.getId(), c);
                userIds.add(c.getUserId());
            }
        }
        Map<Long, MomentComment> momentComments = new HashMap<>();
        if (!mcIds.isEmpty()) {
            for (MomentComment c : momentCommentMapper.selectBatchIds(mcIds)) {
                momentComments.put(c.getId(), c);
                userIds.add(c.getUserId());
            }
        }
        Map<Long, UserPubVO> users = userPubCache.getOrLoad(userIds);

        List<SocialReportVO> list = new ArrayList<>(rows.size());
        for (Report r : rows) {
            SocialReportVO vo = new SocialReportVO();
            vo.setId(r.getId());
            vo.setReporterId(r.getReporterId());
            UserPubVO reporter = users.get(r.getReporterId());
            vo.setReporterNick(reporter == null ? null : reporter.getNickname());
            vo.setTargetType(r.getTargetType());
            ReportTargetTypeEnum t = ReportTargetTypeEnum.fromCodeOrNull(r.getTargetType());
            vo.setTargetTypeName(t == null ? null : t.getDesc());
            vo.setTargetId(r.getTargetId());
            vo.setReason(r.getReason());
            vo.setEvidence(readEvidence(r.getEvidence()));
            vo.setStatus(r.getStatus());
            vo.setResult(r.getResult());
            vo.setHandleNote(r.getHandleNote());
            vo.setCreateTime(r.getCreateTime());

            if (t != null) {
                switch (t) {
                    case MOMENT -> {
                        UserMoment m = moments.get(r.getTargetId());
                        if (m != null) {
                            vo.setTargetSummary(trim(m.getContent()));
                            vo.setTargetAuthorId(m.getUserId());
                        }
                    }
                    case ITEM_COMMENT -> {
                        ItemComment c = itemComments.get(r.getTargetId());
                        if (c != null) {
                            vo.setTargetSummary(trim(c.getContent()));
                            vo.setTargetAuthorId(c.getUserId());
                        }
                    }
                    case MOMENT_COMMENT -> {
                        MomentComment c = momentComments.get(r.getTargetId());
                        if (c != null) {
                            vo.setTargetSummary(trim(c.getContent()));
                            vo.setTargetAuthorId(c.getUserId());
                        }
                    }
                    default -> {
                        // 非社交类型不会进列表
                    }
                }
            }
            UserPubVO author = vo.getTargetAuthorId() == null ? null : users.get(vo.getTargetAuthorId());
            vo.setTargetAuthorNick(author == null ? null : author.getNickname());
            list.add(vo);
        }
        return list;
    }

    private String trim(String content) {
        if (content == null) {
            return null;
        }
        return content.length() > SUMMARY_MAX ? content.substring(0, SUMMARY_MAX) + '…' : content;
    }

    private List<String> readEvidence(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}

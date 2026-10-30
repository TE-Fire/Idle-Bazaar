package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.CommonErrorCode;
import com.campus.common.result.PageResult;
import com.campus.core.dto.req.NoticePublishReq;
import com.campus.core.dto.resp.AdminNoticeVO;
import com.campus.core.entity.AppUser;
import com.campus.core.entity.MsgMessage;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.mapper.MsgMessageMapper;
import com.campus.core.mapper.MsgMessageMapper.NoticeBatchRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台公告的发布 / 列表 / 撤回（notes/22 S12，管理端 A11）。
 *
 * <p><b>写放大是明知的</b>：学生端读的是「按 `receiver_id` 取我的消息」，不是「取广播再判断我看不看」，
 * 所以一条公告发 N 人就写 N 行、共享一个 `batchId`。万级用户下这个模型撑不住，
 * 届时应改成「公告表 + 已读表」的拉模型——那是重构读接口的事，不在本期。
 *
 * <p><b>撤回只软删收件行</b>，不删管理端的记录：列表按 `batch_id` 聚合、不看 `deleted`，
 * 所以撤回后那一行仍在，只是标成「已撤回」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeAdminService {

    /** 单条 INSERT 的行数上限：拼接超长 SQL 会撞 max_allowed_packet */
    private static final int INSERT_BATCH = 500;

    /** 单次发送人数上限：防误触「全员公告」的闸门（notes/22 S12 规则 6） */
    private static final int MAX_RECEIVERS = 5000;

    /** 账号状态：2 封禁，不发公告——发了也读不到，只徒增未读数 */
    private static final int USER_BANNED = 2;

    private final MsgMessageMapper msgMapper;
    private final AppUserMapper appUserMapper;
    private final SensitiveWordService sensitiveWordService;

    /**
     * 发布：先定接收人再写行，接收人为空视为参数错误。
     *
     * <p>正文与标题都过敏感词——公告是平台自己说的话，更不能带违规内容（高危词直接抛 4024）。
     */
    @Transactional
    public Long publish(NoticePublishReq req) {
        String title = trimToNull(req.getTitle());
        if (title != null) {
            title = sensitiveWordService.filter(title);
        }
        String content = sensitiveWordService.filter(req.getContent().trim());
        Long campusId = req.getCampusId();

        List<Long> receivers = receivers(campusId);
        BizAssert.isTrue(!receivers.isEmpty(), CommonErrorCode.PARAM_ERROR,
                "该范围内没有可接收的账号（封禁账号不发公告）");
        BizAssert.isTrue(receivers.size() <= MAX_RECEIVERS, UserErrorCode.NOTICE_AUDIENCE_TOO_LARGE);

        long batchId = IdWorker.getId();
        String extra = NoticeService.extraOf(title, campusId);
        int written = 0;
        for (int from = 0; from < receivers.size(); from += INSERT_BATCH) {
            List<MsgMessage> rows = new ArrayList<>(INSERT_BATCH);
            for (Long receiverId : receivers.subList(from, Math.min(from + INSERT_BATCH, receivers.size()))) {
                rows.add(new MsgMessage()
                        .setId(IdWorker.getId())
                        .setSenderId(0L)
                        .setReceiverId(receiverId)
                        .setContent(content)
                        .setExtra(extra)
                        .setBatchId(batchId));
            }
            written += msgMapper.insertAnnouncements(rows);
        }
        log.info("[notice] 公告已发布 batch={} 接收人={} 写入行={} campus={}", batchId, receivers.size(), written, campusId);
        return batchId;
    }

    /** 公告列表：一条公告一行，标题/正文从该批次的样本行取（各收件行内容相同） */
    public PageResult<AdminNoticeVO> list(int pageNum, int pageSize) {
        int pn = Math.max(1, pageNum);
        int ps = Math.min(Math.max(1, pageSize), 50);
        long total = msgMapper.countNoticeBatch();
        if (total == 0) {
            return PageResult.of(List.of(), 0L, pn, ps);
        }
        List<NoticeBatchRow> rows = msgMapper.selectNoticeBatchPage((long) (pn - 1) * ps, ps);
        Map<Long, MsgMessage> samples = samples(rows);
        List<AdminNoticeVO> list = new ArrayList<>(rows.size());
        for (NoticeBatchRow row : rows) {
            AdminNoticeVO vo = new AdminNoticeVO();
            vo.setBatchId(row.getBatchId());
            vo.setReceiverCount(row.getReceiverCount());
            vo.setAliveCount(row.getAliveCount());
            vo.setReadCount(row.getReadCount());
            vo.setRevoked(row.getAliveCount() == null || row.getAliveCount() == 0);
            vo.setCreateTime(row.getCreateTime());
            MsgMessage sample = samples.get(row.getSampleId());
            if (sample != null) {
                vo.setContent(sample.getContent());
                vo.setTitle(NoticeService.titleOf(sample.getExtra()));
                vo.setCampusId(NoticeService.campusIdOf(sample.getExtra()));
            }
            list.add(vo);
        }
        return PageResult.of(list, total, pn, ps);
    }

    /** 撤回：已撤回再撤一次算成功（幂等），返回本次软删的行数 */
    @Transactional
    public int revoke(Long batchId) {
        BizAssert.notNull(batchId, CommonErrorCode.PARAM_ERROR);
        int affected = msgMapper.revokeNoticeBatch(batchId, System.currentTimeMillis());
        log.info("[notice] 公告撤回 batch={} 软删行={}", batchId, affected);
        return affected;
    }

    /** 接收人：正常与警告账号都在内，封禁的排除；指定校区时按 `campus_id` 收窄 */
    private List<Long> receivers(Long campusId) {
        LambdaQueryWrapper<AppUser> w = new LambdaQueryWrapper<AppUser>()
                .select(AppUser::getId)
                .eq(AppUser::getDeleted, 0L)
                .ne(AppUser::getStatus, USER_BANNED);
        if (campusId != null) {
            w.eq(AppUser::getCampusId, campusId);
        }
        List<Long> ids = new ArrayList<>();
        for (AppUser u : appUserMapper.selectList(w)) {
            ids.add(u.getId());
        }
        return ids;
    }

    /** 本页各批次的样本行（一次 IN 查询，不逐批点查） */
    private Map<Long, MsgMessage> samples(List<NoticeBatchRow> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = new ArrayList<>(rows.size());
        for (NoticeBatchRow row : rows) {
            ids.add(row.getSampleId());
        }
        Map<Long, MsgMessage> map = new HashMap<>(ids.size());
        for (MsgMessage m : msgMapper.selectList(
                new LambdaQueryWrapper<MsgMessage>().in(MsgMessage::getId, ids))) {
            map.put(m.getId(), m);
        }
        return map;
    }

    private static String trimToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}

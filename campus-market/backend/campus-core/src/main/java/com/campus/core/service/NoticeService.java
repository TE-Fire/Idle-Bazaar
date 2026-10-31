package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.PageResult;
import com.campus.core.dto.resp.BulletinVO;
import com.campus.core.dto.resp.NoticeVO;
import com.campus.core.entity.MsgMessage;
import com.campus.core.mapper.MsgMessageMapper;
import com.campus.core.redis.NoticeRedisKeys;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知（复用 `msg_message` 的 `zone=2` 分区，`sender_id=0` 表示平台发出）。
 *
 * <p>三类正文同一分区、靠 `type` 区分：`4` 系统处置（下架/裁决结果）、`5` 互动（被赞/被评/被关注）、
 * `6` 平台公告（管理端发布，见 notes/22 S12）。同属「平台发给个人」的通知，
 * 前端一个列表按标签分色即可，不必再拆分区。
 *
 * <p>推送失败不抛——通知是业务处置的附属品，处置本身成功就不该因为一条消息回滚。
 * 公告发布是**主动行为**，不适用这条：它的失败要回滚，见 {@link NoticeAdminService}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

    /** 分区：2 系统通知（1 私信 / 3 交易在 notes/19 预留） */
    private static final int ZONE_SYSTEM = 2;
    private static final long SENDER_SYSTEM = 0L;
    /** 类型：4 系统处置 / 5 互动（赞·评·关注） / 6 平台公告 */
    private static final int TYPE_SYSTEM = 4;
    private static final int TYPE_INTERACT = 5;

    /** 公告标题存在 `extra.title`（notes/22 S12），解析只读不写，用独立 mapper 即可 */
    private static final ObjectMapper EXTRA_MAPPER = new ObjectMapper();
    private static final int READ_NO = 0;
    private static final int READ_YES = 1;
    /** 播报条上限：首页顶栏不是公告栏，超过三条给「共 N 条」入口 */
    private static final int MAX_BULLETIN = 3;

    private final MsgMessageMapper msgMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** 推送一条系统通知；内部吞异常，调用方不必包 try */
    public void push(long receiverId, String content) {
        insert(receiverId, TYPE_SYSTEM, content);
    }

    /** 推送一条互动通知（被赞 / 被评 / 被关注） */
    public void pushInteract(long receiverId, String content) {
        insert(receiverId, TYPE_INTERACT, content);
    }

    /**
     * 事件去重：同一事件在 TTL 内只推一次。
     *
     * <p>Redis 不可用时返回 true（照常推）——重复通知只是打扰，漏通知是信息丢失。
     */
    public boolean firstTime(String event, Duration ttl) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(NoticeRedisKeys.eventKey(event), "1", ttl));
        } catch (Exception e) {
            log.warn("[notice] 去重检查失败，按首次处理 event={}", event, e);
            return true;
        }
    }

    private void insert(long receiverId, int type, String content) {
        if (receiverId <= 0 || content == null || content.isBlank()) {
            return;
        }
        try {
            MsgMessage msg = new MsgMessage()
                    .setZone(ZONE_SYSTEM)
                    .setSenderId(SENDER_SYSTEM)
                    .setReceiverId(receiverId)
                    .setType(type)
                    .setContent(content.length() > 2000 ? content.substring(0, 2000) : content)
                    .setReadFlag(READ_NO)
                    .setCreateTime(LocalDateTime.now())
                    .setDeleted(0L);
            msgMapper.insert(msg);
        } catch (Exception e) {
            log.warn("[notice] 推送失败 receiver={} content={}", receiverId, content, e);
        }
    }

    /** 通知列表，时间倒序 */
    public PageResult<NoticeVO> list(long userId, int pageNum, int pageSize) {
        int pn = Math.max(1, pageNum);
        int ps = Math.min(Math.max(1, pageSize), 50);
        Page<MsgMessage> p = msgMapper.selectPage(new Page<>(pn, ps), new LambdaQueryWrapper<MsgMessage>()
                .eq(MsgMessage::getReceiverId, userId)
                .eq(MsgMessage::getZone, ZONE_SYSTEM)
                .eq(MsgMessage::getSenderId, SENDER_SYSTEM)
                .eq(MsgMessage::getDeleted, 0L)
                .orderByDesc(MsgMessage::getCreateTime)
                .orderByDesc(MsgMessage::getId));

        List<NoticeVO> list = new ArrayList<>(p.getRecords().size());
        for (MsgMessage m : p.getRecords()) {
            NoticeVO vo = new NoticeVO();
            vo.setId(m.getId());
            vo.setType(m.getType());
            vo.setTitle(titleOf(m.getExtra()));
            vo.setContent(m.getContent());
            vo.setReadFlag(m.getReadFlag());
            vo.setCreateTime(m.getCreateTime());
            list.add(vo);
        }
        return PageResult.of(list, p.getTotal(), p.getCurrent(), p.getSize());
    }

    /**
     * 首页播报条：最近 N 条公告，**免登录**。
     *
     * <p>不复用 {@link #list}：那个按收件人查，未登录没有收件人；而且混排了系统处置与互动，
     * 活跃用户最近 20 条里可能一条公告都没有。这里直接按批次查公告本身。
     *
     * <p>不按校区过滤——未登录没有校区，过滤会让"只发某校区"的公告在匿名态整条消失，
     * 而公告只是知会，跨校区看到无害。
     */
    public List<BulletinVO> bulletins(int limit) {
        int n = Math.min(Math.max(1, limit), MAX_BULLETIN);
        List<MsgMessageMapper.BulletinRow> rows = msgMapper.selectBulletins(n);
        List<BulletinVO> list = new ArrayList<>(rows.size());
        for (MsgMessageMapper.BulletinRow row : rows) {
            BulletinVO vo = new BulletinVO();
            vo.setId(row.getId());
            vo.setTitle(titleOf(row.getExtra()));
            vo.setContent(row.getContent());
            vo.setCreateTime(row.getCreateTime());
            list.add(vo);
        }
        return list;
    }

    /** 未读条数 */    public long unread(long userId) {
        Long n = msgMapper.selectCount(new LambdaQueryWrapper<MsgMessage>()
                .eq(MsgMessage::getReceiverId, userId)
                .eq(MsgMessage::getZone, ZONE_SYSTEM)
                .eq(MsgMessage::getSenderId, SENDER_SYSTEM)
                .eq(MsgMessage::getReadFlag, READ_NO)
                .eq(MsgMessage::getDeleted, 0L));
        return n == null ? 0L : n;
    }

    /**
     * 单条已读：点开一封信时调用。
     *
     * <p>条件里带 `receiverId`，不能只按 id 改——那是拿别人的通知当自己的标本。
     * 已读、已删、id 不属于自己一律静默成功，重复点开同一封不该报错。
     */
    @Transactional
    public void markRead(long userId, long id) {
        msgMapper.update(null, new LambdaUpdateWrapper<MsgMessage>()
                .eq(MsgMessage::getId, id)
                .eq(MsgMessage::getReceiverId, userId)
                .eq(MsgMessage::getZone, ZONE_SYSTEM)
                .eq(MsgMessage::getSenderId, SENDER_SYSTEM)
                .eq(MsgMessage::getReadFlag, READ_NO)
                .eq(MsgMessage::getDeleted, 0L)
                .set(MsgMessage::getReadFlag, READ_YES)
                .set(MsgMessage::getReadTime, LocalDateTime.now()));
    }

    /** 全部标记已读（不按条点，系统通知没有逐条已读的必要） */
    @Transactional
    public void markAllRead(long userId) {
        msgMapper.update(null, new LambdaUpdateWrapper<MsgMessage>()
                .eq(MsgMessage::getReceiverId, userId)
                .eq(MsgMessage::getZone, ZONE_SYSTEM)
                .eq(MsgMessage::getSenderId, SENDER_SYSTEM)
                .eq(MsgMessage::getReadFlag, READ_NO)
                .set(MsgMessage::getReadFlag, READ_YES)
                .set(MsgMessage::getReadTime, LocalDateTime.now()));
    }

    /** 从 `extra` 取公告标题；非公告、无标题、格式坏掉一律返回 null（标题是锦上添花，不能让它拖挂列表） */
    public static String titleOf(String extra) {
        if (extra == null || extra.isBlank()) {
            return null;
        }
        try {
            JsonNode node = EXTRA_MAPPER.readTree(extra);
            JsonNode title = node.get("title");
            return title == null || title.isNull() ? null : title.asText();
        } catch (Exception e) {
            log.warn("[notice] extra 解析失败，按无标题处理 extra={}", extra);
            return null;
        }
    }

    /** 组装公告的 `extra`：标题与接收校区，撤回时靠行上的 `batchId` 定位，不靠这里 */
    public static String extraOf(String title, Long campusId) {
        Map<String, Object> map = new HashMap<>(2);
        if (title != null) {
            map.put("title", title);
        }
        if (campusId != null) {
            map.put("campusId", campusId);
        }
        if (map.isEmpty()) {
            return null;
        }
        try {
            return EXTRA_MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("[notice] extra 组装失败 title={} campusId={}", title, campusId, e);
            return null;
        }
    }

    /** 从 `extra` 取接收校区（空 = 全员） */
    public static Long campusIdOf(String extra) {
        if (extra == null || extra.isBlank()) {
            return null;
        }
        try {
            JsonNode node = EXTRA_MAPPER.readTree(extra).get("campusId");
            return node == null || node.isNull() ? null : node.asLong();
        } catch (Exception e) {
            return null;
        }
    }
}

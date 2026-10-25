package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.common.asserts.BizAssert;
import com.campus.core.dto.req.ChatSendReq;
import com.campus.core.dto.resp.ChatConversationVO;
import com.campus.core.dto.resp.ChatMessageVO;
import com.campus.core.entity.AppUser;
import com.campus.core.entity.ChatSession;
import com.campus.core.entity.Item;
import com.campus.core.entity.MsgMessage;
import com.campus.core.enums.ItemStatusEnum;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.mapper.ChatSessionMapper;
import com.campus.core.mapper.ItemMapper;
import com.campus.core.mapper.MsgMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 询单留言（business/01 W-B5 / business/02 M-B4，2026-10-22 同步留言决策）。
 *
 * <p>同步模式：无 WebSocket、无推送，前端进会话轮询拉取、拉取即已读。会话按
 * 「商品 + 双方」唯一（chat_session.uk_pair），买家询价与卖家回复落在同一行。
 * 消息只发文本——图片消息与面交卡片是原 IM 设计，本期不做，表结构已就绪
 * （zone / type 列），二期升级实时 IM 不需要改表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 分区：1 私信（本期恒 1，系统/交易通知不做） */
    private static final int ZONE_PRIVATE = 1;

    /** 消息类型：1 文本（本期恒 1） */
    private static final int TYPE_TEXT = 1;

    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 50;

    private final ChatSessionMapper sessionMapper;
    private final MsgMessageMapper msgMapper;
    private final ItemMapper itemMapper;
    private final AppUserMapper appUserMapper;

    /**
     * 发送留言，同事务 upsert 会话并插消息。
     *
     * <p>发送权限：买家发起时对方必须是该商品卖家（防止借询单通道给任意用户发消息）；
     * 卖家回复时对方不限——买家是在该商品下询过单的人，由会话存在性天然约束。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long send(Long senderId, ChatSendReq req) {
        Item item = itemMapper.selectById(req.getItemId());
        BizAssert.notNull(item, UserErrorCode.CHAT_ITEM_NOT_FOUND);
        if (!senderId.equals(item.getSellerId())) {
            BizAssert.isTrue(req.getToUserId().equals(item.getSellerId()), UserErrorCode.CHAT_PEER_INVALID);
        }

        ChatSession session = upsertSession(item.getId(), senderId, req.getToUserId());
        MsgMessage msg = new MsgMessage()
                .setZone(ZONE_PRIVATE)
                .setSessionId(session.getId())
                .setSenderId(senderId)
                .setReceiverId(req.getToUserId())
                .setType(TYPE_TEXT)
                .setContent(req.getContent().trim())
                .setReadFlag(0);
        msgMapper.insert(msg);

        sessionMapper.update(null, new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getId, session.getId())
                .set(ChatSession::getLastMsgId, msg.getId())
                .set(ChatSession::getLastMsgAt, LocalDateTime.now()));
        return msg.getId();
    }

    /**
     * 会话列表：我参与的会话按最近消息倒序，回填商品摘要、对方昵称与未读数。
     *
     * <p>未读一次 group by 查齐（只取 session_id 一列），不逐会话 count；
     * 商品与对方批量查后 Java 组装——一个用户的会话数是商品级，全量取不翻页。
     */
    public List<ChatConversationVO> conversations(Long userId) {
        List<ChatSession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .and(w -> w.eq(ChatSession::getUserA, userId).or().eq(ChatSession::getUserB, userId))
                .eq(ChatSession::getDeleted, 0)
                .orderByDesc(ChatSession::getLastMsgAt)
                .orderByDesc(ChatSession::getId));
        if (sessions.isEmpty()) {
            return List.of();
        }

        List<Long> sessionIds = sessions.stream().map(ChatSession::getId).toList();
        Map<Long, Integer> unreadMap = new HashMap<>();
        for (MsgMessage m : msgMapper.selectList(new LambdaQueryWrapper<MsgMessage>()
                .select(MsgMessage::getSessionId)
                .in(MsgMessage::getSessionId, sessionIds)
                .eq(MsgMessage::getReceiverId, userId)
                .eq(MsgMessage::getReadFlag, 0)
                .eq(MsgMessage::getDeleted, 0))) {
            unreadMap.merge(m.getSessionId(), 1, Integer::sum);
        }

        List<Long> lastMsgIds = sessions.stream()
                .map(ChatSession::getLastMsgId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, MsgMessage> lastMap = lastMsgIds.isEmpty()
                ? Map.of()
                : msgMapper.selectBatchIds(lastMsgIds).stream()
                .collect(Collectors.toMap(MsgMessage::getId, Function.identity(), (a, b) -> a));

        Map<Long, Item> itemMap = itemMapper.selectBatchIds(sessions.stream()
                        .map(ChatSession::getItemId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Item::getId, Function.identity(), (a, b) -> a));
        Map<Long, AppUser> userMap = appUserMapper.selectBatchIds(sessions.stream()
                        .map(s -> userId.equals(s.getUserA()) ? s.getUserB() : s.getUserA())
                        .filter(Objects::nonNull).distinct().toList())
                .stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity(), (a, b) -> a));

        List<ChatConversationVO> list = new ArrayList<>();
        for (ChatSession s : sessions) {
            Long peerId = userId.equals(s.getUserA()) ? s.getUserB() : s.getUserA();
            Item item = itemMap.get(s.getItemId());
            AppUser peer = userMap.get(peerId);
            MsgMessage last = lastMap.get(s.getLastMsgId());

            ChatConversationVO vo = new ChatConversationVO();
            vo.setItemId(s.getItemId());
            vo.setItemTitle(item == null ? null : item.getTitle());
            vo.setItemMainImage(item == null ? null : item.getMainImage());
            vo.setPrice(item == null ? null : item.getPrice());
            vo.setStatus(item == null ? null : ItemStatusEnum.fromCode(item.getStatus()));
            vo.setPeerUserId(peerId);
            vo.setPeerNick(peer == null ? null : peer.getNickname());
            vo.setLastContent(last == null ? null : last.getContent());
            vo.setLastAt(s.getLastMsgAt());
            vo.setUnread(unreadMap.getOrDefault(s.getId(), 0));
            list.add(vo);
        }
        return list;
    }

    /**
     * 会话消息分页（倒序拉历史，beforeId 游标向上翻）。无会话返回空列表。
     * 已读标记不在这里做——拉历史是读操作，标已读走 {@link #markRead}。
     */
    public List<ChatMessageVO> messages(Long userId, Long itemId, Long peerId, Long beforeId, Integer sizeIn) {
        ChatSession session = findSession(itemId, userId, peerId);
        if (session == null) {
            return List.of();
        }
        int size = sizeIn == null || sizeIn < 1 ? DEFAULT_PAGE_SIZE : Math.min(sizeIn, MAX_PAGE_SIZE);

        LambdaQueryWrapper<MsgMessage> q = new LambdaQueryWrapper<MsgMessage>()
                .eq(MsgMessage::getSessionId, session.getId())
                .eq(MsgMessage::getDeleted, 0)
                .orderByDesc(MsgMessage::getId)
                .last("limit " + size);
        if (beforeId != null) {
            q.lt(MsgMessage::getId, beforeId);
        }
        return msgMapper.selectList(q).stream()
                .map(m -> {
                    ChatMessageVO vo = new ChatMessageVO();
                    vo.setId(m.getId());
                    vo.setSenderId(m.getSenderId());
                    vo.setContent(m.getContent());
                    vo.setCreatedAt(m.getCreateTime());
                    vo.setMine(userId.equals(m.getSenderId()));
                    return vo;
                })
                .toList();
    }

    /** 标已读：对方发来的未读消息。进会话时调，之后未读数归零 */
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long userId, Long itemId, Long peerId) {
        ChatSession session = findSession(itemId, userId, peerId);
        if (session == null) {
            return;
        }
        msgMapper.update(null, new LambdaUpdateWrapper<MsgMessage>()
                .eq(MsgMessage::getSessionId, session.getId())
                .eq(MsgMessage::getReceiverId, userId)
                .eq(MsgMessage::getReadFlag, 0)
                .set(MsgMessage::getReadFlag, 1)
                .set(MsgMessage::getReadTime, LocalDateTime.now()));
    }

    /** 按「商品 + 双方」找会话；user_a/user_b 大小序与 {@link #upsertSession} 一致 */
    private ChatSession findSession(Long itemId, Long me, Long peer) {
        long a = Math.min(me, peer);
        long b = Math.max(me, peer);
        return sessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getItemId, itemId)
                .eq(ChatSession::getUserA, a)
                .eq(ChatSession::getUserB, b)
                .eq(ChatSession::getDeleted, 0));
    }

    /** 先查后插；并发首聊撞 uk_pair 时取回对方刚建的那一行（DuplicateKey 兜底） */
    private ChatSession upsertSession(Long itemId, Long senderId, Long peerId) {
        ChatSession exist = findSession(itemId, senderId, peerId);
        if (exist != null) {
            return exist;
        }
        ChatSession fresh = new ChatSession()
                .setItemId(itemId)
                .setUserA(Math.min(senderId, peerId))
                .setUserB(Math.max(senderId, peerId));
        try {
            sessionMapper.insert(fresh);
            return fresh;
        } catch (DuplicateKeyException e) {
            ChatSession again = findSession(itemId, senderId, peerId);
            BizAssert.notNull(again, UserErrorCode.CHAT_ITEM_NOT_FOUND, "会话创建冲突，请重试");
            return again;
        }
    }
}

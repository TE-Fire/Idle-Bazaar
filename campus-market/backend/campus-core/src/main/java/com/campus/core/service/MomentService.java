package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.BizException;
import com.campus.common.exception.CommonErrorCode;
import com.campus.core.dto.req.MomentCommentReq;
import com.campus.core.dto.req.MomentPublishReq;
import com.campus.core.dto.resp.MomentCommentVO;
import com.campus.core.dto.resp.MomentDetailVO;
import com.campus.core.dto.resp.MomentPageVO;
import com.campus.core.dto.resp.MomentVO;
import com.campus.core.dto.resp.UserPubVO;
import com.campus.core.entity.Item;
import com.campus.core.entity.MomentComment;
import com.campus.core.entity.MomentLike;
import com.campus.core.entity.UserMoment;
import com.campus.core.enums.ItemStatusEnum;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.mapper.ItemMapper;
import com.campus.core.mapper.MomentCommentMapper;
import com.campus.core.mapper.MomentLikeMapper;
import com.campus.core.mapper.UserMomentMapper;
import com.campus.core.service.cache.UserPubCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动态与评论（notes/22 S3–S6）。
 *
 * <p>审核三态在本期只有「正常(0)」会写：高危敏感词在发布/评论时直接拒（不入库），
 * 低危词替换后仍写正常(0)；「敏感词屏蔽(1)/举报下架(2)」由批次 3 的举报处置置位。
 * 广场流与评论列表一律只取 `deleted=0 AND audit_status=0`。
 *
 * <p>点赞与关注同构为 B 类软删（revive 复用行），评论为 A 类留墓碑。
 * 列表的作者/商品点赞/评论计数均为**批量查**后内存组装，不逐条打库。
 */
@Service
@RequiredArgsConstructor
public class MomentService {

    private static final int AUDIT_NORMAL = 0;
    private static final int TYPE_STATUS = 1;
    private static final int TYPE_SHARE = 2;
    private static final int MAX_IMAGES = 9;
    private static final int MAX_COMMENT_LEN = 500;

    private final UserMomentMapper momentMapper;
    private final MomentLikeMapper likeMapper;
    private final MomentCommentMapper commentMapper;
    private final ItemMapper itemMapper;
    private final UserPubCache userPubCache;
    private final SensitiveWordService sensitiveWordService;
    private final ObjectMapper objectMapper;

    /** 发布动态；返回新动态 id */
    @Transactional
    public Long publish(long userId, MomentPublishReq req) {
        Integer type = req.getType();
        BizAssert.isTrue(type != null && (type == TYPE_STATUS || type == TYPE_SHARE),
                CommonErrorCode.PARAM_ERROR, "动态类型不合法");

        List<String> images = req.getImages() == null ? List.of() : req.getImages();
        BizAssert.isTrue(images.size() <= MAX_IMAGES, CommonErrorCode.PARAM_ERROR, "图片不能超过 9 张");

        String content = req.getContent() == null ? null : req.getContent().trim();
        if (type == TYPE_STATUS) {
            BizAssert.isTrue((content != null && !content.isBlank()) || !images.isEmpty(),
                    CommonErrorCode.PARAM_ERROR, "动态内容不能为空");
        }

        Long itemId = req.getItemId();
        if (type == TYPE_SHARE) {
            BizAssert.notNull(itemId, CommonErrorCode.PARAM_ERROR, "分享商品需指定商品");
            Item item = itemMapper.selectById(itemId);
            BizAssert.isTrue(item != null
                            && Integer.valueOf(ItemStatusEnum.ON_SALE.getCode()).equals(item.getStatus()),
                    UserErrorCode.MOMENT_ITEM_INVALID);
        }

        // 高危词命中在此抛异常；低危词已替换
        String clean = sensitiveWordService.filter(content);

        UserMoment m = new UserMoment();
        m.setUserId(userId);
        m.setType(type);
        m.setContent(clean);
        m.setImages(writeImages(images));
        m.setItemId(type == TYPE_SHARE ? itemId : null);
        m.setAuditStatus(AUDIT_NORMAL);
        momentMapper.insert(m);
        return m.getId();
    }

    /** 广场流：全站正常动态，时间倒序 */
    public MomentPageVO square(long viewerId, int pageNum, int pageSize) {
        Page<UserMoment> p = momentMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<UserMoment>()
                        .eq(UserMoment::getDeleted, 0L)
                        .eq(UserMoment::getAuditStatus, AUDIT_NORMAL)
                        .orderByDesc(UserMoment::getCreateTime));
        return pageVO(p, viewerId);
    }

    /** 个人主页动态：指定作者，时间倒序 */
    public MomentPageVO userMoments(long viewerId, long targetUserId, int pageNum, int pageSize) {
        Page<UserMoment> p = momentMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<UserMoment>()
                        .eq(UserMoment::getUserId, targetUserId)
                        .eq(UserMoment::getDeleted, 0L)
                        .eq(UserMoment::getAuditStatus, AUDIT_NORMAL)
                        .orderByDesc(UserMoment::getCreateTime));
        return pageVO(p, viewerId);
    }

    /** 动态详情：正文 + 评论列表（正序） */
    public MomentDetailVO detail(long viewerId, long momentId) {
        UserMoment m = assertNormal(momentId);
        MomentDetailVO vo = new MomentDetailVO();
        vo.setMoment(toVOList(List.of(m), viewerId).get(0));
        vo.setComments(loadComments(momentId));
        return vo;
    }

    /** 点赞：幂等，B 类软删复用行 */
    public void like(long userId, long momentId) {
        assertNormal(momentId);
        if (likeMapper.revive(momentId, userId) > 0) {
            return;
        }
        Long active = likeMapper.selectCount(new LambdaQueryWrapper<MomentLike>()
                .eq(MomentLike::getMomentId, momentId)
                .eq(MomentLike::getUserId, userId)
                .eq(MomentLike::getDeleted, 0L));
        if (active != null && active > 0) {
            return;
        }
        MomentLike l = new MomentLike();
        l.setMomentId(momentId);
        l.setUserId(userId);
        try {
            likeMapper.insert(l);
        } catch (DuplicateKeyException e) {
            // 并发首赞撞唯一键，视为已赞（同一关系恒一行）
        }
    }

    /** 取消点赞：幂等 */
    public void unlike(long userId, long momentId) {
        assertNormal(momentId);
        likeMapper.softDelete(momentId, userId, System.currentTimeMillis());
    }

    /** 发评论：敏感词过滤后入库，audit_status=正常(0) */
    @Transactional
    public void comment(long userId, long momentId, MomentCommentReq req) {
        assertNormal(momentId);
        String content = req.getContent() == null ? null : req.getContent().trim();
        BizAssert.notBlank(content, CommonErrorCode.PARAM_ERROR, "评论内容不能为空");
        BizAssert.isTrue(content.length() <= MAX_COMMENT_LEN,
                CommonErrorCode.PARAM_ERROR, "评论不能超过 500 字");

        String clean = sensitiveWordService.filter(content);
        MomentComment c = new MomentComment();
        c.setMomentId(momentId);
        c.setUserId(userId);
        c.setReplyTo(req.getReplyTo());
        c.setContent(clean);
        c.setAuditStatus(AUDIT_NORMAL);
        commentMapper.insert(c);
    }

    /* ── 组装 ── */

    private MomentPageVO pageVO(Page<UserMoment> p, long viewerId) {
        MomentPageVO vo = new MomentPageVO();
        vo.setList(toVOList(p.getRecords(), viewerId));
        vo.setTotal(p.getTotal());
        vo.setPageNum(p.getCurrent());
        vo.setPageSize(p.getSize());
        return vo;
    }

    /** 批量组装：作者、商品摘要、点赞数/我的赞、评论数 */
    private List<MomentVO> toVOList(List<UserMoment> rows, long viewerId) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new HashSet<>();
        Set<Long> itemIds = new HashSet<>();
        List<Long> momentIds = new ArrayList<>();
        for (UserMoment m : rows) {
            userIds.add(m.getUserId());
            momentIds.add(m.getId());
            if (Integer.valueOf(TYPE_SHARE).equals(m.getType()) && m.getItemId() != null) {
                itemIds.add(m.getItemId());
            }
        }
        Map<Long, UserPubVO> authors = userPubCache.getOrLoad(userIds);

        Map<Long, Item> items = new HashMap<>();
        if (!itemIds.isEmpty()) {
            for (Item it : itemMapper.selectBatchIds(itemIds)) {
                items.put(it.getId(), it);
            }
        }

        Map<Long, Long> likeCounts = new HashMap<>();
        Set<Long> likedByMe = new HashSet<>();
        for (MomentLike l : likeMapper.selectList(new LambdaQueryWrapper<MomentLike>()
                .in(MomentLike::getMomentId, momentIds)
                .eq(MomentLike::getDeleted, 0L))) {
            likeCounts.merge(l.getMomentId(), 1L, Long::sum);
            if (l.getUserId() == viewerId) {
                likedByMe.add(l.getMomentId());
            }
        }

        Map<Long, Long> commentCounts = new HashMap<>();
        for (MomentComment c : commentMapper.selectList(new LambdaQueryWrapper<MomentComment>()
                .in(MomentComment::getMomentId, momentIds)
                .eq(MomentComment::getDeleted, 0L)
                .eq(MomentComment::getAuditStatus, AUDIT_NORMAL))) {
            commentCounts.merge(c.getMomentId(), 1L, Long::sum);
        }

        List<MomentVO> list = new ArrayList<>(rows.size());
        for (UserMoment m : rows) {
            MomentVO vo = new MomentVO();
            vo.setId(m.getId());
            vo.setUserId(m.getUserId());
            vo.setAuthor(authors.get(m.getUserId()));
            vo.setType(m.getType());
            vo.setContent(m.getContent());
            vo.setImages(readImages(m.getImages()));
            vo.setCreateTime(m.getCreateTime());
            vo.setLikeCount(likeCounts.getOrDefault(m.getId(), 0L));
            vo.setLiked(likedByMe.contains(m.getId()));
            vo.setCommentCount(commentCounts.getOrDefault(m.getId(), 0L));

            if (Integer.valueOf(TYPE_SHARE).equals(m.getType())) {
                vo.setItemId(m.getItemId());
                Item it = m.getItemId() == null ? null : items.get(m.getItemId());
                if (it == null) {
                    vo.setItemTitle("商品已下架");
                } else {
                    vo.setItemTitle(it.getTitle());
                    vo.setItemMainImage(it.getMainImage());
                    vo.setItemPrice(it.getPrice());
                }
            }
            list.add(vo);
        }
        return list;
    }

    private List<MomentCommentVO> loadComments(long momentId) {
        List<MomentComment> rows = commentMapper.selectList(new LambdaQueryWrapper<MomentComment>()
                .eq(MomentComment::getMomentId, momentId)
                .eq(MomentComment::getDeleted, 0L)
                .eq(MomentComment::getAuditStatus, AUDIT_NORMAL)
                .orderByAsc(MomentComment::getCreateTime));
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new HashSet<>();
        for (MomentComment c : rows) {
            userIds.add(c.getUserId());
            if (c.getReplyTo() != null) {
                userIds.add(c.getReplyTo());
            }
        }
        Map<Long, UserPubVO> users = userPubCache.getOrLoad(userIds);

        List<MomentCommentVO> list = new ArrayList<>(rows.size());
        for (MomentComment c : rows) {
            MomentCommentVO vo = new MomentCommentVO();
            vo.setId(c.getId());
            vo.setMomentId(c.getMomentId());
            vo.setUserId(c.getUserId());
            vo.setAuthor(users.get(c.getUserId()));
            vo.setReplyTo(c.getReplyTo());
            UserPubVO rt = c.getReplyTo() == null ? null : users.get(c.getReplyTo());
            vo.setReplyToNick(rt == null ? null : rt.getNickname());
            vo.setContent(c.getContent());
            vo.setCreateTime(c.getCreateTime());
            list.add(vo);
        }
        return list;
    }

    /** 动态存在且正常，否则 4022 */
    private UserMoment assertNormal(long momentId) {
        UserMoment m = momentMapper.selectOne(new LambdaQueryWrapper<UserMoment>()
                .eq(UserMoment::getId, momentId)
                .eq(UserMoment::getDeleted, 0L)
                .eq(UserMoment::getAuditStatus, AUDIT_NORMAL));
        return BizAssert.notNull(m, UserErrorCode.MOMENT_NOT_FOUND);
    }

    private String writeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (JsonProcessingException e) {
            throw new BizException(CommonErrorCode.SYSTEM_ERROR);
        }
    }

    private List<String> readImages(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}

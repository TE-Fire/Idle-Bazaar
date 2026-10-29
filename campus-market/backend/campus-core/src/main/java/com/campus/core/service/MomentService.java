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
import com.campus.core.enums.ContentAuditStatusEnum;
import com.campus.core.enums.ItemStatusEnum;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.mapper.ItemMapper;
import com.campus.core.mapper.MomentCommentMapper;
import com.campus.core.mapper.MomentLikeMapper;
import com.campus.core.mapper.UserMomentMapper;
import com.campus.core.redis.MomentRedisKeys;
import com.campus.core.service.cache.MomentCache;
import com.campus.core.service.cache.UserPubCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动态与评论（notes/22 S3–S6）。
 *
 * <p>审核三态：高危敏感词在发布/评论时直接拒（不入库），低危词替换后仍写正常(0)；
 * 「敏感词屏蔽(1)」只有作者自己在个人主页能看到（带 `auditStatus` 标），
 * 「举报下架(2)」对所有人不可见。广场流与关注流只取正常(0)。
 *
 * <p>点赞与关注同构为 B 类软删（revive 复用行），评论为 A 类留墓碑。
 * 列表的作者/商品点赞/评论计数均为**批量查**后内存组装，不逐条打库。
 *
 * <p>互动通知（被赞/被评）在写路径末尾推送，失败只 warn 不影响主流程；
 * 同一人对同一动态的点赞 24 小时内只推一次、评论 30 分钟一次，避免取消再赞刷屏。
 *
 * <p>列表缓存只装公共视图（`liked` 恒 false、作者本人视角不进缓存），命中后按 viewer 补。
 * 发布**不**主动失效列表——最迟一个 TTL 后出现在广场；下架则 INCR 版本戳，要求立刻不可见。
 */
@Service
@RequiredArgsConstructor
public class MomentService {

    private static final int TYPE_STATUS = 1;
    private static final int TYPE_SHARE = 2;
    private static final int MAX_IMAGES = 9;
    private static final int MAX_COMMENT_LEN = 500;
    private static final int NOTICE_BRIEF_LEN = 30;
    /** 关注流最多拼多少个 followee：关注数无上限，`IN` 列表不设口子会打爆 SQL */
    private static final int FOLLOW_FEED_MAX_FOLLOWEES = 300;

    /** 只缓存前 3 页：更深的页命中率趋零，存下来只是占内存 */
    private static final int MAX_CACHED_PAGE = 3;

    /** 广场流 TTL（秒）：首页最热，保底 90 + 随机 0–60；深页 180 + 0–120 */
    private static final long SQUARE_P1_BASE = 90;
    private static final long SQUARE_P1_JITTER = 60;
    private static final long SQUARE_PN_BASE = 180;
    private static final long SQUARE_PN_JITTER = 120;

    /** 个人主页 TTL（秒）：按作者共享但访问更分散，给得比广场长 */
    private static final long USER_P1_BASE = 180;
    private static final long USER_P1_JITTER = 120;
    private static final long USER_PN_BASE = 300;
    private static final long USER_PN_JITTER = 180;

    /**
     * 加载公共视图时用的假 viewer：`liked` 恒为 false，命中后由 {@link #patchLiked} 按
     * 当前用户补。不能传真实 userId —— 第一个访问者的点赞态会被烤进共享缓存。
     */
    private static final long ANON_VIEWER = 0L;

    private final UserMomentMapper momentMapper;
    private final MomentLikeMapper likeMapper;
    private final MomentCommentMapper commentMapper;
    private final ItemMapper itemMapper;
    private final UserPubCache userPubCache;
    private final MomentCache momentCache;
    private final SensitiveWordService sensitiveWordService;
    private final NoticeService noticeService;
    private final FollowService followService;
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
        m.setAuditStatus(ContentAuditStatusEnum.NORMAL.getCode());
        momentMapper.insert(m);
        return m.getId();
    }

    /** 广场流：全站正常动态，时间倒序。前 3 页读穿缓存，点赞态按当前用户补 */
    public MomentPageVO square(long viewerId, int pageNum, int pageSize) {
        if (!cacheablePage(pageNum)) {
            return loadSquare(viewerId, pageNum, pageSize);
        }
        long ver = momentCache.feedVersion();
        if (ver == MomentCache.NO_VERSION) {
            return loadSquare(viewerId, pageNum, pageSize);
        }
        MomentPageVO vo = momentCache.getOrLoad(
                MomentRedisKeys.squarePage(ver, pageNum, pageSize), MomentPageVO.class,
                pageNum == 1 ? MomentCache.ttl(SQUARE_P1_BASE, SQUARE_P1_JITTER)
                        : MomentCache.ttl(SQUARE_PN_BASE, SQUARE_PN_JITTER),
                () -> loadSquare(ANON_VIEWER, pageNum, pageSize));
        return patchLiked(vo, viewerId);
    }

    private MomentPageVO loadSquare(long viewerId, int pageNum, int pageSize) {
        Page<UserMoment> p = momentMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<UserMoment>()
                        .eq(UserMoment::getDeleted, 0L)
                        .eq(UserMoment::getAuditStatus, ContentAuditStatusEnum.NORMAL.getCode())
                        .orderByDesc(UserMoment::getCreateTime));
        return pageVO(p, viewerId);
    }

    /**
     * 关注流：只看我关注的人，时间倒序。关注关系读 Redis（见 FollowService#followingIds）。
     *
     * <p>per-viewer 视角、几乎不交叉，缓存命中率极低且 key 随用户数线性增长，因此不进缓存。
     */
    public MomentPageVO following(long viewerId, int pageNum, int pageSize) {
        List<Long> followees = followService.followingIds(viewerId, FOLLOW_FEED_MAX_FOLLOWEES);
        if (followees.isEmpty()) {
            MomentPageVO empty = new MomentPageVO();
            empty.setList(List.of());
            empty.setTotal(0L);
            empty.setPageNum(Math.max(pageNum, 1));
            empty.setPageSize(pageSize);
            return empty;
        }
        Page<UserMoment> p = momentMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<UserMoment>()
                        .eq(UserMoment::getDeleted, 0L)
                        .eq(UserMoment::getAuditStatus, ContentAuditStatusEnum.NORMAL.getCode())
                        .in(UserMoment::getUserId, followees)
                        .orderByDesc(UserMoment::getCreateTime));
        return pageVO(p, viewerId);
    }

    /**
     * 个人主页动态：指定作者，时间倒序。
     *
     * <p>看自己的主页时纳入「敏感词屏蔽(1)」——否则用户发出去的内容自己看不见，会以为发布失败；
     * 看别人时只出正常内容。「举报下架(2)」对自己也不可见，下架是平台结论，不是待处理的草稿。
     *
     * <p><b>本人视角不走缓存</b>：可见范围随 viewer 变化，缓存只能装公共视图。
     */
    public MomentPageVO userMoments(long viewerId, long targetUserId, int pageNum, int pageSize) {
        boolean self = viewerId == targetUserId;
        if (self || !cacheablePage(pageNum)) {
            return loadUserMoments(viewerId, targetUserId, pageNum, pageSize);
        }
        long ver = momentCache.feedVersion();
        if (ver == MomentCache.NO_VERSION) {
            return loadUserMoments(viewerId, targetUserId, pageNum, pageSize);
        }
        MomentPageVO vo = momentCache.getOrLoad(
                MomentRedisKeys.userPage(ver, targetUserId, pageNum, pageSize), MomentPageVO.class,
                pageNum == 1 ? MomentCache.ttl(USER_P1_BASE, USER_P1_JITTER)
                        : MomentCache.ttl(USER_PN_BASE, USER_PN_JITTER),
                () -> loadUserMoments(ANON_VIEWER, targetUserId, pageNum, pageSize));
        return patchLiked(vo, viewerId);
    }

    private MomentPageVO loadUserMoments(long viewerId, long targetUserId, int pageNum, int pageSize) {
        boolean self = viewerId == targetUserId;
        LambdaQueryWrapper<UserMoment> w = new LambdaQueryWrapper<UserMoment>()
                .eq(UserMoment::getUserId, targetUserId)
                .eq(UserMoment::getDeleted, 0L)
                .orderByDesc(UserMoment::getCreateTime);
        if (self) {
            w.in(UserMoment::getAuditStatus,
                    ContentAuditStatusEnum.NORMAL.getCode(), ContentAuditStatusEnum.MASKED.getCode());
        } else {
            w.eq(UserMoment::getAuditStatus, ContentAuditStatusEnum.NORMAL.getCode());
        }
        return pageVO(momentMapper.selectPage(new Page<>(pageNum, pageSize), w), viewerId);
    }

    /** 动态详情：正文 + 评论列表（正序）；作者本人可看自己被屏蔽的动态 */
    public MomentDetailVO detail(long viewerId, long momentId) {
        UserMoment m = momentMapper.selectOne(new LambdaQueryWrapper<UserMoment>()
                .eq(UserMoment::getId, momentId)
                .eq(UserMoment::getDeleted, 0L));
        boolean mine = m != null && m.getUserId() != null && m.getUserId() == viewerId;
        BizAssert.isTrue(m != null && (ContentAuditStatusEnum.NORMAL.getCode().equals(m.getAuditStatus())
                || (mine && ContentAuditStatusEnum.MASKED.getCode().equals(m.getAuditStatus()))),
                UserErrorCode.MOMENT_NOT_FOUND);
        MomentDetailVO vo = new MomentDetailVO();
        vo.setMoment(toVOList(List.of(m), viewerId).get(0));
        vo.setComments(loadComments(momentId));
        return vo;
    }

    /** 点赞：幂等，B 类软删复用行；真正新增时通知作者 */
    public void like(long userId, long momentId) {
        UserMoment m = assertNormal(momentId);
        boolean added = likeMapper.revive(momentId, userId) > 0;
        if (!added) {
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
                added = true;
            } catch (DuplicateKeyException e) {
                // 并发首赞撞唯一键，视为已赞（同一关系恒一行）
            }
        }
        if (added) {
            notifyLike(m, userId);
        }
    }

    /** 取消点赞：幂等 */
    public void unlike(long userId, long momentId) {
        assertNormal(momentId);
        likeMapper.softDelete(momentId, userId, System.currentTimeMillis());
    }

    /** 发评论：敏感词过滤后入库，audit_status=正常(0)；通知动态作者与被回复者 */
    @Transactional
    public void comment(long userId, long momentId, MomentCommentReq req) {
        UserMoment m = assertNormal(momentId);
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
        c.setAuditStatus(ContentAuditStatusEnum.NORMAL.getCode());
        commentMapper.insert(c);

        notifyComment(m, userId, req.getReplyTo(), clean);
    }

    /* ── 互动通知 ── */

    private void notifyLike(UserMoment m, long operatorId) {
        if (m.getUserId() == null || m.getUserId() == operatorId) {
            return;
        }
        if (!noticeService.firstTime("like:" + m.getId() + ":" + operatorId, Duration.ofHours(24))) {
            return;
        }
        String nick = nickOf(operatorId);
        if (nick != null) {
            noticeService.pushInteract(m.getUserId(), nick + " 赞了你的动态");
        }
    }

    private void notifyComment(UserMoment m, long operatorId, Long replyTo, String clean) {
        // 同一人对同一条动态半小时内只推一条：连发多条评论时不把信箱刷成列表
        if (!noticeService.firstTime("cmt:" + m.getId() + ":" + operatorId, Duration.ofMinutes(30))) {
            return;
        }
        String nick = nickOf(operatorId);
        if (nick == null) {
            return;
        }
        String brief = clean.length() > NOTICE_BRIEF_LEN
                ? clean.substring(0, NOTICE_BRIEF_LEN) + "…" : clean;
        if (m.getUserId() != null && m.getUserId() != operatorId) {
            noticeService.pushInteract(m.getUserId(), nick + " 评论了你的动态：" + brief);
        }
        // 被回复者与作者可能是同一人，同一事件不重复推
        if (replyTo != null && replyTo != operatorId
                && (m.getUserId() == null || !replyTo.equals(m.getUserId()))) {
            noticeService.pushInteract(replyTo, nick + " 回复了你的评论：" + brief);
        }
    }

    /** 通知文案里的操作者昵称；用户不存在 / 无昵称则返回 null，调用方跳过推送 */
    private String nickOf(long userId) {
        UserPubVO u = userPubCache.getOrLoad(List.of(userId)).get(userId);
        return u == null || u.getNickname() == null || u.getNickname().isBlank() ? null : u.getNickname();
    }

    /* ── 组装 ── */

    /** 前 3 页才有缓存价值：深页访问稀疏，命中率趋零 */
    private static boolean cacheablePage(int pageNum) {
        return pageNum >= 1 && pageNum <= MAX_CACHED_PAGE;
    }

    /**
     * 补「我是否赞过」：缓存里 `liked` 恒为 false，按当前用户批量查一次点赞表。
     *
     * <p>一次 `IN` 走 `idx` 全覆盖，比为每个 viewer 单独存一份列表便宜得多；
     * 若把点赞态烤进共享缓存，列表就只能给一个人用，命中率从全站塌成单人。
     */
    private MomentPageVO patchLiked(MomentPageVO vo, long viewerId) {
        if (vo == null || vo.getList() == null || vo.getList().isEmpty() || viewerId <= 0) {
            return vo;
        }
        List<Long> ids = vo.getList().stream().map(MomentVO::getId).toList();
        Set<Long> mine = new HashSet<>();
        for (MomentLike l : likeMapper.selectList(new LambdaQueryWrapper<MomentLike>()
                .in(MomentLike::getMomentId, ids)
                .eq(MomentLike::getUserId, viewerId)
                .eq(MomentLike::getDeleted, 0L))) {
            mine.add(l.getMomentId());
        }
        for (MomentVO m : vo.getList()) {
            m.setLiked(mine.contains(m.getId()));
        }
        return vo;
    }

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
                .eq(MomentComment::getAuditStatus, ContentAuditStatusEnum.NORMAL.getCode()))) {
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
            vo.setAuditStatus(m.getAuditStatus());

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
                .eq(MomentComment::getAuditStatus, ContentAuditStatusEnum.NORMAL.getCode())
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
                .eq(UserMoment::getAuditStatus, ContentAuditStatusEnum.NORMAL.getCode()));
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

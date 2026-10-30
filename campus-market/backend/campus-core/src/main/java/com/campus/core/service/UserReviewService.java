package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.asserts.BizAssert;
import com.campus.common.result.PageResult;
import com.campus.core.dto.req.UserReviewCreateReq;
import com.campus.core.dto.resp.OrderReviewVO;
import com.campus.core.dto.resp.OrderVO;
import com.campus.core.dto.resp.ReviewSummaryVO;
import com.campus.core.dto.resp.UserReviewVO;
import com.campus.core.entity.AppUser;
import com.campus.core.entity.UserReview;
import com.campus.core.enums.OrderStatusEnum;
import com.campus.core.enums.ReviewTagEnum;
import com.campus.core.errcode.OrderErrorCode;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.mapper.UserReviewMapper;
import com.campus.core.mapper.UserReviewMapper.RatingCount;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 交易互评（notes/13 O18）。
 *
 * <p><b>三条硬规则</b>：只有已完成(4)的订单能评；面交后 30 天内可评；一单一评。
 * 「一单一评」由唯一键兜底，重复提交**返回既有评价**而不是覆盖——评价是公开内容，
 * 允许改会让「先给好评再改差评」变成报复手段。
 *
 * <p><b>不做双盲</b>：谁先评谁先被看见。双盲要额外的揭晓状态与超时揭晓任务，
 * 收益只是防报复评价，代价是链路变长；这条代价记在 notes/13 的已知缺口里。
 *
 * <p><b>不联动信用分</b>：信用分在确认收货时已 +1，互评再动一次就是同一件事扣两次。
 * 信用分记行为，评分记主观感受，两套口径分开。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserReviewService {

    /** 评价窗口：面交核销后 30 天（库里没有「收货时间」列，用 verify_time 起算） */
    private static final int REVIEW_WINDOW_DAYS = 30;

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserReviewMapper userReviewMapper;
    private final AppUserMapper appUserMapper;
    private final OrderService orderService;
    private final SensitiveWordService sensitiveWordService;
    private final ObjectMapper objectMapper;

    /**
     * 提交评价：`targetId` 一律按「订单的对方」推出，不接受前端传。
     *
     * <p>幂等：同一单同一人已有评价时直接返回既有那条，不覆盖、不报错。
     */
    @Transactional
    public UserReviewVO submit(Long orderId, Long userId, UserReviewCreateReq req) {
        OrderVO order = orderService.detail(orderId, userId);

        UserReview exist = mine(orderId, userId);
        if (exist != null) {
            return toVO(exist, reviewerOf(exist.getReviewerId()));
        }

        BizAssert.isTrue(order.getStatus() == OrderStatusEnum.COMPLETED, OrderErrorCode.REVIEW_NOT_ALLOWED);
        LocalDateTime anchor = order.getVerifyTime() != null ? order.getVerifyTime() : order.getPayTime();
        BizAssert.isTrue(anchor != null && !anchor.plusDays(REVIEW_WINDOW_DAYS).isBefore(LocalDateTime.now()),
                OrderErrorCode.REVIEW_EXPIRED);

        List<String> tags = normaliseTags(req.getTags());
        String comment = req.getComment() == null ? null : req.getComment().trim();
        if (comment != null && comment.isBlank()) {
            comment = null;
        }
        if (comment != null) {
            // 高危词直接抛 4024，低危词替换成等长 *（与动态/评论同一套词库）
            comment = sensitiveWordService.filter(comment);
        }

        long targetId = userId.equals(order.getBuyerId()) ? order.getSellerId() : order.getBuyerId();
        UserReview review = new UserReview()
                .setOrderId(orderId)
                .setReviewerId(userId)
                .setTargetId(targetId)
                .setRating(req.getRating())
                .setTags(writeTags(tags))
                .setComment(comment);
        userReviewMapper.insert(review);
        log.info("[review] 订单 {} 由用户 {} 评价用户 {}，{} 星", orderId, userId, targetId, req.getRating());
        return toVO(review, reviewerOf(userId));
    }

    /** 订单的评价状态：我评没评、对方评没评、我现在能不能评 */
    public OrderReviewVO ofOrder(Long orderId, Long userId) {
        OrderVO order = orderService.detail(orderId, userId);

        UserReview mine = mine(orderId, userId);
        long peerId = userId.equals(order.getBuyerId()) ? order.getSellerId() : order.getBuyerId();
        UserReview peer = mine(orderId, peerId);

        Map<Long, AppUser> users = usersOf(List.of(userId, peerId));
        OrderReviewVO vo = new OrderReviewVO();
        vo.setOrderId(orderId);
        vo.setMine(mine == null ? null : toVO(mine, users.get(mine.getReviewerId())));
        vo.setPeer(peer == null ? null : toVO(peer, users.get(peer.getReviewerId())));

        LocalDateTime anchor = order.getVerifyTime() != null ? order.getVerifyTime() : order.getPayTime();
        LocalDateTime deadline = anchor == null ? null : anchor.plusDays(REVIEW_WINDOW_DAYS);
        boolean expired = deadline != null && deadline.isBefore(LocalDateTime.now());
        vo.setCanReview(order.getStatus() == OrderStatusEnum.COMPLETED && mine == null && !expired);
        vo.setExpireAt(expired || deadline == null ? null : deadline.format(MINUTE));
        vo.setTagTable(ReviewTagEnum.table());
        return vo;
    }

    /** 某人收到的评价，时间倒序 */
    public PageResult<UserReviewVO> listOf(Long targetId, int pageNum, int pageSize) {
        int pn = Math.max(1, pageNum);
        int ps = Math.min(Math.max(1, pageSize), 50);
        Page<UserReview> page = userReviewMapper.selectPage(new Page<>(pn, ps),
                new LambdaQueryWrapper<UserReview>()
                        .eq(UserReview::getTargetId, targetId)
                        .eq(UserReview::getDeleted, 0L)
                        .orderByDesc(UserReview::getCreateTime)
                        .orderByDesc(UserReview::getId));

        List<UserReview> rows = page.getRecords();
        Map<Long, AppUser> users = usersOf(rows.stream().map(UserReview::getReviewerId).distinct().toList());
        List<UserReviewVO> list = new ArrayList<>(rows.size());
        for (UserReview r : rows) {
            list.add(toVO(r, users.get(r.getReviewerId())));
        }
        return PageResult.of(list, page.getTotal(), pn, ps);
    }

    /**
     * 某人收到的评价汇总：平均分 + 五档条数 + 最近 3 条。
     *
     * <p>实时聚合，不做冗余列——评价量在百级，`AVG` 与 `GROUP BY` 都是毫秒级；
     * 加了冗余列就得在每个写入点同步，漏一处就永远对不上。
     */
    public ReviewSummaryVO summary(Long targetId) {
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (int star = 1; star <= 5; star++) {
            counts.put(star, 0L);
        }
        long total = 0;
        long sum = 0;
        for (RatingCount row : userReviewMapper.countByRating(targetId)) {
            long n = row.getNum() == null ? 0L : row.getNum();
            counts.put(row.getStar(), n);
            total += n;
            sum += row.getStar() * n;
        }

        ReviewSummaryVO vo = new ReviewSummaryVO();
        vo.setTotal(total);
        vo.setAvg(total == 0
                ? "0.0"
                : BigDecimal.valueOf(sum / (double) total).setScale(1, RoundingMode.HALF_UP).toPlainString());
        vo.setCounts(counts);

        PageResult<UserReviewVO> latest = listOf(targetId, 1, 3);
        vo.setLatest(latest.getList());
        return vo;
    }

    /* ══ 内部 ═══════════════════════════════════════════ */

    private UserReview mine(Long orderId, Long reviewerId) {
        return userReviewMapper.selectOne(new LambdaQueryWrapper<UserReview>()
                .eq(UserReview::getOrderId, orderId)
                .eq(UserReview::getReviewerId, reviewerId)
                .eq(UserReview::getDeleted, 0L)
                .last("LIMIT 1"));
    }

    /** 标签只认服务端常量表，去空去重后上限 3 个 */
    private List<String> normaliseTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(tags.size());
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            BizAssert.isTrue(ReviewTagEnum.isValid(tag), OrderErrorCode.REVIEW_TAG_INVALID);
            if (!out.contains(tag)) {
                out.add(tag);
            }
        }
        BizAssert.isTrue(out.size() <= 3, OrderErrorCode.REVIEW_TAG_INVALID);
        return out;
    }

    private String writeTags(List<String> tags) {
        if (tags.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (Exception e) {
            log.warn("[review] 标签序列化失败，按无标签入库", e);
            return null;
        }
    }

    private List<String> readTags(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("[review] 标签解析失败，按无标签展示 json={}", json);
            return List.of();
        }
    }

    private Map<Long, AppUser> usersOf(List<Long> ids) {
        Map<Long, AppUser> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (AppUser u : appUserMapper.selectList(new LambdaQueryWrapper<AppUser>()
                .select(AppUser::getId, AppUser::getNickname, AppUser::getAvatarUrl)
                .in(AppUser::getId, ids))) {
            map.put(u.getId(), u);
        }
        return map;
    }

    private AppUser reviewerOf(Long userId) {
        return usersOf(List.of(userId)).get(userId);
    }

    private UserReviewVO toVO(UserReview r, AppUser reviewer) {
        UserReviewVO vo = new UserReviewVO();
        vo.setId(r.getId());
        vo.setOrderId(r.getOrderId());
        vo.setReviewerId(r.getReviewerId());
        vo.setReviewerNick(reviewer == null ? "已注销用户" : reviewer.getNickname());
        vo.setReviewerAvatar(reviewer == null ? null : reviewer.getAvatarUrl());
        vo.setRating(r.getRating());
        vo.setTags(readTags(r.getTags()));
        vo.setComment(r.getComment());
        vo.setCreateTime(r.getCreateTime());
        return vo;
    }
}

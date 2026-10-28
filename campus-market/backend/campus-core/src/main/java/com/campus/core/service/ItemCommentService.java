package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.CommonErrorCode;
import com.campus.common.result.PageResult;
import com.campus.core.dto.req.ItemCommentReq;
import com.campus.core.dto.resp.ItemCommentVO;
import com.campus.core.dto.resp.UserPubVO;
import com.campus.core.entity.Item;
import com.campus.core.entity.ItemComment;
import com.campus.core.errcode.ItemErrorCode;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.mapper.ItemCommentMapper;
import com.campus.core.mapper.ItemMapper;
import com.campus.core.service.cache.UserPubCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 商品评论（notes/22 S7）：仅登录用户可发、仅在售商品可评，与购后星级评价 user_review 并存。
 *
 * <p>「在售」是发布入口的硬门槛：下架后不能新增，但历史评论照常可读（走 idx_item，正序）。
 */
@Service
@RequiredArgsConstructor
public class ItemCommentService {

    private static final int AUDIT_NORMAL = 0;
    private static final int MAX_LEN = 500;
    private static final int MAX_PAGE_SIZE = 50;

    private final ItemCommentMapper commentMapper;
    private final ItemMapper itemMapper;
    private final UserPubCache userPubCache;
    private final SensitiveWordService sensitiveWordService;

    /** 发评论：敏感词过滤后入库，audit_status=正常(0) */
    @Transactional
    public void publish(long userId, long itemId, ItemCommentReq req) {
        Item item = itemMapper.selectById(itemId);
        BizAssert.isTrue(item != null && Long.valueOf(0L).equals(item.getDeleted()),
                ItemErrorCode.ITEM_NOT_FOUND);
        BizAssert.isTrue(Integer.valueOf(3).equals(item.getStatus()), UserErrorCode.ITEM_NOT_COMMENTABLE);

        String content = req.getContent() == null ? null : req.getContent().trim();
        BizAssert.notBlank(content, CommonErrorCode.PARAM_ERROR, "评论内容不能为空");
        BizAssert.isTrue(content.length() <= MAX_LEN,
                CommonErrorCode.PARAM_ERROR, "评论不能超过 500 字");

        ItemComment c = new ItemComment();
        c.setItemId(itemId);
        c.setUserId(userId);
        c.setReplyTo(req.getReplyTo());
        c.setContent(sensitiveWordService.filter(content));
        c.setAuditStatus(AUDIT_NORMAL);
        commentMapper.insert(c);
    }

    /** 评论列表：正序分页；下架商品也能读历史评论 */
    public PageResult<ItemCommentVO> list(long itemId, int pageNum, int pageSize) {
        int pn = Math.max(1, pageNum);
        int ps = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        Page<ItemComment> p = commentMapper.selectPage(new Page<>(pn, ps),
                new LambdaQueryWrapper<ItemComment>()
                        .eq(ItemComment::getItemId, itemId)
                        .eq(ItemComment::getDeleted, 0L)
                        .eq(ItemComment::getAuditStatus, AUDIT_NORMAL)
                        .orderByAsc(ItemComment::getCreateTime)
                        .orderByAsc(ItemComment::getId));
        return PageResult.of(toVOList(p.getRecords()), p.getTotal(), p.getCurrent(), p.getSize());
    }

    private List<ItemCommentVO> toVOList(List<ItemComment> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new HashSet<>();
        for (ItemComment c : rows) {
            userIds.add(c.getUserId());
            if (c.getReplyTo() != null) {
                userIds.add(c.getReplyTo());
            }
        }
        Map<Long, UserPubVO> users = userPubCache.getOrLoad(userIds);

        List<ItemCommentVO> list = new ArrayList<>(rows.size());
        for (ItemComment c : rows) {
            ItemCommentVO vo = new ItemCommentVO();
            vo.setId(c.getId());
            vo.setItemId(c.getItemId());
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
}

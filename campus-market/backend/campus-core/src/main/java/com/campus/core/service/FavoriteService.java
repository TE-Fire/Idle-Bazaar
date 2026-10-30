package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.asserts.BizAssert;
import com.campus.common.result.PageResult;
import com.campus.core.dto.resp.ApiItemVO;
import com.campus.core.entity.Favorite;
import com.campus.core.entity.Item;
import com.campus.core.errcode.ItemErrorCode;
import com.campus.core.mapper.FavoriteMapper;
import com.campus.core.mapper.ItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 收藏（notes/24）：加入 / 取消 / 我的收藏列表。
 *
 * <p><b>同一对「用户—商品」恒为一行</b>：取消把 deleted 写成毫秒时间戳，再收藏复活原行
 * （{@code FavoriteMapper#revive}）。唯一键 (user_id, item_id, deleted) 决定了不能写常量 1，
 * 否则第二次收藏会撞上那条「已删除」的行。
 *
 * <p><b>item.fav_count 只做展示</b>，真值永远以本表行数为准；对不上时不回写，
 * 让计数继续偏着比用一次统计覆盖掉并发写入更安全。
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private static final int PAGE_MAX = 50;

    private final FavoriteMapper favoriteMapper;

    private final ItemMapper itemMapper;

    private final ItemService itemService;

    /** 收藏。重复收藏直接返回成功，不重复计数——前端连点两次不该报错 */
    @Transactional(rollbackFor = Exception.class)
    public void add(long userId, long itemId) {
        Item item = itemMapper.selectById(itemId);
        BizAssert.isTrue(item != null && item.getDeleted() != null && item.getDeleted() == 0,
                ItemErrorCode.FAVORITE_ITEM_NOT_FOUND);
        if (exists(userId, itemId)) {
            return;
        }
        int revived = favoriteMapper.revive(userId, itemId);
        if (revived == 0) {
            try {
                favoriteMapper.insert(new Favorite().setUserId(userId).setItemId(itemId));
            } catch (DuplicateKeyException e) {
                // 并发下另一请求刚插入同一对关系：按已收藏处理，不重复加计数
                return;
            }
        }
        itemMapper.updateFavCount(itemId, 1);
    }

    /** 取消收藏。影响 0 行说明本就没收藏，直接返回成功且不动计数 */
    @Transactional(rollbackFor = Exception.class)
    public void remove(long userId, long itemId) {
        int affected = favoriteMapper.softDelete(userId, itemId, System.currentTimeMillis());
        if (affected > 0) {
            itemMapper.updateFavCount(itemId, -1);
        }
    }

    /**
     * 我的收藏，按收藏时间倒序。
     *
     * <p>先分页查关系再批量取商品：商品被删或强制下架时这一页会少几条，total 仍按关系行数给——
     * 用过滤后的条数当 total 会让分页器在还有数据时提前显示「到底了」。
     */
    public PageResult<ApiItemVO> list(long userId, Integer pageNum, Integer pageSize) {
        long page = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = Math.min(pageSize == null || pageSize < 1 ? 10 : pageSize, PAGE_MAX);

        Page<Favorite> p = favoriteMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .eq(Favorite::getDeleted, 0)
                        .orderByDesc(Favorite::getCreateTime)
                        .orderByDesc(Favorite::getId));
        if (p.getRecords().isEmpty()) {
            return PageResult.of(List.of(), p.getTotal(), page, size);
        }
        List<Long> itemIds = p.getRecords().stream().map(Favorite::getItemId).toList();
        Map<Long, ApiItemVO> byId = itemService.cardsByIds(itemIds).stream()
                .collect(Collectors.toMap(ApiItemVO::getId, Function.identity(), (a, b) -> a));
        Map<Long, java.time.LocalDateTime> favoritedAt = p.getRecords().stream()
                .collect(Collectors.toMap(Favorite::getItemId, Favorite::getCreateTime, (a, b) -> a));
        List<ApiItemVO> list = itemIds.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .peek(vo -> vo.setFavoritedAt(favoritedAt.get(vo.getId())))
                .toList();
        return PageResult.of(list, p.getTotal(), page, size);
    }

    private boolean exists(long userId, long itemId) {
        return favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getItemId, itemId)
                .eq(Favorite::getDeleted, 0)) > 0;
    }
}

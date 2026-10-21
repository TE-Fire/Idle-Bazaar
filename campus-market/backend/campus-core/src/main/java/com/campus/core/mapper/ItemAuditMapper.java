package com.campus.core.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.core.entity.ItemAudit;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 商品审核（`item_audit`）。一个商品可有多条（重提再进队），latestOf 取时间线上最后一条。
 */
@Mapper
public interface ItemAuditMapper extends BaseMapper<ItemAudit> {

    /** 该商品最新一条审核记录（按创建时间倒序），没有则 null */
    default ItemAudit latestOf(Long itemId) {
        List<ItemAudit> list = selectList(new LambdaQueryWrapper<ItemAudit>()
                .eq(ItemAudit::getItemId, itemId)
                .eq(ItemAudit::getDeleted, 0)
                .orderByDesc(ItemAudit::getCreateTime)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }
}

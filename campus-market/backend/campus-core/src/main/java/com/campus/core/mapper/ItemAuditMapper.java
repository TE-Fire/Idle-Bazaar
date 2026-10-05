package com.campus.core.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.core.entity.ItemAudit;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 商品审核（`item_audit`）。
 *
 * <p>一个商品可有多条（重提会再进队一次），所以「取一条」必须显式定义取哪条——
 * {@link #latestOf(Long)} 取的是时间线上最后一条，列表展示的风险分与驳回原因都来自它。
 */
@Mapper
public interface ItemAuditMapper extends BaseMapper<ItemAudit> {

    /**
     * 取该商品最新一条审核记录，没有则返回 null。
     *
     * <p>`LIMIT 1` 写在 `last()` 里：MP 的 Wrapper 没有 limit 方法，
     * 而按创建时间倒序取一条是稳定的写法，不涉及外部输入，不存在注入问题。
     */
    default ItemAudit latestOf(Long itemId) {
        List<ItemAudit> list = selectList(new LambdaQueryWrapper<ItemAudit>()
                .eq(ItemAudit::getItemId, itemId)
                .eq(ItemAudit::getDeleted, 0)
                .orderByDesc(ItemAudit::getCreateTime)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }
}

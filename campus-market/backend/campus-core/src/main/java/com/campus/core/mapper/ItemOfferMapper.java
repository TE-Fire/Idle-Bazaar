package com.campus.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.core.entity.ItemOffer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 议价单（`item_offer`）的数据访问。
 *
 * <p>不另写 SQL：进行中判重、卖家待办、付款后批量失效都是单表条件查询或条件更新，
 * 用 {@code LambdaQueryWrapper} 表达即可，而且条件更新能直接拿来判定并发（影响行数）。
 *
 * <p>唯一索引 {@code uk_alive} 是「同买家同商品仅一条进行中」的最终裁决者：
 * 连点两次不会插出两行，第二次直接抛 {@code DuplicateKeyException}，
 * 由 Service 转成可读的业务错误。
 */
@Mapper
public interface ItemOfferMapper extends BaseMapper<ItemOffer> {
}

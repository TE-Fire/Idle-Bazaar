package com.campus.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.core.entity.TradeOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单（`trade_order`）的数据访问。
 *
 * <p>只挂 {@link BaseMapper}，不预先写自定义 SQL：订单的查询与流转都能由主键、
 * `item_id` + `status` 表达，且这些组合已被 `idx_item`、`idx_buyer`、
 * `idx_seller`、`idx_expire` 四个索引覆盖。将来若出现需要聚合统计的查询再按需补方法。
 *
 * <p><b>占位只能用条件更新，不能先查后改</b>：两个买家同时付款时，
 * 先查后改的两个请求都会通过校验，后一个会静默覆盖前一个的结果。
 * 这一点在 `OrderService` 的付款流程里落地，这里是它的执行终端。
 */
@Mapper
public interface TradeOrderMapper extends BaseMapper<TradeOrder> {
}

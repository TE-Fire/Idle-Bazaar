package com.campus.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.core.entity.Item;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品（`item`）。
 *
 * <p>除 BaseMapper 外只有一个聚合查询：按分类统计已成交均价。
 * 放这里而不是新建 TradeOrderMapper——统计的是「商品的分类维度」，
 * 返回值以 `category_id` 为主键，归商品模块所有。
 */
@Mapper
public interface ItemMapper extends BaseMapper<Item> {

    /**
     * 按分类统计已成交均价的行类型。
     *
     * <p>是 Mapper 内部的行映射对象，不是对外的 API 契约，所以不进 `dto` 包。
     * 用普通类而不是 record：MyBatis 走无参构造 + setter 实例化，record 没有无参构造。
     */
    @Data
    class CategoryAvgRow {
        private Long categoryId;
        private BigDecimal avgPrice;
    }

    /**
     * 各分类的已成交均价（只统计 `trade_order.status = 4` 已完成）。
     *
     * <p>全量 GROUP BY 而不按当前页的 categoryId 过滤：分类只有几十个，
     * 结果形状稳定，算一次就能给整页复用。
     *
     * <p>**调用方必须走缓存**（`CategoryAvgPriceCache#categoryAvgPrices()`）：这条是全表聚合，
     * 成本随订单历史增长、不随分页下降，实测 5 万订单要 380–1000ms。加索引也救不了——
     * 瓶颈在按 `item_id` 回表取 `category_id`，加候选索引反而让优化器换错 JOIN 驱动方向。
     *
     * <p>没成交的分类**不出现在结果里**，调用方按「查不到就是 0」处理——
     * 前端 `isPriceAbnormal` 里 `avg &lt;= 0` 会直接返回 false，不会误标红。
     */
    @Select("SELECT i.category_id AS category_id, AVG(o.amount) AS avg_price " +
            "FROM trade_order o " +
            "JOIN item i ON i.id = o.item_id " +
            "WHERE o.deleted = 0 AND o.status = 4 AND i.deleted = 0 " +
            "GROUP BY i.category_id")
    List<CategoryAvgRow> avgPriceByCategory();
}

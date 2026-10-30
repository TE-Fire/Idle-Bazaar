package com.campus.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.core.entity.Item;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品（`item`）。除 BaseMapper 外只有一个聚合查询：按分类统计已成交均价
 * （统计的是商品的分类维度，故归商品模块而非 TradeOrderMapper）。
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
     * 各分类已成交均价（只统计 `trade_order.status = 4` 已完成）。
     *
     * <p>全量 GROUP BY、不随分页与筛选变化，成本随订单历史增长（5 万订单数百 ms），
     * 加索引救不了（瓶颈在按 item_id 回表取 category_id）。**调用方必须走
     * {@code CategoryAvgPriceCache}**。
     *
     * <p>没成交的分类不出现在结果里，调用方按「查不到就是 0」处理。
     */
    @Select("SELECT i.category_id AS category_id, AVG(o.amount) AS avg_price " +
            "FROM trade_order o " +
            "JOIN item i ON i.id = o.item_id " +
            "WHERE o.deleted = 0 AND o.status = 4 AND i.deleted = 0 " +
            "GROUP BY i.category_id")
    List<CategoryAvgRow> avgPriceByCategory();

    /**
     * 收藏数增减：SQL 里自算，不先读后写。
     * `GREATEST(..., 0)` 兜住历史脏数据——计数为负比数不准更糟，前端会显示「-1 人收藏」。
     */
    @Update("UPDATE item SET fav_count = GREATEST(fav_count + #{delta}, 0) WHERE id = #{itemId}")
    int updateFavCount(@Param("itemId") Long itemId, @Param("delta") int delta);
}

package com.campus.core.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 举报（`report`）的聚合查询。
 *
 * <p>只声明商品列表需要的计数方法，**不 extends BaseMapper**：
 * 举报实体与举报处置流程属 A-B3（举报与申诉），本期不做，
 * 现在建实体等于替下个模块做设计决定。A-B3 开工时在这里补实体与 BaseMapper。
 *
 * <p>计数是**实时**的，与 `item_audit.report_count`（进队快照）不是一个口径——
 * 后者给权重公式用、不随时间变化；这个给运营看「又被举报了」。
 */
@Mapper
public interface ReportMapper {

    /** 按商品统计举报数的行类型（Mapper 内部用，不是对外的 API 契约） */
    @Data
    class ItemReportCountRow {
        private Long itemId;
        private Long cnt;
    }

    /**
     * 按商品统计未删除的举报数（`target_type = 1` 表示举报对象为商品）。
     *
     * <p>只查传入的这批 itemId：列表是分页的，一页最多几十条，
     * 全量 GROUP BY 会把已经下架很久的商品的举报也一起算出来。
     *
     * <p>没有被举报的商品不出现在结果里，调用方按「查不到就是 0」处理。
     */
    @Select("<script>" +
            "SELECT target_id AS item_id, COUNT(*) AS cnt " +
            "FROM report " +
            "WHERE deleted = 0 AND target_type = 1 AND target_id IN " +
            "<foreach collection='itemIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY target_id" +
            "</script>")
    List<ItemReportCountRow> countByItem(@Param("itemIds") Collection<Long> itemIds);
}

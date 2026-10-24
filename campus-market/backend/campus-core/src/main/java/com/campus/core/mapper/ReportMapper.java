package com.campus.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.core.entity.Report;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 举报（`report`）的数据访问。
 *
 * <p>上一版只有聚合计数方法、刻意不 extends BaseMapper（当时 A-B3 未开工，
 * 建实体等于替下个模块做设计决定）。A-B3 开工后实体已落地，这里补上
 * {@link BaseMapper}：举报处置要按主键做条件更新（notes/09 附录 A.2 的并发控制）。
 *
 * <p>计数是**实时**的（只计未结单），与 `item_audit.report_count`（进队快照）不是一个口径——
 * 后者给权重公式用、不随时间变化；这个给运营看「又被举报了」。
 */
@Mapper
public interface ReportMapper extends BaseMapper<Report> {

    /** 按商品统计举报数的行类型（Mapper 内部用，不是对外的 API 契约） */
    @Data
    class ItemReportCountRow {
        private Long itemId;
        private Long cnt;
    }

    /**
     * 按商品统计**未结**举报数（`target_type = 1` 表示举报对象为商品；
     * 未结 = `status ∈ { 待受理(1), 处理中(2) }`，已处理(3)不计）。
     *
     * <p>只查传入的这批 itemId：列表是分页的，一页最多几十条，
     * 全量 GROUP BY 会把已经下架很久的商品的举报也一起算出来。
     *
     * <p>已处理的不计：查实不成立的举报若永久留在计数里，卖家改价重提时
     * `item_audit.report_count` 快照会把旧数带进新审核单，权重只涨不落；
     * 查实成立的举报商品已被强制下架，本就不在队列里。
     *
     * <p>没有被举报的商品不出现在结果里，调用方按「查不到就是 0」处理。
     */
    @Select("<script>" +
            "SELECT target_id AS item_id, COUNT(*) AS cnt " +
            "FROM report " +
            "WHERE deleted = 0 AND target_type = 1 AND status IN (1, 2) AND target_id IN " +
            "<foreach collection='itemIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY target_id" +
            "</script>")
    List<ItemReportCountRow> countByItem(@Param("itemIds") Collection<Long> itemIds);
}

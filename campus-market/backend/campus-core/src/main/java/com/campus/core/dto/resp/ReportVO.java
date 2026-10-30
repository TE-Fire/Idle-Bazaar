package com.campus.core.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * A8 数据报表的三个可视化接口 + SLA 明细（口径见 notes/25 §三）。
 *
 * <p>2026-10-30 起只剩三张校园图：学期时间轴、学院 × 品类矩阵、面交所热力。
 * 第四张「二手循环率与减碳」随减碳功能整体下线（db/01 第六节），
 * **不要**再从任何地方把它加回来——系数无法用本校数据校准。
 */
public final class ReportVO {

    private ReportVO() {
    }

    /** 学期时间轴：18 个自然周，缺数据的周补 0，前端才能画连续折线 */
    @Data
    public static class Timeline {
        /** 轴标签，如 `第 3 周`——序号由后端算，别让前端自己推 */
        private List<String> labels;
        private List<Long> published;
        private List<Long> sold;
    }

    /**
     * 学院 × 品类矩阵：行是学院（含「未指定」），列是一级品类，空格补 0。
     *
     * <p>`cells` 与 `colleges` / `categories` 同序对齐（`cells[row][col]`），
     * 前端 heatmap 直接按下标取，不再自己对齐。
     */
    @Data
    public static class Matrix {
        private String metric;
        private List<String> colleges;
        private List<String> categories;
        private List<List<Long>> cells;
    }

    /** 面交所热力：五档固定时段，`meet_time` 为空的单不计（未约时间就没有时段） */
    @Data
    public static class MeetHeat {
        private List<String> points;
        private List<String> slots;
        private List<List<Long>> cells;
    }

    /** 分审核员明细：一行一个人，`total` 是全量合计（与看板 SLA 卡同源，不另算一套） */
    @Data
    public static class SlaTable {
        private List<Reviewer> rows;
        private Reviewer total;
    }

    @Data
    public static class Reviewer {
        private String name;
        private Long handled;
        private Long avgMin;
        private String okRate;
        private String p95Min;
        private Long handleSec;
    }
}

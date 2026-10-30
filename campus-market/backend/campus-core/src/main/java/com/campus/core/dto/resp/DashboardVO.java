package com.campus.core.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * A2 看板的一次性响应（`/admin/stats/dashboard`，口径见 notes/25 §二）。
 *
 * <p><b>六块一起返回</b>：KPI / 趋势 / 构成 / TOP-N / 待处置 / 异常。拆成六个接口会让首屏出现
 * 「KPI 已经到了、图还在转」的割裂，前端还得自己拼加载态。SLA 卡是本页第七块，
 * 与 A8 报表的「分审核员明细」口径不同，不能互相推导。
 *
 * <p>金额与比率一律用字符串下发（项目统一约定），件数人数用数字。
 */
@Data
public class DashboardVO {

    private Kpi kpi;

    private Trend trend;

    /** 在售商品按一级分类（环图） */
    private List<NameValue> categorySlices;

    /** 在售商品按成色（柱） */
    private List<NameValue> gradeBars;

    /** 热销品类 TOP5 */
    private List<NameValue> topCategories;

    /** 活跃学院 TOP10 */
    private List<NameValue> topColleges;

    private Pending pending;

    /** 异常预警三条：只给计数，文案与跳转由前端常量维护（口径在同一个文件里） */
    private List<Alert> alerts;

    private Sla sla;

    /** KPI 四格；GMV 是累计值，其余三项是**今日**口径 */
    @Data
    public static class Kpi {
        private String gmv;
        private Long itemCount;
        private Long orderCount;
        private Long activeUsers;
    }

    /** 趋势：`days` 窗口内按天，缺口补 0 不断线 */
    @Data
    public static class Trend {
        private List<String> labels;
        private List<Long> published;
        private List<Long> sold;
        private List<String> gmv;
    }

    @Data
    public static class NameValue {
        private String name;
        private Long value;
    }

    /** 待处置角标：退款含退款中(6)与纠纷中(8)，都需人工介入 */
    @Data
    public static class Pending {
        private Long audit;
        private Long report;
        private Long appeal;
        private Long refund;
    }

    @Data
    public static class Alert {
        /** trade / brush / price */
        private String key;
        private Long count;
    }

    /**
     * SLA 卡：阈值 + 实况 + 近 14 日曲线。
     *
     * <p>`okRate` / `p95Min` 是字符串——比率要给前端算阈值判断，格式化在前端做；
     * `backlog` 是**现在还没处理完**的数量，与 `avgMin`（已处置的统计）口径不同。
     */
    @Data
    public static class Sla {
        private SlaConfigVO thresholds;
        private Long handled;
        private Long avgMin;
        private String okRate;
        private String p95Min;
        private Long backlog;
        private Long handleSec;
        private List<DayPoint> daily;
    }

    @Data
    public static class DayPoint {
        private String day;
        private BigDecimal value;
    }
}

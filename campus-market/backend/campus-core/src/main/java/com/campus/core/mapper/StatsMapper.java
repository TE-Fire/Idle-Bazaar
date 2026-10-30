package com.campus.core.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 看板与报表的聚合查询（notes/25 定死了每条口径，改 SQL 前先看它）。
 *
 * <p><b>为什么这些 SQL 敢写聚合</b>：业务模块的列表查询刻意避开 `GROUP BY`（MP 分页会算错 count），
 * 但这里只有一种用法——把一整个窗口的数压成几十行图表数据，不分页、不要 count，
 * 聚合正是它该待的地方。全部实时算，不建日统计中间表（notes/25 §一）。
 *
 * <p><b>时间字段不许用 `create_time` 顶替</b>：发布看 `item.published_at`（审核通过那一刻），
 * 成交看 `trade_order.pay_time`，面交看 `meet_time`。用创建时间会把草稿也算成发布。
 */
@Mapper
public interface StatsMapper {

    /* ── KPI ─────────────────────────────────────── */

    /** 累计 GMV：已完成订单的成交额（`status=4`） */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM trade_order WHERE deleted = 0 AND status = 4")
    BigDecimal gmv();

    @Select("SELECT COUNT(*) FROM item WHERE deleted = 0 AND published_at >= #{from}")
    long itemsPublishedSince(@Param("from") LocalDateTime from);

    @Select("SELECT COUNT(*) FROM trade_order WHERE deleted = 0 AND pay_time >= #{from}")
    long ordersPaidSince(@Param("from") LocalDateTime from);

    @Select("SELECT COUNT(*) FROM app_user WHERE deleted = 0 AND last_login_at >= #{from}")
    long activeUsersSince(@Param("from") LocalDateTime from);

    /* ── 趋势（按天，缺口在 Java 补 0） ──────────────── */

    @Select("SELECT DATE_FORMAT(published_at, '%Y-%m-%d') AS day, COUNT(*) AS num "
            + "FROM item WHERE deleted = 0 AND published_at >= #{from} "
            + "GROUP BY day")
    List<DayNum> publishedByDay(@Param("from") LocalDateTime from);

    @Select("SELECT DATE_FORMAT(pay_time, '%Y-%m-%d') AS day, COUNT(*) AS num, "
            + "COALESCE(SUM(amount), 0) AS amount "
            + "FROM trade_order WHERE deleted = 0 AND pay_time >= #{from} "
            + "GROUP BY day")
    List<DayNum> paidByDay(@Param("from") LocalDateTime from);

    /* ── 构成（在售商品） ──────────────────────────── */

    /** 在售商品按一级分类：`item.category_id` 是二级，上卷到 `parent_id` */
    @Select("SELECT COALESCE(p.name, '未分类') AS name, COUNT(*) AS num "
            + "FROM item i "
            + "LEFT JOIN dict_category c ON c.id = i.category_id AND c.deleted = 0 "
            + "LEFT JOIN dict_category p ON p.id = c.parent_id AND p.deleted = 0 "
            + "WHERE i.deleted = 0 AND i.status = 3 "
            + "GROUP BY name ORDER BY num DESC")
    List<NameNum> onSaleByCategory();

    @Select("SELECT grade AS name, COUNT(*) AS num FROM item "
            + "WHERE deleted = 0 AND status = 3 GROUP BY grade ORDER BY grade")
    List<NameNum> onSaleByGrade();

    /* ── TOP-N ───────────────────────────────────── */

    /** 热销品类：窗口内已付款订单按一级分类计数 */
    @Select("SELECT COALESCE(p.name, '未分类') AS name, COUNT(*) AS num "
            + "FROM trade_order o "
            + "JOIN item i ON i.id = o.item_id "
            + "LEFT JOIN dict_category c ON c.id = i.category_id AND c.deleted = 0 "
            + "LEFT JOIN dict_category p ON p.id = c.parent_id AND p.deleted = 0 "
            + "WHERE o.deleted = 0 AND o.pay_time >= #{from} "
            + "GROUP BY name ORDER BY num DESC LIMIT #{limit}")
    List<NameNum> topCategories(@Param("from") LocalDateTime from, @Param("limit") int limit);

    /** 活跃学院：窗口内成交按**卖家**学院计数（卖家是供给方，活跃看供给） */
    @Select("SELECT COALESCE(col.name, '未指定') AS name, COUNT(*) AS num "
            + "FROM trade_order o "
            + "JOIN app_user u ON u.id = o.seller_id AND u.deleted = 0 "
            + "LEFT JOIN dict_college col ON col.id = u.college_id AND col.deleted = 0 "
            + "WHERE o.deleted = 0 AND o.pay_time >= #{from} "
            + "GROUP BY name ORDER BY num DESC LIMIT #{limit}")
    List<NameNum> topColleges(@Param("from") LocalDateTime from, @Param("limit") int limit);

    /* ── 待处置队列 ────────────────────────────────── */

    /**
     * 四个角标一次查回。四个数来自四张表，合并成一行只为少三次往返。
     *
     * <p>`refund` 含退款中(6)与纠纷中(8)：两者都要人工介入，角标里不区分，
     * 点进去订单页按状态再筛。
     */
    @Select("SELECT "
            + "(SELECT COUNT(*) FROM item_audit WHERE deleted = 0 AND status = 1) AS audit, "
            + "(SELECT COUNT(*) FROM report WHERE deleted = 0 AND status IN (1, 2)) AS report, "
            + "(SELECT COUNT(*) FROM appeal WHERE deleted = 0 AND status = 1) AS appeal, "
            + "(SELECT COUNT(*) FROM trade_order WHERE deleted = 0 AND status IN (6, 8)) AS refund")
    PendingRow pending();

    /* ── 异常预警三条 ──────────────────────────────── */

    /**
     * 价格异常：售价比同分类在售均价低 30% 以上。
     *
     * <p>口径直接算 SQL 而**不读** `cm:item:catAvg`——那是给商品详情做「同分类均价」展示用的缓存，
     * 缺项时会静默少算；预警数字宁可慢一点，也不能少。
     */
    @Select("SELECT COUNT(*) FROM item i "
            + "JOIN (SELECT category_id, AVG(price) AS avg_price FROM item "
            + "      WHERE deleted = 0 AND status = 3 GROUP BY category_id) a "
            + "  ON a.category_id = i.category_id "
            + "WHERE i.deleted = 0 AND i.status = 3 AND i.price < a.avg_price * 0.7")
    long priceAbnormal();

    /** 高频发布：同一卖家最近 1 小时内发布 ≥ 5 件，用来抓批量搬运 */
    @Select("SELECT COUNT(*) FROM ("
            + "  SELECT seller_id FROM item "
            + "  WHERE deleted = 0 AND published_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR) "
            + "  GROUP BY seller_id HAVING COUNT(*) >= 5) t")
    long brushSellers();

    /** 低信用在售：信用分 < 60 且名下有在售商品，这类本该走人工审核 */
    @Select("SELECT COUNT(DISTINCT u.id) FROM app_user u "
            + "JOIN item i ON i.seller_id = u.id AND i.deleted = 0 AND i.status = 3 "
            + "WHERE u.deleted = 0 AND u.credit_score < 60")
    long lowCreditSellers();

    /* ── SLA（响应时效 + 操作效率） ──────────────────── */

    /**
     * 已处置的审核单：首审时长与处置耗时的原始行，P95 在 Java 排序后取。
     *
     * <p>不在 SQL 里算 P95——MySQL 没有百分位函数，窗口函数写法在条数极少时反而不如
     * 「取排序后第 95% 条」好解释（notes/25 §三）。
     */
    @Select("SELECT handler_id, enter_queue_at, first_handle_at, handle_cost_ms FROM item_audit "
            + "WHERE deleted = 0 AND first_handle_at IS NOT NULL")
    List<AuditRow> auditHandled();

    @Select("SELECT COUNT(*) FROM item_audit WHERE deleted = 0 AND status = 1 "
            + "AND enter_queue_at < #{before}")
    long backlog(@Param("before") LocalDateTime before);

    /** 近 N 日平均首审时长（分钟），按处置日分组 */
    @Select("SELECT DATE_FORMAT(first_handle_at, '%m-%d') AS day, "
            + "ROUND(AVG(TIMESTAMPDIFF(MINUTE, enter_queue_at, first_handle_at))) AS num "
            + "FROM item_audit WHERE deleted = 0 AND first_handle_at >= #{from} "
            + "GROUP BY day ORDER BY MIN(first_handle_at)")
    List<DayNum> slaDaily(@Param("from") LocalDateTime from);

    /* ── 报表：学期时间轴（自然周，周一为界） ──────────── */

    @Select("SELECT DATE_FORMAT(DATE_SUB(DATE(published_at), INTERVAL WEEKDAY(published_at) DAY), "
            + "'%Y-%m-%d') AS day, COUNT(*) AS num "
            + "FROM item WHERE deleted = 0 AND published_at >= #{from} "
            + "GROUP BY day")
    List<DayNum> publishedByWeek(@Param("from") LocalDateTime from);

    @Select("SELECT DATE_FORMAT(DATE_SUB(DATE(pay_time), INTERVAL WEEKDAY(pay_time) DAY), "
            + "'%Y-%m-%d') AS day, COUNT(*) AS num "
            + "FROM trade_order WHERE deleted = 0 AND pay_time >= #{from} "
            + "GROUP BY day")
    List<DayNum> soldByWeek(@Param("from") LocalDateTime from);

    /* ── 报表：学院 × 品类矩阵 ──────────────────────── */

    /** 成交：按**买家**学院 × 一级品类计数 */
    @Select("SELECT COALESCE(col.name, '未指定') AS college, COALESCE(p.name, '未分类') AS category, "
            + "COUNT(*) AS num "
            + "FROM trade_order o "
            + "JOIN app_user u ON u.id = o.buyer_id AND u.deleted = 0 "
            + "LEFT JOIN dict_college col ON col.id = u.college_id AND col.deleted = 0 "
            + "JOIN item i ON i.id = o.item_id "
            + "LEFT JOIN dict_category c ON c.id = i.category_id AND c.deleted = 0 "
            + "LEFT JOIN dict_category p ON p.id = c.parent_id AND p.deleted = 0 "
            + "WHERE o.deleted = 0 AND o.pay_time IS NOT NULL "
            + "GROUP BY college, category")
    List<MatrixRow> matrixSold();

    /** 发布：按**卖家**学院 × 一级品类计数 */
    @Select("SELECT COALESCE(col.name, '未指定') AS college, COALESCE(p.name, '未分类') AS category, "
            + "COUNT(*) AS num "
            + "FROM item i "
            + "JOIN app_user u ON u.id = i.seller_id AND u.deleted = 0 "
            + "LEFT JOIN dict_college col ON col.id = u.college_id AND col.deleted = 0 "
            + "LEFT JOIN dict_category c ON c.id = i.category_id AND c.deleted = 0 "
            + "LEFT JOIN dict_category p ON p.id = c.parent_id AND p.deleted = 0 "
            + "WHERE i.deleted = 0 AND i.published_at IS NOT NULL "
            + "GROUP BY college, category")
    List<MatrixRow> matrixPublished();

    /* ── 报表：面交所热力 ──────────────────────────── */

    /** 面交点 × 小时；时段分档在 Java 做（五档边界是业务口径，写在 SQL 里以后没人找得到） */
    @Select("SELECT COALESCE(m.name, '未指定') AS point, HOUR(o.meet_time) AS hour, COUNT(*) AS num "
            + "FROM trade_order o "
            + "LEFT JOIN dict_meet_point m ON m.id = o.meet_point_id AND m.deleted = 0 "
            + "WHERE o.deleted = 0 AND o.meet_time IS NOT NULL "
            + "GROUP BY point, hour")
    List<MeetRow> meetHeat();

    /* ── 投影类型 ─────────────────────────────────── */

    @Data
    class NameNum {
        private String name;
        private Long num;
    }

    @Data
    class DayNum {
        private String day;
        private Long num;
        private BigDecimal amount;
    }

    @Data
    class MatrixRow {
        private String college;
        private String category;
        private Long num;
    }

    @Data
    class MeetRow {
        private String point;
        private Integer hour;
        private Long num;
    }

    @Data
    class AuditRow {
        private Long handlerId;
        private LocalDateTime enterQueueAt;
        private LocalDateTime firstHandleAt;
        private Integer handleCostMs;
    }

    @Data
    class PendingRow {
        private Long audit;
        private Long report;
        private Long appeal;
        private Long refund;
    }
}

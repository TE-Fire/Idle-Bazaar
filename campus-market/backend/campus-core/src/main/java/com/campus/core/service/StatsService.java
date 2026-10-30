package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.CommonErrorCode;
import com.campus.core.dto.resp.DashboardVO;
import com.campus.core.dto.resp.ReportVO;
import com.campus.core.dto.resp.SlaConfigVO;
import com.campus.core.entity.AdminUser;
import com.campus.core.entity.DictCollege;
import com.campus.core.mapper.AdminUserMapper;
import com.campus.core.mapper.DictCollegeMapper;
import com.campus.core.mapper.StatsMapper;
import com.campus.core.mapper.StatsMapper.AuditRow;
import com.campus.core.mapper.StatsMapper.DayNum;
import com.campus.core.mapper.StatsMapper.MatrixRow;
import com.campus.core.mapper.StatsMapper.MeetRow;
import com.campus.core.mapper.StatsMapper.NameNum;
import com.campus.core.mapper.StatsMapper.PendingRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 看板与报表的取数（notes/25 是唯一口径来源，改这里先改它）。
 *
 * <p><b>全部实时聚合</b>，不建日统计中间表：百级商品、几十单的规模下 `GROUP BY` 在毫秒级，
 * 建 `stat_daily` 就要加定时任务、处理补数，还得解释「为什么今天的数据要等五分钟」。
 *
 * <p><b>算在 Java 还是算在 SQL</b>：能一句聚合说明白的（计数、求和、分组）交给 SQL；
 * 需要「排序取第 95% 条」这种 SQL 里没有的语义（P95、首审时长分档），取原始行回 Java 算——
 * 条数少，可读性比省几次循环重要。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM-dd");

    /** SLA 曲线固定看近 14 日：短于两周看不出趋势，长于一屏挤成糊 */
    private static final int SLA_DAYS = 14;

    /** 面交热力五档时段，边界与标签同源（notes/25 §三） */
    private static final String[] SLOT_LABELS = {"早 8–12", "中午 12–14", "下午 14–18", "晚 18–21", "夜间 21–23"};

    private final StatsMapper statsMapper;
    private final SysConfigService sysConfigService;
    private final DictCollegeMapper dictCollegeMapper;
    private final AdminUserMapper adminUserMapper;

    /* ══ A2 看板 ══════════════════════════════════════ */

    public DashboardVO dashboard(int days) {
        BizAssert.isTrue(days == 7 || days == 30, CommonErrorCode.PARAM_ERROR);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime windowFrom = todayStart.minusDays(days - 1L);

        DashboardVO vo = new DashboardVO();

        DashboardVO.Kpi kpi = new DashboardVO.Kpi();
        BigDecimal gmv = statsMapper.gmv();
        kpi.setGmv(money(gmv));
        kpi.setItemCount(statsMapper.itemsPublishedSince(todayStart));
        kpi.setOrderCount(statsMapper.ordersPaidSince(todayStart));
        kpi.setActiveUsers(statsMapper.activeUsersSince(todayStart));
        vo.setKpi(kpi);

        vo.setTrend(trend(todayStart, days));
        vo.setCategorySlices(nameValues(statsMapper.onSaleByCategory()));
        vo.setGradeBars(nameValues(statsMapper.onSaleByGrade()));
        vo.setTopCategories(nameValues(statsMapper.topCategories(windowFrom, 5)));
        vo.setTopColleges(nameValues(statsMapper.topColleges(windowFrom, 10)));

        PendingRow p = statsMapper.pending();
        DashboardVO.Pending pending = new DashboardVO.Pending();
        if (p != null) {
            pending.setAudit(nz(p.getAudit()));
            pending.setReport(nz(p.getReport()));
            pending.setAppeal(nz(p.getAppeal()));
            pending.setRefund(nz(p.getRefund()));
        }
        vo.setPending(pending);

        // 三条预警，多一条都不做：每条都要能在演示时讲清判据
        List<DashboardVO.Alert> alerts = new ArrayList<>(3);
        alerts.add(alert("price", statsMapper.priceAbnormal()));
        alerts.add(alert("brush", statsMapper.brushSellers()));
        alerts.add(alert("credit", statsMapper.lowCreditSellers()));
        vo.setAlerts(alerts);

        vo.setSla(slaCard());
        return vo;
    }

    /** 趋势两条件数线 + 一条金额线，缺口补 0（断线的图没法判断走向） */
    private DashboardVO.Trend trend(LocalDateTime todayStart, int days) {
        LocalDateTime from = todayStart.minusDays(days - 1L);
        Map<String, Long> published = byDay(statsMapper.publishedByDay(from));
        Map<String, Long> sold = new HashMap<>();
        Map<String, BigDecimal> gmv = new HashMap<>();
        for (DayNum row : statsMapper.paidByDay(from)) {
            sold.put(row.getDay(), nz(row.getNum()));
            gmv.put(row.getDay(), row.getAmount() == null ? BigDecimal.ZERO : row.getAmount());
        }

        List<String> labels = new ArrayList<>(days);
        List<Long> publishedSeries = new ArrayList<>(days);
        List<Long> soldSeries = new ArrayList<>(days);
        List<String> gmvSeries = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            String key = todayStart.minusDays(days - 1L - i).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
            labels.add(todayStart.minusDays(days - 1L - i).toLocalDate().format(DAY));
            publishedSeries.add(published.getOrDefault(key, 0L));
            soldSeries.add(sold.getOrDefault(key, 0L));
            gmvSeries.add(money(gmv.getOrDefault(key, BigDecimal.ZERO)));
        }
        DashboardVO.Trend t = new DashboardVO.Trend();
        t.setLabels(labels);
        t.setPublished(publishedSeries);
        t.setSold(soldSeries);
        t.setGmv(gmvSeries);
        return t;
    }

    /** SLA 卡：阈值来自 sys_config，实况从已处置的审核单算 */
    private DashboardVO.Sla slaCard() {
        SlaConfigVO thresholds = sysConfigService.sla();
        List<AuditRow> rows = statsMapper.auditHandled();

        List<Integer> minutes = new ArrayList<>(rows.size());
        long costSum = 0;
        long costCount = 0;
        for (AuditRow r : rows) {
            Integer m = minutesBetween(r.getEnterQueueAt(), r.getFirstHandleAt());
            if (m != null) {
                minutes.add(m);
            }
            if (r.getHandleCostMs() != null) {
                costSum += r.getHandleCostMs();
                costCount++;
            }
        }

        DashboardVO.Sla sla = new DashboardVO.Sla();
        sla.setThresholds(thresholds);
        sla.setHandled((long) minutes.size());
        sla.setAvgMin(minutes.isEmpty() ? 0L : Math.round(minutes.stream().mapToInt(Integer::intValue).average().orElse(0)));
        sla.setOkRate(oneDecimal(okRate(minutes, thresholds.getFirstResponseHours())));
        sla.setP95Min(oneDecimal(p95(minutes)));
        sla.setHandleSec(costCount == 0 ? 0L : Math.round(costSum / 1000.0 / costCount));
        sla.setBacklog(statsMapper.backlog(LocalDateTime.now().minusHours(thresholds.getBacklogHours())));

        List<DashboardVO.DayPoint> daily = new ArrayList<>();
        for (DayNum row : statsMapper.slaDaily(LocalDate.now().minusDays(SLA_DAYS - 1L).atStartOfDay())) {
            DashboardVO.DayPoint p = new DashboardVO.DayPoint();
            p.setDay(row.getDay());
            p.setValue(row.getNum() == null ? BigDecimal.ZERO : BigDecimal.valueOf(row.getNum()));
            daily.add(p);
        }
        sla.setDaily(daily);
        return sla;
    }

    /* ══ A8 报表 ══════════════════════════════════════ */

    /** 学期时间轴：`weeks` 个自然周（周一为界），缺数据的周补 0 */
    public ReportVO.Timeline timeline(int weeks) {
        int w = Math.min(Math.max(weeks, 4), 26);
        LocalDate thisMonday = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        LocalDate start = thisMonday.minusWeeks(w - 1L);

        Map<String, Long> published = byDay(statsMapper.publishedByWeek(start.atStartOfDay()));
        Map<String, Long> sold = byDay(statsMapper.soldByWeek(start.atStartOfDay()));

        ReportVO.Timeline vo = new ReportVO.Timeline();
        List<String> labels = new ArrayList<>(w);
        List<Long> publishedSeries = new ArrayList<>(w);
        List<Long> soldSeries = new ArrayList<>(w);
        for (int i = 0; i < w; i++) {
            String key = start.plusWeeks(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
            labels.add("第 " + (i + 1) + " 周");
            publishedSeries.add(published.getOrDefault(key, 0L));
            soldSeries.add(sold.getOrDefault(key, 0L));
        }
        vo.setLabels(labels);
        vo.setPublished(publishedSeries);
        vo.setSold(soldSeries);
        return vo;
    }

    /**
     * 学院 × 品类矩阵。
     *
     * <p>行取 `dict_college` 的顺序（停用学院也留着——历史成交还在里面），
     * 末尾补「未指定」兜住学院为空的用户；列按总量降序，空组合补 0。
     */
    public ReportVO.Matrix matrix(String metric) {
        boolean rate = "rate".equals(metric);
        BizAssert.isTrue("sold".equals(metric) || "published".equals(metric) || rate, CommonErrorCode.PARAM_ERROR);

        List<MatrixRow> soldRows = statsMapper.matrixSold();
        List<MatrixRow> publishedRows = statsMapper.matrixPublished();

        // 列：出现过的一级品类，按两表合计降序
        Map<String, Long> categoryTotal = new LinkedHashMap<>();
        for (MatrixRow r : soldRows) {
            categoryTotal.merge(r.getCategory(), nz(r.getNum()), Long::sum);
        }
        for (MatrixRow r : publishedRows) {
            categoryTotal.merge(r.getCategory(), nz(r.getNum()), Long::sum);
        }
        List<String> categories = new ArrayList<>(categoryTotal.keySet());
        categories.sort(Comparator.comparingLong((String c) -> categoryTotal.getOrDefault(c, 0L)).reversed());

        // 行：学院字典顺序 + 数据里出现的其他名字（如「未指定」）
        List<String> colleges = new ArrayList<>();
        for (DictCollege c : dictCollegeMapper.selectList(new LambdaQueryWrapper<DictCollege>()
                .eq(DictCollege::getDeleted, 0L)
                .orderByAsc(DictCollege::getSort))) {
            colleges.add(c.getName());
        }
        Set<String> extra = new LinkedHashSet<>();
        soldRows.forEach(r -> extra.add(r.getCollege()));
        publishedRows.forEach(r -> extra.add(r.getCollege()));
        for (String name : extra) {
            if (!colleges.contains(name)) {
                colleges.add(name);
            }
        }

        Map<String, Long> soldMap = toMap(soldRows);
        Map<String, Long> publishedMap = toMap(publishedRows);

        List<List<Long>> cells = new ArrayList<>(colleges.size());
        for (String college : colleges) {
            List<Long> row = new ArrayList<>(categories.size());
            for (String category : categories) {
                String key = college + "|" + category;
                long s = soldMap.getOrDefault(key, 0L);
                long p = publishedMap.getOrDefault(key, 0L);
                row.add(rate ? (p == 0 ? 0L : Math.round(s * 100.0 / p)) : ("sold".equals(metric) ? s : p));
            }
            cells.add(row);
        }

        ReportVO.Matrix vo = new ReportVO.Matrix();
        vo.setMetric(metric);
        vo.setColleges(colleges);
        vo.setCategories(categories);
        vo.setCells(cells);
        return vo;
    }

    /** 面交所热力：面交点 × 五档时段；`meet_time` 为空的单不计 */
    public ReportVO.MeetHeat meetPoint() {
        Map<String, long[]> byPoint = new LinkedHashMap<>();
        for (MeetRow r : statsMapper.meetHeat()) {
            int slot = slotOf(r.getHour());
            if (slot < 0) {
                continue;
            }
            byPoint.computeIfAbsent(r.getPoint(), k -> new long[SLOT_LABELS.length])[slot] += nz(r.getNum());
        }

        List<String> points = new ArrayList<>(byPoint.keySet());
        points.sort(Comparator.comparingLong((String p) -> {
            long sum = 0;
            for (long v : byPoint.get(p)) {
                sum += v;
            }
            return sum;
        }).reversed());

        List<List<Long>> cells = new ArrayList<>(points.size());
        for (String p : points) {
            List<Long> row = new ArrayList<>(SLOT_LABELS.length);
            for (long v : byPoint.get(p)) {
                row.add(v);
            }
            cells.add(row);
        }

        ReportVO.MeetHeat vo = new ReportVO.MeetHeat();
        vo.setPoints(points);
        vo.setSlots(List.of(SLOT_LABELS));
        vo.setCells(cells);
        return vo;
    }

    /** 分审核员 SLA 明细：按处置人分组，`total` 是全量合计 */
    public ReportVO.SlaTable slaTable() {
        SlaConfigVO thresholds = sysConfigService.sla();
        List<AuditRow> rows = statsMapper.auditHandled();

        Map<Long, List<AuditRow>> byHandler = new LinkedHashMap<>();
        for (AuditRow r : rows) {
            byHandler.computeIfAbsent(r.getHandlerId() == null ? 0L : r.getHandlerId(), k -> new ArrayList<>()).add(r);
        }
        Map<Long, String> names = adminNames(byHandler.keySet());

        List<ReportVO.Reviewer> list = new ArrayList<>(byHandler.size());
        for (Map.Entry<Long, List<AuditRow>> e : byHandler.entrySet()) {
            ReportVO.Reviewer r = reviewer(names.getOrDefault(e.getKey(), "管理员 " + e.getKey()), e.getValue(), thresholds);
            list.add(r);
        }
        list.sort(Comparator.comparingLong(ReportVO.Reviewer::getHandled).reversed());

        ReportVO.SlaTable vo = new ReportVO.SlaTable();
        vo.setRows(list);
        vo.setTotal(reviewer("合计", rows, thresholds));
        return vo;
    }

    /* ══ 小工具 ═══════════════════════════════════════ */

    private ReportVO.Reviewer reviewer(String name, List<AuditRow> rows, SlaConfigVO thresholds) {
        List<Integer> minutes = new ArrayList<>(rows.size());
        long costSum = 0;
        long costCount = 0;
        for (AuditRow r : rows) {
            Integer m = minutesBetween(r.getEnterQueueAt(), r.getFirstHandleAt());
            if (m != null) {
                minutes.add(m);
            }
            if (r.getHandleCostMs() != null) {
                costSum += r.getHandleCostMs();
                costCount++;
            }
        }
        ReportVO.Reviewer vo = new ReportVO.Reviewer();
        vo.setName(name);
        vo.setHandled((long) minutes.size());
        vo.setAvgMin(minutes.isEmpty() ? 0L : Math.round(minutes.stream().mapToInt(Integer::intValue).average().orElse(0)));
        vo.setOkRate(oneDecimal(okRate(minutes, thresholds.getFirstResponseHours())));
        vo.setP95Min(oneDecimal(p95(minutes)));
        vo.setHandleSec(costCount == 0 ? 0L : Math.round(costSum / 1000.0 / costCount));
        return vo;
    }

    private Map<Long, String> adminNames(Set<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (AdminUser u : adminUserMapper.selectList(new LambdaQueryWrapper<AdminUser>()
                .in(AdminUser::getId, ids))) {
            map.put(u.getId(), u.getRealName() == null || u.getRealName().isBlank() ? u.getUsername() : u.getRealName());
        }
        return map;
    }

    /** 首审分钟数；时间缺失或倒挂（手工改库）返回 null，不让它污染平均与 P95 */
    private static Integer minutesBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null || to.isBefore(from)) {
            return null;
        }
        return (int) Duration.between(from, to).toMinutes();
    }

    /** 达标率：≤ 阈值小时数的占比（百分数） */
    private static double okRate(List<Integer> minutes, int hours) {
        if (minutes.isEmpty()) {
            return 0;
        }
        long ok = minutes.stream().filter(m -> m <= hours * 60L).count();
        return ok * 100.0 / minutes.size();
    }

    /** P95：排序后取第 95 百分位那一条，不做插值（条数少时插值没有意义） */
    private static double p95(List<Integer> minutes) {
        if (minutes.isEmpty()) {
            return 0;
        }
        List<Integer> sorted = new ArrayList<>(minutes);
        sorted.sort(Comparator.naturalOrder());
        int idx = (int) Math.ceil(sorted.size() * 0.95) - 1;
        idx = Math.min(Math.max(idx, 0), sorted.size() - 1);
        return sorted.get(idx);
    }

    /** 时段分档：五档的边界写在代码里，SQL 只给小时数 */
    private static int slotOf(int hour) {
        if (hour >= 8 && hour < 12) {
            return 0;
        }
        if (hour >= 12 && hour < 14) {
            return 1;
        }
        if (hour >= 14 && hour < 18) {
            return 2;
        }
        if (hour >= 18 && hour < 21) {
            return 3;
        }
        if (hour >= 21 && hour < 23) {
            return 4;
        }
        return -1;
    }

    private static Map<String, Long> toMap(List<MatrixRow> rows) {
        Map<String, Long> map = new HashMap<>(rows.size());
        for (MatrixRow r : rows) {
            map.merge(r.getCollege() + "|" + r.getCategory(), nz(r.getNum()), Long::sum);
        }
        return map;
    }

    private static Map<String, Long> byDay(List<DayNum> rows) {
        Map<String, Long> map = new HashMap<>(rows.size());
        for (DayNum r : rows) {
            map.merge(r.getDay(), nz(r.getNum()), Long::sum);
        }
        return map;
    }

    private static List<DashboardVO.NameValue> nameValues(List<NameNum> rows) {
        List<DashboardVO.NameValue> list = new ArrayList<>(rows.size());
        for (NameNum r : rows) {
            DashboardVO.NameValue nv = new DashboardVO.NameValue();
            nv.setName(r.getName());
            nv.setValue(nz(r.getNum()));
            list.add(nv);
        }
        return list;
    }

    private static DashboardVO.Alert alert(String key, long count) {
        DashboardVO.Alert a = new DashboardVO.Alert();
        a.setKey(key);
        a.setCount(count);
        return a;
    }

    /** 金额统一两位小数字符串（对外金额一律字符串，前端 Number() 再运算） */
    private static String money(BigDecimal amount) {
        BigDecimal v = amount == null ? BigDecimal.ZERO : amount;
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String oneDecimal(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }
}

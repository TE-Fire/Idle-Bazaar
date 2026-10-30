package com.campus.core.dto.resp;

import lombok.Data;

/**
 * SLA 六个阈值（`sys_config` 的 `sla.*` 六键）。
 *
 * <p>响应时效三相（`firstResponseHours` / `p95Hours` / `targetRate`）与积压红牌 `backlogHours` 是一级；
 * `avgHandleSeconds` 是操作效率，层级不同不能混算（business/03 F-A9）。
 * `appealHours` 是申诉复审承诺，与首审无关。
 */
@Data
public class SlaConfigVO {

    /** 首审响应小时数：进队到首次处置 ≤ 该值算达标 */
    private Integer firstResponseHours;

    /** P95 首审时长小时数上限 */
    private Integer p95Hours;

    /** 达标率下限（百分数，95 表示 ≥95%） */
    private Integer targetRate;

    /** 积压红牌阈值：超该小时数未处置需当日清零 */
    private Integer backlogHours;

    /** 单条平均处置秒数上限（操作效率） */
    private Integer avgHandleSeconds;

    /** 申诉复审承诺小时数 */
    private Integer appealHours;
}

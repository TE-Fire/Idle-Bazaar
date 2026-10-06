package com.campus.core.dto.resp;

import lombok.Data;

/**
 * 审核权重的五个组成部分（对齐前端 `WeightParts`，`packages/shared/src/admin.ts`）。
 *
 * <p>拆开返回而不是只给一个总分，是为了让 UI 能解释「为什么它排第一」——
 * 这是 F-A2 判据里最容易被答辩追问的一点：只给一个数字，审核员无法判断该不该信它。
 *
 * <p>五项计算口径见 business/03 F-A2（2026-10-06 勘误后）：
 * <pre>
 * report = 举报次数 × 3
 * risk   = 图片风险分 0–100 归一化为 0–10
 * credit = (100 − 信用分) / 10
 * price  = 价格异常 5 分
 * wait   = 等待每满 12 小时 + 1
 * </pre>
 *
 * <p><b>前四项取 `item_audit` 的进队快照，不取实时值</b>：快照不随后续变化，
 * 队列顺序才不会因为又来一条举报就跳变（审核员正在处理的单子不能自己跑掉）。
 * 展示用的 `reportCount` 是实时值，两者数值可能不同，这是有意的。
 */
@Data
public class WeightPartsVO {

    /** 举报次数 × 3 */
    private Integer report;

    /** 图片风险分归一化后的 0–10 */
    private Integer risk;

    /** 卖家信用扣减分 */
    private Integer credit;

    /** 价格异常：0 或 5 */
    private Integer price;

    /** 等待补偿：FLOOR(等待小时 / 12) */
    private Integer wait;
}

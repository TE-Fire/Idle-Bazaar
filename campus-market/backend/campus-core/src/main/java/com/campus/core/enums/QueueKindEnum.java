package com.campus.core.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审核队列里一条任务的来源（`item` / `report` / `appeal`）。
 *
 * <p>它不是库里的字段——三张单据表各有一个 `kind` 语义，压成一条队列时才需要这个枚举
 * （notes/09 问题 3：三种来源压成一个接口，靠它区分）。
 *
 * <p><b>枚举名与前端 `QUEUE_KINDS` 逐字对齐</b>（`packages/shared/src/admin.ts`），
 * 且 `order` 就是队列的分组优先级：申诉整体先于举报，举报整体先于商品审核。
 * 理由：申诉是「已经处置过一次且当事人不服」，多等一天就是多一天的错误处置；
 * 举报是别人主动提出的风险；商品审核只是常规上架流程，可以最后处理。
 */
@Getter
@AllArgsConstructor
public enum QueueKindEnum {

    APPEAL("appeal", "申诉复审", 0),
    REPORT("report", "举报处理", 1),
    ITEM("item", "商品审核", 2);

    private final String code;

    private final String desc;

    /** 分组排序键，越小越靠前 */
    private final int order;

    /** 对外输出小写枚举名，与前端 `QueueKind` 同形 */
    @JsonValue
    public String value() {
        return code;
    }

    public static QueueKindEnum fromCode(String code) {
        if (code != null) {
            for (QueueKindEnum k : values()) {
                if (k.code.equalsIgnoreCase(code.trim())) {
                    return k;
                }
            }
        }
        return null;
    }
}

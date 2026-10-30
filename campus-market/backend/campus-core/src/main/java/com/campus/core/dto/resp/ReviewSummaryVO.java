package com.campus.core.dto.resp;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 某人收到的评价汇总（个人主页头部，`GET /api/user/{id}/review-summary`）。
 *
 * <p>`avg` 是字符串（比率统一用字符串下发，前端 `Number()` 再格式化）；
 * `counts` 是星级 → 条数的映射，缺的星级补 0，这样前端画五条横条时不用自己补。
 */
@Data
public class ReviewSummaryVO {

    private String avg;

    private Long total;

    /** 1–5 星条数，五档齐全（没有的填 0） */
    private Map<Integer, Long> counts;

    /** 最近 3 条，个人主页头部先露一眼，完整列表走 reviews 分页接口 */
    private List<UserReviewVO> latest;
}

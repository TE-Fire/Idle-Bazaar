package com.campus.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评价标签（notes/13 O18 规则 4）。
 *
 * <p>**不进字典表**：它跟交易场景绑死（「描述一致」「拖延」只在这件事里有意义），
 * 不是运营可配的内容；进字典反而要担心运营把标签删了导致历史评价挂着空标签。
 *
 * <p>入库存的是标签文案本身（`user_review.tags` 是 JSON 数组），
 * 校验时按 {@link #label} 匹配——改文案会导致历史评价里的旧词认不出来，所以文案定稿后不要动，
 * 要加就加新枚举项。
 */
@Getter
@AllArgsConstructor
public enum ReviewTagEnum {

    DESC_MATCH("描述一致", 1),
    FAST_DELIVERY("发货快", 1),
    GOOD_COMM("沟通顺畅", 1),
    NICE_ATTITUDE("态度好", 1),
    FAIR_PRICE("价格实惠", 1),
    DESC_DIFF("描述不符", 2),
    SLOW("拖延", 2),
    BAD_COMM("沟通差", 2),
    BAD_ATTITUDE("态度恶劣", 2),
    ITEM_PROBLEM("商品有问题", 2);

    /** 标签文案（入库值） */
    private final String label;

    /** 1 好评向 / 2 差评向，前端据此分色 */
    private final int polarity;

    public static boolean isValid(String label) {
        if (label == null) {
            return false;
        }
        for (ReviewTagEnum t : values()) {
            if (t.label.equals(label)) {
                return true;
            }
        }
        return false;
    }

    /** 给前端下发的标签表：{positive: [...], negative: [...]} */
    public static Map<String, List<Map<String, Object>>> table() {
        List<Map<String, Object>> positive = new ArrayList<>();
        List<Map<String, Object>> negative = new ArrayList<>();
        for (ReviewTagEnum t : values()) {
            Map<String, Object> item = new LinkedHashMap<>(2);
            item.put("label", t.label);
            item.put("polarity", t.polarity);
            (t.polarity == 1 ? positive : negative).add(item);
        }
        Map<String, List<Map<String, Object>>> map = new LinkedHashMap<>(2);
        map.put("positive", positive);
        map.put("negative", negative);
        return map;
    }
}

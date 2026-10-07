package com.campus.core.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 管理端全量字典（含停用项）一次聚合，对应 Redis key {@code cm:dict:admin:full}。
 *
 * <p>商品 / 审核列表的字典名回填，以及管理端字典页，都要看到<b>停用项</b>——停用的分类仍可能被
 * 历史商品引用，缺了它名称会解析成 null。所以它与 {@link DictAllVO}（只含启用项、给学生端发布页）
 * 是两套数据、两个 key，不能合并。
 *
 * <p>四个列表都含 {@code deleted = 0} 的全量，不按 {@code status} 过滤。
 */
@Data
public class DictAdminFullVO {

    private List<DictCategoryVO> categories;

    private List<DictCampusVO> campuses;

    private List<DictMeetPointVO> meetPoints;

    private List<DictCollegeVO> colleges;
}

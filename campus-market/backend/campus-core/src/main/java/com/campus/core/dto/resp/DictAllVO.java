package com.campus.core.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 五张字典一次下发（`/api/dict/all`，学生端发布页 / 筛选用）
 *
 * <p>为什么做成"一次拿全"：字典总量小（几十条）、改动极少，
 * 让发布页分五次请求去拉会拖慢首屏，也会让"分类拉到了、成色没拉到"的中间态有可能出现。
 *
 * <p>这个接口**免登录**（学生端将来用），所以只下发**启用中**的字典，
 * 停用的分类不出现在发布页的可选列表里。
 */
@Data
public class DictAllVO {

    /** 分类平铺（带 parentId），树由前端自己组装 */
    private List<DictCategoryVO> categories;

    private List<DictGradeVO> grades;

    private List<DictMeetPointVO> meetPoints;

    private List<DictCollegeVO> colleges;

    private List<DictCampusVO> campuses;
}

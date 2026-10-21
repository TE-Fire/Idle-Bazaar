package com.campus.core.dto.req;

import lombok.Data;

/**
 * 商品列表查询条件。管理端 `/admin/item/list` 与学生端 `/api/item/mine` 共用。
 *
 * <p>id 一律 String 接：前端雪花 id 本就是字符串，统一在 Service 转换，非法值报 1000。
 * `categoryId` 传一级分类 id，命中其下所有二级（传二级则精确匹配）。
 * `status` 传枚举名（ON_SALE 等，与前端 AdminItem.status 一致）。
 */
@Data
public class ItemListReq {

    /** 标题 / 卖家昵称模糊匹配 */
    private String keyword;

    /** 一级分类 id；空 = 全部 */
    private String categoryId;

    /** 商品状态枚举名；空 = 全部 */
    private String status;

    /** 校区 id；空 = 全部 */
    private String campusId;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}

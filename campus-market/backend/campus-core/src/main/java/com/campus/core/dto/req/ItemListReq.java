package com.campus.core.dto.req;

import lombok.Data;

/**
 * 商品列表查询条件（管理端 `/admin/item/list`）。
 *
 * <p>id 一律用 String 接：前端拿到的是雪花 ID 转成的字符串（tech/01 §5.3 规则 1），
 * 发回来也是字符串。用 Long 接空串会得到 null，但传 'ALL' 这种占位值会直接绑不上——
 * 统一成 String 后在 Service 里转换，非法值报 1000 而不是 400。
 *
 * <p>`categoryId` 传的是**一级分类** id，命中该一级下所有二级分类（见 notes/08 问题 8）。
 * 传二级 id 也能用——Service 会退化成精确匹配。
 *
 * <p>`status` 传枚举名（`ON_SALE` 等，与前端 `AdminItem.status` 一致），不传数字。
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

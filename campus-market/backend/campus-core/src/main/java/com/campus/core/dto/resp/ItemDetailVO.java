package com.campus.core.dto.resp;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 商品详情（对齐前端 `AdminItem` + 图片列表）。
 *
 * <p>只读：管理端不提供编辑接口——business/03 §一明确「不做替用户改价改描述」，
 * 只能强制下架，不能代编辑，避免责任不清。所以这里有 `images` 与 `description`，
 * 没有任何可提交回去的字段。
 *
 * <p>图片按 `sort` 升序，`sort = 0` 是主图（与 `item.main_image` 同一张）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ItemDetailVO extends ItemListVO {

    /** 按 sort 升序，≤9 张 */
    private List<ItemImageVO> images;
}

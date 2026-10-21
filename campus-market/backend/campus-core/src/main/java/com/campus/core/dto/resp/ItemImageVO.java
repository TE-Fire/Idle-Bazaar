package com.campus.core.dto.resp;

import lombok.Data;

/**
 * 商品图（`item_image` 一行）。详情页轮播用；带 `sort`，前端不必自己猜主图。
 */
@Data
public class ItemImageVO {

    private Long id;

    private String url;

    /** 0 为主图 */
    private Integer sort;
}

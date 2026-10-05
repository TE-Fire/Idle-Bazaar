package com.campus.core.dto.resp;

import lombok.Data;

/**
 * 商品图（`item_image` 一行）。
 *
 * <p>前端详情页轮播用。`sort` 带上是为了让前端不必自己猜哪张是主图。
 */
@Data
public class ItemImageVO {

    private Long id;

    private String url;

    /** 0 为主图 */
    private Integer sort;
}

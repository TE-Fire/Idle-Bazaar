package com.campus.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页响应体（商品 / 订单 / 用户 / 日志列表共用）。
 *
 * <p>放在 common 而不是各模块自己的 dto：分页形状是全端一致的约定，
 * 前端 AntD Table 只需要 `list` 与 `total`，`pageNum` / `pageSize` 用于回填查询条件。
 *
 * <p>字段名用 `list` 而不是 `records`：跟 MyBatis-Plus 的 {@code Page#getRecords()} 划清界限——
 * 那是持久层的对象，直接序列化出去会把 `orders`、`optimizeCountSql` 之类的字段一起带出来。
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> list;

    /** 总条数（不是总页数） */
    private long total;

    private long pageNum;

    private long pageSize;

    public static <T> PageResult<T> of(List<T> list, long total, long pageNum, long pageSize) {
        PageResult<T> r = new PageResult<>();
        r.setList(list);
        r.setTotal(total);
        r.setPageNum(pageNum);
        r.setPageSize(pageSize);
        return r;
    }
}

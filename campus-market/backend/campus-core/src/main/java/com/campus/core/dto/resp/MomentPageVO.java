package com.campus.core.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 动态分页（notes/22 S4，广场 / 个人主页共用）。
 */
@Data
public class MomentPageVO {

    private List<MomentVO> list;

    private long total;

    private long pageNum;

    private long pageSize;
}

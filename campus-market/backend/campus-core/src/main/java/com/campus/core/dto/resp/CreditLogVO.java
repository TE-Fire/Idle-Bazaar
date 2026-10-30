package com.campus.core.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 信用分流水（管理端用户详情展示「分数为什么变了」） */
@Data
public class CreditLogVO {

    private Long id;

    private Integer changeValue;

    private Integer beforeScore;

    private Integer afterScore;

    private String reasonType;

    private String remark;

    private LocalDateTime createTime;
}

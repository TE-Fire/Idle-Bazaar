package com.campus.core.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 动态详情（notes/22 S4）：动态正文 + 评论列表（正序）。
 */
@Data
public class MomentDetailVO {

    private MomentVO moment;

    private List<MomentCommentVO> comments;
}

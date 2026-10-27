package com.campus.core.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 关注 / 取关请求体（notes/22 S1） */
@Data
public class FollowReq {

    /** 被关注者 id；不能为空，也不能是自己（服务层兜底） */
    @NotNull(message = "关注对象不能为空")
    private Long followeeId;
}

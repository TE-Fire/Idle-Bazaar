package com.campus.core.dto.req;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 认证申请裁决：通过不需要原因，驳回必须写（原样展示给学生） */
@Data
public class UserAuthJudgeReq {

    @NotNull(message = "请选择通过或驳回")
    private Boolean pass;

    @Size(max = 255, message = "原因不超过 255 个字符")
    private String rejectReason;
}

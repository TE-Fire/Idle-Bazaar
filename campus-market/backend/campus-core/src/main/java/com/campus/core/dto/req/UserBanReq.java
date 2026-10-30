package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/** 封禁学生账号：原因必填（会展示在记录里），到期时间留空表示永久 */
@Data
public class UserBanReq {

    @NotBlank(message = "请填写封禁原因")
    @Size(max = 255, message = "原因不超过 255 个字符")
    private String reason;

    /** 为空 = 永久封禁 */
    private LocalDateTime banUntil;
}

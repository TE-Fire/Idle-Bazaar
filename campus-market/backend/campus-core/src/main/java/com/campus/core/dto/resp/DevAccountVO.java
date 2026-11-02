package com.campus.core.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 联调用的账号项（小程序「测试账号」面板）。
 *
 * <p>只在验证码回显开启（{@code campus.user.*.echo=true}）时下发，上线关掉回显即
 * 一并失效——手机号是敏感字段，不能有常开的批量出口。
 */
@Data
public class DevAccountVO {

    private Long id;

    private String nickname;

    private String phone;

    private String avatarUrl;

    /** 0未认证 1审核中 2已认证 3未通过 */
    private Integer authStatus;

    private Integer creditScore;

    private LocalDateTime createTime;
}

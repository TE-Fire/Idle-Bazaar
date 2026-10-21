package com.campus.core.dto.resp;

import lombok.Data;

/** 登录响应（发码接口的响应另见 {@link SmsCodeVO}） */
@Data
public class UserLoginVO {

    private Long userId;

    private String nickname;

    private String avatarUrl;

    /** 认证：0未认证 1审核中 2已认证 3未通过 */
    private Integer authStatus;

    private Integer creditScore;

    /** 与请求头同名的令牌字段（satoken-user），前端原样带回 */
    private String tokenName;

    /** 令牌值；登出后失效 */
    private String tokenValue;
}

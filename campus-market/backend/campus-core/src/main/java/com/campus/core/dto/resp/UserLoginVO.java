package com.campus.core.dto.resp;

import lombok.Data;

/** 登录 / 当前用户响应（发码接口的响应另见 {@link VerifyCodeVO}） */
@Data
public class UserLoginVO {

    private Long userId;

    private String nickname;

    private String avatarUrl;

    /** 认证：0未认证 1审核中 2已认证 3未通过 */
    private Integer authStatus;

    private Integer creditScore;

    /** 手机号（本人可见，用于账号展示与找回入口预填） */
    private String phone;

    /** QQ 邮箱（同上） */
    private String email;

    /** 是否已设密码：为 false 时前端在账号安全处提示「设置密码」 */
    private Boolean hasPassword;

    /** 与请求头同名的令牌字段（satoken-user），前端原样带回 */
    private String tokenName;

    /** 令牌值；登出后失效 */
    private String tokenValue;
}

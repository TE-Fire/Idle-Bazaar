package com.campus.core.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/** 管理端学生列表行：手机号脱敏——列表会被截图、会投屏，详情才给全量 */
@Data
public class AppUserVO {

    private Long id;

    private String nickname;

    private String avatarUrl;

    /** 138****1234；无手机号为空 */
    private String phoneMasked;

    private Long collegeId;

    private Long campusId;

    /** 0未认证 1审核中 2已认证 3未通过 */
    private Integer authStatus;

    /** 0正常 1警告 2封禁 */
    private Integer status;

    private Integer creditScore;

    private LocalDateTime banUntil;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createTime;
}

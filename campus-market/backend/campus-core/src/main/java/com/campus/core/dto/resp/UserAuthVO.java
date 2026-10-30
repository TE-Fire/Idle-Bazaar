package com.campus.core.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/** 学号认证申请视图（学生端回显驳回原因、管理端审核队列共用） */
@Data
public class UserAuthVO {

    private Long id;

    private Long userId;

    /** 申请人昵称与脱敏手机号：仅管理端待审队列填充，学生端为空 */
    private String nickname;

    private String phoneMasked;

    private String realName;

    private String studentNo;

    private Long collegeId;

    private String credentialUrl;

    /** 1审核中 2通过 3未通过 */
    private Integer status;

    private String rejectReason;

    private LocalDateTime handleTime;

    private LocalDateTime createTime;
}

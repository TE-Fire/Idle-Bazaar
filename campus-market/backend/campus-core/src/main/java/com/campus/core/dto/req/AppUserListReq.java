package com.campus.core.dto.req;

import lombok.Data;

/** 管理端学生列表查询条件（A4 用户管理） */
@Data
public class AppUserListReq {

    /** 昵称 / 手机号 / 学号，模糊匹配 */
    private String keyword;

    /** 认证态：0未认证 1审核中 2已认证 3未通过 */
    private Integer authStatus;

    /** 账号态：0正常 1警告 2封禁 */
    private Integer status;

    /** 学院筛选 */
    private Long collegeId;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}

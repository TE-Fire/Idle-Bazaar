package com.campus.core.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 管理端学生详情：资料 + 认证历史 + 信用流水 + 商品/订单计数 */
@Data
public class AppUserDetailVO {

    private Long id;

    private String nickname;

    private String avatarUrl;

    /** 详情给全量：列表脱敏是为了防截图，详情本身是「已进入处置流程」的场景 */
    private String phone;

    private String realName;

    private String studentNo;

    private Long collegeId;

    private Long campusId;

    private Integer authStatus;

    private Integer status;

    private Integer creditScore;

    private String banReason;

    private LocalDateTime banUntil;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createTime;

    /** 认证申请历史，时间倒序（含已驳回的） */
    private List<UserAuthVO> authList;

    /** 最近 20 条信用流水，时间倒序 */
    private List<CreditLogVO> creditLogs;

    private Long itemCount;

    private Long orderCount;
}

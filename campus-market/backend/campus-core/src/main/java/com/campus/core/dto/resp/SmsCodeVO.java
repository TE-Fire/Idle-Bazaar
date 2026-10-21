package com.campus.core.dto.resp;

import lombok.Data;

/** 发短信验证码的响应 */
@Data
public class SmsCodeVO {

    /** 距下次可重发的秒数（60），前端做倒计时 */
    private int resendAfter;

    /** 演示期回显的验证码；`campus.user.sms.echo=false` 时为 null */
    private String devCode;
}

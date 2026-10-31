package com.campus.core.dto.resp;

import lombok.Data;

/** 验证码下发响应（短信 / 邮箱共用） */
@Data
public class VerifyCodeVO {

    /** 距下次可重发的秒数（60），前端做倒计时 */
    private int resendAfter;

    /** 1 短信 2 QQ 邮箱：前端据此选提示文案，不靠账号形态再猜一遍 */
    private int channel;

    /** 演示期回显的验证码；关闭回显开关后为 null */
    private String devCode;
}

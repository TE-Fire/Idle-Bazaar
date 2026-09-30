package com.campus.core.dto.resp;

import lombok.Data;

/**
 * 图形验证码下发结果。
 *
 * <p>`key` 与图片**分开返回**而不是塞进图片 URL：校验时前端只回传 key + 用户输入，
 * 验证码文本本身永远不过前端（过前端就等于把答案告诉刷题的人）。
 */
@Data
public class CaptchaVO {

    /** 验证码 key，提交表单时原样带回 */
    private String captchaKey;

    /** PNG 图片 base64（不含 data: 前缀，前端自行拼接） */
    private String captchaImage;

    /** 开关关闭时为 true：前端此时不显示验证码输入框 */
    private Boolean disabled;

    public CaptchaVO() {
    }

    public CaptchaVO(String captchaKey, String captchaImage) {
        this.captchaKey = captchaKey;
        this.captchaImage = captchaImage;
    }
}

package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 学号认证申请（notes/20 D5） */
@Data
public class UserAuthApplyReq {

    @NotBlank(message = "请填写真实姓名")
    @Size(max = 32, message = "姓名不超过 32 个字符")
    private String realName;

    @NotBlank(message = "请填写学号")
    @Size(max = 32, message = "学号不超过 32 个字符")
    private String studentNo;

    @NotNull(message = "请选择学院")
    private Long collegeId;

    /** 一卡通或校园邮箱截图，走既有票据上传拿到 URL 后提交 */
    @Size(max = 255, message = "凭证地址过长")
    private String credentialUrl;
}

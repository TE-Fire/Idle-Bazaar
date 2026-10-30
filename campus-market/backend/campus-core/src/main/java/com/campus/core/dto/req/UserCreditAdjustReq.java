package com.campus.core.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 人工调整信用分：单次幅度 ±20 以内，理由必填（写进 credit_log 供追溯） */
@Data
public class UserCreditAdjustReq {

    @NotNull(message = "请填写调整幅度")
    @Min(value = -20, message = "单次最多下调 20 分")
    @Max(value = 20, message = "单次最多上调 20 分")
    private Integer delta;

    @NotBlank(message = "请填写调整理由")
    @Size(max = 255, message = "理由不超过 255 个字符")
    private String reason;
}

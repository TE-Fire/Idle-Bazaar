package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 平台公告发布入参（`POST /admin/notice`，notes/22 S12）。
 *
 * <p>`title` 可空：运营常发一两句话的提醒，强制起标题只会得到「通知」这种空标题。
 * `campusId` 为空 = 全员，填了只发该校区（**不做**按学院/信用分定向，business/03 F-A12）。
 */
@Data
public class NoticePublishReq {

    @Size(max = 60, message = "标题不超过 60 个字符")
    private String title;

    @NotBlank(message = "公告正文不能为空")
    @Size(max = 2000, message = "公告正文不超过 2000 个字符")
    private String content;

    /** 接收校区；空 = 全部校区 */
    private Long campusId;
}

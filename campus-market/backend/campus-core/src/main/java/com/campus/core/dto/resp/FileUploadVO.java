package com.campus.core.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 服务端转发上传的结果。与票据里的 accessUrl 一致（同 key 同桶），
 * 单独返回是让前端上传完成后能就地校验，不必信票据。
 */
@Data
@AllArgsConstructor
public class FileUploadVO {

    private String objectKey;

    private String url;
}

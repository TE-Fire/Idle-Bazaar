package com.campus.support.storage.upload;

import lombok.Data;

import java.util.Map;

/**
 * 上传票据：策略签发的「怎么传」指令，前端按 mode 分流执行，成功后直接用 accessUrl 提交业务。
 *
 * <p>PROXY：uploadUrl 指向服务端自己的 multipart 端点，formFields 是要附带的表单字段；
 * DIRECT：uploadUrl 是 MinIO PUT 签名地址，headers 为 PUT 必须携带的头。
 * accessUrl 两种模式一致——桶公开读直拼，对象 key 签票时已定，故上传前就能给出。
 */
@Data
public class UploadTicket {

    /** 策略标识，前端按它选择上传动作 */
    private UploadMode mode;

    /** 对象名（item/{userId}/{uuid}.{ext}），proxy 端点按它落桶 */
    private String objectKey;

    /** 实际发起上传的地址（proxy 是服务端相对路径，direct 是 MinIO 绝对地址） */
    private String uploadUrl;

    /** POST（multipart）/ PUT（直传） */
    private String httpMethod;

    /** proxy 的表单附带字段（objectKey）；direct 为空 */
    private Map<String, String> formFields;

    /** direct 的 PUT 头；proxy 为空 */
    private Map<String, String> headers;

    /** 公开读 URL：提交商品时进 images 的就是它 */
    private String accessUrl;

    /** 签名有效期秒数；proxy 为 0（不过期，服务端收口） */
    private int expiresIn;
}

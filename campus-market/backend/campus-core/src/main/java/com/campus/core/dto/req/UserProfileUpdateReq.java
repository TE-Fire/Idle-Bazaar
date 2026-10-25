package com.campus.core.dto.req;

import lombok.Data;

/**
 * 学生端资料编辑：昵称与头像，局部更新。
 *
 * <p>两个字段都可选，至少传一个（都不传 = 参数错误）；字段为 null 表示不修改，
 * 传空串按非法值处理而非「不修改」——否则前端用空串表达清空时会静默丢操作。
 * 头像只收上传票据通道返回的 URL，不接收文件流。
 */
@Data
public class UserProfileUpdateReq {

    /** 昵称：去首尾空格后 1–64 字符 */
    private String nickname;

    /** 头像 URL：上传通道返回的 accessUrl，≤255 字符 */
    private String avatarUrl;
}

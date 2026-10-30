package com.campus.core.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感词一行（管理端词库维护，notes/22 §五）。
 *
 * <p>`id` 出字符串：雪花 ID 超出 JS 安全整数范围，前端拿 number 会精度丢失。
 */
@Data
public class SensitiveWordVO {

    private String id;

    private String word;

    /** 1 高危（发布直接拒）2 低危（替换成等长 `*`） */
    private Integer level;

    /** 1 启用 0 停用 */
    private Integer status;

    private LocalDateTime createTime;
}

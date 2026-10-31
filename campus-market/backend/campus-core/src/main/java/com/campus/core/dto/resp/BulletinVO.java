package com.campus.core.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页公告播报条（notes/22 S12）。
 *
 * <p>只带播报需要的三样：标题、正文、时间。没有已读态——公告是给所有人看的知会，
 * 不该因为读过就从播报条消失。
 */
@Data
public class BulletinVO {

    private Long id;
    private String title;
    private String content;
    private LocalDateTime createTime;
}

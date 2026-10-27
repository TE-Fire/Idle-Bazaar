package com.campus.core.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 关注 / 粉丝分页列表（notes/22 S2）。
 *
 * <p>`total` 是当前 `type` 的总数（关注数或粉丝数），`followingCount` / `fansCount`
 * 是目标用户两项计数，个人主页顶部直接取用，免去再发一次统计请求。
 */
@Data
public class FollowListVO {

    private List<FollowUserVO> list;

    private long total;

    private long pageNum;

    private long pageSize;

    /** 目标用户的关注数 */
    private long followingCount;

    /** 目标用户的粉丝数 */
    private long fansCount;
}

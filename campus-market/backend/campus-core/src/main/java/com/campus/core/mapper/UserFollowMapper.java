package com.campus.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.core.entity.UserFollow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserFollowMapper extends BaseMapper<UserFollow> {

    /**
     * 复活一条软删行：取关后再关注复用原行，避免同一对关系堆多行墓碑。
     * create_time 一并刷新成当前时间——它是「关系建立时间」，重新关注应按新关系计时，
     * 否则 Redis 重建后列表顺序会退回旧时间。加 LIMIT 1 是为了「历史脏数据里若存在
     * 多条墓碑」时只复活一条，不撞唯一键。
     */
    @Update("UPDATE user_follow SET deleted = 0, create_time = NOW() "
            + "WHERE follower_id = #{followerId} AND followee_id = #{followeeId} AND deleted > 0 LIMIT 1")
    int revive(@Param("followerId") Long followerId,
               @Param("followeeId") Long followeeId);

    /** 取关：只动活跃行；影响 0 行说明本就没关注（幂等） */
    @Update("UPDATE user_follow SET deleted = #{deleted} "
            + "WHERE follower_id = #{followerId} AND followee_id = #{followeeId} AND deleted = 0")
    int softDelete(@Param("followerId") Long followerId,
                   @Param("followeeId") Long followeeId,
                   @Param("deleted") Long deleted);
}

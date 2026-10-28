package com.campus.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.core.entity.MomentLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MomentLikeMapper extends BaseMapper<MomentLike> {

    /** 复活一条软删行：取消赞后再赞复用原行，create_time 一并刷新为本次点赞时间 */
    @Update("UPDATE moment_like SET deleted = 0, create_time = NOW() "
            + "WHERE moment_id = #{momentId} AND user_id = #{userId} AND deleted > 0 LIMIT 1")
    int revive(@Param("momentId") Long momentId, @Param("userId") Long userId);

    /** 取消赞：只动活跃行；影响 0 行说明本就没赞（幂等） */
    @Update("UPDATE moment_like SET deleted = #{deleted} "
            + "WHERE moment_id = #{momentId} AND user_id = #{userId} AND deleted = 0")
    int softDelete(@Param("momentId") Long momentId,
                   @Param("userId") Long userId,
                   @Param("deleted") Long deleted);
}

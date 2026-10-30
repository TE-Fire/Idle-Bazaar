package com.campus.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.core.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {

    /**
     * 复活一条软删行：取消后再收藏复用原行。
     * create_time 刷新成当前时间——收藏列表按它倒序，不刷新会让刚收的东西沉到列表底部。
     * LIMIT 1 兜住「历史脏数据里有多条墓碑」的情况，避免撞唯一键。
     */
    @Update("UPDATE favorite SET deleted = 0, create_time = NOW() "
            + "WHERE user_id = #{userId} AND item_id = #{itemId} AND deleted > 0 LIMIT 1")
    int revive(@Param("userId") Long userId, @Param("itemId") Long itemId);

    /** 取消收藏：只动活跃行；影响 0 行说明本就没收藏（幂等） */
    @Update("UPDATE favorite SET deleted = #{deleted} "
            + "WHERE user_id = #{userId} AND item_id = #{itemId} AND deleted = 0")
    int softDelete(@Param("userId") Long userId,
                   @Param("itemId") Long itemId,
                   @Param("deleted") Long deleted);
}

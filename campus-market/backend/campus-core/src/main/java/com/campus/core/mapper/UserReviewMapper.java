package com.campus.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.core.entity.UserReview;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserReviewMapper extends BaseMapper<UserReview> {

    /**
     * 某人收到的各星级条数。汇总不做冗余列（notes/13 O18 规则 7）：
     * 评价量在百级，一次 `GROUP BY` 比维护一张统计表省心。
     */
    @Select("SELECT rating AS star, COUNT(*) AS num FROM user_review "
            + "WHERE target_id = #{targetId} AND deleted = 0 GROUP BY rating")
    List<RatingCount> countByRating(@Param("targetId") Long targetId);

    /** 星级分布一行：`star` 是星数（1–5），`num` 是条数 */
    @Data
    class RatingCount {
        private Integer star;
        private Long num;
    }
}

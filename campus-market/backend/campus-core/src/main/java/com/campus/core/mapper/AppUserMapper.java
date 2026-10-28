package com.campus.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.core.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {

    /** 登录用：按手机号查未逻辑删除的学生用户 */
    @Select("SELECT * FROM app_user WHERE phone = #{phone} AND deleted = 0 LIMIT 1")
    AppUser selectByPhone(@Param("phone") String phone);

    /** 信用分变动前读当前值并锁行（须在事务内调用），保证流水前后值准确 */
    @Select("SELECT credit_score FROM app_user WHERE id = #{userId} AND deleted = 0 FOR UPDATE")
    Integer selectCreditForUpdate(@Param("userId") long userId);

    @Update("UPDATE app_user SET credit_score = #{score} WHERE id = #{userId} AND deleted = 0")
    int updateCredit(@Param("userId") long userId, @Param("score") int score);
}

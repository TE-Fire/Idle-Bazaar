package com.campus.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.core.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {

    /** 登录用：按手机号查未逻辑删除的学生用户 */
    @Select("SELECT * FROM app_user WHERE phone = #{phone} AND deleted = 0 LIMIT 1")
    AppUser selectByPhone(@Param("phone") String phone);
}

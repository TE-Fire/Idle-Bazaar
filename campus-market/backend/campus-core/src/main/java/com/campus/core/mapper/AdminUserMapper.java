package com.campus.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.core.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {

    /**
     * 登录用：只查未逻辑删除的账号。
     * 唯一索引 uk_username 带 deleted，所以必须显式带 deleted=0。
     */
    @Select("SELECT * FROM admin_user WHERE username = #{username} AND deleted = 0 LIMIT 1")
    AdminUser selectByUsername(@Param("username") String username);

    @Select("SELECT COUNT(1) FROM admin_user WHERE deleted = 0")
    long countAlive();
}

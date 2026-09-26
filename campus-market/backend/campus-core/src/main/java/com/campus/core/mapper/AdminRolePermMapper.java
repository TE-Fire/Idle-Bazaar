package com.campus.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.core.entity.AdminRolePerm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminRolePermMapper extends BaseMapper<AdminRolePerm> {

    /** Sa-Token 鉴权时按角色取权限点，走 uk_role_perm */
    @Select("SELECT * FROM admin_role_perm WHERE role_id = #{roleId}")
    List<AdminRolePerm> selectByRoleId(@Param("roleId") Long roleId);

    @Select("SELECT perm_code FROM admin_role_perm WHERE role_id = #{roleId}")
    List<String> selectPermCodes(@Param("roleId") Long roleId);
}

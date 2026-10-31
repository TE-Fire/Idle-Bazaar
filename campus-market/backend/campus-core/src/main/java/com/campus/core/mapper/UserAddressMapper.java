package com.campus.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.core.entity.UserAddress;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户地址（`user_address`）Mapper。基础 CRUD 由 MyBatis-Plus 提供；
 * 软删与「同类默认地址唯一」等逻辑在 {@code UserAddressService} 里用条件更新保证。
 */
@Mapper
public interface UserAddressMapper extends BaseMapper<UserAddress> {
}

package com.campus.core.satoken;

import cn.dev33.satoken.stp.StpInterface;
import com.campus.core.entity.AdminRole;
import com.campus.core.entity.AdminUser;
import com.campus.core.mapper.AdminRoleMapper;
import com.campus.core.mapper.AdminRolePermMapper;
import com.campus.core.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 的权限/角色数据源：从数据库读，不写死在代码里（tech/01 8.2）。
 *
 * <p>这样「审核员没有 user.ban」这类约束就落在数据层：
 * admin_role_perm 里少插一行即可，改权限不用改代码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final AdminUserMapper adminUserMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminRolePermMapper adminRolePermMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        if (!StpAdminUtil.TYPE.equals(loginType)) {
            return Collections.emptyList();
        }
        AdminUser admin = adminUserMapper.selectById(String.valueOf(loginId));
        if (admin == null || admin.getRoleId() == null) {
            return Collections.emptyList();
        }
        // 每次鉴权都查库：管理员数量是个位数，代价可忽略；
        // 若将来压力大，在这里加一层 Redis 缓存（角色变更时主动失效）
        return adminRolePermMapper.selectPermCodes(admin.getRoleId());
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (!StpAdminUtil.TYPE.equals(loginType)) {
            return Collections.emptyList();
        }
        AdminUser admin = adminUserMapper.selectById(String.valueOf(loginId));
        if (admin == null || admin.getRoleId() == null) {
            return Collections.emptyList();
        }
        AdminRole role = adminRoleMapper.selectById(admin.getRoleId());
        return List.of(role == null ? "UNKNOWN" : role.getRoleCode());
    }
}

package com.campus.core.service;

import com.campus.common.constant.PermCodes;
import com.campus.common.exception.BizException;
import com.campus.common.exception.ErrorCode;
import com.campus.core.entity.AdminRole;
import com.campus.core.entity.AdminUser;
import com.campus.core.mapper.AdminRoleMapper;
import com.campus.core.mapper.AdminRolePermMapper;
import com.campus.core.mapper.AdminUserMapper;
import com.campus.core.satoken.MultiAccountConfig;
import com.campus.core.satoken.StpAdminUtil;
import com.campus.core.service.dto.AdminLoginReq;
import com.campus.core.service.dto.AdminLoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端登录（business/03 F-A1）。
 *
 * <p>密码校验由本服务自己做：Sa-Token 只管会话，不管密码。
 * 失败 5 次锁定 10 分钟，落 admin_user.fail_count / lock_until。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private static final int MAX_FAIL = 5;
    private static final int LOCK_MINUTES = 10;
    private static final String CAPTCHA_KEY_PREFIX = "cm:captcha:";

    private final AdminUserMapper adminUserMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminRolePermMapper adminRolePermMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${campus.captcha.enabled:true}")
    private boolean captchaEnabled;

    @Transactional(rollbackFor = Exception.class)
    public AdminLoginVO login(AdminLoginReq req) {
        checkCaptcha(req);

        AdminUser admin = adminUserMapper.selectByUsername(req.getUsername());
        if (admin == null || admin.getStatus() == null || admin.getStatus() == 0) {
            throw BizException.of(ErrorCode.ADMIN_BAD_CREDENTIAL);
        }

        // 锁定期内直接拒绝，不再比对密码（避免爆破）
        if (admin.getLockUntil() != null && LocalDateTime.now().isBefore(admin.getLockUntil())) {
            throw BizException.of(ErrorCode.ADMIN_ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(req.getPassword(), admin.getPasswordHash())) {
            int fails = (admin.getFailCount() == null ? 0 : admin.getFailCount()) + 1;
            AdminUser update = new AdminUser();
            update.setId(admin.getId());
            update.setFailCount(fails);
            if (fails >= MAX_FAIL) {
                update.setLockUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                update.setFailCount(0);
                log.warn("[admin] 账号 {} 连续失败 {} 次，锁定 {} 分钟", admin.getUsername(), fails, LOCK_MINUTES);
            }
            adminUserMapper.updateById(update);
            throw BizException.of(ErrorCode.ADMIN_BAD_CREDENTIAL);
        }

        // 登录成功：清零失败计数、写最近登录时间
        AdminUser update = new AdminUser();
        update.setId(admin.getId());
        update.setFailCount(0);
        update.setLockUntil(null);
        update.setLastLoginAt(LocalDateTime.now());
        adminUserMapper.updateById(update);

        StpAdminUtil.login(admin.getId());

        AdminRole role = adminRoleMapper.selectById(admin.getRoleId());
        List<String> permCodes = adminRolePermMapper.selectPermCodes(admin.getRoleId());

        AdminLoginVO vo = new AdminLoginVO();
        vo.setAdminId(admin.getId());
        vo.setRealName(admin.getRealName());
        vo.setRoleCode(role == null ? "UNKNOWN" : role.getRoleCode());
        vo.setTokenName(MultiAccountConfig.ADMIN_TOKEN_NAME);
        vo.setTokenValue(StpAdminUtil.getTokenValue());
        vo.setPermCodes(permCodes);
        return vo;
    }

    /** 图形验证码（A1 需求）；开关关闭时跳过，便于本地联调 */
    private void checkCaptcha(AdminLoginReq req) {
        if (!captchaEnabled) {
            return;
        }
        if (!StringUtils.hasText(req.getCaptchaKey()) || !StringUtils.hasText(req.getCaptchaCode())) {
            throw BizException.of(ErrorCode.PARAM_ERROR);
        }
        String cached = stringRedisTemplate.opsForValue().get(CAPTCHA_KEY_PREFIX + req.getCaptchaKey());
        if (cached == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "验证码已失效，请刷新");
        }
        // 一次性：校验通过立即删除
        stringRedisTemplate.delete(CAPTCHA_KEY_PREFIX + req.getCaptchaKey());
        if (!cached.equalsIgnoreCase(req.getCaptchaCode())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "验证码不正确");
        }
    }

    /** 演示用：判断当前登录人是否持有某权限点（实际校验走注解） */
    public boolean hasPermission(String permCode) {
        return StpAdminUtil.hasPermission(permCode);
    }

    /** 权限点清单，供管理端 A9 矩阵页对照（与 PermCodes.ALL 同源） */
    public List<String> allPermCodes() {
        return List.of(PermCodes.ALL);
    }
}

package com.campus.core.service;

import com.campus.common.asserts.BizAssert;
import com.campus.common.constant.PermCodes;
import com.campus.common.exception.CommonErrorCode;
import com.campus.core.entity.AdminRole;
import com.campus.core.entity.AdminUser;
import com.campus.core.enums.AdminStatusEnum;
import com.campus.core.enums.LoginTypeEnum;
import com.campus.core.enums.MailCodeSceneEnum;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.mapper.AdminRoleMapper;
import com.campus.core.mapper.AdminRolePermMapper;
import com.campus.core.mapper.AdminUserMapper;
import com.campus.core.redis.AdminRedisKeys;
import com.campus.core.satoken.MultiAccountConfig;
import com.campus.core.satoken.StpAdminUtil;

import com.campus.core.dto.req.AdminLoginReq;
import com.campus.core.dto.resp.AdminLoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 管理端登录（business/03 F-A1）。密码校验自己做：Sa-Token 只管会话，不管密码。
 * 失败 5 次锁定 10 分钟，以 `fail_count / lock_until` 为准，Redis 锁标记只是查库前的快路径。
 *
 * <p>两种登录方式合在一个方法：只有「按哪个字段查库」和「凭据怎么校验」不同，
 * 分开写会让「邮箱登录忘了写 last_login_at」这类遗漏有机可乘。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private static final int MAX_FAIL = 5;
    private static final int LOCK_MINUTES = 10;

    private final AdminUserMapper adminUserMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminRolePermMapper adminRolePermMapper;
    private final CaptchaService captchaService;
    private final StringRedisTemplate stringRedisTemplate;
    /** 只为复用 `verifyMailCode`（邮箱验证码的取-比对-删除），登录本身不依赖注册/找回流程 */
    private final AdminAccountService adminAccountService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /*
     * 不加 @Transactional：事务内 throw 会把失败分支的 fail_count 写入一并回滚，
     * 「失败 5 次锁定」形同虚设（2026-09-30 实测踩过）。单条 UPDATE 本身原子。
     */
    public AdminLoginVO login(AdminLoginReq req) {
        captchaService.verify(req.getCaptchaKey(), req.getCaptchaCode());

        LoginTypeEnum type = LoginTypeEnum.fromCodeOrNull(req.getType());
        BizAssert.notNull(type, CommonErrorCode.PARAM_ERROR);

        String account = req.getUsernameOrEmail().trim();

        // 快路径只对用户名登录有效：锁 key 是 cm:admin:lock:{username}，邮箱登录拼不出同一个。
        // 邮箱登录由下面 DB 的 lock_until 兜底，不漏判
        if (type == LoginTypeEnum.BY_USERNAME) {
            BizAssert.isTrue(!isLocked(account), UserErrorCode.ADMIN_ACCOUNT_LOCKED);
        }

        // switch 表达式强制穷尽：将来加第三种登录方式会编译失败，不会静默漏分支
        AdminUser admin = switch (type) {
            case BY_USERNAME -> adminUserMapper.selectByUsername(account);
            case BY_EMAIL -> adminUserMapper.selectByEmail(account.toLowerCase(Locale.ROOT)); // 库里存的是小写
        };
        BizAssert.notNull(admin, UserErrorCode.ADMIN_BAD_CREDENTIAL);
        BizAssert.notNull(admin.getStatus(), UserErrorCode.ADMIN_BAD_CREDENTIAL);

        AdminStatusEnum status = AdminStatusEnum.fromCode(admin.getStatus());
        BizAssert.isTrue(!status.isDisabled(), UserErrorCode.ADMIN_BAD_CREDENTIAL);
        // 待分配角色单独报 4006：提示成密码错误会让人以为注册失败（business/03 已决）
        BizAssert.isTrue(!status.isPendingRole(), UserErrorCode.ADMIN_PENDING_ROLE);

        // 锁定期内不再比对凭据，避免爆破；这里兜住「Redis 被清空 / 邮箱登录」的情况
        BizAssert.isTrue(admin.getLockUntil() == null || LocalDateTime.now().isAfter(admin.getLockUntil()),
                UserErrorCode.ADMIN_ACCOUNT_LOCKED);

        String credential = req.getPasswordOrCode();

        switch (type) {
            case BY_USERNAME -> {
                if (!passwordEncoder.matches(credential, admin.getPasswordHash())) {
                    int fails = (admin.getFailCount() == null ? 0 : admin.getFailCount()) + 1;
                    AdminUser update = new AdminUser()
                            .setId(admin.getId())
                            .setFailCount(fails >= MAX_FAIL ? 0 : fails);
                    if (fails >= MAX_FAIL) {
                        update.setLockUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                        // 用库里的 username 拼 key：入参大小写可能不同，拼错就白写了
                        markLocked(admin.getUsername());
                        log.warn("[admin] 账号 {} 连续失败 {} 次，锁定 {} 分钟",
                                admin.getUsername(), fails, LOCK_MINUTES);
                    }
                    adminUserMapper.updateById(update);
                    throw UserErrorCode.ADMIN_BAD_CREDENTIAL.ex();
                }
            }
            case BY_EMAIL -> {
                // 比对成功才删码，与注册 / 找回密码同一套
                adminAccountService.verifyMailCode(
                        account.toLowerCase(Locale.ROOT), MailCodeSceneEnum.LOGIN, credential);
                // ★ 不计 fail_count：否则拿一个已知邮箱乱猜 5 次就能把该管理员锁 10 分钟（DoS）。
                //   邮箱登录的防爆破靠提交时的图形验证码，以及验证码自身的错误次数上限（待加）
            }
            default -> throw new IllegalStateException("未处理的登录方式：" + type);
        }

        // 登录成功：清零失败计数、写最近登录时间
        AdminUser update = new AdminUser()
                .setId(admin.getId())
                .setFailCount(0)
                .setLockUntil(null)
                .setLastLoginAt(LocalDateTime.now());
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


    /* ── 登录锁定：Redis 快路径 + DB 兜底 ───────────────────── */

    /**
     * 账号是否在锁定窗口内。
     *
     * <p>Redis 只是**快路径**，不是唯一凭据：连不上或数据被清空时返回 false，
     * 由下面 DB 的 `lock_until` 兜底。反过来（异常时当作已锁定）会让 Redis 一抖
     * 全体登不进来——DB 还在，"多查一次库"远比"谁都登不了"便宜。
     */
    private boolean isLocked(String username) {
        try {
            return Boolean.TRUE.equals(
                    stringRedisTemplate.hasKey(AdminRedisKeys.buildLockKey(username)));
        } catch (Exception e) {
            log.warn("[admin] 查询登录锁定态失败，降级为查库判断：{}", username, e);
            return false;
        }
    }

    /**
     * 写锁定标记，TTL 与 DB 的 `lock_until` 用同一时长，两侧同时失效。
     *
     * <p>写失败只记日志：此时 DB 的 `lock_until` 已生效，防爆破不受影响。
     */
    private void markLocked(String username) {
        try {
            stringRedisTemplate.opsForValue()
                    .set(AdminRedisKeys.buildLockKey(username), "1", Duration.ofMinutes(LOCK_MINUTES));
        } catch (Exception e) {
            log.warn("[admin] 写登录锁定标记失败，仅靠 DB 兜底：{}", username, e);
        }
    }

    /** 权限点清单，供管理端 A9 矩阵页对照（与 PermCodes.ALL 同源） */
    public List<String> allPermCodes() {
        return List.of(PermCodes.ALL);
    }

}

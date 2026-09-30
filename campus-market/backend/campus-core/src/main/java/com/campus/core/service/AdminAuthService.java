package com.campus.core.service;

import com.campus.common.asserts.BizAssert;
import com.campus.common.constant.PermCodes;
import com.campus.core.entity.AdminRole;
import com.campus.core.entity.AdminUser;
import com.campus.core.enums.AdminStatus;
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

/**
 * 管理端登录（business/03 F-A1）。
 *
 * <p>密码校验由本服务自己做：Sa-Token 只管会话，不管密码。
 * 失败 5 次锁定 10 分钟，以 `admin_user.fail_count / lock_until` 为准，
 * Redis 的锁定标记（`cm:admin:lock:{username}`）只是查库前的快路径。
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

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /*
     * 故意不加 @Transactional：失败分支要在抛 BizException 之前把 fail_count 落库，
     * 而 @Transactional(rollbackFor = Exception.class) 会把这个写入一并回滚——
     * 结果是失败次数永远累加不到 MAX_FAIL，「失败 5 次锁定 10 分钟」形同虚设
     * （2026-09-30 实测发现）。失败与成功分支各自只更新 admin_user 一行，
     * 单条 UPDATE 本身原子，不需要事务。
     */
    public AdminLoginVO login(AdminLoginReq req) {
        // 与注册 / 找回页共用同一套验证码（key 前缀与「一次性」语义都在 CaptchaService）
        captchaService.verify(req.getCaptchaKey(), req.getCaptchaCode());

        String username = req.getUsername();

        // 锁定判断放在**查库之前**：命中就直接拒绝，省掉这一次 DB 查询——
        // 账号被锁期间往往正被反复试探，省下的正是最密集的那些请求。
        // 用入参 username 拼 key：此刻还没查库，拿不到 id。
        BizAssert.isTrue(!isLocked(username), UserErrorCode.ADMIN_ACCOUNT_LOCKED);

        AdminUser admin = adminUserMapper.selectByUsername(username);
        BizAssert.notNull(admin, UserErrorCode.ADMIN_BAD_CREDENTIAL);
        // status 在库里有 NOT NULL 约束，取到 null 说明数据异常；按凭证错误返回，不泄漏细节
        BizAssert.notNull(admin.getStatus(), UserErrorCode.ADMIN_BAD_CREDENTIAL);

        AdminStatus status = AdminStatus.fromCode(admin.getStatus());
        BizAssert.isTrue(!status.isDisabled(), UserErrorCode.ADMIN_BAD_CREDENTIAL);
        // status=2 待分配角色：不与「密码错误」混为一谈——注册完登录被拦是设计，
        // 若提示成密码错误，用户会以为自己注册失败（business/03 已决 2026-09-29）
        BizAssert.isTrue(!status.isPendingRole(), UserErrorCode.ADMIN_PENDING_ROLE);

        // 锁定期内直接拒绝，不再比对密码（避免爆破）。
        // Redis 是快路径（前面已判），这里兜住「Redis 被清空 / 判断降级」的情况
        BizAssert.isTrue(admin.getLockUntil() == null || LocalDateTime.now().isAfter(admin.getLockUntil()),
                UserErrorCode.ADMIN_ACCOUNT_LOCKED);

        if (!passwordEncoder.matches(req.getPassword(), admin.getPasswordHash())) {
            int fails = (admin.getFailCount() == null ? 0 : admin.getFailCount()) + 1;
            int reached = fails;
            AdminUser update = new AdminUser()
                    .setId(admin.getId())
                    .setFailCount(fails >= MAX_FAIL ? 0 : fails);
            if (fails >= MAX_FAIL) {
                update.setLockUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                markLocked(username);
                log.warn("[admin] 账号 {} 连续失败 {} 次，锁定 {} 分钟", username, reached, LOCK_MINUTES);
            }
            adminUserMapper.updateById(update);
            throw UserErrorCode.ADMIN_BAD_CREDENTIAL.ex();
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

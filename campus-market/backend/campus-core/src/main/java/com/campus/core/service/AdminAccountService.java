package com.campus.core.service;

import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.CommonErrorCode;
import com.campus.core.entity.AdminRole;
import com.campus.core.entity.AdminUser;
import com.campus.core.enums.AdminStatusEnum;
import com.campus.core.enums.MailCodeSceneEnum;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.mapper.AdminRoleMapper;
import com.campus.core.mapper.AdminUserMapper;
import com.campus.core.redis.MailRedisKeys;
import com.campus.core.satoken.StpAdminUtil;
import com.campus.core.dto.req.AdminRegisterReq;
import com.campus.core.dto.req.AdminResetReq;
import com.campus.core.dto.req.AdminSendCodeReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;

/**
 * 管理员注册与找回密码（business/03 F-A0 / F-A10）。
 *
 * <p>与 {@link AdminAuthService} 分开：登录是高频只读校验，注册/找回是低频写入，
 * 混在一起会让登录链路背上发邮件、生成验证码这些无关依赖。
 *
 * <p>三条安全边界（改这里前先读 business/03「已决（2026-09-29）」）：
 * ① 注册不接受 roleId，否则任何人都能给自己审核/封禁权限；
 * ② 注册后是待分配态，登录被拦，由超管在 A9 分配角色；
 * ③ 找回密码不暴露邮箱是否注册，统一返回「已发送」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    /** 只允许 QQ 系：与 design/03「注册不限 / 仅 QQ 系」的决策一致 */
    private static final List<String> MAIL_SUFFIXES = List.of("@qq.com", "@foxmail.com");
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration THROTTLE_TTL = Duration.ofSeconds(60);
    private static final String SUPER_ROLE_CODE = "SUPER";

    private final AdminUserMapper adminUserMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final CaptchaService captchaService;
    private final MailService mailService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    /* ── 注册 ─────────────────────────────────────────────── */

    public void register(AdminRegisterReq req) {
        captchaService.verify(req.getCaptchaKey(), req.getCaptchaCode());

        String email = normalize(req.getEmail());
        BizAssert.isTrue(isQqMail(email), UserErrorCode.EMAIL_INVALID);
        BizAssert.isTrue(adminUserMapper.selectByEmail(email) == null, UserErrorCode.EMAIL_ALREADY_REGISTERED);

        verifyMailCode(email, MailCodeSceneEnum.REGISTER, req.getMailCode());

        AdminUser admin = new AdminUser()
                .setEmail(email)
                .setUsername(deriveUsername(email))
                .setPasswordHash(passwordEncoder.encode(req.getPassword()))
                .setRealName(req.getRealName())
                // ★ 关键：不给角色、不给权限，等超管分配
                .setRoleId(0L)
                .setStatus(AdminStatusEnum.PENDING_ROLE.getCode())
                .setFailCount(0);
        try {
            adminUserMapper.insert(admin);
        } catch (DuplicateKeyException e) {
            // 并发下唯一索引兜底：username 派生冲突也会走到这里
            throw UserErrorCode.EMAIL_ALREADY_REGISTERED.ex();
        }
        log.info("[admin] 新账号注册待分配角色：{} (id={})", email, admin.getId());
    }

    /* ── 发邮箱验证码（注册 / 找回密码 / 邮箱登录 三场景共用一个入口）── */

    /**
     * 给邮箱发 6 位验证码，场景由 {@code req.type}（{@link MailCodeSceneEnum}）决定。
     * 三场景的发码动作完全一致，只有两点不同，故合成一处用 switch 分：
     * ① 注册暴露「邮箱已注册」（4008），找回与登录必须不暴露，否则成了枚举管理员邮箱的工具；
     * ② 发信文案取 {@link MailCodeSceneEnum#getDesc()}。
     *
     * <p>节流必须在查库之前且三场景顺序一致：先查库后节流会让「找回/登录」的响应耗时
     * 随邮箱是否存在而变化，等于开了计时侧信道。代价是注册场景用已注册邮箱点第二次
     * 拿到 4010 而非 4008，可接受。
     *
     * <p>`default` 写 throw 而非静默兜底：将来加第四种场景漏写分支时，静默兜底不会有任何提醒。
     */
    public void sendCode(AdminSendCodeReq req) {
        captchaService.verify(req.getCaptchaKey(), req.getCaptchaCode());

        String email = normalize(req.getEmail());
        BizAssert.isTrue(isQqMail(email), UserErrorCode.EMAIL_INVALID);

        MailCodeSceneEnum scene = MailCodeSceneEnum.fromCodeOrNull(req.getType());
        BizAssert.notNull(scene, CommonErrorCode.PARAM_ERROR);

        // 按邮箱节流（不按场景），见 MailRedisKeys.buildThrottleKey
        Boolean fresh = stringRedisTemplate.opsForValue()
                .setIfAbsent(MailRedisKeys.buildThrottleKey(email), "1", THROTTLE_TTL);
        BizAssert.isTrue(Boolean.TRUE.equals(fresh), UserErrorCode.MAIL_CODE_TOO_FREQUENT);

        switch (scene) {
            // 注册页是公开入口，填了别人的邮箱要立刻知道
            case REGISTER -> BizAssert.isTrue(adminUserMapper.selectByEmail(email) == null,
                    UserErrorCode.EMAIL_ALREADY_REGISTERED);
            // 不发信，但对外仍返回「已发送」，不暴露该邮箱是否注册
            case RESET, LOGIN -> {
                AdminUser admin = adminUserMapper.selectByEmail(email);
                if (admin == null || isDisabled(admin.getStatus())) {
                    log.info("[admin] {}：邮箱 {} 无可用账号，按「已发送」返回", scene.getDesc(), email);
                    return;
                }
            }
            default -> throw new IllegalStateException("未处理的邮箱验证码场景：" + scene);
        }

        String code = sixDigitCode();
        stringRedisTemplate.opsForValue().set(MailRedisKeys.buildCodeKey(scene, email), code, CODE_TTL);
        mailService.sendVerifyCode(email, code, scene.getDesc());
    }

    /* ── 找回密码：两步 ────────────────────────────────────── */

    /** 第二步：校验验证码并重置密码 */
    public void resetPassword(AdminResetReq req) {
        String email = normalize(req.getEmail());
        BizAssert.isTrue(isQqMail(email), UserErrorCode.EMAIL_INVALID);

        AdminUser admin = adminUserMapper.selectByEmail(email);
        BizAssert.notNull(admin, UserErrorCode.MAIL_CODE_INVALID);
        BizAssert.isTrue(!isDisabled(admin.getStatus()), UserErrorCode.MAIL_CODE_INVALID);

        // 「新密码不能与旧密码相同」必须在校验验证码之前判：验证码一次性，
        // 先校验再判重的话，用户输个重复密码就把码用掉了，还得等 60 秒节流
        BizAssert.isTrue(!passwordEncoder.matches(req.getPassword(), admin.getPasswordHash()),
                UserErrorCode.PASSWORD_SAME_AS_OLD);

        verifyMailCode(email, MailCodeSceneEnum.RESET, req.getMailCode());

        adminUserMapper.updateById(new AdminUser()
                .setId(admin.getId())
                .setPasswordHash(passwordEncoder.encode(req.getPassword()))
                .setFailCount(0)
                .setLockUntil(null));

        // 改密后强制下线所有会话：旧会话（可能已被盗用）必须失效
        StpAdminUtil.stpLogic.kickout(admin.getId());
        log.info("[admin] 邮箱 {} 已重置密码，旧会话全部下线", email);
    }

    /* ── 超管分配角色 ─────────────────────────────────────── */

    /**
     * 给账号分配角色，同时把 status 置为启用。
     *
     * <p>不允许分配 SUPER：超管只能由开发者经种子数据创建（business/03 已决 2026-09-29），
     * 否则超管能批量造超管，权限模型就塌了。
     */
    public void assignRole(Long adminId, Long roleId) {
        AdminUser admin = adminUserMapper.selectById(adminId);
        BizAssert.notNull(admin, CommonErrorCode.NOT_FOUND);

        AdminRole role = adminRoleMapper.selectById(roleId);
        BizAssert.notNull(role, CommonErrorCode.NOT_FOUND);
        BizAssert.isTrue(!SUPER_ROLE_CODE.equalsIgnoreCase(role.getRoleCode()),
                UserErrorCode.ROLE_SUPER_FORBIDDEN);

        adminUserMapper.updateById(new AdminUser()
                .setId(adminId)
                .setRoleId(roleId)
                .setStatus(AdminStatusEnum.ENABLED.getCode()));
        log.info("[admin] 账号 {} 分配角色 {} 并启用", admin.getEmail(), role.getRoleCode());
    }

    /* ── 内部工具 ─────────────────────────────────────────── */

    /**
     * 邮箱验证码校验：一次性，比对通过才删。
     *
     * <p>必须带场景：注册码只能注册、找回码只能找回、登录码只能登录
     * （key 里带场景，见 {@link MailRedisKeys#buildCodeKey}），跨场景能用等于让掉一道校验。
     *
     * <p>对外可见（而非 private）是因为登录也要校验 LOGIN 场景的码；登录是高频只读链路，
     * 不并进本类，只复用这一个方法。
     */
    public void verifyMailCode(String email, MailCodeSceneEnum scene, String code) {
        String key = MailRedisKeys.buildCodeKey(scene, email);
        String cached = stringRedisTemplate.opsForValue().get(key);
        BizAssert.notNull(cached, UserErrorCode.MAIL_CODE_INVALID);
        // 先比对、后删除：反过来的话输错一次就把正确的码也删了，用户只能重新发
        BizAssert.isTrue(cached.equals(code.trim()), UserErrorCode.MAIL_CODE_INVALID);
        stringRedisTemplate.delete(key);
    }

    /** 状态是否「停用」。status 理论上非空（库里有 NOT NULL），仍兜住查询结果为 null 的情况 */
    private static boolean isDisabled(Integer status) {
        return status != null && AdminStatusEnum.fromCode(status).isDisabled();
    }

    private String sixDigitCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    /** QQ 系邮箱校验（应用层，数据库只保证唯一——将来放开域名不用改表） */
    private static boolean isQqMail(String email) {
        String e = normalize(email);
        return MAIL_SUFFIXES.stream().anyMatch(e::endsWith);
    }

    /** 登录账号取邮箱前缀（3037749727@qq.com → 3037749727），冲突时补 4 位随机后缀 */
    private String deriveUsername(String email) {
        String base = email.substring(0, email.indexOf('@'));
        // 截断避免 varchar(32) 溢出
        if (base.length() > 24) {
            base = base.substring(0, 24);
        }
        String candidate = base;
        for (int i = 0; i < 5 && adminUserMapper.selectByUsername(candidate) != null; i++) {
            candidate = base + String.format("%04d", random.nextInt(10_000));
        }
        return candidate;
    }
}

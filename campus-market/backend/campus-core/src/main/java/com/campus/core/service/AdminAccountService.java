package com.campus.core.service;

import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.CommonErrorCode;
import com.campus.core.entity.AdminRole;
import com.campus.core.entity.AdminUser;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.mapper.AdminRoleMapper;
import com.campus.core.mapper.AdminUserMapper;
import com.campus.core.satoken.StpAdminUtil;
import com.campus.core.service.dto.AdminRegisterReq;
import com.campus.core.service.dto.AdminResetReq;
import com.campus.core.service.dto.AdminSendCodeReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;

/**
 * 管理员注册与找回密码（business/03 F-A0 / F-A10）。
 *
 * <p>本类与 {@link AdminAuthService} 分开：**登录是高频只读校验，注册/找回是低频写入**，
 * 混在一起会让登录链路背上发邮件、生成验证码这些跟登录无关的依赖。
 *
 * <p>三条安全边界（改这里前先读 business/03 的「已决（2026-09-29）」）：
 * <ol>
 *   <li>注册接口**不接受 roleId**——否则任何人都能给自己审核/封禁权限；</li>
 *   <li>注册后是 `status=2` 待分配，**登录会被拦**，由超管在 A9 分配角色；</li>
 *   <li>找回密码**不暴露邮箱是否注册**，统一返回「已发送」。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    /** 只允许 QQ 系：与 design/03「注册不限 / 仅 QQ 系」的决策一致 */
    private static final List<String> MAIL_SUFFIXES = List.of("@qq.com", "@foxmail.com");
    private static final String MAIL_CODE_PREFIX = "cm:mail:code:";
    private static final String MAIL_THROTTLE_PREFIX = "cm:mail:throttle:";
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration THROTTLE_TTL = Duration.ofSeconds(60);
    private static final String SUPER_ROLE_CODE = "SUPER";
    /** status：0 停用 / 1 启用 / 2 待分配角色 */
    private static final int STATUS_PENDING = 2;
    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    private final AdminUserMapper adminUserMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final CaptchaService captchaService;
    private final MailService mailService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    /* ── 注册 ─────────────────────────────────────────────── */

    @Transactional(rollbackFor = Exception.class)
    public void register(AdminRegisterReq req) {
        captchaService.verify(req.getCaptchaKey(), req.getCaptchaCode());

        String email = normalize(req.getEmail());
        BizAssert.isTrue(isQqMail(email), UserErrorCode.EMAIL_INVALID);
        BizAssert.isTrue(adminUserMapper.selectByEmail(email) == null, UserErrorCode.EMAIL_ALREADY_REGISTERED);

        verifyMailCode(email, req.getMailCode());

        AdminUser admin = new AdminUser()
                .setEmail(email)
                .setUsername(deriveUsername(email))
                .setPasswordHash(passwordEncoder.encode(req.getPassword()))
                .setRealName(req.getRealName())
                // ★ 关键：不给角色、不给权限，等超管分配
                .setRoleId(0L)
                .setStatus(STATUS_PENDING)
                .setFailCount(0);
        try {
            adminUserMapper.insert(admin);
        } catch (DuplicateKeyException e) {
            // 并发下唯一索引兜底：username 派生冲突也会走到这里
            throw UserErrorCode.EMAIL_ALREADY_REGISTERED.ex();
        }
        log.info("[admin] 新账号注册待分配角色：{} (id={})", email, admin.getId());
    }

    /**
     * 注册前置：给邮箱发 6 位验证码。
     *
     * <p>这里**可以**暴露「邮箱已注册」——注册页本就是公开入口，用户填了别人的邮箱
     * 需要立刻知道；与找回密码（要防枚举）的结论相反，别把两者改成同一个方法。
     */
    public void sendRegisterCode(AdminSendCodeReq req) {
        captchaService.verify(req.getCaptchaKey(), req.getCaptchaCode());

        String email = normalize(req.getEmail());
        BizAssert.isTrue(isQqMail(email), UserErrorCode.EMAIL_INVALID);
        BizAssert.isTrue(adminUserMapper.selectByEmail(email) == null,
                UserErrorCode.EMAIL_ALREADY_REGISTERED);

        Boolean fresh = stringRedisTemplate.opsForValue()
                .setIfAbsent(MAIL_THROTTLE_PREFIX + email, "1", THROTTLE_TTL);
        BizAssert.isTrue(Boolean.TRUE.equals(fresh), UserErrorCode.MAIL_CODE_TOO_FREQUENT);

        String code = sixDigitCode();
        stringRedisTemplate.opsForValue().set(MAIL_CODE_PREFIX + email, code, CODE_TTL);
        mailService.sendVerifyCode(email, code, "注册");
    }

    /* ── 找回密码：两步 ────────────────────────────────────── */

    /**
     * 第一步：下发邮箱验证码。
     *
     * <p>**邮箱未注册时也返回成功**——若区分「已发送 / 邮箱不存在」，
     * 这个接口就成了枚举管理员邮箱的工具（撞库前置）。代价是用户输错邮箱时
     * 收不到邮件也没提示，所以用「10 分钟有效 + 60 秒节流」把滥用成本顶上去。
     */
    public void sendResetCode(AdminSendCodeReq req) {
        captchaService.verify(req.getCaptchaKey(), req.getCaptchaCode());

        String email = normalize(req.getEmail());
        BizAssert.isTrue(isQqMail(email), UserErrorCode.EMAIL_INVALID);

        // 60 秒内同一邮箱只发一次
        Boolean fresh = stringRedisTemplate.opsForValue()
                .setIfAbsent(MAIL_THROTTLE_PREFIX + email, "1", THROTTLE_TTL);
        BizAssert.isTrue(Boolean.TRUE.equals(fresh), UserErrorCode.MAIL_CODE_TOO_FREQUENT);

        AdminUser admin = adminUserMapper.selectByEmail(email);
        if (admin == null || admin.getStatus() == null || admin.getStatus() == STATUS_DISABLED) {
            log.info("[admin] 找回密码：邮箱 {} 无可用账号，按「已发送」返回", email);
            return;
        }

        String code = sixDigitCode();
        stringRedisTemplate.opsForValue().set(MAIL_CODE_PREFIX + email, code, CODE_TTL);
        mailService.sendVerifyCode(email, code, "找回密码");
    }

    /** 第二步：校验验证码并重置密码 */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(AdminResetReq req) {
        String email = normalize(req.getEmail());
        BizAssert.isTrue(isQqMail(email), UserErrorCode.EMAIL_INVALID);

        AdminUser admin = adminUserMapper.selectByEmail(email);
        BizAssert.notNull(admin, UserErrorCode.MAIL_CODE_INVALID);
        BizAssert.isTrue(admin.getStatus() == null || admin.getStatus() != STATUS_DISABLED,
                UserErrorCode.MAIL_CODE_INVALID);

        // ★「新密码不能与旧密码相同」必须在校验验证码**之前**判：
        // 验证码是一次性的，若先校验再判重，用户输了个重复密码就把码用掉了，
        // 还得等 60 秒节流过去才能重发——等于拿验证码替用户做输入校验。
        BizAssert.isTrue(!passwordEncoder.matches(req.getPassword(), admin.getPasswordHash()),
                UserErrorCode.PASSWORD_SAME_AS_OLD);

        // 验证码最后一次使用：校验通过即删，防止同一个码被反复用来改密码
        verifyMailCode(email, req.getMailCode());

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
     * 给待分配（或已在册）账号分配角色，同时把 status 置为启用。
     *
     * <p>**不允许分配 SUPER**：超级管理员只能由开发者经种子数据创建
     * （business/03 已决 2026-09-29）。否则超管能批量造超管，权限模型就塌了。
     */
    @Transactional(rollbackFor = Exception.class)
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
                .setStatus(STATUS_ENABLED));
        log.info("[admin] 账号 {} 分配角色 {} 并启用", admin.getEmail(), role.getRoleCode());
    }

    /* ── 内部工具 ─────────────────────────────────────────── */

    /** 邮箱验证码校验：一次性，通过即删 */
    private void verifyMailCode(String email, String code) {
        BizAssert.isTrue(code != null && !code.isBlank(), CommonErrorCode.PARAM_ERROR);
        String cached = stringRedisTemplate.opsForValue().get(MAIL_CODE_PREFIX + email);
        BizAssert.notNull(cached, UserErrorCode.MAIL_CODE_INVALID);
        // ★ 先比对、后删除：反过来的话，输错一次就把正确验证码也删了，用户只能重新发
        BizAssert.isTrue(cached.equals(code.trim()), UserErrorCode.MAIL_CODE_INVALID);
        stringRedisTemplate.delete(MAIL_CODE_PREFIX + email);
    }

    private String sixDigitCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    /** QQ 系邮箱校验（应用层，数据库只保证唯一——将来放开域名不用改表） */
    public static boolean isQqMail(String email) {
        String e = normalize(email);
        return MAIL_SUFFIXES.stream().anyMatch(e::endsWith);
    }

    /**
     * 登录账号取邮箱前缀（3037749727@qq.com → 3037749727）；冲突时补 4 位随机后缀。
     * 注册页不单独让用户填用户名——邮箱已经是唯一标识，再填一个是负担。
     */
    private String deriveUsername(String email) {
        String base = email.substring(0, email.indexOf('@'));
        // 太短或纯数字前缀太长都截断，避免 varchar(32) 溢出
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

package com.campus.user.controller;

import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.CommonErrorCode;
import com.campus.common.result.Result;
import com.campus.core.context.UserContext;
import com.campus.core.dto.req.UserLoginReq;
import com.campus.core.dto.req.UserProfileUpdateReq;
import com.campus.core.dto.req.UserRegisterReq;
import com.campus.core.dto.req.UserResetPasswordReq;
import com.campus.core.dto.req.UserSendCodeReq;
import com.campus.core.dto.resp.DevAccountVO;
import com.campus.core.dto.resp.UserLoginVO;
import com.campus.core.dto.resp.VerifyCodeVO;
import com.campus.core.enums.MailCodeSceneEnum;
import com.campus.core.satoken.StpUserUtil;
import com.campus.core.service.UserAccountService;
import com.campus.core.service.UserAuthService;
import com.campus.core.service.UserCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学生端注册、登录与会话（notes/26：注册 / 验证码登录 / 密码登录 / 找回密码）。
 *
 * <p>与管理端 {@code /admin/auth} 分立：两端账号体系不同（app_user / admin_user），
 * 令牌也不同（satoken-user / satoken-admin），共用一套 Sa-Token 多账号配置。
 */
@Tag(name = "学生端 · 登录与权限")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserAuthController {

    private final UserCodeService userCodeService;
    private final UserAuthService userAuthService;
    private final UserAccountService userAccountService;

    /** 与 UserCodeService 同一组开关：回显关掉后联调账号列表一并失效 */
    @Value("${campus.user.sms.echo:false}")
    private boolean smsEcho;

    @Value("${campus.user.mail.echo:false}")
    private boolean mailEcho;

    @Operation(summary = "发验证码",
            description = "account 传手机号走短信、传 QQ 邮箱走邮件；scene 1注册 2找回密码 3登录。"
                    + "60 秒节流、10 分钟有效、错 5 次作废；演示期验证码随响应回显（devCode）")
    @PostMapping("/send-code")
    public Result<VerifyCodeVO> sendCode(@Valid @RequestBody UserSendCodeReq req) {
        MailCodeSceneEnum scene = MailCodeSceneEnum.fromCodeOrNull(req.getScene());
        // 越界场景转成参数错误：抛在枚举里会被兜底成 5000
        BizAssert.notNull(scene, CommonErrorCode.PARAM_ERROR);
        return Result.ok(userCodeService.send(req.getAccount(), scene));
    }

    @Operation(summary = "注册",
            description = "手机号 + QQ 邮箱 + 昵称 + 密码 + 邮箱验证码；手机号/邮箱已注册分别报 4036 / 4008")
    @PostMapping("/register")
    public Result<UserLoginVO> register(@Valid @RequestBody UserRegisterReq req) {
        userAccountService.register(req);
        // 注册后直接登录：少一次「刚设完密码又要验证码」的往返，账号已通过邮箱验证
        UserLoginReq loginReq = new UserLoginReq();
        loginReq.setAccount(req.getPhone());
        loginReq.setType(2);
        loginReq.setCredential(req.getPassword());
        return Result.ok(userAuthService.login(loginReq));
    }

    @Operation(summary = "登录",
            description = "account 传手机号或 QQ 邮箱，type 1 验证码登录 / 2 密码登录；"
                    + "未注册 4015/4035，密码错误 4037，未设密码 4038，错 5 次锁 10 分钟 4039")
    @PostMapping("/login")
    public Result<UserLoginVO> login(@Valid @RequestBody UserLoginReq req) {
        return Result.ok(userAuthService.login(req));
    }

    @Operation(summary = "找回密码",
            description = "账号 + 该渠道验证码 + 新密码；成功后该账号所有会话下线。新密码与旧密码相同报 4011")
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody UserResetPasswordReq req) {
        userAccountService.resetPassword(req);
        return Result.ok();
    }

    @Operation(summary = "当前登录人信息", description = "刷新页面后重建会话视图用；未登录返回 1001")
    @GetMapping("/info")
    public Result<UserLoginVO> info() {
        return Result.ok(userAuthService.currentUser());
    }

    @Operation(summary = "编辑资料", description = "昵称 / 头像局部更新，两个字段至少传一个；未登录 1001，昵称不合法 4020，头像地址不合法 4021")
    @PutMapping("/profile")
    public Result<UserLoginVO> updateProfile(@Valid @RequestBody UserProfileUpdateReq req) {
        return Result.ok(userAuthService.updateProfile(UserContext.getUserId(), req));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUserUtil.logout();
        return Result.ok();
    }

    @Operation(summary = "联调账号列表",
            description = "仅当验证码回显开启时返回最近注册的账号（免登录），供小程序测试账号面板使用；"
                    + "关掉 echo 后返回空列表")
    @GetMapping("/dev/accounts")
    public Result<List<DevAccountVO>> devAccounts() {
        if (!smsEcho && !mailEcho) {
            return Result.ok(List.of());
        }
        return Result.ok(userAuthService.recentAccounts(50));
    }
}

package com.campus.core.satoken;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.stp.StpLogic;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * 双 StpLogic 注册（tech/01 8.1）：学生端与管理端各自独立的 token 与会话配置。
 *
 * <p>类名不要叫 SaTokenConfig —— 会和 cn.dev33.satoken.config.SaTokenConfig 撞名，
 * 一旦撞名，注解处理轮会崩，Lombok 全部失效（本项目踩过）。
 *
 * <p>两端差异由业务决定：管理端 2 小时无操作即失效（安全优先），
 * 学生端 7 天（手机 + 电脑同时在线，频繁重登会赶人）。
 *
 * <p>【为什么不能写成 @Bean】Sa-Token 自动配置注册的 SaBeanInject 会按类型注入
 * 「唯一一个」StpLogic，容器里出现两个候选时启动直接失败：
 * <pre>
 * Parameter 0 of method setStpLogic in cn.dev33.satoken.spring.SaBeanInject
 * required a single bean, but 2 were found: stpLogicUser, stpLogicAdmin
 * </pre>
 * 报错里提示的 -parameters 编译参数是干扰项（本工程已在根 pom 的 maven-compiler-plugin
 * 里显式加了 {@code <parameters>true</parameters>}，因为它没有继承 spring-boot-starter-parent），
 * 真正原因是类型注入歧义，标 @Primary 也只是掩盖（会把另一个账号体系挤出全局注册）。
 * 业务侧一律通过 {@link StpUserUtil} / {@link StpAdminUtil} 静态工具访问，
 * 工具类内部自持 StpLogic，不依赖容器注入，因此按官方多账号 demo 的做法：手动 new，静态绑定。
 *
 * <p>【必须同时 putStpLogic】只给工具类 setStpLogic **不够**：方法上的
 * {@code @SaCheckPermission(type = "admin")} 是由 SaInterceptor 转交
 * {@code SaManager.getStpLogic("admin")} 执行的，而 SaManager 的 stpLogicMap 里
 * 只有它自己 createStpLogic 出来的实例——不注册进去，注解校验就会拿到一个
 * tokenName=satoken 的默认 StpLogic，永远读不到管理端的 satoken-admin，表现是
 * 「明明登录了却返回 1001 未登录」。两套逻辑必须指向同一个对象。
 */
@Configuration
public class MultiAccountConfig {

    public static final String USER_TOKEN_NAME = "satoken-user";
    public static final String ADMIN_TOKEN_NAME = "satoken-admin";

    @PostConstruct
    public void registerStpLogics() {
        // 学生端：7 天，允许并发登录（isShare=false 保证每次登录新 token，便于按设备踢下线）
        StpLogic userLogic = buildLogic(StpUserUtil.TYPE, USER_TOKEN_NAME, 60 * 60 * 24 * 7);
        StpUserUtil.setStpLogic(userLogic);
        SaManager.putStpLogic(userLogic);

        // 管理端：2 小时，允许并发（保留会话列表便于审计）
        StpLogic adminLogic = buildLogic(StpAdminUtil.TYPE, ADMIN_TOKEN_NAME, 60 * 60 * 2);
        StpAdminUtil.setStpLogic(adminLogic);
        SaManager.putStpLogic(adminLogic);
    }

    private StpLogic buildLogic(String type, String tokenName, int timeoutSeconds) {
        StpLogic logic = new StpLogic(type);
        SaTokenConfig cfg = new SaTokenConfig();
        cfg.setTokenName(tokenName);
        cfg.setTimeout(timeoutSeconds);
        cfg.setActiveTimeout(-1);
        cfg.setIsConcurrent(true);
        cfg.setIsShare(false);
        cfg.setTokenStyle("uuid");
        logic.setConfig(cfg);
        return logic;
    }
}

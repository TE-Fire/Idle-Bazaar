package com.campus.core.satoken;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.stp.StpLogic;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * 双 StpLogic 注册（tech/01 8.1）：学生端与管理端各自独立的 token 与会话。
 * 管理端 2 小时（安全优先），学生端 7 天（手机 + 电脑同时在线，频繁重登会赶人）。
 *
 * <p>类名不要叫 SaTokenConfig：会和 `cn.dev33.satoken.config.SaTokenConfig` 撞名，
 * 撞名后注解处理轮崩掉、Lombok 全部失效（本项目踩过）。
 *
 * <p>【不能写成 @Bean】SaBeanInject 按类型注入「唯一一个」StpLogic，容器里两个就启动失败
 * （`required a single bean, but 2 were found`）；报错里提示的 -parameters 是干扰项，
 * 标 @Primary 也只是掩盖。业务侧一律走 {@link StpUserUtil} / {@link StpAdminUtil} 静态工具，
 * 工具类自持 StpLogic，不依赖容器注入。
 *
 * <p>【必须同时 putStpLogic】只给工具类 setStpLogic 不够：`@SaCheckPermission(type = "admin")`
 * 由 SaManager.getStpLogic("admin") 执行，不注册进去就会拿到默认的 satoken，
 * 读不到管理端的 satoken-admin，表现为「明明登录了却返回 1001」。两套逻辑必须指向同一对象。
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

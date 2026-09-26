package com.campus.core.satoken;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.stp.StpLogic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 双 StpLogic 注册（tech/01 8.1）：学生端与管理端各自独立的 token 与会话配置。
 *
 * <p>类名不要叫 SaTokenConfig —— 会和 cn.dev33.satoken.config.SaTokenConfig 撞名，
 * 一旦撞名，注解处理轮会崩，Lombok 全部失效（本项目踩过）。
 *
 * <p>两端差异由业务决定：管理端 2 小时无操作即失效（安全优先），
 * 学生端 7 天（手机 + 电脑同时在线，频繁重登会赶人）。
 */
@Configuration
public class MultiAccountConfig {

    public static final String USER_TOKEN_NAME = "satoken-user";
    public static final String ADMIN_TOKEN_NAME = "satoken-admin";

    /** 学生端：7 天，允许并发登录 */
    @Bean
    public StpLogic stpLogicUser() {
        StpLogic logic = new StpLogic(StpUserUtil.TYPE);
        SaTokenConfig cfg = new SaTokenConfig();
        cfg.setTokenName(USER_TOKEN_NAME);
        cfg.setTimeout(60 * 60 * 24 * 7);
        cfg.setActiveTimeout(-1);
        cfg.setIsConcurrent(true);
        cfg.setIsShare(false);
        cfg.setTokenStyle("uuid");
        logic.setConfig(cfg);
        StpUserUtil.setStpLogic(logic);
        return logic;
    }

    /** 管理端：2 小时，允许并发（保留会话列表便于审计） */
    @Bean
    public StpLogic stpLogicAdmin() {
        StpLogic logic = new StpLogic(StpAdminUtil.TYPE);
        SaTokenConfig cfg = new SaTokenConfig();
        cfg.setTokenName(ADMIN_TOKEN_NAME);
        cfg.setTimeout(60 * 60 * 2);
        cfg.setActiveTimeout(-1);
        cfg.setIsConcurrent(true);
        cfg.setIsShare(false);
        cfg.setTokenStyle("uuid");
        logic.setConfig(cfg);
        StpAdminUtil.setStpLogic(logic);
        return logic;
    }
}

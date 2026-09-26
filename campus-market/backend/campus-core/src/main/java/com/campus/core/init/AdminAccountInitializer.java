package com.campus.core.init;

import com.campus.core.entity.AdminUser;
import com.campus.core.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 演示账号初始化（对应原型 A1 的四种角色）。
 *
 * <p>密码不在 SQL 里写死哈希——BCrypt 每次结果都不同，写死反而容易对不上。
 * 这里在启动时生成，且只在 admin_user 为空时执行，可重复运行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    /** 统一演示密码，四个角色相同，便于答辩时切换角色 */
    public static final String DEMO_PASSWORD = "Campus@2026";

    private final AdminUserMapper adminUserMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void run(ApplicationArguments args) {
        if (adminUserMapper.countAlive() > 0) {
            return;
        }
        String hash = passwordEncoder.encode(DEMO_PASSWORD);
        long[][] accounts = {
                {2101L, 2001L},   // 超级管理员
                {2102L, 2002L},   // 审核员
                {2103L, 2003L},   // 运营
                {2104L, 2004L},   // 只读
        };
        String[] names = {"super", "reviewer", "operator", "readonly"};
        String[] realNames = {"超级管理员", "审核员", "运营", "只读账号"};

        for (int i = 0; i < accounts.length; i++) {
            AdminUser u = new AdminUser();
            u.setId(accounts[i][0]);
            u.setUsername(names[i]);
            u.setPasswordHash(hash);
            u.setRealName(realNames[i]);
            u.setRoleId(accounts[i][1]);
            u.setStatus(1);
            u.setFailCount(0);
            u.setCreateTime(LocalDateTime.now());
            u.setUpdateTime(LocalDateTime.now());
            u.setDeleted(0L);
            adminUserMapper.insert(u);
        }
        log.info("[init] 已创建 {} 个演示管理员账号，统一密码：{}", accounts.length, DEMO_PASSWORD);
    }
}

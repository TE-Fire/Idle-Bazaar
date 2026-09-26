package com.campus.core.init;

import com.campus.core.entity.AdminUser;
import com.campus.core.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 演示账号初始化（对应原型 A1 的四种角色）。
 *
 * <p>密码不在 SQL 里写死哈希——BCrypt 每次结果都不同，写死反而容易对不上。
 * 这里在启动时生成，且只在 admin_user 为空时执行，可重复运行。
 * create_time / update_time / deleted 由 AuditMetaObjectHandler 自动填充，不手动 set。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    /** 统一演示密码，四个角色相同，便于答辩时切换角色 */
    public static final String DEMO_PASSWORD = "Campus@2026";

    /** 一条种子账号：与其拆数组按下标对位，不如一个 record 自解释 */
    private record Seed(long id, long roleId, String username, String realName) {
    }

    private static final List<Seed> SEEDS = List.of(
            new Seed(2101L, 2001L, "super", "超级管理员"),
            new Seed(2102L, 2002L, "reviewer", "审核员"),
            new Seed(2103L, 2003L, "operator", "运营"),
            new Seed(2104L, 2004L, "readonly", "只读账号")
    );

    private final AdminUserMapper adminUserMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void run(ApplicationArguments args) {
        if (adminUserMapper.countAlive() > 0) {
            return;
        }
        String hash = passwordEncoder.encode(DEMO_PASSWORD);
        SEEDS.forEach(seed -> adminUserMapper.insert(new AdminUser()
                .setId(seed.id())
                .setUsername(seed.username())
                .setPasswordHash(hash)
                .setRealName(seed.realName())
                .setRoleId(seed.roleId())
                .setStatus(1)
                .setFailCount(0)));
        log.info("[init] 已创建 {} 个演示管理员账号，统一密码：{}", SEEDS.size(), DEMO_PASSWORD);
    }
}

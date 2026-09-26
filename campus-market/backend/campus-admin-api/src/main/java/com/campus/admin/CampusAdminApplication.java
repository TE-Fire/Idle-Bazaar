package com.campus.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 唯一启动类：单 jar 部署，学生端 /api/** 与管理端 /admin/** 在同一进程。
 *
 * <p>扫 com.campus 全包，这样 user-api 里的 Controller 也能注册进来。
 */
@SpringBootApplication(scanBasePackages = "com.campus")
@MapperScan("com.campus.core.mapper")
public class CampusAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusAdminApplication.class, args);
    }
}

package com.campus.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启定时任务。独立成一个配置类而不是挂在别的上，是因为「项目允不允许起后台任务」
 * 这件事需要有个只负责它的地方——挂在随手一个配置里，将来排查「为什么任务没跑」
 * 就得翻遍全项目的注解。
 *
 * <p>当前的唯一使用者是订单超时扫描。按 notes/13 第九节决策 1，**本期不引入 RocketMQ**，
 * 待付款超时改由 {@code OrderService#cancelExpired} 扫描 `idx_expire(status, expire_time)` 做。
 * 需要说明这不是权宜之计：`tech/01` §9.2 的设计里，延迟消息本来就要配一个定时扫描作补偿，
 * 用于防消息丢失。先落这一步将来补 MQ 时不用返工，代价只是取消时机的精度等于扫描间隔。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

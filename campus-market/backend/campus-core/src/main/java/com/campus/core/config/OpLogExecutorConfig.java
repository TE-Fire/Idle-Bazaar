package com.campus.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 操作日志的异步线程池（tech/01 §5.4 第 2 条）。
 *
 * <p><b>为什么不用默认线程池</b>：`@Async` 的默认执行器是全应用共用的，
 * 日志落库这种「可以丢、但不该拖累别人」的任务混在里面，一旦日志堆积
 * （比如数据库慢），会把共用队列占满，反过来影响别的异步任务。
 * 单独一个池，行为、容量、拒绝策略都能按「日志」这个用途调。
 *
 * <p><b>这个池是本项目第一个线程池</b>：此前全项目没有 `@Async` 也没有 `Executor` Bean。
 * 于是有一个连带效果——Spring Boot 的 `TaskExecutionAutoConfiguration` 只在
 * 容器里没有 `Executor` Bean 时才创建 `applicationTaskExecutor`，现在它退让了。
 * 影响面为零：项目没有 `@Async`，也没有用了异步支持的 MVC 接口
 * （空闲时只有 `SimpleAsyncTaskExecutor` 兜底）。将来真要用 `@Async`，
 * 应当显式指定执行器而不是依赖自动配置 —— `@Async` 不写执行器名会去找
 * `applicationTaskExecutor`，那时它已经不存在了。
 *
 * <p><b>规格</b>：
 * <ul>
 *   <li>core 1 / max 2 —— 写日志是极短的单条 INSERT，单线程就够；
 *       固定小并发是为了让插入顺序贴近发生顺序，而不是把库打满；</li>
 *   <li>队列 **有界** 512 —— 无界队列在库挂掉时会一直堆到 OOM，这是拒绝策略能生效的前提；</li>
 *   <li>拒绝策略见 {@link OpLogRejectedHandler} —— 丢弃 + ERROR，绝不回退到请求线程执行；</li>
 *   <li>关停时等最多 10 秒把队列里的日志写完 —— 否则每次重启都会丢掉最后一批操作记录。</li>
 * </ul>
 */
@Slf4j
@Configuration
public class OpLogExecutorConfig {

    /** Bean 名：`OpLogWriter` 的构造器按这个名字取，避免与将来别的池混淆 */
    public static final String EXECUTOR_BEAN_NAME = "opLogExecutor";

    private static final int CORE_POOL_SIZE = 1;

    private static final int MAX_POOL_SIZE = 2;

    private static final int QUEUE_CAPACITY = 512;

    private static final int AWAIT_TERMINATION_SECONDS = 10;

    @Bean(EXECUTOR_BEAN_NAME)
    public ThreadPoolTaskExecutor opLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        // 线程名进日志（console pattern 里的 %thread），排查时一眼能看出是日志池的线程
        executor.setThreadNamePrefix("op-log-");
        executor.setRejectedExecutionHandler(new OpLogRejectedHandler());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        return executor;
    }

    /**
     * 队列满 / 池已关停时**丢弃并打 ERROR**。
     *
     * <p>不用 `CallerRunsPolicy`：它会把任务退回请求线程同步执行，
     * 等于「数据库慢 → 队列满 → 用户请求多等一次数据库往返」，与「日志不能影响主流程」
     * 的方向正好相反。也不抛 `AbortPolicy`：那会让切面抛异常，
     * 而日志失败没有任何理由影响业务结果。
     *
     * <p>丢掉的日志不可恢复，所以必须可靠地喊出来——ERROR 里带上排队数量，
     * 用于判断是瞬时尖峰（偶发一条）还是库真的卡住了（持续刷屏）。
     */
    static class OpLogRejectedHandler implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
            if (executor.isShutdown()) {
                log.error("[oplog] 线程池已关闭，本条操作日志丢弃");
                return;
            }
            log.error("[oplog] 队列已满（排队 {}/{}，活动线程 {}），本条操作日志丢弃",
                    executor.getQueue().size(), QUEUE_CAPACITY, executor.getActiveCount());
        }
    }
}

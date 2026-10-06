package com.campus.core.oplog;

import com.campus.core.entity.AdminRole;
import com.campus.core.entity.AdminUser;
import com.campus.core.entity.SysOperationLog;
import com.campus.core.mapper.AdminRoleMapper;
import com.campus.core.mapper.AdminUserMapper;
import com.campus.core.mapper.SysOperationLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 操作日志的「提交 → 异步落库」这一段（tech/01 §5.4 第 1、2 条）。
 *
 * <p><b>事务边界</b>：{@link #record} 先看当前线程有没有活动的事务同步。
 * 有（注解挂在某个 `@Transactional` 的 Service 方法上）→ 注册 `afterCommit`，
 * 回滚即整条丢弃：业务没发生，日志就不该留痕，否则日志表里会长出
 * 「从不存在过的操作」。没有（注解挂在 Controller 上，本项目当前的用法）→ 直接提交，
 * 此时 Service 的事务早已提交完毕，语义同样成立。两种挂法都对，不必二选一。
 *
 * <p><b>绝不阻塞请求线程</b>：`submit` 只做一次 `execute`。队列满时
 * `OpLogRejectedHandler` 直接丢弃并打 ERROR，不退回调用线程执行——退回去就等于
 * 「日志写不进去时让用户多等一次数据库往返」，方向反了。
 *
 * <p><b>落库失败不影响业务，但必须留痕</b>：任何异常都在这里 catch 掉并打 ERROR，
 * 带上 module/type/traceId，方便从日志反查丢的是哪一条。反过来不 catch 会让
 * 线程池的异常处理器默默吞掉（`ThreadPoolTaskExecutor` 默认的
 * `execute` 会把未捕获异常交给 `Thread.UncaughtExceptionHandler`，只打印堆栈、无上下文）。
 *
 * <p><b>为什么 adminName / roleCode 在这里查</b>：表注释写明它们是「避免删号后查不到」
 * 的冗余。查补需要两次主键查询，放在线程池线程上做，与请求耗时无关。
 * 查不到只让两列为空，主记录照落——日志的第一价值是「有这个动作」。
 */
@Slf4j
@Component
public class OpLogWriter {

    /** 与 `sys_operation_log.result` 对齐 */
    private static final int RESULT_SUCCESS = 1;

    private static final int RESULT_FAIL = 0;

    private final SysOperationLogMapper logMapper;

    private final AdminUserMapper adminUserMapper;

    private final AdminRoleMapper adminRoleMapper;

    private final ThreadPoolTaskExecutor opLogExecutor;

    public OpLogWriter(SysOperationLogMapper logMapper,
                       AdminUserMapper adminUserMapper,
                       AdminRoleMapper adminRoleMapper,
                       @Qualifier("opLogExecutor") ThreadPoolTaskExecutor opLogExecutor) {
        this.logMapper = logMapper;
        this.adminUserMapper = adminUserMapper;
        this.adminRoleMapper = adminRoleMapper;
        this.opLogExecutor = opLogExecutor;
    }

    /**
     * 切面在请求线程上调用的唯一入口。
     *
     * <p><b>成功与失败走不同的路，这不是不对称，是刻意的</b>：
     * <ul>
     *   <li><b>成功</b>且当前线程有活动事务 → 注册 `afterCommit`。事务后来被回滚（例如本方法
     *       嵌在一个更大的事务里，外层最终回滚）时整条丢弃——业务没发生，日志就不该留痕。</li>
     *   <li><b>失败</b> → 立即提交，**不挂 `afterCommit`**。失败的事务一定会回滚，挂上去就等于
     *       让回滚把「这次失败」一起抹掉；而 tech/01 §5.4 第 3 条要的恰恰是这条线索
     *       （「谁反复失败」）。失败记录描述的是**尝试**，回滚不改变「有人试过」。</li>
     * </ul>
     *
     * <p>两种挂法下都成立：挂 Controller 时事务已提交（两分支同效），
     * 挂 `@Transactional` 方法上时两个分支各自正确。
     */
    public void record(OpLogRecord record) {
        if (record.success() && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(record);
                }
            });
            return;
        }
        submit(record);
    }

    private void submit(OpLogRecord record) {
        try {
            opLogExecutor.execute(() -> write(record));
        } catch (Exception e) {
            log.error("[oplog] 提交异步落库失败，本条丢弃 module={} type={} traceId={}",
                    record.module(), record.opType(), record.traceId(), e);
        }
    }

    private void write(OpLogRecord record) {
        try {
            SysOperationLog row = new SysOperationLog()
                    .setAdminId(record.adminId())
                    .setModule(truncate(record.module(), 32))
                    .setOpType(truncate(record.opType(), 32))
                    .setBizId(record.bizId())
                    .setDescription(truncate(record.description(), 255))
                    .setAfterValue(record.afterValue())
                    .setResult(record.success() ? RESULT_SUCCESS : RESULT_FAIL)
                    .setErrorCode(record.errorCode())
                    .setIp(truncate(record.ip(), 64))
                    .setUa(truncate(record.ua(), 255))
                    .setTraceId(truncate(record.traceId(), 64))
                    .setCostMs(record.costMs())
                    .setCreateTime(record.occurredAt());
            fillOperator(row, record.adminId());
            logMapper.insert(row);
        } catch (Exception e) {
            log.error("[oplog] 落库失败 module={} type={} adminId={} traceId={}",
                    record.module(), record.opType(), record.adminId(), record.traceId(), e);
        }
    }

    /** 按 adminId 查补姓名与角色码；查不到不算失败，日志的主体是动作本身 */
    private void fillOperator(SysOperationLog row, Long adminId) {
        try {
            AdminUser admin = adminUserMapper.selectById(adminId);
            if (admin == null) {
                return;
            }
            row.setAdminName(truncate(admin.getRealName(), 32));
            if (admin.getRoleId() != null) {
                AdminRole role = adminRoleMapper.selectById(admin.getRoleId());
                row.setRoleCode(truncate(role == null ? null : role.getRoleCode(), 32));
            }
        } catch (Exception e) {
            log.warn("[oplog] 查补操作人信息失败 adminId={}", adminId, e);
        }
    }

    /**
     * 按列宽截断。不截会影响主记录：MySQL 严格模式下超长直接抛
     * `Data too long`，整条 insert 失败 —— 一个超长的 User-Agent 就能让日志消失。
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

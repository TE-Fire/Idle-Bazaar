package com.campus.core.oplog;

import com.campus.common.exception.BizException;
import com.campus.common.web.RequestContext;
import com.campus.common.web.RequestContextHolder;
import com.campus.core.context.AdminContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * `@OpLog` 的实现（tech/01 §5.4）。
 *
 * <p><b>职责边界</b>：本类只做「在请求线程上把要落库的东西抓成快照」——
 * 取登录人、取请求上下文、求 bizId、脱敏入参、量耗时、判成败，然后交给
 * {@link OpLogWriter}。**落库不在这里做**，异步写与线程池策略都在 Writer。
 * 这样切开是因为最容易出错的两件事（上下文抓取时机、异步边界）分别在两个类里，
 * 改一个不会碰另一个。
 *
 * <p><b>抓取时机是硬约束</b>：`AdminContext` 依赖 Sa-Token 的 request，
 * `RequestContextHolder` 是 ThreadLocal，两者在 `opLogExecutor` 的线程里都取不到。
 * 所以下面的 emit 必须在 `pjp.proceed()` 的**同一个线程**里跑完，
 * 交给线程池的只能是不含任何上下文引用的 {@link OpLogRecord}。
 *
 * <p><b>成功与失败两条路都记</b>：`proceed()` 正常返回 → `success = true`；
 * 抛异常 → `success = false` 并把异常原样抛回（绝不能被切面吞掉，
 * 否则业务失败会变成静默成功）。这里是 `@Around` 而不是「`@AfterReturning` +
 * `@AfterThrowing`」两个通知，就是为了让两条路共用同一段快照代码，
 * 避免以后只改了一边导致成功与失败记的字段不一样。
 */
@Slf4j
@Aspect
@Component
public class OpLogAspect {

    /** 与 `sys_operation_log.error_code` 的 varchar(32) 对齐 */
    private static final int MAX_ERROR_CODE_LENGTH = 32;

    private final OpLogMasker masker;

    private final OpLogWriter writer;

    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public OpLogAspect(OpLogMasker masker, OpLogWriter writer) {
        this.masker = masker;
        this.writer = writer;
    }

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint pjp, OpLog opLog) throws Throwable {
        // 下面这一组必须在请求线程上取，且要在 proceed 之前 —— 方法一跑完，
        // ContextHolder 仍在本线程上（清理发生在 Filter 的 finally），但登录态与入参
        // 以「进入方法时」为准更准，避免方法内改了入参导致日志与实际提交不符。
        Long adminId = currentAdminId();
        String afterValue = opLog.saveDiff() ? masker.toJson(arguments(pjp)) : null;
        Long bizId = resolveBizId(opLog, pjp);
        long beginNano = System.nanoTime();

        try {
            Object result = pjp.proceed();
            emit(opLog, adminId, afterValue, bizId, beginNano, true, null);
            return result;
        } catch (Throwable t) {
            emit(opLog, adminId, afterValue, bizId, beginNano, false, errorCodeOf(t));
            throw t;
        }
    }

    private void emit(OpLog opLog, Long adminId, String afterValue, Long bizId,
                      long beginNano, boolean success, String errorCode) {
        if (adminId == null) {
            // 本注解约定挂在挂了 @SaCheckPermission 的接口上，走到这里说明鉴权没拦住
            // （如方法被内部直调、或有人把注解挂到了匿名接口）。日志表 admin_id 非空，
            // 记不成，但也不能静默——留一行 warn 说明「本该记而没记」。
            log.warn("[oplog] 未取到登录态，本条不落库 module={} type={}", opLog.module(), opLog.type());
            return;
        }
        RequestContext ctx = RequestContextHolder.get();
        OpLogRecord record = new OpLogRecord(
                adminId,
                opLog.module(),
                opLog.type(),
                bizId,
                blankToNull(opLog.desc()),
                afterValue,
                success,
                errorCode,
                (int) ((System.nanoTime() - beginNano) / 1_000_000L),
                LocalDateTime.now(),
                ctx == null ? null : ctx.getClientIp(),
                ctx == null ? null : ctx.getUserAgent(),
                ctx == null ? null : ctx.getTraceId());
        writer.record(record);
    }

    /**
     * 取当前登录管理员。`AdminContext` 在未登录时抛 NotLoginException（→ 1001），
     * 这里转成 null 由 {@link #emit} 统一跳过——切面不该因为记日志失败而改变接口行为。
     */
    private Long currentAdminId() {
        try {
            return AdminContext.getAdminId();
        } catch (Exception e) {
            log.debug("[oplog] 取登录态失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 入参 → 「形参名: 值」的有序表，交给 {@link OpLogMasker} 序列化脱敏。
     *
     * <p>形参名靠编译期 `-parameters`（父 pom 已开启）。取不到名字时退化成 `arg0`/`arg1`，
     * 而不是丢字段——宁可名字丑，也别让日志缺内容。
     *
     * <p>把 Servlet 对象与文件上传排除掉：它们的序列化要么抛异常要么输出一整页二进制，
     * 对排查没价值。
     */
    private Map<String, Object> arguments(ProceedingJoinPoint pjp) {
        Object[] args = pjp.getArgs();
        if (args == null || args.length == 0) {
            return Map.of();
        }
        String[] names = null;
        if (pjp.getSignature() instanceof MethodSignature signature) {
            names = parameterNameDiscoverer.getParameterNames(signature.getMethod());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!isLoggable(args[i])) {
                continue;
            }
            String name = names != null && i < names.length && names[i] != null ? names[i] : "arg" + i;
            result.put(name, args[i]);
        }
        return result;
    }

    private boolean isLoggable(Object arg) {
        return arg != null
                && !(arg instanceof ServletRequest)
                && !(arg instanceof ServletResponse)
                && !(arg instanceof MultipartFile)
                && !(arg instanceof Errors);
    }

    /**
     * 求 `@OpLog#bizId()` 的 SpEL 值。表达式写错只打 warn 不抛异常——
     * 记不到 bizId 是内容缺失，把业务请求打挂才是事故。
     *
     * <p>返回值统一按数字取：`@PathVariable Long id` 直接是 Long，
     * 但若有人写 `#req.itemId` 而该字段是 String，也能兜住。
     */
    private Long resolveBizId(OpLog opLog, ProceedingJoinPoint pjp) {
        String expression = opLog.bizId();
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                    pjp.getTarget(), signature.getMethod(), pjp.getArgs(), parameterNameDiscoverer);
            Object value = spelParser.parseExpression(expression).getValue(context);
            if (value == null) {
                return null;
            }
            return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString().trim());
        } catch (Exception e) {
            log.warn("[oplog] bizId 表达式求值失败 expr={}，本条不记业务 id", expression, e);
            return null;
        }
    }

    private String errorCodeOf(Throwable t) {
        if (t instanceof BizException biz) {
            return String.valueOf(biz.getCode());
        }
        return abbreviate(t.getClass().getSimpleName());
    }

    private String abbreviate(String text) {
        return text.length() <= MAX_ERROR_CODE_LENGTH ? text : text.substring(0, MAX_ERROR_CODE_LENGTH);
    }

    private String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}

package com.campus.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.campus.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * 全局异常处理（tech/01 5.5）：
 * 业务异常不打堆栈；未知异常兜底为 5000 并回传 traceId，详情页不暴露堆栈。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：可预期，warn 级别 */
    @ExceptionHandler(BizException.class)
    public Result<?> handleBiz(BizException e) {
        log.warn("[biz] code={} msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage()).trace();
    }

    /** 参数校验：只返回第一个错误字段，不整包抛出 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? CommonErrorCode.PARAM_ERROR.getMsg() : fe.getField() + " " + fe.getDefaultMessage();
        return Result.fail(CommonErrorCode.PARAM_ERROR.getCode(), msg).trace();
    }

    @ExceptionHandler(BindException.class)
    public Result<?> handleBind(BindException e) {
        List<FieldError> errors = e.getBindingResult().getFieldErrors();
        String msg = errors.isEmpty() ? CommonErrorCode.PARAM_ERROR.getMsg()
                : errors.get(0).getField() + " " + errors.get(0).getDefaultMessage();
        return Result.fail(CommonErrorCode.PARAM_ERROR.getCode(), msg).trace();
    }

    /** Sa-Token 未登录 / 无权限 / 角色不足 */
    @ExceptionHandler(NotLoginException.class)
    public Result<?> handleNotLogin(NotLoginException e) {
        return Result.fail(CommonErrorCode.UNAUTHORIZED.getCode(), CommonErrorCode.UNAUTHORIZED.getMsg()).trace();
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public Result<?> handleNotPermission(Exception e) {
        log.warn("[perm] {}", e.getMessage());
        return Result.fail(CommonErrorCode.FORBIDDEN.getCode(), CommonErrorCode.FORBIDDEN.getMsg()).trace();
    }

    /**
     * 静态资源不存在：浏览器总会请求 /favicon.ico，而 Spring Boot 3.2 起找不到静态资源
     * 会抛 NoResourceFoundException（旧版是静默 404）。不接住它就会落进下面的 Exception
     * 兜底，每次开页面刷一条 ERROR + 全栈，日志很快被污染。这里按 404 warn 一行即可。
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public Result<?> handleNoResource(org.springframework.web.servlet.resource.NoResourceFoundException e) {
        log.warn("[404] {}", e.getMessage());
        return Result.fail(CommonErrorCode.NOT_FOUND.getCode(), CommonErrorCode.NOT_FOUND.getMsg()).trace();
    }

    /** 兜底：未知异常统一 5000，返回 traceId 供排查，不暴露堆栈 */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("[sys] traceId={} uri_error", MDC.get("traceId"), e);
        return Result.fail(CommonErrorCode.SYSTEM_ERROR.getCode(), CommonErrorCode.SYSTEM_ERROR.getMsg()).trace();
    }
}

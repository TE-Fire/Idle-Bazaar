package com.campus.common.result;

import com.campus.common.exception.IErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.slf4j.MDC;

import java.io.Serializable;

/**
 * 统一响应体：{code, msg, data, traceId}
 *
 * <p>对应 tech/01 5.3 规则 4：null 字段不输出，前端少做判空；
 * traceId 用于前后端联调时对齐日志。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 0 表示成功，非 0 见各模块错误码枚举（实现 {@link IErrorCode} 契约） */
    private int code;

    private String msg;

    private T data;

    /** 链路追踪 ID，从 MDC 取，由 TraceIdFilter 写入 */
    private String traceId;

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.setCode(0);
        r.setMsg("成功");
        r.setData(data);
        return r;
    }

    public static <T> Result<T> fail(int code, String msg) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setTraceId(MDC.get("traceId"));
        return r;
    }

    /** 失败入参直接吃错误码契约，与 BizException / BizAssert 同源 */
    public static <T> Result<T> fail(IErrorCode ec) {
        return fail(ec.getCode(), ec.getMsg());
    }

    /** 失败并覆盖提示文案（保留原错误码），如 1000 + 「验证码已失效，请刷新」 */
    public static <T> Result<T> fail(IErrorCode ec, String detail) {
        return fail(ec.getCode(), detail);
    }

    /** 失败时把当前 traceId 补上，便于用户截图反馈后直接查日志 */
    public Result<T> trace() {
        this.traceId = MDC.get("traceId");
        return this;
    }
}

package com.campus.common.exception;

/**
 * 错误码契约：整个后端的「错误描述」只认这个接口，不认任何具体枚举。
 *
 * <p>契约链（谁实现它，谁就能进这条链）：
 * <pre>
 *   IErrorCode  ◀── 实现 ── CommonErrorCode / ItemErrorCode / OrderErrorCode ...（各模块错误码枚举）
 *       │
 *       ├── BizException(IErrorCode)      业务异常构造器入参
 *       ├── Result.fail(IErrorCode)       统一响应体失败入参
 *       └── BizAssert.isTrue(cond, IErrorCode)  断言入参
 * </pre>
 * 好处：common 不需要知道每个业务的错误码——业务模块新增错误码枚举，
 * 只要实现本接口，异常/响应/断言三处全部天然可用，common 零改动。
 */
public interface IErrorCode {

    int getCode();

    String getMsg();

    /** 快捷抛出：throw ItemErrorCode.ITEM_NOT_FOUND.ex(); */
    default BizException ex() {
        return new BizException(this);
    }

    /** 快捷抛出并补充细节：throw ItemErrorCode.ITEM_NOT_ON_SALE.ex("商品#" + id); */
    default BizException ex(String detail) {
        return new BizException(this, detail);
    }
}

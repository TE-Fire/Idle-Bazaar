package com.campus.common.exception;

/**
 * 错误码契约：整个后端的「错误描述」只认这个接口，不认任何具体枚举。
 *
 * <p>各模块错误码枚举（CommonErrorCode / ItemErrorCode / OrderErrorCode …）实现它，
 * 于是在 BizException、Result.fail、BizAssert 三处天然可用；新增错误码枚举时 common 零改动。
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

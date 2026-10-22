package com.campus.core.errcode;

import com.campus.common.exception.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 字典模块错误码（段位 60xx，登记于 tech/01 5.5）。
 *
 * <p>只放了「声明式校验（`@NotBlank` / `@Pattern`）管不了」的那些：都要跨记录查库
 * （父级是不是一级、校区存不存在、同层有没有重名），注解做不了，只能进 Service 用 `BizAssert`。
 * 纯格式问题（名称为空、色值不是 #RRGGBB）在 `*SaveReq` 上已被注解拦掉，返回 1000，不占这里。
 */
@Getter
@AllArgsConstructor
public enum DictErrorCode implements IErrorCode {

    DICT_NOT_FOUND(6001, "字典项不存在"),
    DICT_NAME_DUPLICATE(6002, "同层级下已存在同名记录"),
    DICT_PARENT_INVALID(6003, "上级分类本身不是一级分类"),
    DICT_PARENT_DISABLED(6004, "上级分类已停用，不能挂到它下面"),
    DICT_CAMPUS_INVALID(6005, "所属校区不存在或已停用"),
    /** 商品发布/编辑引用校验：发布时四项须启用；编辑时只查存在（历史商品可引用停用项） */
    DICT_REF_INVALID(6006, "商品引用的字典项不存在或已停用"),
    ;

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return msg;
    }

    private final int code;
    private final String msg;
}

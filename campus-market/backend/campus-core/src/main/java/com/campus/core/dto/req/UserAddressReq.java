package com.campus.core.dto.req;

import com.campus.core.enums.AddressKindEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户地址新增 / 修改（收货 SHIP / 自提 PICKUP 复用同一结构）。
 *
 * <p>`kind` 决定地址归属：收货地址由买家维护、自提地址由卖家维护，二者互不可见，
 * 由 Service 按当前登录人 + kind 隔离。仅 `detail` 必填（联系人与电话在提交层再校验非空）。
 */
@Data
public class UserAddressReq {

    /** 1 收货地址 2 自提地址（见 AddressKindEnum） */
    @NotNull
    private Integer kind;

    @NotBlank
    private String contactName;

    @NotBlank
    private String contactPhone;

    private String province;

    private String city;

    private String district;

    @NotBlank
    private String detail;

    /** 是否设为同类默认地址 */
    private Integer isDefault;
}

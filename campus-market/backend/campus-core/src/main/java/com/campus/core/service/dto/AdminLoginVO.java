package com.campus.core.service.dto;

import lombok.Data;

import java.util.List;

/**
 * 登录结果。
 *
 * <p>permCodes 直接下发给前端，前端按它渲染菜单与按钮（data-perm）；
 * 后端每个写接口仍会独立校验，不依赖前端传参。
 *
 * <p>注意 id 是 Long，Jackson 已全局转成 String，前端不要定义成 number。
 */
@Data
public class AdminLoginVO {

    private Long adminId;

    private String realName;

    private String roleCode;

    private String tokenName;

    private String tokenValue;

    /** 该角色持有的权限点，与 PermCodes 常量同名 */
    private List<String> permCodes;
}

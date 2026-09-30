package com.campus.admin.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import com.campus.core.satoken.StpAdminUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 管理端接口鉴权拦截器（tech/01 8.1，business/03 A9 权限模型）。
 *
 * <p>必须有这个类：Sa-Token 的 `@SaCheckLogin` / `@SaCheckPermission` 不会自动生效，
 * 它们是给拦截器调用的注解处理器，容器里没注册 SaInterceptor 时注解只是摆设——
 * 本项目此前就缺这一步，未登录直接 GET `/admin/admins/pending` 也返回 200。
 *
 * <p>规则：`/admin/**` 全部要求登录，`/admin/auth/**` 排除（登录/注册/找回/验证码本身
 * 不能要求登录，否则未登录连登录接口都调不了）。权限点仍由方法上的 `@SaCheckPermission`
 * 校验——SaInterceptor 先跑 handler 再跑注解，这里只补登录态。
 *
 * <p>只管 `/admin/**`：学生端 `/api/**` 目前只有健康检查，加统一登录会误伤联调，
 * 等接口成型后在同一位置补一条即可。
 */
@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpAdminUtil.checkLogin()))
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/auth/**");
    }
}

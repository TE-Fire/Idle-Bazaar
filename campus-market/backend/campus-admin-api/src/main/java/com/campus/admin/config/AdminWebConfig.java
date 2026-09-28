package com.campus.admin.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import com.campus.core.satoken.StpAdminUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 管理端接口鉴权拦截器（tech/01 8.1，business/03 A9 权限模型）。
 *
 * <p>【为什么必须有这个类】Sa-Token 的 {@code @SaCheckLogin} / {@code @SaCheckPermission}
 * **不会自动生效**——它们是给拦截器/过滤器调用的「注解处理器」，容器里没有注册
 * SaInterceptor 时注解只是摆设。本项目此前就缺这一步，实测未登录直接 GET
 * `/admin/admins/pending` 也返回 200（data 空），等于待分配管理员名单对外裸奔。
 *
 * <p>两条拦截规则：
 * <ol>
 *   <li>`/admin/**` 全部要求登录（handler 里的 checkLogin）；</li>
 *   <li>`/admin/auth/**` 排除——登录、注册、找回密码、验证码本身不能要求登录，
 *       否则未登录用户连登录接口都调不了（典型的先有鸡还是先有蛋）。</li>
 * </ol>
 *
 * <p>权限点由方法上的 {@code @SaCheckPermission} 继续校验：SaInterceptor 的执行顺序是
 * 「先跑 handler，再跑注解」，所以这里只补登录态，权限判定仍写在各 Controller 方法上。
 *
 * <p>【为什么只管 /admin/**】学生端 `/api/**` 目前只有健康检查，业务接口尚未落，
 * 此时给 `/api/**` 加统一登录会误伤后续联调；等学生端接口成型后在同一位置补一条即可。
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

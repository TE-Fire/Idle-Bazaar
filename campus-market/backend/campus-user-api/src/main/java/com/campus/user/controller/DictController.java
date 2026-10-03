package com.campus.user.controller;

import com.campus.common.result.Result;
import com.campus.core.dto.resp.DictAllVO;
import com.campus.core.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典下发（学生端只读）。
 *
 * <p><b>免登录</b>：发布页在未登录时也要能渲染出分类 / 成色 / 校区下拉，
 * 加了登录拦截等于逼用户先登录才能填发布表单。`AdminWebConfig` 目前只拦 `/admin/**`，
 * 这正是它的设计意图（见该类注释）。
 *
 * <p><b>只下发启用项</b>：停用的字典不给 C 端选，这是"停用而非删除"能成立的前提
 * ——字典被商品引用着，真删会留下孤儿数据，停用后只是不再出现在可选列表里。
 *
 * <p>缓存见 `DictService#all`：60s TTL 只是兜底，写操作提交后会主动失效。
 */
@Tag(name = "学生端 · 字典")
@RestController
@RequestMapping("/api/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    @Operation(summary = "全部字典", description = "五张字典的启用项一次下发；分类为平铺结构（带 parentId），树由前端组装")
    @GetMapping("/all")
    public Result<DictAllVO> all() {
        return Result.ok(dictService.all());
    }
}

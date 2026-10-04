package com.campus.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.campus.common.constant.PermCodes;
import com.campus.common.result.Result;
import com.campus.core.dto.req.DictCampusSaveReq;
import com.campus.core.dto.req.DictCategorySaveReq;
import com.campus.core.dto.req.DictCollegeSaveReq;
import com.campus.core.dto.req.DictGradeSaveReq;
import com.campus.core.dto.req.DictGradeStatusReq;
import com.campus.core.dto.req.DictMeetPointSaveReq;
import com.campus.core.dto.req.DictStatusReq;
import com.campus.core.dto.resp.DictCampusVO;
import com.campus.core.dto.resp.DictCategoryVO;
import com.campus.core.dto.resp.DictCollegeVO;
import com.campus.core.dto.resp.DictGradeVO;
import com.campus.core.dto.resp.DictMeetPointVO;
import com.campus.core.satoken.StpAdminUtil;
import com.campus.core.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字典维护（管理端 A7 内容管理，流程见 notes/07）。
 *
 * <p>权限：全部挂 `content.edit`。该权限点 2026-09-30 起已从运营角色移除，只有超管持有——
 * 字典一改三端同步变，写权限必须收口。不需要新造权限码，改的是角色授权。
 *
 * <p>读接口也挂权限：管理端字典列表只给维护者看，没有只读角色也要看字典列表的场景。
 * 学生端那一路走 `/api/dict/all`（免登录）。
 *
 * <p>新增与编辑是两个接口，body 里没有 id：改哪一条由 URL 路径的 `{id}` 决定，
 * Service 对应拆成 `createXxx(req)` / `updateXxx(id, req)`。
 * 成色是例外——`code` 是业务主键（A–D，真数据），不是路由用的代理键，所以留在 body 里。
 *
 * <p>写接口的 `@OpLog` 待该注解落地后统一补（CR 清单第 15 条：不留裸 TODO）。
 */
@Tag(name = "管理端 · 字典维护")
@RestController
@RequestMapping("/admin/dict")
@RequiredArgsConstructor
public class DictAdminController {

    private final DictService dictService;

    /* ── 分类 ── */

    @Operation(summary = "分类列表", description = "两级平铺（带 parentId，一级为 null），含停用项；树由前端组装")
    @GetMapping("/categories")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    public Result<List<DictCategoryVO>> categories() {
        return Result.ok(dictService.categories());
    }

    @Operation(summary = "新增分类", description = "parentId 为空=一级；非空时父级必须存在、启用且本身是一级")
    @PostMapping("/category")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    public Result<Long> createCategory(@Valid @RequestBody DictCategorySaveReq req) {
        return Result.ok(dictService.createCategory(req));
    }

    @Operation(summary = "编辑分类", description = "改名 / 改图标 / 改排序 / 改上级；同名会报 6002")
    @PutMapping("/category/{id}")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    public Result<Void> updateCategory(@PathVariable("id") Long id,
                                       @Valid @RequestBody DictCategorySaveReq req) {
        dictService.updateCategory(id, req);
        return Result.ok();
    }

    @Operation(summary = "启停分类", description = "停用一级分类会连带停用其下二级；启用不连带")
    @PostMapping("/category/{id}/status")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    public Result<Void> updateCategoryStatus(@PathVariable("id") Long id,
                                             @Valid @RequestBody DictStatusReq req) {
        dictService.updateCategoryStatus(id, req.getEnabled());
        return Result.ok();
    }

    /* ── 成色 ── */

    @Operation(summary = "成色列表", description = "A–D 四档，含文案与徽章色值，含停用项")
    @GetMapping("/grades")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    public Result<List<DictGradeVO>> grades() {
        return Result.ok(dictService.grades());
    }

    @Operation(summary = "新增 / 编辑成色", description = "按 code（A–D）定位，不存在则新增；color 为空时取 #8E8E93")
    @PutMapping("/grade/{code}")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    public Result<Void> saveGrade(@PathVariable("code") String code,
                                  @Valid @RequestBody DictGradeSaveReq req) {
        req.setCode(code);
        dictService.saveGrade(req);
        return Result.ok();
    }

    @Operation(summary = "启停成色", description = "按 code 定位；停用后该成色不出现在发布页")
    @PostMapping("/grade/{code}/status")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    public Result<Void> updateGradeStatus(@PathVariable("code") String code,
                                          @Valid @RequestBody DictGradeStatusReq req) {
        req.setCode(code);
        dictService.updateGradeStatus(code, req.getEnabled());
        return Result.ok();
    }

    /* ── 面交点 ── */

    @Operation(summary = "面交点列表", description = "带冗余 campusName，省掉前端二次查询")
    @GetMapping("/meet-points")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    public Result<List<DictMeetPointVO>> meetPoints() {
        return Result.ok(dictService.meetPoints());
    }

    @Operation(summary = "新增面交点", description = "campusId 必须存在且启用")
    @PostMapping("/meet-point")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    public Result<Long> createMeetPoint(@Valid @RequestBody DictMeetPointSaveReq req) {
        return Result.ok(dictService.createMeetPoint(req));
    }

    @Operation(summary = "编辑面交点")
    @PutMapping("/meet-point/{id}")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    public Result<Void> updateMeetPoint(@PathVariable("id") Long id,
                                        @Valid @RequestBody DictMeetPointSaveReq req) {
        dictService.updateMeetPoint(id, req);
        return Result.ok();
    }

    @Operation(summary = "启停面交点")
    @PostMapping("/meet-point/{id}/status")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    public Result<Void> updateMeetPointStatus(@PathVariable("id") Long id,
                                              @Valid @RequestBody DictStatusReq req) {
        dictService.updateMeetPointStatus(id, req.getEnabled());
        return Result.ok();
    }

    /* ── 学院 ── */

    @Operation(summary = "学院列表", description = "带 campusId 与冗余 campusName")
    @GetMapping("/colleges")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    public Result<List<DictCollegeVO>> colleges() {
        return Result.ok(dictService.colleges());
    }

    @Operation(summary = "新增学院", description = "campusId 为空表示未指定校区（落库 0）")
    @PostMapping("/college")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    public Result<Long> createCollege(@Valid @RequestBody DictCollegeSaveReq req) {
        return Result.ok(dictService.createCollege(req));
    }

    @Operation(summary = "编辑学院")
    @PutMapping("/college/{id}")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    public Result<Void> updateCollege(@PathVariable("id") Long id,
                                      @Valid @RequestBody DictCollegeSaveReq req) {
        dictService.updateCollege(id, req);
        return Result.ok();
    }

    @Operation(summary = "启停学院")
    @PostMapping("/college/{id}/status")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    public Result<Void> updateCollegeStatus(@PathVariable("id") Long id,
                                            @Valid @RequestBody DictStatusReq req) {
        dictService.updateCollegeStatus(id, req.getEnabled());
        return Result.ok();
    }

    /* ── 校区 ── */

    @Operation(summary = "校区列表")
    @GetMapping("/campuses")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    public Result<List<DictCampusVO>> campuses() {
        return Result.ok(dictService.campuses());
    }

    @Operation(summary = "新增校区")
    @PostMapping("/campus")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    public Result<Long> createCampus(@Valid @RequestBody DictCampusSaveReq req) {
        return Result.ok(dictService.createCampus(req));
    }

    @Operation(summary = "编辑校区")
    @PutMapping("/campus/{id}")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    public Result<Void> updateCampus(@PathVariable("id") Long id,
                                     @Valid @RequestBody DictCampusSaveReq req) {
        dictService.updateCampus(id, req);
        return Result.ok();
    }

    @Operation(summary = "启停校区", description = "停用会连带停用该校区下的面交点与学院；启用不连带")
    @PostMapping("/campus/{id}/status")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    public Result<Void> updateCampusStatus(@PathVariable("id") Long id,
                                           @Valid @RequestBody DictStatusReq req) {
        dictService.updateCampusStatus(id, req.getEnabled());
        return Result.ok();
    }
}

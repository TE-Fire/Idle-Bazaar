package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.BizException;
import com.campus.common.exception.CommonErrorCode;
import com.campus.core.dto.req.DictCampusSaveReq;
import com.campus.core.dto.req.DictCategorySaveReq;
import com.campus.core.dto.req.DictCollegeSaveReq;
import com.campus.core.dto.req.DictGradeSaveReq;
import com.campus.core.dto.req.DictMeetPointSaveReq;
import com.campus.core.dto.resp.DictAllVO;
import com.campus.core.dto.resp.DictCampusVO;
import com.campus.core.dto.resp.DictCategoryVO;
import com.campus.core.dto.resp.DictCollegeVO;
import com.campus.core.dto.resp.DictGradeVO;
import com.campus.core.dto.resp.DictMeetPointVO;
import com.campus.core.entity.DictCampus;
import com.campus.core.entity.DictCategory;
import com.campus.core.entity.DictCollege;
import com.campus.core.entity.DictGrade;
import com.campus.core.entity.DictMeetPoint;
import com.campus.core.errcode.DictErrorCode;
import com.campus.core.mapper.DictCampusMapper;
import com.campus.core.mapper.DictCategoryMapper;
import com.campus.core.mapper.DictCollegeMapper;
import com.campus.core.mapper.DictGradeMapper;
import com.campus.core.mapper.DictMeetPointMapper;
import com.campus.core.redis.DictRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 字典模块五张表的读写（business/03 F-A7，流程见 notes/07）。
 *
 * <p><b>权限</b>：由 Controller 的 `@SaCheckPermission(content.edit)` 收口，本类不重复判断。
 * 本类也可能被内部任务调用，权限判断分散到多处容易漏。
 *
 * <p><b>新增与编辑是两个方法</b>：`createXxx(req)` / `updateXxx(id, req)`。
 * id 是 `updateXxx` 的必填参数（来自 URL 路径），方法内不判断它是否为空。
 *
 * <p><b>缓存</b>：只有 {@link #all()}（`/api/dict/all`）读缓存，管理端列表直查库——
 * 两者返回的内容不同（含不含停用项），不能共用一个 key。
 * 写操作统一调用 {@link #evictCache()} 失效。
 *
 * <p><b>事务</b>：只有「停用一级分类连带子级」「停用校区连带面交点与学院」这类多条 UPDATE
 * 用 `TransactionTemplate`。单条 INSERT / UPDATE 不加事务，它本身是原子的。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictService {

    /** 缓存有效期。正常由写操作主动失效，这个值只用于 del 失败时限制脏数据存活时间 */
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    /** 成色默认色值，与 schema.sql 的 DEFAULT 一致 */
    private static final String DEFAULT_GRADE_COLOR = "#8E8E93";

    private static final int DEFAULT_SORT = 99;

    private final DictCategoryMapper categoryMapper;
    private final DictGradeMapper gradeMapper;
    private final DictMeetPointMapper meetPointMapper;
    private final DictCollegeMapper collegeMapper;
    private final DictCampusMapper campusMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PlatformTransactionManager txManager;

    /* ══ 一、分类（两级树） ══════════════════════════════════ */

    /** 管理端列表：含停用项，按 sort、id 排序 */
    public List<DictCategoryVO> categories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<DictCategory>()
                        .eq(DictCategory::getDeleted, 0)
                        .orderByAsc(DictCategory::getSort)
                        .orderByAsc(DictCategory::getId))
                .stream()
                .map(this::toCategoryVO)
                .toList();
    }

    /** 新增分类，返回新 id。`parentId` 不传 = 一级（库里存 0），传了 = 二级并校验父级 */
    public Long createCategory(DictCategorySaveReq req) {
        String name = req.getName().trim();
        long parentIdInDb = parseIdOrZero(req.getParentId());
        if (parentIdInDb != 0L) {
            requireParent(parentIdInDb, null);
        }
        BizAssert.isTrue(!existsCategoryName(name, parentIdInDb, null), DictErrorCode.DICT_NAME_DUPLICATE);

        DictCategory row = new DictCategory()
                .setParentId(parentIdInDb)
                .setName(name)
                .setIcon(req.getIcon())
                .setSort(req.getSort() == null ? DEFAULT_SORT : req.getSort())
                .setStatus(1);
        categoryMapper.insert(row);
        evictCache();
        return row.getId();
    }

    /** 编辑分类。id 来自 URL 路径，不存在时抛 6001 */
    public void updateCategory(Long id, DictCategorySaveReq req) {
        String name = req.getName().trim();
        long parentIdInDb = parseIdOrZero(req.getParentId());

        DictCategory row = requireCategory(id);

        BizAssert.isTrue(!existsCategoryName(name, parentIdInDb, id), DictErrorCode.DICT_NAME_DUPLICATE);

        categoryMapper.updateById(new DictCategory()
                .setId(id)
                .setParentId(parentIdInDb)
                .setName(name)
                .setIcon(req.getIcon())
                .setSort(req.getSort() == null ? row.getSort() : req.getSort()));
        evictCache();
    }

    /**
     * 启停分类。停用一级分类时连带停用它的子分类，避免出现「父停用、子仍可选」的状态。
     * 启用不连带：启用是放宽限制，不该一次放宽一大批子分类。
     */
    public void updateCategoryStatus(Long id, boolean enabled) {
        DictCategory row = requireCategory(id);
        if (isRoot(row) && !enabled) {
            inTransaction(() -> {
                setCategoryStatus(id, enabled);
                categoryMapper.update(null, new LambdaUpdateWrapper<DictCategory>()
                        .eq(DictCategory::getParentId, id)
                        .eq(DictCategory::getDeleted, 0)
                        .set(DictCategory::getStatus, 0));
            });
            return;
        }
        setCategoryStatus(id, enabled);
        evictCache();
    }

    /* ══ 二、成色（按 code 定位） ════════════════════════════ */

    public List<DictGradeVO> grades() {
        return gradeMapper.selectList(new LambdaQueryWrapper<DictGrade>()
                        .eq(DictGrade::getDeleted, 0)
                        .orderByAsc(DictGrade::getSort)
                        .orderByAsc(DictGrade::getId))
                .stream()
                .map(this::toGradeVO)
                .toList();
    }

    /**
     * 按 `code` 新增或编辑成色（存在则改，不存在则插）。
     *
     * <p>不拆成 create / update：四档成色是固定集合（A–D），空库初次维护时若只认编辑，
     * 就得先手工插数据才能改文案。
     */
    public void saveGrade(DictGradeSaveReq req) {
        String code = req.getCode().trim().toUpperCase(Locale.ROOT);
        String color = req.getColor() == null || req.getColor().isBlank()
                ? DEFAULT_GRADE_COLOR
                : req.getColor().trim();

        DictGrade row = gradeMapper.selectOne(new LambdaQueryWrapper<DictGrade>()
                .eq(DictGrade::getCode, code)
                .eq(DictGrade::getDeleted, 0));

        if (row == null) {
            gradeMapper.insert(new DictGrade()
                    .setCode(code)
                    .setName(req.getName().trim())
                    .setColor(color)
                    .setDescription(req.getDescription())
                    .setSort(req.getSort() == null ? DEFAULT_SORT : req.getSort())
                    .setStatus(1));
        } else {
            gradeMapper.update(null, new LambdaUpdateWrapper<DictGrade>()
                    .eq(DictGrade::getId, row.getId())
                    .set(DictGrade::getName, req.getName().trim())
                    .set(DictGrade::getColor, color)
                    .set(DictGrade::getDescription, req.getDescription())
                    .set(DictGrade::getSort, req.getSort() == null ? row.getSort() : req.getSort()));
        }
        evictCache();
    }

    public void updateGradeStatus(String code, boolean enabled) {
        DictGrade row = gradeMapper.selectOne(new LambdaQueryWrapper<DictGrade>()
                .eq(DictGrade::getCode, code.trim().toUpperCase(Locale.ROOT))
                .eq(DictGrade::getDeleted, 0));
        BizAssert.notNull(row, DictErrorCode.DICT_NOT_FOUND);
        gradeMapper.update(null, new LambdaUpdateWrapper<DictGrade>()
                .eq(DictGrade::getId, row.getId())
                .set(DictGrade::getStatus, enabled ? 1 : 0));
        evictCache();
    }

    /* ══ 三、面交点 ═════════════════════════════════════════ */

    public List<DictMeetPointVO> meetPoints() {
        Map<Long, String> campusNames = campusNameMap();
        return meetPointMapper.selectList(new LambdaQueryWrapper<DictMeetPoint>()
                        .eq(DictMeetPoint::getDeleted, 0)
                        .orderByAsc(DictMeetPoint::getSort)
                        .orderByAsc(DictMeetPoint::getId))
                .stream()
                .map(r -> toMeetPointVO(r, campusNames))
                .toList();
    }

    /** 新增面交点，返回新 id。`campusId` 必填，不传或传 0 时抛 6006 */
    public Long createMeetPoint(DictMeetPointSaveReq req) {
        String name = req.getName().trim();
        long campusId = parseIdOrZero(req.getCampusId());
        requireEnabledCampus(campusId);
        BizAssert.isTrue(!existsMeetPointName(name, campusId, null), DictErrorCode.DICT_NAME_DUPLICATE);

        DictMeetPoint row = new DictMeetPoint()
                .setCampusId(campusId)
                .setName(name)
                .setOpenHours(req.getOpenHours())
                .setSort(req.getSort() == null ? DEFAULT_SORT : req.getSort())
                .setStatus(1);
        meetPointMapper.insert(row);
        evictCache();
        return row.getId();
    }

    public void updateMeetPoint(Long id, DictMeetPointSaveReq req) {
        String name = req.getName().trim();
        long campusId = parseIdOrZero(req.getCampusId());
        requireEnabledCampus(campusId);

        DictMeetPoint row = requireMeetPoint(id);
        BizAssert.isTrue(!existsMeetPointName(name, campusId, id), DictErrorCode.DICT_NAME_DUPLICATE);
        meetPointMapper.updateById(new DictMeetPoint()
                .setId(id)
                .setCampusId(campusId)
                .setName(name)
                .setOpenHours(req.getOpenHours())
                .setSort(req.getSort() == null ? row.getSort() : req.getSort()));
        evictCache();
    }

    public void updateMeetPointStatus(Long id, boolean enabled) {
        requireMeetPoint(id);
        meetPointMapper.update(null, new LambdaUpdateWrapper<DictMeetPoint>()
                .eq(DictMeetPoint::getId, id)
                .eq(DictMeetPoint::getDeleted, 0)
                .set(DictMeetPoint::getStatus, enabled ? 1 : 0));
        evictCache();
    }

    /* ══ 四、学院 ═══════════════════════════════════════════ */

    public List<DictCollegeVO> colleges() {
        Map<Long, String> campusNames = campusNameMap();
        return collegeMapper.selectList(new LambdaQueryWrapper<DictCollege>()
                        .eq(DictCollege::getDeleted, 0)
                        .orderByAsc(DictCollege::getSort)
                        .orderByAsc(DictCollege::getId))
                .stream()
                .map(r -> toCollegeVO(r, campusNames))
                .toList();
    }

    /** 新增学院，返回新 id。`campusId` 不传 = 未指定校区（库里存 0） */
    public Long createCollege(DictCollegeSaveReq req) {
        String name = req.getName().trim();
        long campusIdInDb = parseIdOrZero(req.getCampusId());
        if (campusIdInDb != 0L) {
            requireEnabledCampus(campusIdInDb);
        }
        BizAssert.isTrue(!existsCollegeName(name, null), DictErrorCode.DICT_NAME_DUPLICATE);

        DictCollege row = new DictCollege()
                .setCampusId(campusIdInDb)
                .setName(name)
                .setSort(req.getSort() == null ? DEFAULT_SORT : req.getSort())
                .setStatus(1);
        collegeMapper.insert(row);
        evictCache();
        return row.getId();
    }

    public void updateCollege(Long id, DictCollegeSaveReq req) {
        String name = req.getName().trim();
        long campusIdInDb = parseIdOrZero(req.getCampusId());
        if (campusIdInDb != 0L) {
            requireEnabledCampus(campusIdInDb);
        }

        DictCollege row = requireCollege(id);
        BizAssert.isTrue(!existsCollegeName(name, id), DictErrorCode.DICT_NAME_DUPLICATE);
        collegeMapper.updateById(new DictCollege()
                .setId(id)
                .setCampusId(campusIdInDb)
                .setName(name)
                .setSort(req.getSort() == null ? row.getSort() : req.getSort()));
        evictCache();
    }

    public void updateCollegeStatus(Long id, boolean enabled) {
        requireCollege(id);
        collegeMapper.update(null, new LambdaUpdateWrapper<DictCollege>()
                .eq(DictCollege::getId, id)
                .eq(DictCollege::getDeleted, 0)
                .set(DictCollege::getStatus, enabled ? 1 : 0));
        evictCache();
    }

    /* ══ 五、校区（引用链顶端） ══════════════════════════════ */

    public List<DictCampusVO> campuses() {
        return campusMapper.selectList(new LambdaQueryWrapper<DictCampus>()
                        .eq(DictCampus::getDeleted, 0)
                        .orderByAsc(DictCampus::getSort)
                        .orderByAsc(DictCampus::getId))
                .stream()
                .map(this::toCampusVO)
                .toList();
    }

    /** 新增校区，返回新 id */
    public Long createCampus(DictCampusSaveReq req) {
        String name = req.getName().trim();
        BizAssert.isTrue(!existsCampusName(name, null), DictErrorCode.DICT_NAME_DUPLICATE);

        DictCampus row = new DictCampus()
                .setName(name)
                .setSort(req.getSort() == null ? DEFAULT_SORT : req.getSort())
                .setStatus(1);
        campusMapper.insert(row);
        evictCache();
        return row.getId();
    }

    public void updateCampus(Long id, DictCampusSaveReq req) {
        String name = req.getName().trim();
        DictCampus row = requireCampus(id);
        BizAssert.isTrue(!existsCampusName(name, id), DictErrorCode.DICT_NAME_DUPLICATE);
        campusMapper.updateById(new DictCampus()
                .setId(id)
                .setName(name)
                .setSort(req.getSort() == null ? row.getSort() : req.getSort()));
        evictCache();
    }

    /**
     * 启停校区。停用时连带停用该校区下的面交点与学院，避免出现「校区停用、面交点仍可选」。
     * 启用不连带，理由同分类。
     */
    public void updateCampusStatus(Long id, boolean enabled) {
        requireCampus(id);
        if (!enabled) {
            inTransaction(() -> {
                setCampusStatus(id, false);
                meetPointMapper.update(null, new LambdaUpdateWrapper<DictMeetPoint>()
                        .eq(DictMeetPoint::getCampusId, id)
                        .eq(DictMeetPoint::getDeleted, 0)
                        .set(DictMeetPoint::getStatus, 0));
                collegeMapper.update(null, new LambdaUpdateWrapper<DictCollege>()
                        .eq(DictCollege::getCampusId, id)
                        .eq(DictCollege::getDeleted, 0)
                        .set(DictCollege::getStatus, 0));
            });
            return;
        }
        setCampusStatus(id, true);
        evictCache();
    }

    /* ══ 六、只读下发（走缓存） ══════════════════════════════ */

    /**
     * 五张字典的启用项聚合，给 `/api/dict/all` 用。
     *
     * <p>Redis 读写异常时不抛错，改为直接查库返回。字典是发布页的依赖项，
     * Redis 一次抖动就让发布页 500 不划算。
     */
    public DictAllVO all() {
        Object cached = null;
        try {
            cached = redisTemplate.opsForValue().get(DictRedisKeys.ALL);
        } catch (Exception e) {
            log.warn("[dict] 读缓存失败，降级直查库", e);
        }
        if (cached instanceof DictAllVO vo) {
            return vo;
        }

        DictAllVO fresh = loadEnabledAll();
        try {
            redisTemplate.opsForValue().set(DictRedisKeys.ALL, fresh, CACHE_TTL);
        } catch (Exception e) {
            log.warn("[dict] 写缓存失败，本次直查结果仍然可用", e);
        }
        return fresh;
    }

    private DictAllVO loadEnabledAll() {
        Map<Long, String> campusNames = campusNameMap();

        DictAllVO vo = new DictAllVO();
        vo.setCategories(categoryMapper.selectList(new LambdaQueryWrapper<DictCategory>()
                        .eq(DictCategory::getDeleted, 0)
                        .eq(DictCategory::getStatus, 1)
                        .orderByAsc(DictCategory::getSort)
                        .orderByAsc(DictCategory::getId))
                .stream().map(this::toCategoryVO).toList());
        vo.setGrades(gradeMapper.selectList(new LambdaQueryWrapper<DictGrade>()
                        .eq(DictGrade::getDeleted, 0)
                        .eq(DictGrade::getStatus, 1)
                        .orderByAsc(DictGrade::getSort)
                        .orderByAsc(DictGrade::getId))
                .stream().map(this::toGradeVO).toList());
        vo.setMeetPoints(meetPointMapper.selectList(new LambdaQueryWrapper<DictMeetPoint>()
                        .eq(DictMeetPoint::getDeleted, 0)
                        .eq(DictMeetPoint::getStatus, 1)
                        .orderByAsc(DictMeetPoint::getSort)
                        .orderByAsc(DictMeetPoint::getId))
                .stream().map(r -> toMeetPointVO(r, campusNames)).toList());
        vo.setColleges(collegeMapper.selectList(new LambdaQueryWrapper<DictCollege>()
                        .eq(DictCollege::getDeleted, 0)
                        .eq(DictCollege::getStatus, 1)
                        .orderByAsc(DictCollege::getSort)
                        .orderByAsc(DictCollege::getId))
                .stream().map(r -> toCollegeVO(r, campusNames)).toList());
        vo.setCampuses(campusMapper.selectList(new LambdaQueryWrapper<DictCampus>()
                        .eq(DictCampus::getDeleted, 0)
                        .eq(DictCampus::getStatus, 1)
                        .orderByAsc(DictCampus::getSort)
                        .orderByAsc(DictCampus::getId))
                .stream().map(this::toCampusVO).toList());
        return vo;
    }

    /* ══ 七、缓存失效与事务 ══════════════════════════════════ */

    /**
     * 失效 `/api/dict/all` 的缓存。事务内注册 afterCommit，提交成功后才删；事务外立即删。
     *
     * <p>先提交数据库、再删缓存这个顺序不能反。反过来（事务内写缓存）有两个问题：
     * 事务回滚后 Redis 里已是新值；事务提交后 Redis 写入失败则两边不一致。
     */
    private void evictCache() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteCacheKey();
                }
            });
            return;
        }
        deleteCacheKey();
    }

    /**
     * 删除缓存 key，不重新计算。
     *
     * <p>重算要重新查五张表并组装序列化，删只需一次 DEL，下次读取时重建。
     * 另外并发下两次写的先后顺序无法保证，重算有可能把旧快照写回缓存。
     */
    private void deleteCacheKey() {
        try {
            redisTemplate.delete(DictRedisKeys.ALL);
        } catch (Exception e) {
            log.warn("[dict] 删除缓存失败，{} 秒后随 TTL 过期", CACHE_TTL.toSeconds(), e);
        }
    }

    /** 多条 UPDATE 用编程式事务，事务边界就是这个 lambda */
    private void inTransaction(Runnable work) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            work.run();
            evictCache();
        });
    }

    /* ══ 八、内部工具 ════════════════════════════════════════ */

    private DictCategory requireCategory(Long id) {
        return BizAssert.notNull(categoryMapper.selectOne(new LambdaQueryWrapper<DictCategory>()
                .eq(DictCategory::getId, id)
                .eq(DictCategory::getDeleted, 0)), DictErrorCode.DICT_NOT_FOUND);
    }

    private DictMeetPoint requireMeetPoint(Long id) {
        return BizAssert.notNull(meetPointMapper.selectOne(new LambdaQueryWrapper<DictMeetPoint>()
                .eq(DictMeetPoint::getId, id)
                .eq(DictMeetPoint::getDeleted, 0)), DictErrorCode.DICT_NOT_FOUND);
    }

    private DictCollege requireCollege(Long id) {
        return BizAssert.notNull(collegeMapper.selectOne(new LambdaQueryWrapper<DictCollege>()
                .eq(DictCollege::getId, id)
                .eq(DictCollege::getDeleted, 0)), DictErrorCode.DICT_NOT_FOUND);
    }

    private DictCampus requireCampus(Long id) {
        return BizAssert.notNull(campusMapper.selectOne(new LambdaQueryWrapper<DictCampus>()
                .eq(DictCampus::getId, id)
                .eq(DictCampus::getDeleted, 0)), DictErrorCode.DICT_NOT_FOUND);
    }

    /**
     * 校验父级分类，只在 `parentId` 非 0（二级分类）时调用。查的是父级那条记录。
     *
     * @param selfId 被编辑的分类 id，用于拒绝自环——`parent_id = 自身 id`
     *               会让前端按 parentId 组装树时死循环
     */
    private void requireParent(long parentIdInDb, Long selfId) {
        if (selfId != null) {
            BizAssert.isTrue(parentIdInDb != selfId.longValue(),
                    CommonErrorCode.PARAM_ERROR, "上级分类不能是自己");
        }
        DictCategory parent = requireCategory(parentIdInDb);
        BizAssert.isTrue(isRoot(parent), DictErrorCode.DICT_PARENT_INVALID);
        BizAssert.isTrue(isEnabled(parent), DictErrorCode.DICT_PARENT_DISABLED);
    }

    private void requireEnabledCampus(long campusId) {
        DictCampus campus = campusMapper.selectOne(new LambdaQueryWrapper<DictCampus>()
                .eq(DictCampus::getId, campusId)
                .eq(DictCampus::getDeleted, 0));
        BizAssert.notNull(campus, DictErrorCode.DICT_CAMPUS_INVALID);
        BizAssert.isTrue(isEnabled(campus), DictErrorCode.DICT_CAMPUS_INVALID);
    }

    private void setCategoryStatus(Long id, boolean enabled) {
        categoryMapper.update(null, new LambdaUpdateWrapper<DictCategory>()
                .eq(DictCategory::getId, id)
                .eq(DictCategory::getDeleted, 0)
                .set(DictCategory::getStatus, enabled ? 1 : 0));
    }

    private void setCampusStatus(Long id, boolean enabled) {
        campusMapper.update(null, new LambdaUpdateWrapper<DictCampus>()
                .eq(DictCampus::getId, id)
                .eq(DictCampus::getDeleted, 0)
                .set(DictCampus::getStatus, enabled ? 1 : 0));
    }

    private boolean isRoot(DictCategory row) {
        return row.getParentId() == null || row.getParentId() == 0L;
    }

    private boolean isEnabled(DictCampus row) {
        return Integer.valueOf(1).equals(row.getStatus());
    }

    private boolean isEnabled(DictCategory row) {
        return Integer.valueOf(1).equals(row.getStatus());
    }

    /** 同一父级下是否已存在同名分类。excludeId 传被编辑的分类自己，否则改名会撞上自己 */
    private boolean existsCategoryName(String name, long parentId, Long excludeId) {
        LambdaQueryWrapper<DictCategory> q = new LambdaQueryWrapper<DictCategory>()
                .eq(DictCategory::getName, name)
                .eq(DictCategory::getParentId, parentId)
                .eq(DictCategory::getDeleted, 0);
        if (excludeId != null) {
            q.ne(DictCategory::getId, excludeId);
        }
        return categoryMapper.selectCount(q) > 0;
    }

    private boolean existsMeetPointName(String name, long campusId, Long excludeId) {
        LambdaQueryWrapper<DictMeetPoint> q = new LambdaQueryWrapper<DictMeetPoint>()
                .eq(DictMeetPoint::getName, name)
                .eq(DictMeetPoint::getCampusId, campusId)
                .eq(DictMeetPoint::getDeleted, 0);
        if (excludeId != null) {
            q.ne(DictMeetPoint::getId, excludeId);
        }
        return meetPointMapper.selectCount(q) > 0;
    }

    private boolean existsCollegeName(String name, Long excludeId) {
        LambdaQueryWrapper<DictCollege> q = new LambdaQueryWrapper<DictCollege>()
                .eq(DictCollege::getName, name)
                .eq(DictCollege::getDeleted, 0);
        if (excludeId != null) {
            q.ne(DictCollege::getId, excludeId);
        }
        return collegeMapper.selectCount(q) > 0;
    }

    private boolean existsCampusName(String name, Long excludeId) {
        LambdaQueryWrapper<DictCampus> q = new LambdaQueryWrapper<DictCampus>()
                .eq(DictCampus::getName, name)
                .eq(DictCampus::getDeleted, 0);
        if (excludeId != null) {
            q.ne(DictCampus::getId, excludeId);
        }
        return campusMapper.selectCount(q) > 0;
    }

    /** 校区 id → 名称。面交点与学院列表都要显示校区名，一次查完避免逐行回查 */
    private Map<Long, String> campusNameMap() {
        return campusMapper.selectList(new LambdaQueryWrapper<DictCampus>()
                        .eq(DictCampus::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(DictCampus::getId, DictCampus::getName, (a, b) -> a));
    }

    /** 把「一级 / 未指定」的入参转成库里的 0。不传、传空串、传 "0" 都算 0 */
    private long parseIdOrZero(String id) {
        Long v = parseId(id);
        return v == null ? 0L : v;
    }

    private Long parseId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "字典 id 不合法：" + id);
        }
    }

    /* ══ 九、Entity → VO ═════════════════════════════════════ */

    private DictCategoryVO toCategoryVO(DictCategory r) {
        DictCategoryVO vo = new DictCategoryVO();
        vo.setId(r.getId());
        vo.setName(r.getName());
        // 一级在库里是 0，前端契约是 null
        vo.setParentId(isRoot(r) ? null : r.getParentId());
        vo.setSort(r.getSort());
        vo.setEnabled(isEnabled(r));
        // 商品模块落地前恒为 0，接口消费方不要当成真实库存
        vo.setItemCount(0);
        return vo;
    }

    private DictGradeVO toGradeVO(DictGrade r) {
        DictGradeVO vo = new DictGradeVO();
        vo.setCode(r.getCode());
        vo.setLabel(r.getName());
        vo.setDesc(r.getDescription());
        vo.setColor(r.getColor());
        vo.setEnabled(Integer.valueOf(1).equals(r.getStatus()));
        return vo;
    }

    private DictMeetPointVO toMeetPointVO(DictMeetPoint r, Map<Long, String> campusNames) {
        DictMeetPointVO vo = new DictMeetPointVO();
        vo.setId(r.getId());
        vo.setName(r.getName());
        vo.setCampusId(r.getCampusId());
        vo.setCampusName(campusNames.get(r.getCampusId()));
        vo.setOpenHours(r.getOpenHours());
        vo.setEnabled(Integer.valueOf(1).equals(r.getStatus()));
        return vo;
    }

    private DictCollegeVO toCollegeVO(DictCollege r, Map<Long, String> campusNames) {
        DictCollegeVO vo = new DictCollegeVO();
        vo.setId(r.getId());
        vo.setName(r.getName());
        // 未指定校区在库里是 0，前端契约是 null
        vo.setCampusId(r.getCampusId() == null || r.getCampusId() == 0L ? null : r.getCampusId());
        vo.setCampusName(vo.getCampusId() == null ? null : campusNames.get(vo.getCampusId()));
        vo.setSort(r.getSort());
        vo.setEnabled(Integer.valueOf(1).equals(r.getStatus()));
        return vo;
    }

    private DictCampusVO toCampusVO(DictCampus r) {
        DictCampusVO vo = new DictCampusVO();
        vo.setId(r.getId());
        vo.setName(r.getName());
        vo.setSort(r.getSort());
        vo.setEnabled(isEnabled(r));
        return vo;
    }

}

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
 * <p><b>权限</b>：写接口只在 Controller 上挂 `content.edit`——2026-09-30 起该权限点
 * 已从运营角色移除，只有超管持有。Service 不重复判权限：它也可能被内部任务调用，
 * 权限判断统一收口在入口层，避免「有的地方查了有的地方没查」。
 *
 * <p><b>新增与编辑是两个方法，不靠 `id` 是否为空来分叉</b>：
 * `createXxx(req)` / `updateXxx(id, req)`。id 是 `updateXxx` 的**必填参数**（来自 URL 路径），
 * 方法内部不需要也不能判断它是否为空。此前写成一个 `saveXxx(req)` + `if (id == null)` 是
 * 拿 null 当 flag——那个分支 100% 由 Controller 决定（POST 强制置空、PUT 强灌路径 id），
 * Service 自己没有判断力，读代码的人必须追到 Controller 才知道哪条分支会走。
 * 拆开之后语义进方法签名，`*SaveReq` 里也就不再需要那个谁都不会真正采信的 `id` 字段。
 *
 * <p><b>缓存与事务的关系（本类最容易写错的地方）</b>：
 * 只有 `/api/dict/all` 走缓存（{@link DictRedisKeys} 说明了为什么管理端读接口不缓存）。
 * 失效动作统一走 {@link #evictCache()}：
 * <ul>
 *   <li>在事务外 → 立即删；</li>
 *   <li>在事务内 → 注册成 `afterCommit`，**提交成功才删**。</li>
 * </ul>
 * 后者是关键：如果像「事务内更新缓存」那样写，会有两个洞——
 * ① 事务回滚了但 Redis 已经写进去，脏数据要等 TTL 才自愈；
 * ② 事务提交了但 Redis 写失败，同样是脏数据。
 * 「提交后再删」同时堵住这两个洞：回滚时缓存里的旧值与库一致（本来就不用动），
 * 提交后删失败也只是回到「等 TTL」这一条兜底路径。
 *
 * <p><b>什么时候进事务</b>：单条 INSERT / UPDATE 本身是原子的，**不加事务**
 * （`AdminAuthService` 那行注释记过同一个教训：把无关操作裹进事务，throw 时会连坐回滚）。
 * 只有「停用父分类连带子分类」「停用校区连带面交点与学院」这种**多条 UPDATE** 才用
 * 编程式 `TransactionTemplate`——范围小、边界清楚，不会把一整个方法拖成长事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictService {

    /** 缓存兜底时长：字典改动极少，正常靠写操作主动失效，这里只兜 del 失败的情况 */
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    /** 成色默认色值，与 schema.sql 的 DEFAULT 一致——新增时忘了填也不会渲染出透明徽章 */
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

    /**
     * 新增分类，返回新 id。
     *
     * <p>一级 / 二级只看 `parentId`：**不传 = 一级**（库里存 0），传了 = 二级。
     * 传了才会去校验父级（{@link #requireParent}），一级没有任何父级约束要查。
     */
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

    /**
     * 编辑分类。id 是必填参数（来自 URL 路径），不存在直接 6001。
     *
     * <p>比新增多一条约束：这次要挂到别人底下（`parentIdInDb != 0`）时，自己不能已经有子分类，
     * 否则那些子分类会变成三级。不额外判「自己当前必须是一级」——二级按理不可能有子，
     * 这段对它是空转；但保留通用写法可以顺带兜住「二级挂着子」的脏数据。
     */
    public void updateCategory(Long id, DictCategorySaveReq req) {
        String name = req.getName().trim();
        long parentIdInDb = parseIdOrZero(req.getParentId());
        if (parentIdInDb != 0L) {
            requireParent(parentIdInDb, id);
        }

        DictCategory row = requireCategory(id);
        if (parentIdInDb != 0L) {
            BizAssert.isTrue(!hasChildren(id), DictErrorCode.DICT_LEVEL_EXCEEDED);
        }
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
     * 启停分类。
     *
     * <p>停用一级分类时**连带停用它的子分类**：否则会出现「父停用、子还能在发布页选中」
     * 这种半成品状态（notes/07 §5 已拍板）。启用则**不**连带——启用是放宽限制，
     * 不该顺手放宽一大批子分类，由超管逐个确认。
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
     * 按 `code` 新增或编辑成色（upsert）。
     *
     * <p>这里**故意**不拆成 create / update：四档成色是**固定集合**（A–D），
     * 空库初次维护时如果只认编辑，超管就得先手工插数据才能改文案，没必要。
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

    /**
     * 新增面交点，返回新 id。
     *
     * <p>`campusId` 必填：面交点必须挂在具体校区下。不传 / 传 "0" 都归一成 0，
     * 被 {@link #requireEnabledCampus} 判成 6006。
     */
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
     * 启停校区。
     *
     * <p>停用**连带停用**它下面的面交点与学院：校区是引用链顶端（校区 → 面交点 / 学院），
     * 留着「校区停用但面交点仍可选」会出现能选不能去的死数据。启用不连带，理由同分类。
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
     * 五张字典的**启用项**聚合，给 `/api/dict/all` 用。
     *
     * <p>缓存读失败或反序列化不出来时**降级直查库**，不抛异常：字典是发布页的依赖项，
     * Redis 抖一下就让整个发布页 500 不划算（与 `AdminAuthService#isLocked` 同一个取舍）。
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
     * 失效 `/api/dict/all` 的缓存（**删除**而不是重算写入，理由见 {@link #deleteCacheKey}）。
     *
     * <p>在事务内调用时注册 `afterCommit`，提交成功后才删；事务外则立即删。
     * 这样「多条 UPDATE」这类场景下，回滚 = 库没变 = 缓存里的旧值依然正确，无需任何补偿。
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
     * 只删 key，不重算。
     *
     * <p>为什么是删而不是「查一遍再 set 回去」：① 重算要把五张表全查一遍再组装序列化，
     * 而删只需一次 DEL，下一次读自然重建（字典读多写少，重建成本摊得开）；
     * ② 并发下两次写可能乱序——A 改完、B 改完，若 B 先算出旧值写进缓存、A 后写，
     * 缓存里就是 A 的旧快照，要脏满 TTL。DEL 是幂等的，不存在这个顺序问题。
     */
    private void deleteCacheKey() {
        try {
            redisTemplate.delete(DictRedisKeys.ALL);
        } catch (Exception e) {
            log.warn("[dict] 删除缓存失败，最多 {} 秒后由 TTL 兜底", CACHE_TTL.toSeconds(), e);
        }
    }

    /** 多条 UPDATE 才用：编程式事务，边界就是这个 lambda，不会把整个方法拖成长事务 */
    private void inTransaction(Runnable work) {
        // 字典写操作极低频（仅超管），每次 new 一个模板对象无所谓，不值得为它单配一个 Bean
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
     * 校验「要挂到的那个父分类」合不合格，只在 `parentId` 非 0（即二级分类）时调用。
     *
     * @param selfId 编辑时传被编辑分类自己的 id，用来挡住**自环**——
     *               库里真写下 `parent_id = 自身 id`，前端按 parentId 组装树会无限递归
     */
    private void requireParent(long parentIdInDb, Long selfId) {
        if (selfId != null) {
            BizAssert.isTrue(parentIdInDb != selfId.longValue(),
                    CommonErrorCode.PARAM_ERROR, "上级分类不能是自己");
        }
        DictCategory parent = requireCategory(parentIdInDb);
        // 父级必须是「一级」：父级本身是二级，再挂一层就是三级
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

    private boolean hasChildren(Long id) {
        return categoryMapper.selectCount(new LambdaQueryWrapper<DictCategory>()
                .eq(DictCategory::getParentId, id)
                .eq(DictCategory::getDeleted, 0)) > 0;
    }

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

    /**
     * 一次把校区 id → 名称捞成 Map。
     *
     * <p>面交点 / 学院列表都要显示校区名，逐行回查校区表就是典型的 N+1（CR 清单第 7 条）；
     * 字典总量只有几十条，一次查完放内存里最划算。
     */
    private Map<Long, String> campusNameMap() {
        return campusMapper.selectList(new LambdaQueryWrapper<DictCampus>()
                        .eq(DictCampus::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(DictCampus::getId, DictCampus::getName, (a, b) -> a));
    }

    /**
     * 把「一级 / 未指定」的入参归一成 **0**（库里的口径）。
     *
     * <p>请求里不传、传空串、传 "0" 是同一个意思。归一之后方法里就只有一种编码，
     * 不会再出现「到底是 null 还是 0」这种要回头确认一遍的判断。
     */
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
        // 商品模块落地前恒为 0：接口层不要把它当成真实库存（notes/07 问题 2 未拍板）
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

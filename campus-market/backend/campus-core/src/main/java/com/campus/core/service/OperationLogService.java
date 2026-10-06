package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BizException;
import com.campus.common.exception.CommonErrorCode;
import com.campus.common.result.PageResult;
import com.campus.core.dto.req.OperationLogReq;
import com.campus.core.dto.resp.OperationLogVO;
import com.campus.core.entity.SysOperationLog;
import com.campus.core.mapper.SysOperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 管理端操作日志查询（business/03 A9 / F-A11）。
 *
 * <p><b>只读</b>：本 Service 只有这一个方法。`sys_operation_log` 是审计表，
 * 没有编辑与删除的入口——Mapper 上也没有对应方法（见 `SysOperationLogMapper`）。
 * 所以这里也**没有** `@OpLog`：F-A11 明确「读操作不进本表」，查日志这件事
 * 由访问日志负责留痕（`AccessLogFilter`），两者分工不可互相替代。
 *
 * <p><b>没有缓存</b>：条件组合是开放的（模块 × 关键词 × 结果 × 时间区间），
 * 缓存键会退化成「每个请求一个 key」，命中率接近零；而 `idx_create(create_time)`
 * 让默认排序（时间倒序）本身就走索引，分页也只看一页。
 *
 * <p><b>不查总数之外的东西</b>：列表直接返回日志行，不回填操作人姓名、不回查业务对象。
 * 姓名/角色在写入时刻已经冗余进表（`adminName` / `roleCode`），回查反而会拿到
 * 「改名后的现在」而覆盖掉「当时是谁」；业务对象则要靠 `module + bizId` 各自去查，
 * 属于点进去看的事，不该让列表页背。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    /** 上限：不限制的话 pageSize=100000 一次就能把表拖出来 */
    private static final int MAX_PAGE_SIZE = 100;

    private final SysOperationLogMapper operationLogMapper;

    /**
     * 分页查询。
     *
     * <p>排序固定 `create_time` 倒序 + `id` 倒序：同一秒内的多条日志（批量操作）
     * 只靠时间排不出稳定顺序，翻页会看到重复或漏行，补一个 id 让次序确定。
     *
     * <p>`module` **不做白名单校验**：它的取值由 `@OpLog` 在各 Controller 上就地声明，
     * 后端没有一份集中的清单；在这里硬造一份，将来加模块就会两边漂移。
     * 传一个不存在的模块返回空列表——对筛选条件来说，这是个如实回答。
     * 相对地，`result` 只有 0/1 两个合法值，传别的报 1000 而不是静默空表。
     */
    public PageResult<OperationLogVO> list(OperationLogReq req) {
        long pageNum = normalizePageNum(req.getPageNum());
        long pageSize = normalizePageSize(req.getPageSize());

        Integer result = normalizeResult(req.getResult());
        LocalDateTime from = toStartOfDay(req.getStartTime());
        LocalDateTime to = toEndOfDay(req.getEndTime());
        if (from != null && to != null && from.isAfter(to)) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "开始日期不能晚于结束日期");
        }

        String module = trimToNull(req.getModule());
        LambdaQueryWrapper<SysOperationLog> q = new LambdaQueryWrapper<SysOperationLog>()
                .eq(module != null, SysOperationLog::getModule, module)
                .eq(result != null, SysOperationLog::getResult, result)
                .ge(from != null, SysOperationLog::getCreateTime, from)
                .le(to != null, SysOperationLog::getCreateTime, to);
        applyKeyword(q, req.getKeyword());
        q.orderByDesc(SysOperationLog::getCreateTime).orderByDesc(SysOperationLog::getId);

        Page<SysOperationLog> page = operationLogMapper.selectPage(new Page<>(pageNum, pageSize), q);
        List<OperationLogVO> vos = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(vos, page.getTotal(), pageNum, pageSize);
    }

    /* ══ 条件拼装 ═══════════════════════════════════════════ */

    /**
     * 关键词匹配操作者姓名 / 操作类型 / 描述三列。
     *
     * <p>三列是 `or` 关系，整体用 `and(...)` 包起来——不包的话，`or` 会跟前面的
     * 模块/结果/时间条件平级，把筛选条件整个冲掉（`WHERE module=? OR adminName LIKE ?`）。
     */
    private void applyKeyword(LambdaQueryWrapper<SysOperationLog> q, String keyword) {
        String kw = trimToNull(keyword);
        if (kw == null) {
            return;
        }
        q.and(w -> w.like(SysOperationLog::getAdminName, kw)
                .or().like(SysOperationLog::getOpType, kw)
                .or().like(SysOperationLog::getDescription, kw));
    }

    /* ══ 入参归一化 ═════════════════════════════════════════ */

    private long normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /** 只有 0/1 是合法筛选项；传 5 会返回空表，看起来像「真的没有失败记录」 */
    private Integer normalizeResult(Integer result) {
        if (result == null) {
            return null;
        }
        if (result != 0 && result != 1) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "结果只能是 1（成功）或 0（失败）");
        }
        return result;
    }

    private LocalDateTime toStartOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    /** 含结束当天：不补到 23:59:59 的话「查到 10-06」会把 10-06 当天的记录全漏掉 */
    private LocalDateTime toEndOfDay(LocalDate date) {
        return date == null ? null : date.atTime(LocalTime.MAX);
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /* ══ 出参组装 ═══════════════════════════════════════════ */

    /** 字段一对一照搬，故手写而不引 BeanUtils——多一个依赖换不来什么 */
    private OperationLogVO toVO(SysOperationLog row) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId(row.getId());
        vo.setAdminId(row.getAdminId());
        vo.setAdminName(row.getAdminName());
        vo.setRoleCode(row.getRoleCode());
        vo.setModule(row.getModule());
        vo.setOpType(row.getOpType());
        vo.setBizId(row.getBizId());
        vo.setDescription(row.getDescription());
        vo.setBeforeValue(row.getBeforeValue());
        vo.setAfterValue(row.getAfterValue());
        vo.setResult(row.getResult());
        vo.setErrorCode(row.getErrorCode());
        vo.setIp(row.getIp());
        vo.setUa(row.getUa());
        vo.setTraceId(row.getTraceId());
        vo.setCostMs(row.getCostMs());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }
}

package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.CommonErrorCode;
import com.campus.common.result.PageResult;
import com.campus.core.dto.req.SensitiveWordSaveReq;
import com.campus.core.dto.resp.SensitiveWordVO;
import com.campus.core.entity.SensitiveWord;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.mapper.SensitiveWordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 敏感词库维护（管理端，notes/22 §五 缺的写入口）。
 *
 * <p>过滤不在这里：{@link SensitiveWordService} 负责「整表载入内存 + 命中判定」，
 * 本类只改库，改完调它的 {@code refresh()} 让所有实例重载——两个类合起来才是完整的词库闭环。
 *
 * <p><b>写方法不加事务</b>：单表一行写完即提交，随后自增版本戳。
 * 若包进事务，版本戳会在数据可见之前自增，其它实例按新版本号重载却读到旧数据，
 * 而版本号不再变化，这份旧词库会一直用到本地 TTL 过期（5 分钟）。
 * 这是本类唯一需要盯住的顺序约束。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordAdminService {

    /** 词库量级小，一页给足省翻页；仍设上限防前端传 10000 */
    private static final int MAX_PAGE_SIZE = 100;

    private static final int LEVEL_HIGH = 1;
    private static final int LEVEL_LOW = 2;

    private final SensitiveWordMapper sensitiveWordMapper;
    private final SensitiveWordService sensitiveWordService;

    public PageResult<SensitiveWordVO> list(Integer level, Integer status, int pageNum, int pageSize) {
        int pn = Math.max(1, pageNum);
        int ps = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);

        LambdaQueryWrapper<SensitiveWord> w = new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getDeleted, 0L)
                .orderByDesc(SensitiveWord::getCreateTime)
                .orderByDesc(SensitiveWord::getId);
        if (level != null) {
            w.eq(SensitiveWord::getLevel, level);
        }
        if (status != null) {
            w.eq(SensitiveWord::getStatus, status);
        }

        Page<SensitiveWord> p = sensitiveWordMapper.selectPage(new Page<>(pn, ps), w);
        List<SensitiveWordVO> list = p.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(list, p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 新增：同词已在库（含停用词）报 4026——停用词重新启用，比再建一条干净 */
    public Long create(SensitiveWordSaveReq req) {
        String word = req.getWord().trim();
        BizAssert.isTrue(!existsWord(word, null), UserErrorCode.SENSITIVE_WORD_EXISTS);

        SensitiveWord row = new SensitiveWord()
                .setWord(word)
                .setLevel(normalizeLevel(req.getLevel()))
                .setStatus(1);
        sensitiveWordMapper.insert(row);
        sensitiveWordService.refresh();
        return row.getId();
    }

    /** 编辑词面与级别；id 来自 URL 路径，不存在抛 4027 */
    public void update(Long id, SensitiveWordSaveReq req) {
        requireWord(id);
        String word = req.getWord().trim();
        BizAssert.isTrue(!existsWord(word, id), UserErrorCode.SENSITIVE_WORD_EXISTS);

        sensitiveWordMapper.updateById(new SensitiveWord()
                .setId(id)
                .setWord(word)
                .setLevel(normalizeLevel(req.getLevel())));
        sensitiveWordService.refresh();
    }

    /** 启停：停用词不进内存快照，等于立刻从过滤中摘掉 */
    public void updateStatus(Long id, boolean enabled) {
        requireWord(id);
        sensitiveWordMapper.updateById(new SensitiveWord()
                .setId(id)
                .setStatus(enabled ? 1 : 0));
        sensitiveWordService.refresh();
    }

    /**
     * 删除：`deleted` 置当前毫秒而非 0/1。
     *
     * <p>它是删除时间戳，唯一索引是 `(word, deleted)`——置 0 会让该词再也建不回来，
     * 置时间戳则允许同词重建一条新行（历史行留在表里可供追溯）。
     */
    public void delete(Long id) {
        requireWord(id);
        sensitiveWordMapper.updateById(new SensitiveWord()
                .setId(id)
                .setDeleted(System.currentTimeMillis()));
        sensitiveWordService.refresh();
    }

    private SensitiveWord requireWord(Long id) {
        SensitiveWord row = sensitiveWordMapper.selectOne(new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getId, id)
                .eq(SensitiveWord::getDeleted, 0L));
        BizAssert.notNull(row, UserErrorCode.SENSITIVE_WORD_NOT_FOUND);
        return row;
    }

    private boolean existsWord(String word, Long excludeId) {
        Long n = sensitiveWordMapper.selectCount(new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getWord, word)
                .eq(SensitiveWord::getDeleted, 0L)
                .ne(excludeId != null, SensitiveWord::getId, excludeId));
        return n != null && n > 0;
    }

    /** 级别缺省按低危：误设高危会直接拦掉发布，代价比漏拦一句重 */
    private Integer normalizeLevel(Integer level) {
        if (level == null) {
            return LEVEL_LOW;
        }
        BizAssert.isTrue(level == LEVEL_HIGH || level == LEVEL_LOW,
                CommonErrorCode.PARAM_ERROR, "level 只能是 1 高危 / 2 低危");
        return level;
    }

    private SensitiveWordVO toVO(SensitiveWord w) {
        SensitiveWordVO vo = new SensitiveWordVO();
        vo.setId(String.valueOf(w.getId()));
        vo.setWord(w.getWord());
        vo.setLevel(w.getLevel());
        vo.setStatus(w.getStatus());
        vo.setCreateTime(w.getCreateTime());
        return vo;
    }
}

package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.CommonErrorCode;
import com.campus.common.result.PageResult;
import com.campus.core.dto.req.AppUserListReq;
import com.campus.core.dto.req.UserAuthJudgeReq;
import com.campus.core.dto.req.UserBanReq;
import com.campus.core.dto.req.UserCreditAdjustReq;
import com.campus.core.dto.resp.AppUserDetailVO;
import com.campus.core.dto.resp.AppUserVO;
import com.campus.core.dto.resp.CreditLogVO;
import com.campus.core.dto.resp.UserAuthVO;
import com.campus.core.entity.AppUser;
import com.campus.core.entity.CreditLog;
import com.campus.core.entity.Item;
import com.campus.core.entity.TradeOrder;
import com.campus.core.entity.UserAuth;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.mapper.CreditLogMapper;
import com.campus.core.mapper.ItemMapper;
import com.campus.core.mapper.TradeOrderMapper;
import com.campus.core.mapper.UserAuthMapper;
import com.campus.core.satoken.StpUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端学生账号管理（notes/20 D6）：列表 / 详情 / 封禁解封 / 信用调整 / 认证审核。
 *
 * <p><b>封禁不动信用分</b>：信用分记交易行为、封禁是账号处置，混在一起会让解封出来的用户
 * 带着 0 分寸步难行，流水里也会多一条无法区分性质的扣分（db/01 §5.5）。
 *
 * <p><b>学号唯一性只在「通过」这一步校验</b>：提交阶段放行，允许学生自己纠正填错的学号。
 * 数据库 uk_student_no 是最后一道闸，应用层先查一次只是为了给可读的错误文案。
 */
@Service
@RequiredArgsConstructor
public class AppUserAdminService {

    private static final int PAGE_MAX = 100;

    private static final int STATUS_BANNED = 2;

    private static final int STATUS_NORMAL = 0;

    private static final int AUTH_PENDING = 1;

    private static final int AUTH_PASSED = 2;

    private static final int AUTH_REJECTED = 3;

    private final AppUserMapper appUserMapper;

    private final UserAuthMapper userAuthMapper;

    private final CreditLogMapper creditLogMapper;

    private final ItemMapper itemMapper;

    private final TradeOrderMapper tradeOrderMapper;

    private final CreditService creditService;

    public PageResult<AppUserVO> list(AppUserListReq req) {
        long page = req.getPageNum() == null || req.getPageNum() < 1 ? 1 : req.getPageNum();
        long size = Math.min(req.getPageSize() == null || req.getPageSize() < 1 ? 10 : req.getPageSize(), PAGE_MAX);

        LambdaQueryWrapper<AppUser> q = new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getDeleted, 0);
        String kw = req.getKeyword() == null ? null : req.getKeyword().trim();
        if (StringUtils.hasText(kw)) {
            q.and(w -> w.like(AppUser::getNickname, kw)
                    .or().like(AppUser::getPhone, kw)
                    .or().like(AppUser::getStudentNo, kw));
        }
        if (req.getAuthStatus() != null) {
            q.eq(AppUser::getAuthStatus, req.getAuthStatus());
        }
        if (req.getStatus() != null) {
            q.eq(AppUser::getStatus, req.getStatus());
        }
        if (req.getCollegeId() != null) {
            q.eq(AppUser::getCollegeId, req.getCollegeId());
        }
        q.orderByDesc(AppUser::getCreateTime).orderByDesc(AppUser::getId);

        Page<AppUser> p = appUserMapper.selectPage(new Page<>(page, size), q);
        List<AppUserVO> list = p.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(list, p.getTotal(), page, size);
    }

    public AppUserDetailVO detail(long userId) {
        AppUser user = appUserMapper.selectById(userId);
        BizAssert.notNull(user, UserErrorCode.USER_NOT_FOUND);

        AppUserDetailVO vo = new AppUserDetailVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setPhone(user.getPhone());
        vo.setRealName(user.getRealName());
        vo.setStudentNo(user.getStudentNo());
        vo.setCollegeId(user.getCollegeId());
        vo.setCampusId(user.getCampusId());
        vo.setAuthStatus(user.getAuthStatus());
        vo.setStatus(user.getStatus());
        vo.setCreditScore(user.getCreditScore());
        vo.setBanReason(user.getBanReason());
        vo.setBanUntil(user.getBanUntil());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreateTime(user.getCreateTime());

        vo.setAuthList(userAuthMapper.selectList(new LambdaQueryWrapper<UserAuth>()
                        .eq(UserAuth::getUserId, userId)
                        .eq(UserAuth::getDeleted, 0)
                        .orderByDesc(UserAuth::getCreateTime)
                        .orderByDesc(UserAuth::getId))
                .stream().map(UserAuthApplyService::toVO).toList());

        vo.setCreditLogs(creditLogMapper.selectList(new LambdaQueryWrapper<CreditLog>()
                        .eq(CreditLog::getUserId, userId)
                        .eq(CreditLog::getDeleted, 0)
                        .orderByDesc(CreditLog::getCreateTime)
                        .orderByDesc(CreditLog::getId)
                        .last("LIMIT 20"))
                .stream().map(this::toCreditVO).toList());

        vo.setItemCount(itemMapper.selectCount(new LambdaQueryWrapper<Item>()
                .eq(Item::getSellerId, userId)
                .eq(Item::getDeleted, 0)));
        vo.setOrderCount(tradeOrderMapper.selectCount(new LambdaQueryWrapper<TradeOrder>()
                .and(w -> w.eq(TradeOrder::getBuyerId, userId).or().eq(TradeOrder::getSellerId, userId))
                .eq(TradeOrder::getDeleted, 0)));
        return vo;
    }

    /** 封禁：写状态与原因，并立刻踢下线——等令牌自然过期等于「封了还能用 7 天」 */
    @Transactional(rollbackFor = Exception.class)
    public void ban(long userId, UserBanReq req) {
        requireUser(userId);
        appUserMapper.update(null, new LambdaUpdateWrapper<AppUser>()
                .eq(AppUser::getId, userId)
                .set(AppUser::getStatus, STATUS_BANNED)
                .set(AppUser::getBanReason, req.getReason().trim())
                .set(AppUser::getBanUntil, req.getBanUntil()));
        StpUserUtil.kickout(userId);
    }

    /** 解封：状态与封禁字段一起清，否则列表里会留下「已解封但仍显示原因」的脏行 */
    @Transactional(rollbackFor = Exception.class)
    public void unban(long userId) {
        requireUser(userId);
        appUserMapper.update(null, new LambdaUpdateWrapper<AppUser>()
                .eq(AppUser::getId, userId)
                .set(AppUser::getStatus, STATUS_NORMAL)
                .set(AppUser::getBanReason, null)
                .set(AppUser::getBanUntil, null));
    }

    public void adjustCredit(long userId, UserCreditAdjustReq req) {
        requireUser(userId);
        creditService.adjust(userId, req.getDelta(), req.getReason().trim());
    }

    /** 待审认证队列：先到先审，不做排序策略——认证审核没有「权重」这件事 */
    public PageResult<UserAuthVO> pendingAuth(Integer pageNum, Integer pageSize) {
        long page = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = Math.min(pageSize == null || pageSize < 1 ? 20 : pageSize, PAGE_MAX);

        Page<UserAuth> p = userAuthMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<UserAuth>()
                        .eq(UserAuth::getStatus, AUTH_PENDING)
                        .eq(UserAuth::getDeleted, 0)
                        .orderByAsc(UserAuth::getCreateTime)
                        .orderByAsc(UserAuth::getId));
        List<UserAuthVO> list = p.getRecords().stream()
                .map(UserAuthApplyService::toVO)
                .toList();
        fillApplicants(list);
        return PageResult.of(list, p.getTotal(), page, size);
    }

    /**
     * 认证裁决。通过时校验学号是否已被其他账号占用；驳回必须给原因（原样展示给学生）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void judgeAuth(long authId, UserAuthJudgeReq req, long adminId) {
        UserAuth apply = userAuthMapper.selectById(authId);
        BizAssert.notNull(apply, UserErrorCode.AUTH_APPLY_NOT_FOUND);
        BizAssert.isTrue(Integer.valueOf(AUTH_PENDING).equals(apply.getStatus()),
                UserErrorCode.AUTH_NOT_PENDING);

        UserAuth patch = new UserAuth()
                .setId(authId)
                .setHandlerId(adminId)
                .setHandleTime(LocalDateTime.now());

        if (Boolean.TRUE.equals(req.getPass())) {
            Long taken = appUserMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                    .eq(AppUser::getStudentNo, apply.getStudentNo())
                    .eq(AppUser::getDeleted, 0)
                    .ne(AppUser::getId, apply.getUserId()));
            BizAssert.isTrue(taken == null || taken == 0, UserErrorCode.STUDENT_NO_TAKEN);

            patch.setStatus(AUTH_PASSED);
            userAuthMapper.updateById(patch);
            try {
                appUserMapper.update(null, new LambdaUpdateWrapper<AppUser>()
                        .eq(AppUser::getId, apply.getUserId())
                        .set(AppUser::getAuthStatus, AUTH_PASSED)
                        .set(AppUser::getRealName, apply.getRealName())
                        .set(AppUser::getStudentNo, apply.getStudentNo())
                        .set(AppUser::getCollegeId, apply.getCollegeId()));
            } catch (DuplicateKeyException e) {
                // 应用层刚查过，能走到这里说明并发下另一个申请同时通过了同一学号
                throw UserErrorCode.STUDENT_NO_TAKEN.ex();
            }
        } else {
            String reason = req.getRejectReason() == null ? "" : req.getRejectReason().trim();
            BizAssert.isTrue(!reason.isEmpty(), CommonErrorCode.PARAM_ERROR);
            patch.setStatus(AUTH_REJECTED).setRejectReason(reason);
            userAuthMapper.updateById(patch);
            appUserMapper.update(null, new LambdaUpdateWrapper<AppUser>()
                    .eq(AppUser::getId, apply.getUserId())
                    .set(AppUser::getAuthStatus, AUTH_REJECTED));
        }
    }

    private void requireUser(long userId) {
        AppUser user = appUserMapper.selectById(userId);
        BizAssert.notNull(user, UserErrorCode.USER_NOT_FOUND);
    }

    /** 队列里全是 userId 不好辨认，补昵称与脱敏手机号；一次查完，不逐行查 */
    private void fillApplicants(List<UserAuthVO> list) {
        if (list.isEmpty()) {
            return;
        }
        List<Long> ids = list.stream().map(UserAuthVO::getUserId).distinct().toList();
        Map<Long, AppUser> users = appUserMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity(), (a, b) -> a));
        for (UserAuthVO vo : list) {
            AppUser u = users.get(vo.getUserId());
            if (u == null) {
                continue;
            }
            vo.setNickname(u.getNickname());
            vo.setPhoneMasked(maskPhone(u.getPhone()));
        }
    }

    private AppUserVO toVO(AppUser u) {
        AppUserVO vo = new AppUserVO();
        vo.setId(u.getId());
        vo.setNickname(u.getNickname());
        vo.setAvatarUrl(u.getAvatarUrl());
        vo.setPhoneMasked(maskPhone(u.getPhone()));
        vo.setCollegeId(u.getCollegeId());
        vo.setCampusId(u.getCampusId());
        vo.setAuthStatus(u.getAuthStatus());
        vo.setStatus(u.getStatus());
        vo.setCreditScore(u.getCreditScore());
        vo.setBanUntil(u.getBanUntil());
        vo.setLastLoginAt(u.getLastLoginAt());
        vo.setCreateTime(u.getCreateTime());
        return vo;
    }

    private CreditLogVO toCreditVO(CreditLog row) {
        CreditLogVO vo = new CreditLogVO();
        vo.setId(row.getId());
        vo.setChangeValue(row.getChangeValue());
        vo.setBeforeScore(row.getBeforeScore());
        vo.setAfterScore(row.getAfterScore());
        vo.setReasonType(row.getReasonType());
        vo.setRemark(row.getRemark());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }

    /** 138****1234：列表防截图，详情给全量 */
    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}

package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.CommonErrorCode;
import com.campus.core.dto.req.UserAuthApplyReq;
import com.campus.core.dto.resp.UserAuthVO;
import com.campus.core.entity.AppUser;
import com.campus.core.entity.UserAuth;
import com.campus.core.errcode.DictErrorCode;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.mapper.UserAuthMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学号认证申请（学生端，notes/20 D5）。
 *
 * <p><b>学号唯一性不在这里校验</b>：提交时只落申请，通过时由管理端校验（D5 规则 5）——
 * 学生填错学号要能自己重提，提交阶段就卡死等于不给人纠正的机会。
 *
 * <p>被驳回可重提，每次重提新增一行：这张表是 A 类软删，审核历史本身有价值。
 */
@Service
@RequiredArgsConstructor
public class UserAuthApplyService {

    private static final int STATUS_PENDING = 1;

    private static final int AUTH_PENDING = 1;

    private static final int AUTH_PASSED = 2;

    private final UserAuthMapper userAuthMapper;

    private final AppUserMapper appUserMapper;

    private final DictService dictService;

    /** 提交申请：写 user_auth(status=1) 并把 app_user.auth_status 置 1，两处必须同事务 */
    @Transactional(rollbackFor = Exception.class)
    public Long apply(long userId, UserAuthApplyReq req) {
        AppUser user = appUserMapper.selectById(userId);
        BizAssert.notNull(user, UserErrorCode.USER_NOT_FOUND);
        BizAssert.isTrue(!Integer.valueOf(AUTH_PASSED).equals(user.getAuthStatus()),
                UserErrorCode.AUTH_ALREADY_VERIFIED);
        BizAssert.isTrue(!Integer.valueOf(AUTH_PENDING).equals(user.getAuthStatus()),
                UserErrorCode.AUTH_PENDING_EXISTS);

        boolean collegeOk = dictService.colleges().stream()
                .anyMatch(c -> c.getId().equals(req.getCollegeId()) && Boolean.TRUE.equals(c.getEnabled()));
        BizAssert.isTrue(collegeOk, DictErrorCode.DICT_REF_INVALID);

        UserAuth row = new UserAuth()
                .setUserId(userId)
                .setRealName(req.getRealName().trim())
                .setStudentNo(req.getStudentNo().trim())
                .setCollegeId(req.getCollegeId())
                .setCredentialUrl(req.getCredentialUrl())
                .setStatus(STATUS_PENDING);
        BizAssert.isTrue(userAuthMapper.insert(row) > 0, CommonErrorCode.SYSTEM_ERROR);

        appUserMapper.updateById(new AppUser().setId(userId).setAuthStatus(AUTH_PENDING));
        return row.getId();
    }

    /** 我最近一次申请：认证页回显驳回原因用，没有申请返回 null */
    public UserAuthVO latest(long userId) {
        UserAuth row = userAuthMapper.selectOne(new LambdaQueryWrapper<UserAuth>()
                .eq(UserAuth::getUserId, userId)
                .eq(UserAuth::getDeleted, 0)
                .orderByDesc(UserAuth::getCreateTime)
                .orderByDesc(UserAuth::getId)
                .last("LIMIT 1"));
        if (row == null) {
            return null;
        }
        return toVO(row);
    }

    static UserAuthVO toVO(UserAuth row) {
        UserAuthVO vo = new UserAuthVO();
        vo.setId(row.getId());
        vo.setUserId(row.getUserId());
        vo.setRealName(row.getRealName());
        vo.setStudentNo(row.getStudentNo());
        vo.setCollegeId(row.getCollegeId());
        vo.setCredentialUrl(row.getCredentialUrl());
        vo.setStatus(row.getStatus());
        vo.setRejectReason(row.getRejectReason());
        vo.setHandleTime(row.getHandleTime());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }
}

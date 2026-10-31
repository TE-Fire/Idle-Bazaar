package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.CommonErrorCode;
import com.campus.core.dto.req.UserAddressReq;
import com.campus.core.entity.UserAddress;
import com.campus.core.enums.AddressKindEnum;
import com.campus.core.mapper.UserAddressMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户地址簿（收货 / 自提两类），按当前登录人 + kind 隔离，互不可见。
 *
 * <p><b>同类默认地址唯一</b>：设为默认时会把同一用户同类的其余地址清掉默认标记，
 * 查询时默认地址排在最前。这是用条件更新保证的，不靠应用层先查后写——并发下两个「设默认」
 * 同时进来，后到的条件更新因状态已变而落空，不会留下两个默认。
 *
 * <p><b>地址被订单引用后在下单时刻快照进订单</b>，本表只是「可选地址簿」，改它不影响历史订单
 * （{@code UserAddress} 实体注释）。因此删除 / 改默认都是纯地址簿操作，不触碰任何订单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAddressService {

    private final UserAddressMapper addressMapper;

    /** 当前用户的地址列表，按 kind 过滤；默认地址排最前 */
    public List<UserAddress> list(Long userId, Integer kind) {
        AddressKindEnum.fromCode(kind); // 非法 kind 直接报参错
        LambdaQueryWrapper<UserAddress> q = new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getKind, kind)
                .eq(UserAddress::getDeleted, 0)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getUpdateTime);
        return addressMapper.selectList(q);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(Long userId, UserAddressReq req) {
        AddressKindEnum.fromCode(req.getKind());
        UserAddress addr = toEntity(userId, req);
        if (isDefault(req.getIsDefault())) {
            clearDefault(userId, req.getKind());
        }
        addressMapper.insert(addr);
        return addr.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long userId, Long id, UserAddressReq req) {
        AddressKindEnum.fromCode(req.getKind());
        UserAddress existing = requireOwned(userId, id);
        // 仅允许改到同类：跨 kind 改会让收货地址变成自提地址，破坏隔离
        BizAssert.isTrue(existing.getKind().equals(req.getKind()), CommonErrorCode.PARAM_ERROR,
                "地址类型不可修改");
        UserAddress addr = toEntity(userId, req);
        addr.setId(id);
        if (isDefault(req.getIsDefault()) && !isDefault(existing.getIsDefault())) {
            clearDefault(userId, req.getKind());
        }
        addressMapper.updateById(addr);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        requireOwned(userId, id);
        UserAddress addr = new UserAddress().setId(id).setDeleted(System.currentTimeMillis());
        addressMapper.updateById(addr);
    }

    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long id) {
        UserAddress existing = requireOwned(userId, id);
        clearDefault(userId, existing.getKind());
        addressMapper.update(null, new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getId, id)
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getDeleted, 0)
                .set(UserAddress::getIsDefault, 1));
    }

    /** 订单下单时校验并取用地址（买家收货地址 / 卖家自提地址），不存在或不归属当前用户即拒绝 */
    public UserAddress requireOwned(Long userId, Long id, Integer kind) {
        AddressKindEnum.fromCode(kind);
        UserAddress addr = addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, id)
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getKind, kind)
                .eq(UserAddress::getDeleted, 0));
        return BizAssert.notNull(addr, CommonErrorCode.PARAM_ERROR, "地址不存在或不属于当前用户");
    }

    private UserAddress requireOwned(Long userId, Long id) {
        UserAddress addr = addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, id)
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getDeleted, 0));
        return BizAssert.notNull(addr, CommonErrorCode.PARAM_ERROR, "地址不存在或不属于当前用户");
    }

    /** 把同类其余地址默认标记清零（条件更新 = 并发安全，不先查后写） */
    private void clearDefault(Long userId, Integer kind) {
        addressMapper.update(null, new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getKind, kind)
                .eq(UserAddress::getDeleted, 0)
                .set(UserAddress::getIsDefault, 0));
    }

    private UserAddress toEntity(Long userId, UserAddressReq req) {
        return new UserAddress()
                .setUserId(userId)
                .setKind(req.getKind())
                .setContactName(req.getContactName())
                .setContactPhone(req.getContactPhone())
                .setProvince(req.getProvince())
                .setCity(req.getCity())
                .setDistrict(req.getDistrict())
                .setDetail(req.getDetail())
                .setIsDefault(isDefault(req.getIsDefault()) ? 1 : 0);
    }

    private boolean isDefault(Integer v) {
        return v != null && v == 1;
    }
}

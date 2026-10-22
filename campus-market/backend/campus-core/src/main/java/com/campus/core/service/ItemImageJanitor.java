package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.core.entity.ItemImage;
import com.campus.core.mapper.ItemImageMapper;
import com.campus.support.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;

/**
 * 商品软删后清理 MinIO 图片对象。删库与删对象同事务边界：软删提交后才删对象，
 * 回滚则一张都不能动——先删对象再回滚，商品还在而图没了。
 *
 * <p>StorageService 缺省不装配（demo 关闭 MinIO）时整体跳过：孤儿对象留待二期，
 * 不因清理不可用阻断删除主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemImageJanitor {

    private final ItemImageMapper itemImageMapper;

    private final ObjectProvider<StorageService> storageProvider;

    /** 在删除商品的事务内调用：先取未删图片 URL，删除动作挂到提交后 */
    public void cleanupAfterCommit(Long itemId) {
        StorageService storage = storageProvider.getIfAvailable();
        if (storage == null) {
            return;
        }
        List<String> urls = itemImageMapper.selectList(new LambdaQueryWrapper<ItemImage>()
                        .eq(ItemImage::getItemId, itemId)
                        .eq(ItemImage::getDeleted, 0))
                .stream()
                .map(ItemImage::getUrl)
                .filter(Objects::nonNull)
                .toList();
        if (urls.isEmpty()) {
            return;
        }
        Runnable work = () -> urls.forEach(url -> {
            try {
                storage.deleteByUrl(url);
            } catch (Exception e) {
                log.warn("[item] 清理图片对象失败，留待二期：{}", url, e);
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    work.run();
                }
            });
            return;
        }
        work.run();
    }
}

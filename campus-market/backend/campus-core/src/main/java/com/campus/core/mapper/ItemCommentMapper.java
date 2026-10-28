package com.campus.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.core.entity.ItemComment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品评论 Mapper。
 */
@Mapper
public interface ItemCommentMapper extends BaseMapper<ItemComment> {
}

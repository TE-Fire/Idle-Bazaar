package com.campus.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.core.entity.Appeal;
import org.apache.ibatis.annotations.Mapper;

/**
 * 申诉（`appeal`）的数据访问。
 *
 * <p>只声明 {@link BaseMapper} 的通用方法：申诉的查询条件（按 status 取待复审、
 * 按 biz_type + biz_id 定位原处置）都能用 LambdaQueryWrapper 表达，没有需要手写 SQL 的聚合。
 */
@Mapper
public interface AppealMapper extends BaseMapper<Appeal> {
}

package com.campus.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.core.entity.SysOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志（`sys_operation_log`）。
 *
 * <p>**只有写入与查询，没有 update / delete**：这张表只追加不可删改
 * （表注释已写明）。`BaseMapper` 虽然带着 `updateById` / `deleteById`，
 * 但约定的边界由使用侧守住——目前唯一的写入方是 {@link com.campus.core.oplog.OpLogWriter}，
 * 它只调 `insert`。
 *
 * <p>按 `admin_id + create_time` / `module + biz_id` / `create_time` 三个索引查，
 * 见 db/schema.sql 的 `idx_admin` / `idx_biz` / `idx_create`。
 */
@Mapper
public interface SysOperationLogMapper extends BaseMapper<SysOperationLog> {
}

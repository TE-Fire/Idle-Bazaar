package com.campus.core.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 系统配置（`sys_config`）。
 *
 * <p>只承载 SLA 六个阈值（`sla.` 前缀），**不做通用配置中心**——key-value 表一旦什么都能塞，
 * 就没人分得清哪个 key 还活着。读写入口统一在 {@code SysConfigService}。
 */
@Data
@Accessors(chain = true)
@TableName("sys_config")
public class SysConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 唯一键，如 `sla.firstResponseHours` */
    private String configKey;

    /** 字符串存值，读取时按类型转 */
    private String configValue;

    /** 一句话说明，给运维看 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

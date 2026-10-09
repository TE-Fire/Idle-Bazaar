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
 * 商品审核（`item_audit`，权重排序 + SLA 计时）。
 *
 * <p>一个商品可以有多条：重提会再进队一次。按 `item_id` 拉出来就是完整的处置时间线
 * （business/03 F-A11 判据：审核、举报、申诉共用一张表，`biz_id` 串得起复盘链）。
 *
 * <p>`report_count` / `image_risk` / `credit_deduct` / `price_abnormal` 是**进队时的快照**，
 * 不是实时值：`priority_score` 由它们算出，队列排序不能因为后续又有举报就跳变。
 * 列表页展示给运营看的「当前举报次数」另查 `report` 表，不要复用这个字段。
 */
@Data
@Accessors(chain = true)
@TableName("item_audit")
public class ItemAudit {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long itemId;

    /** 1待审核 2已通过 3已驳回 4已强制下架 */
    private Integer status;

    /**
     * 基础权重分（不含等待补偿）。冗余快照：队列排序由 {@code fillWeight} 实时重算，不读此字段，
     * 以免与四项明细漂移。演示数据按同公式填充，供将来 SQL 层排序 / 报表使用。
     */
    private Integer priorityScore;

    /** 举报次数快照（权重 ×3） */
    private Integer reportCount;

    /** 图片风险分 0–100，归一化计入权重：ROUND(值 ÷ 10)，即 0–10 分 */
    private Integer imageRisk;

    /** 价格异常标记 0否 1是（价格偏离同类均价 3 倍以上，计入权重 +5） */
    private Integer priceAbnormal;

    /** 信用扣减：(100 − 信用分) / 10 */
    private Integer creditDeduct;

    /** 进队时间，SLA 响应时效起点 */
    private LocalDateTime enterQueueAt;

    /** 首次处置时间（首审时长 = 该值 − 进队时间） */
    private LocalDateTime firstHandleAt;

    /** 处置人管理员 id */
    private Long handlerId;

    /** 单条处置耗时（服务端动作耗时，非阅读时长） */
    private Integer handleCostMs;

    private String rejectReason;

    /** 处置备注（申诉复盘依据） */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

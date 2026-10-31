-- 2026-10-31 运行库补齐：本轮新增的表与列
-- 背景：schema.sql 只对「新建库」生效，已有的 campus_market 需要手工补这一次。
-- 只跑一次（没有 IF NOT EXISTS 保护第二次执行会报错）。

-- ① SLA 六阈值的落库表（看板 SLA 卡 / 报表分审核员明细 / 审核队列临期判定 三处同源）
CREATE TABLE sys_config (
  id           bigint       NOT NULL COMMENT '雪花ID',
  config_key   varchar(64)  NOT NULL COMMENT '键，只允许 sla. 前缀',
  config_value varchar(255) NOT NULL COMMENT '值，字符串存，读取时按类型转',
  remark       varchar(255) DEFAULT NULL COMMENT '一句话说明，给运维看',
  create_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      bigint       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_key (config_key, deleted)
) ENGINE=InnoDB COMMENT='系统配置（只承载 SLA 六个阈值，不做通用配置中心）';

INSERT INTO sys_config (id, config_key, config_value, remark) VALUES
 (1601,'sla.firstResponseHours','2','首审响应达标线（小时）'),
 (1602,'sla.p95Hours','6','首审耗时 P95 上限（小时）'),
 (1603,'sla.targetRate','95','整体达标率目标（百分比）'),
 (1604,'sla.backlogHours','24','积压红牌线（小时）'),
 (1605,'sla.avgHandleSeconds','30','单条处置目标耗时（秒）'),
 (1606,'sla.appealHours','48','申诉复审承诺（小时）');

-- ② 平台公告的批次列：一条公告发给 N 人写 N 行，撤回按批次软删
ALTER TABLE msg_message
  ADD COLUMN batch_id bigint DEFAULT NULL COMMENT '公告批次：同一条公告发给多人的行同批次，撤回按批次软删' AFTER extra,
  ADD KEY idx_batch (batch_id) COMMENT '公告撤回：按批次批量软删';

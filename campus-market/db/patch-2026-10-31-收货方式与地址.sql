-- 收货方式扩展（需求三）：面交 / 自提 / 送货上门 + 地址簿
--
-- 「schema.sql 只对新建库生效」，运行库用本文件手工补（01-数据库设计.md 迁移约定）。
-- 执行：mysql -h127.0.0.1 -P3307 -uroot -p campus_market --default-character-set=utf8mb4 < 本文件
--
-- 变更清单：
--   1. 新建 user_address（收货 SHIP / 自提 PICKUP 两类同表）
--   2. item.delivery_included（是否包配送）→ delivery_type（1面交 2自提 3送货上门）+ pickup_address_id
--   3. trade_order 增加 delivery_type / address_id / 地址快照三列
--
-- 历史数据迁移口径：原「是否包配送=1」的商品映射到送货上门(3)，其余一律面交(1)。
-- 自提是新增方式，历史商品不存在自提数据，pickup_address_id 保持 NULL。

CREATE TABLE IF NOT EXISTS user_address (
  id            bigint       NOT NULL COMMENT '雪花ID',
  user_id       bigint       NOT NULL COMMENT '所属用户（买家维护收货地址，卖家维护自提地址）',
  kind          tinyint      NOT NULL COMMENT '1收货地址(SHIP) 2自提地址(PICKUP)',
  contact_name  varchar(64)  NOT NULL COMMENT '联系人',
  contact_phone varchar(32)  NOT NULL COMMENT '联系电话',
  province      varchar(64)  DEFAULT NULL COMMENT '省',
  city          varchar(64)  DEFAULT NULL COMMENT '市',
  district      varchar(64)  DEFAULT NULL COMMENT '区/县',
  detail        varchar(255) NOT NULL COMMENT '详细地址（门牌/楼栋/摊位号）',
  is_default    tinyint      NOT NULL DEFAULT 0 COMMENT '是否默认地址（同类地址唯一默认）',
  create_time   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       bigint       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id, kind, deleted) COMMENT '我的收货地址 / 我的自提地址'
) ENGINE=InnoDB COMMENT='用户地址（收货SHIP/自提PICKUP两类同表，下单时快照进订单）';

ALTER TABLE item
  ADD COLUMN delivery_type     tinyint NOT NULL DEFAULT 1 COMMENT '收货方式：1面交 2自提 3送货上门' AFTER negotiable,
  ADD COLUMN pickup_address_id bigint  DEFAULT NULL COMMENT '自提地址（delivery_type=2时必填，引用 user_address）' AFTER delivery_type;

UPDATE item SET delivery_type = 3 WHERE delivery_included = 1;

ALTER TABLE item DROP COLUMN delivery_included;

ALTER TABLE trade_order
  ADD COLUMN delivery_type    tinyint      NOT NULL DEFAULT 1 COMMENT '收货方式：1面交 2自提 3送货上门（下单时从商品抄入）' AFTER status,
  ADD COLUMN address_id       bigint       DEFAULT NULL COMMENT '地址引用：送货上门=买家收货地址；自提=卖家提货地址' AFTER delivery_type,
  ADD COLUMN address_contact  varchar(64)  DEFAULT NULL COMMENT '地址快照·联系人' AFTER address_id,
  ADD COLUMN address_phone    varchar(32)  DEFAULT NULL COMMENT '地址快照·联系电话' AFTER address_contact,
  ADD COLUMN address_detail   varchar(255) DEFAULT NULL COMMENT '地址快照·完整地址（省+市+区+详细）' AFTER address_phone;

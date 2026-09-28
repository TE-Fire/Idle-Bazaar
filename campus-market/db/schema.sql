-- ============================================================
-- 校园二手交易平台 · 建表脚本
-- MySQL 8.0 / InnoDB / utf8mb4
-- 配套文档：db/01-数据库设计.md（不一致时先改文档）
--
-- 前置：my.cnf 需配置 ngram_token_size=2（全文索引用）
--   [mysqld]
--   ngram_token_size=2
--
-- 约定：无外键；deleted 为逻辑删除（0 未删，非 0 为删除时间戳）；
--       唯一索引均带 deleted，支持"删了再加"；金额 decimal(10,2)。
-- ============================================================

CREATE DATABASE IF NOT EXISTS campus_market
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE campus_market;

-- 必须显式声明连接字符集：MySQL 官方镜像执行 /docker-entrypoint-initdb.d 下的脚本时，
-- mysql 客户端的默认字符集**不是 utf8mb4**，中文会被按字节计数——
-- '轻微使用痕迹' 6 个字 = 18 字节，直接撞上 name varchar(16) 报 ERROR 1406，
-- 脚本就地中断（initdb 不加 --force），结果是「表建好了、种子数据一条没灌进去」。
-- 加这一行后，无论用什么客户端执行都不会再踩这个坑。
SET NAMES utf8mb4;

-- ════════════════════════════════════════════════════════════
-- 一、字典（5 张，全端下发的唯一源头）
-- ════════════════════════════════════════════════════════════

CREATE TABLE dict_campus (
  id          bigint       NOT NULL COMMENT '雪花ID',
  name        varchar(32)  NOT NULL COMMENT '校区名称',
  sort        int          NOT NULL DEFAULT 0 COMMENT '排序',
  status      tinyint      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  create_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted     bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删，非0为删除时间戳',
  PRIMARY KEY (id),
  KEY idx_status (status, sort)
) ENGINE=InnoDB COMMENT='校区字典';

CREATE TABLE dict_college (
  id          bigint       NOT NULL COMMENT '雪花ID',
  name        varchar(64)  NOT NULL COMMENT '学院名称',
  sort        int          NOT NULL DEFAULT 0 COMMENT '排序',
  status      tinyint      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  create_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted     bigint       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_status (status, sort)
) ENGINE=InnoDB COMMENT='学院字典（报表学院×品类矩阵的行）';

CREATE TABLE dict_category (
  id          bigint       NOT NULL COMMENT '雪花ID',
  parent_id   bigint       NOT NULL DEFAULT 0 COMMENT '父分类，一级为0',
  name        varchar(32)  NOT NULL COMMENT '分类名称',
  icon        varchar(255) DEFAULT NULL COMMENT '图标URL',
  sort        int          NOT NULL DEFAULT 0 COMMENT '排序',
  status      tinyint      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  create_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted     bigint       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_parent (parent_id, sort),
  KEY idx_status (status, sort)
) ENGINE=InnoDB COMMENT='商品分类树（两级）';

CREATE TABLE dict_grade (
  id          bigint       NOT NULL COMMENT '雪花ID',
  code        char(1)      NOT NULL COMMENT 'A/B/C/D',
  name        varchar(16)  NOT NULL COMMENT '成色名称，如"几乎全新"',
  description varchar(255) DEFAULT NULL COMMENT '成色说明',
  sort        int          NOT NULL DEFAULT 0 COMMENT '排序',
  status      tinyint      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  create_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted     bigint       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code, deleted)
) ENGINE=InnoDB COMMENT='成色等级 A-D（全端共用，前端不得硬编码文案与颜色）';

CREATE TABLE dict_meet_point (
  id          bigint       NOT NULL COMMENT '雪花ID',
  campus_id   bigint       NOT NULL COMMENT '所属校区',
  name        varchar(64)  NOT NULL COMMENT '面交点名称',
  open_hours  varchar(64)  DEFAULT NULL COMMENT '开放时段，如"08:00-22:00"',
  sort        int          NOT NULL DEFAULT 0 COMMENT '排序',
  status      tinyint      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  create_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted     bigint       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_campus (campus_id, status)
) ENGINE=InnoDB COMMENT='校内面交点（报表面交所热力的维度）';

-- ════════════════════════════════════════════════════════════
-- 二、用户与认证
-- ════════════════════════════════════════════════════════════

CREATE TABLE app_user (
  id           bigint       NOT NULL COMMENT '雪花ID',
  openid       varchar(64)  DEFAULT NULL COMMENT '微信openid',
  nickname     varchar(64)  DEFAULT NULL COMMENT '昵称',
  avatar_url   varchar(255) DEFAULT NULL COMMENT '头像',
  phone        varchar(20)  DEFAULT NULL COMMENT '手机号（Web端验证码登录）',
  real_name    varchar(32)  DEFAULT NULL COMMENT '真实姓名（认证后写入，不公开展示）',
  student_no   varchar(32)  DEFAULT NULL COMMENT '学号（不公开展示）',
  college_id   bigint       DEFAULT NULL COMMENT '学院',
  campus_id    bigint       DEFAULT NULL COMMENT '校区',
  auth_status  tinyint      NOT NULL DEFAULT 0 COMMENT '认证：0未认证 1审核中 2已认证 3未通过',
  credit_score int          NOT NULL DEFAULT 100 COMMENT '信用分 0-120，默认100，<60强制人工审核',
  status       tinyint      NOT NULL DEFAULT 0 COMMENT '账号：0正常 1警告 2封禁',
  ban_until    datetime     DEFAULT NULL COMMENT '封禁到期时间',
  ban_reason   varchar(255) DEFAULT NULL COMMENT '封禁原因',
  last_login_at datetime    DEFAULT NULL COMMENT '最近登录',
  create_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      bigint       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_openid (openid, deleted),
  UNIQUE KEY uk_student_no (student_no, deleted),
  KEY idx_college (college_id),
  KEY idx_auth_credit (auth_status, credit_score),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='学生用户';

CREATE TABLE user_auth (
  id             bigint       NOT NULL COMMENT '雪花ID',
  user_id        bigint       NOT NULL COMMENT '申请人',
  real_name      varchar(32)  NOT NULL COMMENT '姓名',
  student_no     varchar(32)  NOT NULL COMMENT '学号',
  college_id     bigint       NOT NULL COMMENT '学院',
  credential_url varchar(255) DEFAULT NULL COMMENT '一卡通/校园邮箱截图',
  status         tinyint      NOT NULL DEFAULT 1 COMMENT '1审核中 2通过 3未通过',
  reject_reason  varchar(255) DEFAULT NULL COMMENT '驳回原因',
  handler_id     bigint       DEFAULT NULL COMMENT '审核管理员',
  handle_time    datetime     DEFAULT NULL COMMENT '审核时间',
  create_time    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted        bigint       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id, status),
  KEY idx_student_no (student_no) COMMENT '排查一学号多账号',
  KEY idx_status (status, create_time)
) ENGINE=InnoDB COMMENT='学号认证申请（可重提，一对多）';

-- ════════════════════════════════════════════════════════════
-- 三、商品
-- ════════════════════════════════════════════════════════════

CREATE TABLE item (
  id                 bigint         NOT NULL COMMENT '雪花ID',
  seller_id          bigint         NOT NULL COMMENT '卖家',
  title              varchar(100)   NOT NULL COMMENT '标题 5-50字',
  category_id        bigint         NOT NULL COMMENT '二级分类',
  grade              char(1)        NOT NULL COMMENT '成色 A/B/C/D',
  price              decimal(10,2)  NOT NULL COMMENT '售价',
  origin_price       decimal(10,2)  DEFAULT NULL COMMENT '原价',
  campus_id          bigint         NOT NULL COMMENT '校区（校园四项字段）',
  meet_point_id      bigint         DEFAULT NULL COMMENT '面交点（校园四项字段）',
  description        varchar(2000)  DEFAULT NULL COMMENT '描述；成色D必须填瑕疵说明',
  main_image         varchar(255)   DEFAULT NULL COMMENT '主图，列表页免查图表',
  status             tinyint        NOT NULL DEFAULT 1 COMMENT '1草稿 2待审核 3在售 4已下架 5已售出 6审核未过 7已强制下架',
  negotiable         tinyint        NOT NULL DEFAULT 0 COMMENT '可议价标签（仅展示，无议价流程）',
  delivery_included  tinyint        NOT NULL DEFAULT 0 COMMENT '是否包配送',
  reject_reason      varchar(255)   DEFAULT NULL COMMENT '驳回原因',
  version            int            NOT NULL DEFAULT 0 COMMENT '乐观锁（付款占位并发用）',
  view_count         int            NOT NULL DEFAULT 0 COMMENT '浏览量',
  fav_count          int            NOT NULL DEFAULT 0 COMMENT '收藏数',
  published_at       datetime       DEFAULT NULL COMMENT '审核通过（上架）时间',
  create_time        datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time        datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted            bigint         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_list (status, category_id, price, published_at) COMMENT '首页/搜索：分类+价格+时间排序',
  KEY idx_filter (status, grade, campus_id, published_at) COMMENT '成色+校区筛选',
  KEY idx_seller (seller_id, status) COMMENT '我发布的',
  KEY idx_published (status, published_at) COMMENT '最新发布/热度排序',
  FULLTEXT KEY ft_search (title, description) WITH PARSER ngram COMMENT '关键词搜索（ngram=2）'
) ENGINE=InnoDB COMMENT='商品（核心表，无"已预定"状态，付款才占位）';

CREATE TABLE item_image (
  id          bigint       NOT NULL COMMENT '雪花ID',
  item_id     bigint       NOT NULL COMMENT '商品',
  url         varchar(255) NOT NULL COMMENT '图片地址',
  sort        int          NOT NULL DEFAULT 0 COMMENT '排序，0为主图',
  create_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted     bigint       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_item (item_id, sort)
) ENGINE=InnoDB COMMENT='商品图（≤9张）';

CREATE TABLE favorite (
  id          bigint   NOT NULL COMMENT '雪花ID',
  user_id     bigint   NOT NULL COMMENT '用户',
  item_id     bigint   NOT NULL COMMENT '商品',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted     bigint   NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_item (user_id, item_id, deleted) COMMENT 'B类删除：取消写时间戳，重新收藏复用同一行置0（见 db/01 删除的两类实现）',
  KEY idx_item (item_id)
) ENGINE=InnoDB COMMENT='收藏';

-- ════════════════════════════════════════════════════════════
-- 四、订单
-- ════════════════════════════════════════════════════════════

CREATE TABLE trade_order (
  id              bigint        NOT NULL COMMENT '雪花ID',
  order_no        varchar(32)   NOT NULL COMMENT '订单号（对外/幂等键）',
  item_id         bigint        NOT NULL COMMENT '商品',
  buyer_id        bigint        NOT NULL COMMENT '买家',
  seller_id       bigint        NOT NULL COMMENT '卖家',
  amount          decimal(10,2) NOT NULL COMMENT '成交金额（一律按标价，议价不进订单）',
  status          tinyint       NOT NULL DEFAULT 1 COMMENT '1待付款 2待面交 3已验货 4已完成 5已取消 6退款中 7已退款 8纠纷中',
  meet_point_id   bigint        DEFAULT NULL COMMENT '面交点',
  meet_time       datetime      DEFAULT NULL COMMENT '约定面交时间',
  verify_code     varchar(32)   DEFAULT NULL COMMENT '核销码（二维码内容）',
  verify_time     datetime      DEFAULT NULL COMMENT '核销时间',
  verify_by       bigint        DEFAULT NULL COMMENT '核销人（扫码方）',
  expire_time     datetime      DEFAULT NULL COMMENT '待付款超时时间（下单+30分钟）',
  pay_time        datetime      DEFAULT NULL COMMENT '付款时间（模拟支付）',
  cancel_by       tinyint       DEFAULT NULL COMMENT '取消方：1买家 2卖家 3系统超时',
  cancel_reason   varchar(255)  DEFAULT NULL COMMENT '取消原因（违约判定依据）',
  item_title      varchar(100)  DEFAULT NULL COMMENT '快照·标题',
  item_price      decimal(10,2) DEFAULT NULL COMMENT '快照·价格',
  item_grade      char(1)       DEFAULT NULL COMMENT '快照·成色',
  item_main_image varchar(255)  DEFAULT NULL COMMENT '快照·主图',
  snapshot        json          DEFAULT NULL COMMENT '快照·完整商品信息（纠纷取证用）',
  version         int           NOT NULL DEFAULT 0 COMMENT '乐观锁',
  create_time     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted         bigint        NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  UNIQUE KEY uk_verify_code (verify_code),
  KEY idx_buyer (buyer_id, status) COMMENT '我买到的',
  KEY idx_seller (seller_id, status) COMMENT '我卖出的',
  KEY idx_item (item_id),
  KEY idx_expire (status, expire_time) COMMENT '30分钟超时扫描',
  KEY idx_meet (meet_point_id, meet_time) COMMENT '面交所热力报表'
) ENGINE=InnoDB COMMENT='订单（含下单时商品快照，防止卖家事后改价改描述）';

-- ════════════════════════════════════════════════════════════
-- 五、消息（三分区合一）
-- ════════════════════════════════════════════════════════════

CREATE TABLE chat_session (
  id           bigint   NOT NULL COMMENT '雪花ID',
  item_id      bigint   NOT NULL DEFAULT 0 COMMENT '关联商品，0为无商品会话',
  user_a       bigint   NOT NULL COMMENT '参与方A（较小userId）',
  user_b       bigint   NOT NULL COMMENT '参与方B',
  last_msg_id  bigint   DEFAULT NULL COMMENT '最后一条消息',
  last_msg_at  datetime DEFAULT NULL COMMENT '最后消息时间（会话排序）',
  a_unread     int      NOT NULL DEFAULT 0 COMMENT 'A未读（Redis为准，库内兜底）',
  b_unread     int      NOT NULL DEFAULT 0 COMMENT 'B未读',
  create_time  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      bigint   NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pair (item_id, user_a, user_b, deleted),
  KEY idx_user_a (user_a, last_msg_at),
  KEY idx_user_b (user_b, last_msg_at)
) ENGINE=InnoDB COMMENT='聊天会话';

CREATE TABLE msg_message (
  id          bigint        NOT NULL COMMENT '雪花ID',
  zone        tinyint       NOT NULL COMMENT '分区：1私信 2系统 3交易',
  session_id  bigint        DEFAULT NULL COMMENT '会话ID（私信时非空）',
  sender_id   bigint        NOT NULL DEFAULT 0 COMMENT '发送者，系统通知为0',
  receiver_id bigint        NOT NULL COMMENT '接收者',
  type        tinyint       NOT NULL DEFAULT 1 COMMENT '1文本 2图片 3卡片（面交约定） 4系统',
  content     varchar(2000) DEFAULT NULL COMMENT '文本内容',
  extra       json          DEFAULT NULL COMMENT '卡片消息结构化载荷（面交点/时间/商品）',
  read_flag   tinyint       NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  read_time   datetime      DEFAULT NULL COMMENT '已读时间',
  create_time datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted     bigint        NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_session (session_id, id) COMMENT '会话内翻页',
  KEY idx_receiver (receiver_id, zone, read_flag) COMMENT '三分区未读查询（Vinted坑的关键索引）'
) ENGINE=InnoDB COMMENT='消息（私信/系统/交易三分区合一）';

-- ════════════════════════════════════════════════════════════
-- 六、审核 · 举报 · 申诉（管理端裁决）
-- ════════════════════════════════════════════════════════════

CREATE TABLE item_audit (
  id              bigint       NOT NULL COMMENT '雪花ID',
  item_id         bigint       NOT NULL COMMENT '商品',
  status          tinyint      NOT NULL DEFAULT 1 COMMENT '1待审核 2已通过 3已驳回 4已强制下架',
  priority_score  int          NOT NULL DEFAULT 0 COMMENT '基础权重分（不含等待衰减，查询时实时补偿）',
  report_count    int          NOT NULL DEFAULT 0 COMMENT '举报次数（权重×3）',
  image_risk      int          NOT NULL DEFAULT 0 COMMENT '图片风险分0-100（权重×2；一期占位值+人工）',
  price_abnormal  tinyint      NOT NULL DEFAULT 0 COMMENT '价格异常：0否 1是（+5分）',
  credit_deduct   int          NOT NULL DEFAULT 0 COMMENT '信用扣减分=(100-信用分)/10',
  enter_queue_at  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '进队时间（SLA响应时效起点）',
  first_handle_at datetime     DEFAULT NULL COMMENT '首次处置时间（首审时长=该值-进队时间）',
  handler_id      bigint       DEFAULT NULL COMMENT '处置人（SLA分人统计）',
  handle_cost_ms  int          DEFAULT NULL COMMENT '单条处置耗时（前端埋点上报，操作效率指标）',
  reject_reason   varchar(255) DEFAULT NULL COMMENT '驳回原因（预设+补充）',
  remark          varchar(255) DEFAULT NULL COMMENT '处置备注（申诉复盘依据）',
  create_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted         bigint       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_queue (status, priority_score, enter_queue_at) COMMENT '审核队列排序（权重优先，不按时间）',
  KEY idx_item (item_id),
  KEY idx_handler (handler_id, first_handle_at) COMMENT '分审核员SLA明细',
  KEY idx_pending_time (status, enter_queue_at) COMMENT '超24h积压红牌扫描'
) ENGINE=InnoDB COMMENT='商品审核（权重排序 + SLA计时）';

CREATE TABLE report (
  id           bigint       NOT NULL COMMENT '雪花ID',
  reporter_id  bigint       NOT NULL COMMENT '举报人',
  target_type  tinyint      NOT NULL COMMENT '1商品 2用户',
  target_id    bigint       NOT NULL COMMENT '被举报对象',
  reason       varchar(255) NOT NULL COMMENT '举报原因',
  evidence     json         DEFAULT NULL COMMENT '凭证图片（≤3）',
  status       tinyint      NOT NULL DEFAULT 1 COMMENT '1待受理 2处理中 3已处理',
  result       tinyint      DEFAULT NULL COMMENT '1成立 2不成立',
  handle_note  varchar(255) DEFAULT NULL COMMENT '处置说明（通知双方）',
  handler_id   bigint       DEFAULT NULL COMMENT '处理人',
  handle_time  datetime     DEFAULT NULL COMMENT '处理时间',
  create_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      bigint       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_target (target_type, target_id),
  KEY idx_queue (status, create_time),
  KEY idx_reporter (reporter_id, status)
) ENGINE=InnoDB COMMENT='举报';

CREATE TABLE appeal (
  id            bigint       NOT NULL COMMENT '雪花ID',
  applicant_id  bigint       NOT NULL COMMENT '申诉人',
  biz_type      tinyint      NOT NULL COMMENT '1商品审核 2举报 3订单纠纷',
  biz_id        bigint       NOT NULL COMMENT '关联业务ID',
  reason        varchar(500) NOT NULL COMMENT '申诉理由',
  evidence      json         DEFAULT NULL COMMENT '新凭证',
  status        tinyint      NOT NULL DEFAULT 1 COMMENT '1待复审 2维持 3撤销（终态，不可再申诉）',
  handler_id    bigint       DEFAULT NULL COMMENT '复审人（不得为原处置人）',
  handle_note   varchar(255) DEFAULT NULL COMMENT '复审结论',
  handle_time   datetime     DEFAULT NULL COMMENT '复审时间（承诺48h内）',
  create_time   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       bigint       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_biz (biz_type, biz_id),
  KEY idx_queue (status, create_time),
  KEY idx_applicant (applicant_id, status)
) ENGINE=InnoDB COMMENT='申诉（优先级高于普通审核，换人复审）';

-- ════════════════════════════════════════════════════════════
-- 七、信用 · 评价
-- ════════════════════════════════════════════════════════════

CREATE TABLE credit_log (
  id           bigint       NOT NULL COMMENT '雪花ID',
  user_id      bigint       NOT NULL COMMENT '用户',
  change_value int          NOT NULL COMMENT '变动值（正/负）',
  before_score int          NOT NULL COMMENT '变动前',
  after_score  int          NOT NULL COMMENT '变动后',
  reason_type  varchar(32)  NOT NULL COMMENT 'TRADE_DONE/BREACH_CANCEL/REPORTED/FORCE_OFF/BAN',
  biz_id       bigint       DEFAULT NULL COMMENT '关联业务ID',
  remark       varchar(255) DEFAULT NULL COMMENT '说明',
  create_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted      bigint       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id, create_time)
) ENGINE=InnoDB COMMENT='信用分变动流水（初始100，完成+1上限120，违约-5，举报成立-10，强制下架-15，封禁清零）';

CREATE TABLE user_review (
  id          bigint      NOT NULL COMMENT '雪花ID',
  order_id    bigint      NOT NULL COMMENT '订单',
  reviewer_id bigint      NOT NULL COMMENT '评价人',
  target_id   bigint      NOT NULL COMMENT '被评价人',
  rating      tinyint     NOT NULL COMMENT '1-5星',
  tags        json        DEFAULT NULL COMMENT '评价标签',
  comment     varchar(500) DEFAULT NULL COMMENT '评价内容',
  create_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted     bigint      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_reviewer (order_id, reviewer_id, deleted) COMMENT '一单一评',
  KEY idx_target (target_id, rating)
) ENGINE=InnoDB COMMENT='交易互评';

-- ════════════════════════════════════════════════════════════
-- 八、管理端 · 权限与日志
-- ════════════════════════════════════════════════════════════

CREATE TABLE admin_role (
  id          bigint      NOT NULL COMMENT '雪花ID',
  role_code   varchar(32) NOT NULL COMMENT 'SUPER/REVIEWER/OPERATOR/READONLY',
  role_name   varchar(32) NOT NULL COMMENT '超级管理员/审核员/运营/只读',
  sort        int         NOT NULL DEFAULT 0 COMMENT '排序',
  create_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted     bigint      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (role_code, deleted)
) ENGINE=InnoDB COMMENT='管理端角色';

CREATE TABLE admin_role_perm (
  id         bigint     NOT NULL COMMENT '雪花ID',
  role_id    bigint     NOT NULL COMMENT '角色',
  perm_code  varchar(32) NOT NULL COMMENT '权限点（与前端data-perm、后端注解三方同名）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_perm (role_id, perm_code),
  KEY idx_perm (perm_code)
) ENGINE=InnoDB COMMENT='角色×权限点（16个点，行式存储便于管理端矩阵渲染）';

CREATE TABLE admin_user (
  id            bigint      NOT NULL COMMENT '雪花ID',
  username      varchar(32) NOT NULL COMMENT '登录账号',
  email         varchar(64) DEFAULT NULL COMMENT '邮箱（注册 / 找回密码用，QQ 系：@qq.com、@foxmail.com）',
  password_hash varchar(128) NOT NULL COMMENT '密码哈希（不存明文）',
  real_name     varchar(32) DEFAULT NULL COMMENT '姓名',
  role_id       bigint      NOT NULL COMMENT '角色',
  status        tinyint     NOT NULL DEFAULT 1 COMMENT '1启用 0停用 2待分配角色（注册后、超管分配前，不可登录）',
  fail_count    int         NOT NULL DEFAULT 0 COMMENT '连续失败次数（5次锁10分钟）',
  lock_until    datetime    DEFAULT NULL COMMENT '锁定到期',
  last_login_at datetime    DEFAULT NULL COMMENT '最近登录',
  create_time   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       bigint      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username, deleted),
  UNIQUE KEY uk_email (email, deleted),
  KEY idx_role (role_id)
) ENGINE=InnoDB COMMENT='管理员';

-- ── 已有库升级（2026-09-29 追加，全新安装不需要执行）──────────────
-- ALTER TABLE admin_user ADD COLUMN email varchar(64) DEFAULT NULL COMMENT '邮箱' AFTER username;
-- ALTER TABLE admin_user ADD UNIQUE KEY uk_email (email, deleted);
-- 演示账号补邮箱（唯一索引下四个账号必须各不相同）：
-- UPDATE admin_user SET email='3037749727@qq.com' WHERE username='super';
-- UPDATE admin_user SET email='3037749728@qq.com' WHERE username='reviewer';
-- UPDATE admin_user SET email='3037749729@qq.com' WHERE username='operator';
-- UPDATE admin_user SET email='3037749730@qq.com' WHERE username='readonly';

CREATE TABLE sys_operation_log (
  id           bigint       NOT NULL COMMENT '雪花ID',
  admin_id     bigint       NOT NULL COMMENT '操作人',
  admin_name   varchar(32)  DEFAULT NULL COMMENT '操作人姓名（冗余，避免删号后查不到）',
  role_code    varchar(32)  DEFAULT NULL COMMENT '操作时角色（冗余）',
  module       varchar(32)  NOT NULL COMMENT '模块：item/order/user/review/system',
  op_type      varchar(32)  NOT NULL COMMENT '操作类型：APPROVE/REJECT/BAN/OFF_SHELF...',
  biz_id       bigint       DEFAULT NULL COMMENT '业务对象ID',
  description  varchar(255) DEFAULT NULL COMMENT '操作描述',
  before_value varchar(2000) DEFAULT NULL COMMENT '变更前（脱敏）',
  after_value  varchar(2000) DEFAULT NULL COMMENT '变更后（脱敏）',
  result       tinyint      NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
  error_code   varchar(32)  DEFAULT NULL COMMENT '失败错误码',
  ip           varchar(64)  DEFAULT NULL COMMENT 'IP',
  ua           varchar(255) DEFAULT NULL COMMENT 'User-Agent',
  trace_id     varchar(64)  DEFAULT NULL COMMENT '链路ID（与日志对齐）',
  cost_ms      int          DEFAULT NULL COMMENT '耗时',
  create_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_admin (admin_id, create_time),
  KEY idx_biz (module, biz_id),
  KEY idx_create (create_time)
) ENGINE=InnoDB COMMENT='操作日志（只追加不可删改；事务提交后异步写入）';

-- ════════════════════════════════════════════════════════════
-- 九、减碳系数（估算假设，论文须标注）
-- ════════════════════════════════════════════════════════════

CREATE TABLE carbon_factor (
  id          bigint         NOT NULL COMMENT '雪花ID',
  category_id bigint         NOT NULL COMMENT '分类',
  factor_kg   decimal(10,2)  NOT NULL COMMENT '减碳系数 kg/件',
  note        varchar(255)   DEFAULT NULL COMMENT '假设说明与局限（论文须标注为估算值）',
  create_time datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted     bigint         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_category (category_id, deleted)
) ENGINE=InnoDB COMMENT='减碳估算系数（教材0.8/数码25/服饰3/生活用品5/其他2，均为假设值）';

CREATE TABLE sys_sensitive_word (
  id          bigint      NOT NULL COMMENT '雪花ID',
  word        varchar(64) NOT NULL COMMENT '词',
  level       tinyint     NOT NULL DEFAULT 2 COMMENT '1高危（直接拒绝）2低危（替换并打标）',
  status      tinyint     NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  create_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted     bigint      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_word (word, deleted),
  KEY idx_level (level, status)
) ENGINE=InnoDB COMMENT='敏感词库（启动时载入内存，Redis 发布订阅热更新）';

-- ════════════════════════════════════════════════════════════
-- 十、种子数据（建库后必灌，否则前端字典为空）
-- ════════════════════════════════════════════════════════════

INSERT INTO dict_grade (id, code, name, description, sort) VALUES
 (1001,'A','几乎全新','无明显使用痕迹，功能完好',1),
 (1002,'B','轻微使用痕迹','有轻微痕迹，不影响使用',2),
 (1003,'C','明显使用痕迹','有明显痕迹或轻微瑕疵，功能正常',3),
 (1004,'D','有瑕疵','存在功能或外观问题，须在描述中说明',4);

INSERT INTO admin_role (id, role_code, role_name, sort) VALUES
 (2001,'SUPER','超级管理员',1),
 (2002,'REVIEWER','审核员',2),
 (2003,'OPERATOR','运营',3),
 (2004,'READONLY','只读',4);

-- 权限点分配（与 business/03 F-A1 矩阵一致；前端 data-perm、后端注解三方同名）
-- SUPER：全部
INSERT INTO admin_role_perm (id, role_id, perm_code) VALUES
 (30001,2001,'dashboard.view'),(30002,2001,'report.view'),(30003,2001,'item.view'),
 (30004,2001,'item.offline'),(30005,2001,'item.delete'),(30006,2001,'user.view'),
 (30007,2001,'user.verify'),(30008,2001,'user.ban'),(30009,2001,'review.judge'),
 (30010,2001,'review.forceoff'),(30011,2001,'review.appeal'),(30012,2001,'order.refund'),
 (30013,2001,'order.arbitrate'),(30014,2001,'content.edit'),(30015,2001,'settings.edit'),
 (30016,2001,'log.view'),
 -- REVIEWER：审核相关，不能碰用户封禁与系统设置（关键互斥）
 (30101,2002,'dashboard.view'),(30102,2002,'report.view'),(30103,2002,'item.view'),
 (30104,2002,'review.judge'),(30105,2002,'review.forceoff'),(30106,2002,'review.appeal'),
 -- OPERATOR：运营，能处置商品/用户认证/订单/内容，不能裁决审核
 (30201,2003,'dashboard.view'),(30202,2003,'report.view'),(30203,2003,'item.view'),
 (30204,2003,'item.offline'),(30205,2003,'user.view'),(30206,2003,'user.verify'),
 (30207,2003,'order.refund'),(30208,2003,'order.arbitrate'),(30209,2003,'content.edit'),
 (30210,2003,'log.view'),
 -- READONLY：答辩巡盘用
 (30301,2004,'dashboard.view'),(30302,2004,'report.view'),(30303,2004,'item.view'),
 (30304,2004,'user.view');

-- ── 校区 ────────────────────────────────────────────────
INSERT INTO dict_campus (id, name, sort) VALUES
 (1001,'主校区',1),
 (1002,'东校区',2);

-- ── 学院（报表「学院×品类矩阵」的行，12 个）────────────────
INSERT INTO dict_college (id, name, sort) VALUES
 (1101,'计算机学院',1),
 (1102,'电子信息学院',2),
 (1103,'机械工程学院',3),
 (1104,'土木工程学院',4),
 (1105,'材料与化学学院',5),
 (1106,'生命科学学院',6),
 (1107,'数学与统计学院',7),
 (1108,'经济管理学院',8),
 (1109,'文法学院',9),
 (1110,'外国语学院',10),
 (1111,'艺术设计学院',11),
 (1112,'教育学院',12);

-- ── 商品分类（一级 8 个，对应小程序金刚区；二级用于筛选）────
INSERT INTO dict_category (id, parent_id, name, sort) VALUES
 (1201,0,'教材教辅',1),
 (1202,0,'数码电子',2),
 (1203,0,'生活用品',3),
 (1204,0,'服饰鞋包',4),
 (1205,0,'运动器材',5),
 (1206,0,'美妆个护',6),
 (1207,0,'票务卡券',7),
 (1208,0,'其他闲置',8);

INSERT INTO dict_category (id, parent_id, name, sort) VALUES
 (1211,1201,'公共课教材',1),
 (1212,1201,'专业课教材',2),
 (1213,1201,'考研资料',3),
 (1221,1202,'手机平板',1),
 (1222,1202,'电脑配件',2),
 (1223,1202,'数码外设',3),
 (1231,1203,'宿舍用品',1),
 (1232,1203,'家电小件',2),
 (1233,1203,'收纳家具',3),
 (1241,1204,'日常服饰',1),
 (1242,1204,'鞋靴箱包',2),
 (1251,1205,'球类器材',1),
 (1252,1205,'健身器械',2),
 (1271,1207,'演出门票',1),
 (1272,1207,'健身卡券',2);

-- ── 校内面交点（报表「面交所热力」的维度）──────────────────
INSERT INTO dict_meet_point (id, campus_id, name, open_hours, sort) VALUES
 (1301,1001,'一食堂门口','07:00-21:00',1),
 (1302,1001,'图书馆一楼大厅','08:00-22:00',2),
 (1303,1001,'宿舍区 A 座','全天',3),
 (1304,1001,'学校南门','06:00-23:00',4),
 (1305,1001,'体育馆北门','08:00-21:00',5),
 (1306,1002,'东校区食堂','07:00-21:00',6);

-- ── 减碳系数（论文必须标注为估算假设值）────────────────────
INSERT INTO carbon_factor (id, category_id, factor_kg, note) VALUES
 (1401,1201,0.80,'教材教辅：按单册纸张重量估算，未计印刷与运输'),
 (1402,1202,25.00,'数码电子：按整机生产碳足迹量级估算，未区分品类与剩余寿命'),
 (1403,1203,5.00,'生活用品：按小件日用品均值估算'),
 (1404,1204,3.00,'服饰鞋包：按单件纺织品均值估算'),
 (1405,1208,2.00,'其他闲置：保守取值，避免高估');
-- 注：以上系数均为假设值，未经本校实测校验，仅用于演示「循环率」指标的量级。

-- ── 敏感词（分级：1 高危直接拒绝 / 2 低危替换打标）──────────
INSERT INTO sys_sensitive_word (id, word, level) VALUES
 (1501,'加微信',2),
 (1502,'私聊',2),
 (1503,'低价出',2),
 (1504,'秒杀',2),
 (1505,'代购',2),
 (1506,'刷单',1),
 (1507,'套现',1),
 (1508,'办证',1),
 (1509,'外挂',1);
-- 说明：低危词（加微信、低价出）在校园场景属正常表述，故只打标送审而不阻断。

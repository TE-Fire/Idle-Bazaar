-- ════════════════════════════════════════════════════════════
-- 学生端注册 / 邮箱登录 / 找回密码（2026-10-31）
--
-- schema.sql 只对新建库生效，已在运行的库要手工执行本补丁。
-- 只做三件事：加 email、加 password_hash、给两个账号列补唯一索引。
--
-- 唯一索引必须带 deleted：逻辑删除的值是删除时刻的时间戳，
-- 只对 (phone) 建唯一会让「注销后重新注册同一手机号」直接冲突。
-- 两列都允许 NULL（未设置密码的历史用户、只登记手机号的用户），
-- MySQL 的唯一索引对 NULL 不去重，多行 NULL 不冲突。
-- ════════════════════════════════════════════════════════════

ALTER TABLE app_user
  ADD COLUMN email varchar(128) DEFAULT NULL COMMENT 'QQ 邮箱（注册 / 登录 / 找回密码）' AFTER phone,
  ADD COLUMN password_hash varchar(100) DEFAULT NULL COMMENT 'BCrypt 密码摘要；为空表示未设置密码' AFTER email;

-- 历史数据手机号唯一（12 个演示账号已核对无重复），可安全加唯一约束
ALTER TABLE app_user
  ADD UNIQUE KEY uk_phone (phone, deleted),
  ADD UNIQUE KEY uk_email (email, deleted);

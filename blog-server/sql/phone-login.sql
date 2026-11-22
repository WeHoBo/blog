-- ============================================================
-- 手机号 + 密码 登录体系
-- ------------------------------------------------------------
-- 一次建好三列一索引，整份脚本可重复执行（幂等）。
--
-- ⚠️ 部署顺序铁律：**先跑这份 SQL，再换 jar**。
--    实体里新增的字段会被 MyBatis-Plus 拼进所有 SELECT/UPDATE，
--    库表没列的话，全站登录与用户读写立刻报 SQL 错误。
--
-- 背景（为什么必须加 password_set 这一列）：
--   OAuth 建号时写进 user.password 的是 BCrypt(随机 UUID)，
--   没有任何人知道原文 —— 也就是说这批账号"有密码字段但没有可用密码"。
--   改造后要靠 password_set 区分：
--      0 = 从没设过（OAuth 自动建号，密码是随机占位）
--      1 = 用户自己设过，密码可用
--   个人中心据此提示"你还没设置密码"，而不是让用户对着一个
--   自己不知道的密码反复尝试。
-- ============================================================

-- ---------- 1. user.phone ----------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'user'
    AND COLUMN_NAME  = 'phone'
);

SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `user` ADD COLUMN `phone` VARCHAR(20) DEFAULT NULL COMMENT ''手机号（11 位，登录/找回密码用）'' AFTER `email`',
  'SELECT ''user.phone 已存在，跳过'''
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. user.password_set ----------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'user'
    AND COLUMN_NAME  = 'password_set'
);

SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `user` ADD COLUMN `password_set` TINYINT NOT NULL DEFAULT 0 COMMENT ''1=用户自己设过密码 0=随机占位（OAuth 建号）'' AFTER `password`',
  'SELECT ''user.password_set 已存在，跳过'''
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3. user.phone_verified_at ----------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'user'
    AND COLUMN_NAME  = 'phone_verified_at'
);

SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `user` ADD COLUMN `phone_verified_at` DATETIME DEFAULT NULL COMMENT ''手机号最近一次验证通过时间'' AFTER `phone`',
  'SELECT ''user.phone_verified_at 已存在，跳过'''
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 4. uk_phone 唯一索引 ----------
-- MySQL 的唯一索引允许多行 NULL，所以 OAuth 老用户（phone 为空）
-- 全部不受影响，不会因为"重复空值"导致建索引失败。
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'user'
    AND INDEX_NAME   = 'uk_phone'
);

SET @ddl := IF(
  @idx_exists = 0,
  'ALTER TABLE `user` ADD UNIQUE KEY `uk_phone` (`phone`)',
  'SELECT ''uk_phone 已存在，跳过'''
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 5. 部署后自检（只读，仅用于确认现状） ----------
-- 看有多少老用户还没绑手机（他们只能用 OAuth 登录，仍需保留 OAuth 入口）：
--   SELECT COUNT(*) AS 未绑手机用户数 FROM `user` WHERE `phone` IS NULL;
--
-- 看有多少账号的密码是随机占位（这批人"设置密码"之前无法用密码登录）：
--   SELECT COUNT(*) AS 无可用密码账号数 FROM `user` WHERE `password_set` = 0;
--
-- 按注册来源分布，判断哪些是 OAuth 老账号：
--   SELECT `source`, COUNT(*) FROM `user` GROUP BY `source`;

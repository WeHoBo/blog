-- 用户个人简介（首页右栏作者卡片展示）
--
-- 为什么要新加字段而不是复用 site_config.bloggerBio：
--   site_config 里的 bloggerBio 是「站点广播」—— 所有访客看到同一份，由管理员在后台编辑；
--   这里是「我这个登录用户是谁」—— 每个人看到自己的，用户在个人中心自己写。
--   两者语义不同，混用会导致「普通用户改了简介，把站点广播改掉了」。
--
-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，所以用 information_schema 判断后再执行，
-- 保证这个脚本可以重复跑（和 brand-ai-config.sql 一样幂等）。

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user'
    AND COLUMN_NAME = 'bio'
);

SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `user` ADD COLUMN `bio` VARCHAR(200) DEFAULT NULL COMMENT ''个人简介，用户在个人中心自己写'' AFTER `avatar`',
  'SELECT ''user.bio 已存在，跳过'''
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

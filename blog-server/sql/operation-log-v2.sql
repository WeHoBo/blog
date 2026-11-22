-- ============================================================
-- 操作审计扩容：operation_log 增加 4 列
-- ------------------------------------------------------------
-- 建 1 列脚本要拆成 4 段是因为 MySQL 8 不支持
-- `ADD COLUMN IF NOT EXISTS`，只能查 information_schema 再动态执行。
-- 整份脚本可重复执行（幂等）。
--
-- 新增列：
--   status     1 成功 / 0 失败   ← 以前只记成功，看不到"试过但被拒"的动作
--   cost_ms    该操作耗时（毫秒）
--   ip         客户端 IP（完整值，仅后台可见）
--   request_id 与文件日志对齐的链路 ID
--
-- ⚠️ 必须先执行本脚本再换 jar：
--    Article 那次的教训 —— 实体多出的字段会被 MyBatis-Plus
--    拼进所有 SELECT/UPDATE，库表没列就是全站读写报错。
-- ============================================================

-- ---------- 1. status ----------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'operation_log'
    AND COLUMN_NAME = 'status'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `operation_log` ADD COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT ''1 成功 / 0 失败'' AFTER `target_type`',
  'SELECT ''operation_log.status 已存在，跳过'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. cost_ms ----------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'operation_log'
    AND COLUMN_NAME = 'cost_ms'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `operation_log` ADD COLUMN `cost_ms` BIGINT DEFAULT NULL COMMENT ''耗时（毫秒）'' AFTER `status`',
  'SELECT ''operation_log.cost_ms 已存在，跳过'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3. ip ----------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'operation_log'
    AND COLUMN_NAME = 'ip'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `operation_log` ADD COLUMN `ip` VARCHAR(64) DEFAULT NULL COMMENT ''客户端 IP（完整值，仅后台可见）'' AFTER `cost_ms`',
  'SELECT ''operation_log.ip 已存在，跳过'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 4. request_id ----------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'operation_log'
    AND COLUMN_NAME = 'request_id'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `operation_log` ADD COLUMN `request_id` VARCHAR(36) DEFAULT NULL COMMENT ''与文件日志对齐的链路 ID'' AFTER `ip`',
  'SELECT ''operation_log.request_id 已存在，跳过'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 5. 索引 ----------
-- 后台默认按 create_time 倒序翻页；「只看失败」要按 status 过滤。
-- 单表数据量不大（每次写操作一行），这里只补一个组合索引就够。
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'operation_log'
    AND INDEX_NAME = 'idx_oplog_time_status'
);
SET @ddl := IF(
  @idx_exists = 0,
  'ALTER TABLE `operation_log` ADD INDEX `idx_oplog_time_status` (`create_time` DESC, `status`)',
  'SELECT ''idx_oplog_time_status 已存在，跳过'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 6. 历史数据回填 ----------
-- 老记录没有 status，按"成功"补 1（当时确实只有成功才写）。
UPDATE `operation_log` SET `status` = 1 WHERE `status` IS NULL;

-- 文章「精品」标记（卡片上的一枚质量徽标，不决定首页布局）
--
-- 为什么不复用 is_top：
--   is_top 是**排位**概念：全站列表都以 `is_top DESC` 打头（归档/分类/标签页），
--   而且现在首页中间那栏完全由它驱动。
--   is_featured 是**质量标记**：只在卡片标题前多一枚「精品」徽标，不改变任何排序与布局。
--   两者混用一个字段会让「精品」顺手把首页布局也改掉，并且会把「置顶」
--   这个普通管理员现在就能用的能力锁成超管专属。
--
-- 只有超级管理员能把它置 1：
--   前台编辑器里的勾选框只对超管渲染，但请求可以伪造，
--   因此 ArticleService#gateFeatured 会在落库前再判一次角色，非超管一律丢弃。
--
-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 判断后再执行，
-- 保证脚本可重复跑（与 user-bio.sql / brand-ai-config.sql 一致）。

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'article'
    AND COLUMN_NAME = 'is_featured'
);

SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `article` ADD COLUMN `is_featured` TINYINT DEFAULT 0 COMMENT ''是否设为精品（卡片徽标），仅超管可设'' AFTER `is_top`',
  'SELECT ''article.is_featured 已存在，跳过'''
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 后台按精品筛选、卡片渲染都会读这一列，补一个复合索引
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'article'
    AND INDEX_NAME = 'idx_featured'
);

SET @ddl_idx := IF(
  @idx_exists = 0,
  'ALTER TABLE `article` ADD INDEX `idx_featured` (`status`, `is_deleted`, `is_featured`, `create_time`)',
  'SELECT ''idx_featured 已存在，跳过'''
);

PREPARE stmt2 FROM @ddl_idx;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- 顺手核对：当前精品文章列表（跑完可以直接看效果）
SELECT id, title, status, is_featured
FROM `article`
WHERE is_featured = 1 AND is_deleted = 0
ORDER BY create_time DESC;

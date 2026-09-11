-- ============================================================
-- 性能与数据完整性增量迁移（用于已存在的生产库）
-- ------------------------------------------------------------
-- 与 blog.sql 保持一致：blog.sql 已包含以下索引，仅供全新安装使用；
-- 本脚本用于给「已经跑起来的库」补索引 / 唯一约束。
--
-- 执行前提：
--   article.word_count、user.huawei_id 两列由应用启动时的 WordCountMigrator 自动补齐，
--   因此请先让应用正常启动过一次（或用 blog.sql 建库）后再执行本脚本。
--
-- 幂等性：重复执行会因索引已存在而报 "Duplicate key name"，
--         属预期错误，可直接忽略（不影响数据）。
-- 建议：执行前先备份。ALTER 加索引在 MySQL 8 上默认 ONLINE（INPLACE），大表也不会长时间锁表。
-- ============================================================

USE blog;

-- 1) 文章列表查询：固定过滤 status + is_deleted，并按 is_top / create_time 排序
ALTER TABLE article
    ADD INDEX idx_list (status, is_deleted, is_top, create_time);

-- 2) 按标签反查文章：article_tag 主键为 (article_id, tag_id)，按 tag_id 查用不上索引
ALTER TABLE article_tag
    ADD INDEX idx_tag (tag_id);

-- 3) 第三方账号唯一：防止并发回调为同一 openId 创建重复用户（MySQL 唯一索引允许多行 NULL）
ALTER TABLE user ADD UNIQUE KEY uk_github_id (github_id);
ALTER TABLE user ADD UNIQUE KEY uk_gitee_id (gitee_id);
ALTER TABLE user ADD UNIQUE KEY uk_huawei_id (huawei_id);

-- 4) 评论列表按文章 + 时间倒序查询
ALTER TABLE comment
    ADD INDEX idx_article_status (article_id, status, create_time);

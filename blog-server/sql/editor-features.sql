-- ============================================================
-- 编辑器能力增强增量迁移（用于已存在的生产库）
-- ------------------------------------------------------------
-- 本脚本为「已跑起来的库」补齐三样东西：
--   1) article.publish_at      —— 定时发布
--   2) article_revision 表     —— 版本历史（保存前自动留快照）
--   3) upload_file 表          —— 素材库（记录历史上传的图片）
--
-- 幂等性：已用 INFORMATION_SCHEMA 做存在性判断 / CREATE TABLE IF NOT EXISTS，
--         可安全重复执行，不会报 "Duplicate column name"。
-- 建议：执行前先备份。
-- 注意：blog.sql 是「全新安装」用的破坏性脚本，已同步包含以上结构；
--       已经跑着的库只需要执行本脚本。
-- ============================================================

USE blog;

-- 1) article.publish_at：定时发布时间
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article' AND COLUMN_NAME = 'publish_at'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE article ADD COLUMN publish_at DATETIME DEFAULT NULL COMMENT ''定时发布时间，到期由后端定时任务自动转为已发布'' AFTER status',
    'SELECT ''article.publish_at 已存在，跳过'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 定时发布任务的扫描索引：固定条件 status + publish_at，且 publish_at 非空
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article' AND INDEX_NAME = 'idx_publish_at'
);
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE article ADD INDEX idx_publish_at (status, publish_at)',
    'SELECT ''idx_publish_at 已存在，跳过'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 文章版本快照表
--    每次「保存」前把被覆盖的那一版写进来，用于 diff 与回滚。
--    故意不存 is_deleted / view_count 等运行期字段，只存内容相关的快照。
CREATE TABLE IF NOT EXISTS article_revision (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    article_id  BIGINT       NOT NULL COMMENT '文章ID',
    title       VARCHAR(200) DEFAULT NULL COMMENT '标题快照',
    slug        VARCHAR(200) DEFAULT NULL COMMENT '别名快照',
    content_md  LONGTEXT     DEFAULT NULL COMMENT '正文快照',
    summary     VARCHAR(500) DEFAULT NULL COMMENT '摘要快照',
    cover       VARCHAR(500) DEFAULT NULL COMMENT '封面快照',
    category_id BIGINT       DEFAULT NULL COMMENT '分类快照',
    series      VARCHAR(100) DEFAULT NULL COMMENT '系列快照',
    status      TINYINT      DEFAULT NULL COMMENT '保存时的发布状态',
    word_count  INT          DEFAULT 0 COMMENT '字数快照',
    editor_id   BIGINT       DEFAULT NULL COMMENT '本次修改人',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '快照时间',
    PRIMARY KEY (id),
    KEY idx_article_time (article_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章版本快照';

-- 3) 上传文件记录表（素材库）
--    只记录元信息，实际文件仍由 FileStorage 落盘/上云。
CREATE TABLE IF NOT EXISTS upload_file (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    url           VARCHAR(500) NOT NULL COMMENT '访问地址',
    original_name VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
    ext           VARCHAR(20)  DEFAULT NULL COMMENT '扩展名（小写）',
    content_type  VARCHAR(100) DEFAULT NULL COMMENT 'Content-Type',
    file_size     BIGINT       DEFAULT 0 COMMENT '字节数',
    uploader_id   BIGINT       DEFAULT NULL COMMENT '上传人ID',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    PRIMARY KEY (id),
    KEY idx_create_time (create_time),
    KEY idx_uploader (uploader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上传文件记录';

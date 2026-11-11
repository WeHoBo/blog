-- 评论举报表（上架合规：UGC 内容需要「可管理 + 有举报入口」）
-- 执行：mysql -uroot -p blog < sql/comment-report.sql

CREATE TABLE IF NOT EXISTS comment_report (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    comment_id  BIGINT       NOT NULL COMMENT '被举报评论ID',
    user_id     BIGINT       NOT NULL COMMENT '举报人用户ID',
    reason      VARCHAR(20)  NOT NULL COMMENT '举报原因（固定枚举）',
    status      TINYINT      DEFAULT 0 COMMENT '0待处理 1已忽略 2已删除该评论',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 同一个人对同一条评论只能举报一次，靠唯一键兜并发
    UNIQUE KEY uk_comment_user (comment_id, user_id),
    KEY idx_status (status),
    KEY idx_comment (comment_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '评论举报';

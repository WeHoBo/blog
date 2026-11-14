-- ============================================================
-- AI 用量与执行轨迹（P-1 止血 + P0 Agent 地基）
-- 手工执行：mysql -uroot -p blog < blog-server/sql/agent.sql
-- ============================================================

-- ------------------------------------------------------------
-- 1) AI 每日用量聚合（按 日期 + 用户 + 模型）
-- 明细不存这里，这里只做预聚合，后台图表查它不扫明细。
-- cost 在写入时按当时单价算好并落库，避免日后调价改写历史账单。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_usage_daily (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    stat_date         DATE           NOT NULL COMMENT '统计日期（服务器时区 +08:00）',
    user_id           BIGINT         NOT NULL COMMENT '提问用户',
    model_code        VARCHAR(100)   NOT NULL DEFAULT '' COMMENT '模型标识，如 deepseek-chat',
    request_count     INT            NOT NULL DEFAULT 0 COMMENT '提问次数（按轮计，一次提问=1）',
    prompt_tokens     BIGINT         NOT NULL DEFAULT 0 COMMENT '输入 token 累计',
    completion_tokens BIGINT         NOT NULL DEFAULT 0 COMMENT '输出 token 累计',
    cost              DECIMAL(12, 6) NOT NULL DEFAULT 0 COMMENT '估算金额（元），写入时快照',
    create_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date_user_model (stat_date, user_id, model_code),
    KEY idx_date (stat_date),
    KEY idx_user (user_id, stat_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI 每日用量聚合';

-- ------------------------------------------------------------
-- 2) Agent 执行轨迹（P0 写入，先建好表）
-- 步骤链形如：[{"step":1,"tool":"search_articles","ms":83,"ok":true}]
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_trace (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL COMMENT '提问用户',
    question          TEXT         NOT NULL COMMENT '原始问题',
    model             VARCHAR(100) NOT NULL DEFAULT '' COMMENT '实际使用的模型',
    steps             JSON         NULL COMMENT '工具调用步骤链',
    step_count        TINYINT      NOT NULL DEFAULT 0,
    prompt_tokens     INT          NOT NULL DEFAULT 0,
    completion_tokens INT          NOT NULL DEFAULT 0,
    total_ms          INT          NOT NULL DEFAULT 0,
    status            VARCHAR(16)  NOT NULL DEFAULT 'ok' COMMENT 'ok|timeout|error|aborted',
    error_msg         VARCHAR(500) NULL,
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_user_time (user_id, create_time),
    KEY idx_time (create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Agent 执行轨迹';

-- ============================================================
-- P2：AI 模型注册表 + 全局设置
-- 手工执行：mysql -uroot -p blog < blog-server/sql/ai-model.sql
-- 幂等，可重复执行。
-- ============================================================

-- ------------------------------------------------------------
-- 1) 模型注册表：一行 = 一个可选模型。
--    多厂商不靠代码分支，靠 base_url + model_id —— 国内主流厂商都是 OpenAI 兼容端点。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_model (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    display_name   VARCHAR(64)   NOT NULL COMMENT '后台展示名，如「DeepSeek 对话」',
    provider       VARCHAR(32)   NOT NULL COMMENT 'deepseek|dashscope|zhipu|moonshot|siliconflow|openrouter|custom',
    model_id       VARCHAR(100)  NOT NULL COMMENT '传给 API 的 model 参数',
    base_url       VARCHAR(200)  NOT NULL COMMENT 'OpenAI 兼容端点',
    -- 密钥两种存放模式，字段互斥：
    --   api_key_cipher 有值 → 后台填的密钥，AES-256-GCM 加密后入库
    --   api_key_env    有值 → 密钥留在服务器 .env，库里只存变量名
    api_key_cipher VARCHAR(500)  NULL     COMMENT 'AES-256-GCM 密文，格式 iv:tag:cipher（Base64）',
    api_key_mask   VARCHAR(32)   NULL     COMMENT '回显用，如 sk-****3f2a，永远不回明文',
    api_key_env    VARCHAR(64)   NULL     COMMENT '密钥所在环境变量名',
    temperature    DECIMAL(3, 2) NOT NULL DEFAULT 0.30,
    max_tokens     INT           NOT NULL DEFAULT 4096,
    context_window INT           NOT NULL DEFAULT 64000 COMMENT '上下文上限，用于裁剪判断',
    support_stream TINYINT(1)    NOT NULL DEFAULT 1,
    support_tools  TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '0 则不下发 tools 参数，否则厂商会 400',
    input_price    DECIMAL(10, 4) NOT NULL DEFAULT 0 COMMENT '元 / 百万 token',
    output_price   DECIMAL(10, 4) NOT NULL DEFAULT 0 COMMENT '元 / 百万 token',
    enabled        TINYINT(1)    NOT NULL DEFAULT 1,
    sort           INT           NOT NULL DEFAULT 0,
    remark         VARCHAR(200)  NULL,
    is_deleted     TINYINT(1)    NOT NULL DEFAULT 0,
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_provider_model (provider, model_id),
    KEY idx_enabled (is_deleted, enabled, sort)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI 模型注册表';

-- ------------------------------------------------------------
-- 2) 全局设置：固定单行 id=1。
--    默认模型用**单值** default_model_id 表达，不用 is_default 布尔 ——
--    布尔位在并发下很容易出现两行都为 1，之后谁都说不清哪个才是默认。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_setting (
    id                  BIGINT PRIMARY KEY DEFAULT 1,
    default_model_id    BIGINT        NULL COMMENT '全局默认模型',
    fallback_model_id   BIGINT        NULL COMMENT '主模型失败时降级到此模型',
    allow_user_choice   TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否允许用户自选模型',
    minute_limit        INT           NOT NULL DEFAULT 5 COMMENT '每 60s 提问次数上限，0=不限',
    hourly_limit        INT           NOT NULL DEFAULT 30,
    daily_limit         INT           NOT NULL DEFAULT 100,
    daily_token_limit   INT           NOT NULL DEFAULT 0 COMMENT '每日 token 上限，0=不限',
    global_daily_budget DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '全站日预算（元），0=不限',
    config_version      BIGINT        NOT NULL DEFAULT 1 COMMENT '每次改动 +1，供运行时缓存失效',
    updated_by          BIGINT        NULL,
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI 全局设置（单行）';

-- ------------------------------------------------------------
-- 3) 种子数据：把现在线上在跑的 DeepSeek 建成默认模型。
--    密钥仍留在 .env（api_key_env），所以升级后**不需要**在后台重新填 key，
--    行为与升级前完全一致；admin 想换厂商时再在后台新增一行即可。
-- ------------------------------------------------------------
INSERT INTO ai_model (id, display_name, provider, model_id, base_url, api_key_env,
                      temperature, max_tokens, support_stream, support_tools,
                      input_price, output_price, enabled, sort, remark)
VALUES (1, 'DeepSeek 对话', 'deepseek', 'deepseek-chat', 'https://api.deepseek.com',
        'DEEPSEEK_API_KEY', 0.30, 4096, 1, 1, 2.0000, 8.0000, 1, 0,
        '默认模型。密钥取自服务器环境变量 DEEPSEEK_API_KEY')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name);

INSERT INTO ai_setting (id, default_model_id, minute_limit, hourly_limit, daily_limit)
VALUES (1, 1, 5, 30, 100)
ON DUPLICATE KEY UPDATE id = id;

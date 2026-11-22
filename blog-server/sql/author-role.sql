-- ============================================================
-- 作者（author）角色 + 答题申请体系
-- ------------------------------------------------------------
-- 一次性把三样东西建好，整份脚本可重复执行（幂等）：
--   1. article.reject_reason      驳回理由（仅 status=3 时有值）
--   2. apply_question             申请用的选择题（答案只在库里，绝不下发）
--   3. apply_setting              通过题数 + 开放开关（单行，id=1）
--
-- ⚠️ 文章状态从这一版起是四个值：
--      0 草稿 / 1 已发布 / 2 待审 / 3 已驳回
--    只有 1 是公开的。全站公开查询都写死 status=1，所以 2/3 天然隐身。
--    🔴 待审**不能**复用 0：ScheduledPublishJob 每分钟会把
--       「status=0 且 publish_at 已到期」的草稿自动翻成已发布，
--       待审若复用 0，作者只要把时间调到过去就能绕过审核上线。
-- ============================================================

-- ---------- 1. article.reject_reason ----------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'article'
    AND COLUMN_NAME = 'reject_reason'
);

SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `article` ADD COLUMN `reject_reason` VARCHAR(500) DEFAULT NULL COMMENT ''驳回理由，仅 status=3 时有值'' AFTER `status`',
  'SELECT ''article.reject_reason 已存在，跳过'''
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. apply_question ----------
CREATE TABLE IF NOT EXISTS `apply_question` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `question`    VARCHAR(300) NOT NULL                COMMENT '题干',
  `option_a`    VARCHAR(200) NOT NULL                COMMENT '选项 A',
  `option_b`    VARCHAR(200) NOT NULL                COMMENT '选项 B',
  `option_c`    VARCHAR(200) NOT NULL                COMMENT '选项 C',
  `answer`      TINYINT      NOT NULL DEFAULT 0      COMMENT '正确选项下标 0=A 1=B 2=C（绝不下发前端）',
  `sort`        INT          NOT NULL DEFAULT 1      COMMENT '展示顺序',
  `enabled`     TINYINT      NOT NULL DEFAULT 1      COMMENT '1 启用 / 0 停用',
  `create_time` DATETIME     DEFAULT NULL,
  `update_time` DATETIME     DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '申请发布权限的选择题';

-- ---------- 3. apply_setting ----------
CREATE TABLE IF NOT EXISTS `apply_setting` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `pass_count`  INT      NOT NULL DEFAULT 2 COMMENT '答对多少题算通过',
  `enabled`     TINYINT  NOT NULL DEFAULT 1 COMMENT '1 开放申请 / 0 关闭',
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '申请发布权限的全局设置（单行）';

-- 单行种子数据。用 NOT EXISTS 而不是 INSERT IGNORE：
-- 后者在"表已有一行但 id 不是 1"时会再插一行，导致配置出现两份。
INSERT INTO `apply_setting` (`pass_count`, `enabled`, `create_time`, `update_time`)
SELECT 2, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `apply_setting`);

-- ---------- 4. 授予 / 收回 author 角色（按需执行，改用户名即可） ----------
-- UPDATE `user` SET `role` = 'author' WHERE `username` = '换成对方的用户名';
-- UPDATE `user` SET `role` = 'user'   WHERE `username` = '换成对方的用户名';
--
-- ⚠️ 改完角色必须让对方**重新登录**（或走一次答题拿新 token）：
--    role 是写死在 JWT claim 里的，旧 token 最长 24 小时内仍认老角色。

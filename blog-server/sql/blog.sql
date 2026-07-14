CREATE DATABASE IF NOT EXISTS blog DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE blog;

DROP TABLE IF EXISTS article_tag;
DROP TABLE IF EXISTS comment;
DROP TABLE IF EXISTS article;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS tag;
DROP TABLE IF EXISTS friend_link;
DROP TABLE IF EXISTS site_config;
DROP TABLE IF EXISTS user;

-- 用户表
CREATE TABLE user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(50)  NOT NULL COMMENT '用户名',
    password    VARCHAR(255) NOT NULL COMMENT '密码',
    nickname    VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    avatar      VARCHAR(500) DEFAULT NULL COMMENT '头像',
    email       VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    role        VARCHAR(20)  DEFAULT 'user' COMMENT '角色 admin/user',
    status      TINYINT      DEFAULT 1 COMMENT '状态 0禁用 1启用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 分类表
CREATE TABLE category (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name          VARCHAR(50)  NOT NULL COMMENT '分类名',
    slug          VARCHAR(100) NOT NULL COMMENT '别名',
    parent_id     BIGINT       DEFAULT 0 COMMENT '父分类ID',
    sort          INT          DEFAULT 0 COMMENT '排序',
    article_count INT          DEFAULT 0 COMMENT '文章数量',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类表';

-- 标签表
CREATE TABLE tag (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(50)  NOT NULL COMMENT '标签名',
    slug        VARCHAR(100) NOT NULL COMMENT '别名',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';

-- 文章表
CREATE TABLE article (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    title         VARCHAR(200)  NOT NULL COMMENT '标题',
    slug          VARCHAR(200)  NOT NULL COMMENT '别名',
    content_md    LONGTEXT      DEFAULT NULL COMMENT 'Markdown内容',
    content_html  LONGTEXT      DEFAULT NULL COMMENT 'HTML内容',
    summary       VARCHAR(500)  DEFAULT NULL COMMENT '摘要',
    cover         VARCHAR(500)  DEFAULT NULL COMMENT '封面图',
    status        TINYINT       DEFAULT 0 COMMENT '状态 0草稿 1已发布 2回收站',
    user_id       BIGINT        NOT NULL COMMENT '作者ID',
    category_id   BIGINT        DEFAULT NULL COMMENT '分类ID',
    is_top        TINYINT       DEFAULT 0 COMMENT '是否置顶',
    view_count    INT           DEFAULT 0 COMMENT '浏览量',
    comment_count INT           DEFAULT 0 COMMENT '评论数',
    like_count    INT           DEFAULT 0 COMMENT '点赞数',
    is_deleted    TINYINT       DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    create_time   DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_slug (slug),
    KEY idx_category (category_id),
    KEY idx_user (user_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章表';

-- 文章标签关联表
CREATE TABLE article_tag (
    article_id BIGINT NOT NULL,
    tag_id     BIGINT NOT NULL,
    PRIMARY KEY (article_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章标签关联表';

-- 评论表
CREATE TABLE comment (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    article_id  BIGINT       NOT NULL COMMENT '文章ID',
    parent_id   BIGINT       DEFAULT 0 COMMENT '父评论ID',
    user_id     BIGINT       NOT NULL COMMENT '评论用户ID',
    content     TEXT         NOT NULL COMMENT '评论内容',
    status      TINYINT      DEFAULT 1 COMMENT '状态 0待审核 1通过 2拒绝',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_article (article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

-- 站点配置表
CREATE TABLE site_config (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_key   VARCHAR(100) NOT NULL COMMENT '配置键',
    config_value TEXT         DEFAULT NULL COMMENT '配置值',
    description  VARCHAR(255) DEFAULT NULL COMMENT '描述',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站点配置表';

-- 友链表
CREATE TABLE friend_link (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(50)  NOT NULL COMMENT '名称',
    url         VARCHAR(500) NOT NULL COMMENT '链接',
    avatar      VARCHAR(500) DEFAULT NULL COMMENT '头像',
    description VARCHAR(200) DEFAULT NULL COMMENT '描述',
    sort        INT          DEFAULT 0 COMMENT '排序',
    status      TINYINT      DEFAULT 1 COMMENT '状态 0隐藏 1显示',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='友链表';

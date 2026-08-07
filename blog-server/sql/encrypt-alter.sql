USE blog;
ALTER TABLE article ADD COLUMN is_encrypted TINYINT DEFAULT 0 COMMENT '是否加密 0否 1是' AFTER is_deleted;
ALTER TABLE article ADD COLUMN password VARCHAR(255) DEFAULT NULL COMMENT 'BCrypt密文' AFTER is_encrypted;

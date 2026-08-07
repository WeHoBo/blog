USE blog;
ALTER TABLE article ADD COLUMN visibility TINYINT DEFAULT 0 COMMENT '0公开 1私密' AFTER is_encrypted;

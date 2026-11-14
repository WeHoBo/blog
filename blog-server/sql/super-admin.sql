-- =====================================================================
-- 超级管理员（super）迁移脚本 —— 幂等，可反复执行
--
-- 设计原则：
--   1. 全站只允许一个 super。后台页面不提供"授予超管"的入口，
--      因为那是"点两下就多一个能改所有人密码的账号"。
--   2. 超管在权限上是「管理员 + 额外危险权限」：JwtAuthenticationFilter 会给
--      role=super 的账号同时发 ROLE_admin 和 ROLE_super，所以历史上所有
--      hasRole('admin') 的接口它都能用，只有显式改成 hasRole('super') 的才收窄。
--   3. 改完必须重新登录：role 是写死在 JWT claim 里的，旧 token 认的还是老角色。
--
-- 执行：mysql -uroot -p blog < super-admin.sql
-- =====================================================================

SET @target := 'gitee_Herbert-Code1';

-- 先确认目标账号存在。@n = 0 时下面两条 UPDATE 全部不生效 ——
-- 这是刻意保护的：万一用户名打错了，绝不能把现有的 super 顺手降级，否则没人能管后台。
SET @n := (SELECT COUNT(*) FROM `user` WHERE username = @target);

-- 清掉可能存在的其它 super（历史手工改数据留下的），保证"唯一超管"
UPDATE `user` SET role = 'admin'
 WHERE role = 'super' AND username <> @target AND @n = 1;

-- 授予目标账号
UPDATE `user` SET role = 'super'
 WHERE username = @target AND @n = 1;

SELECT IF(@n = 1,
          CONCAT('✅ 已将 ', @target, ' 设为超级管理员（请重新登录生效）'),
          CONCAT('⚠️ 没找到用户 ', @target, '，未做任何修改')) AS result;

-- 复核：应当只有一行 role = super
SELECT id, username, nickname, role, status FROM `user` WHERE role IN ('super', 'admin') ORDER BY role DESC, id;

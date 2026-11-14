-- 博观：站名 + 首页横幅 + AI 助手的初始配置（幂等，可重复执行）
--
-- 为什么不建表：site_config 本来就是 KV 表，后台「站点设置」直接读写它，
-- 复用它比再加一张 ai_config 表少一套增删改查，也少一次建表上线。
--
-- 🔴 安全边界：这里只能放**可公开展示**的配置。
--     /api/site-config/public 会把整张表原样返回给任何访客，密钥绝不能放进来
--     （模型的 apiKeyEnv / apiKey 都在 ai_model 表，别混过来）。
--
-- config_key 上有唯一索引 uk_key（见 blog.sql）：
--   UPDATE 只改站名（本次就是要改名），其余用 INSERT IGNORE，
--   管理员后来自己改过的问题池/欢迎语不会被覆盖。

UPDATE site_config SET config_value = '博观' WHERE config_key = 'siteName';

INSERT IGNORE INTO site_config (config_key, config_value, description) VALUES
  ('siteName',      '博观', '站点名称（浏览器标签、SEO、关于页）'),
  ('bloggerFocus',  'Java,AI,Python,Linux,Docker', '首页横幅「关注方向」，英文逗号分隔，最多 6 个'),
  ('aiName',        '博观 AI', 'AI 助手名字（悬浮球提示、面板标题、欢迎语自称）'),
  ('aiGreeting',    '我可以根据本站的技术文章，帮你学习 Java、AI、Linux、Docker 以及计算机基础知识。你想了解什么？', 'AI 面板欢迎语'),
  ('aiQuestions',   '["站内最近更新了哪些文章？","站内有哪些「操作系统」的文章？","帮我推荐几篇文章","RAG 检索增强生成入门讲了什么？","站内有哪些分类？","AI 能帮我做什么？"]', 'AI 问题池：JSON 数组字符串，前台一次抽 4 条，可「换一批」');

# 好啵博客 v1.3 · 「稳、快、能写公式」

> 发布日期：2026-09-20 · 覆盖提交范围：`160860f` → `7ee76cd`（共 20 个提交）
> 线上地址：https://codeup.asia

---

## 一句话概括

这一版做了三件事：**把线上跑不起来的功能修好**、**把写作体验补齐**（公式 / 版本历史 / 定时发布）、
**把服务器从一台换到另一台并顺手加固**。读者能直接感知的是公式能正常显示了、详情页不再是两个版本、分类删得掉了。

---

## ✨ 新增功能

### 编辑器（写作端）

| 功能 | 说明 |
|---|---|
| **版本历史** | 每次保存留快照，可查看 diff 并一键回滚到任意历史版本 |
| **回收站** | 删除的文章进回收站而非直接消失，支持恢复 |
| **素材库** | 上传过的图片集中管理，可从素材库直接选图插入 |
| **定时发布** | 设置 `publishAt` 后由后台任务每分钟扫描，到点自动发布 |
| **Front Matter 导入导出** | 支持带元信息的 Markdown 文件导入导出 |

> 编辑器底层从原有方案迁到 **CodeMirror 6**，同时修掉了滚动跳变与全屏异常。

### 阅读体验（读者端）

| 功能 | 说明 |
|---|---|
| **LaTeX 数学公式** | 接入 **KaTeX**，编辑器预览 / 文章正文 / AI 对话三处统一生效 |
| **图片灯箱** | 点图放大，支持多图轮播、滚轮缩放、移动端手势 |
| **代码块升级** | 支持文件名标注、指定行高亮、折叠、自动换行、一键复制 |
| **文章目录 / 上下篇 / 相关推荐 / 作者卡** | 详情页信息架构补全 |
| **标签与分类独立落地页** | 新增 `/tags`、`/tag/{slug}`、`/categories`、`/category/{slug}`，各自带独立 SEO 信息 |
| **暗色模式** | 全站可切换 |

### 信息架构

- **文章详情双路由统一**：`/post/{slug}` 与 `/article/{id}` 此前是**两份模板**（`/post/` 是精简版），
  导致同一篇文章从不同入口进入内容不一样。现在两个路由都只是「瘦壳」，统一渲染 `ArticleDetail.vue`。
- **分类级联删除**：删父分类时 BFS 收整棵子树，子树下文章的分类置空，不会留下悬空引用。
- **「未建档文章」兜底分类**：无分类归属的文章统一落到内置分类（`uncategorized`），
  **不允许删除**，并在应用启动时幂等回填历史脏数据。
- **相关推荐增强**：优先同分类，不足 4 篇用最新文章补齐（走字段白名单，不拉大字段）。

---

## 🐛 主要修复

| 问题 | 影响 | 状态 |
|---|---|---|
| `$...$` 公式显示为纯文本 | 数学类文章完全不可读 | ✅ 已修 |
| AI 流式问答被异步鉴权掐断 | 回答到一半中断 | ✅ 已修 |
| 同一篇文章两个 URL 内容不一致 | SEO 重复内容 + 用户体验混乱 | ✅ 已修 |
| 代码块复制后丢失换行 | 复制出来的代码不能用 | ✅ 已修 |
| 关于页存在 XSS 注入面 | 安全 | ✅ 已修 |
| 生产环境错误栈泄露 | 安全 | ✅ 已修 |
| 草稿 / 私密文章可被未授权访问 | 安全 | ✅ 已修 |
| JWT 密钥缺失时静默启动 | 安全 | ✅ 已改为 fail-fast |

### 安全加固清单（`5ff6479`）

部署配置、鉴权、CORS 白名单、反序列化白名单、上传类型校验、评论限流、
OAuth `state` CSRF 校验、XSS 收敛 —— 均在这一批处理。

---

## ⚡ 性能优化

- **列表查询收敛返回列**：列表接口加 `.select()` 白名单，排除 `content_md` / `content_html` 两个 LONGTEXT 字段
- **聚合查询替代 N+1**
- **首页 SSR 首屏优化**，`totalArticles` 复用已有请求，不额外发接口调用
- **SEO**：文章与聚合页均输出 canonical、title、description 与结构化数据；sitemap 含聚合页
- **依赖瘦身**：移除 `mermaid` / `@nuxt/icon` / `sharp`，重新锁定依赖
- gzip / brotli 压缩

---

## 🖥️ 服务器迁移（本次重点）

本次发布同时完成了**整机迁移**：旧机 `8.130.49.86` → 新机 `47.116.138.65`。

**迁移方式**：旧机打包（数据库 dump + 配置 + 前端产物）→ `scp` → 新机恢复。
因所有配置项都走 `${ENV_VAR:default}` 注入，**配置键值零改动**。

**新机环境**

| 组件 | 版本 |
|---|---|
| 系统 | Alibaba Cloud Linux 3（EL8 系 / systemd 239 / SELinux Disabled） |
| 规格 | 2 vCPU / 1.8G 内存 + 2G swap |
| Java | 17 |
| Nginx | 1.24.0 |
| MySQL | 8 |
| Redis | 6.2.x |
| Node | 20.20.2 |
| Python | 3.11.13 |
| HTTPS 证书 | 有效期至 2026-12-19 |

> 服务统一以 `ecs-user` 运行；`root` 可用，`admin` 账号已锁定。

**迁移踩坑（已记录，避免复发）**

1. 域名解析**分线路**残留旧 IP → 曾导致 certbot 签发失败，需逐条检查默认/电信/联通/移动/境外线路
2. `spring.config.location` 是**替换**默认位置，jar 内 `application.yml` 在生产**不生效**，新配置必须写进 `/opt/blog/application-prod.yml`
3. 前端部署必须同步**整个 `.output` 目录**并重启 `blog-web`，只拷 `.output/public` 会导致改动「从未上线」
4. `systemd 239` 不支持 `StandardOutput=append:`，日志重定向方式需适配
5. `deploy/` 已纳入版本库；本地改完需**手工同步**到服务器
6. RAG 依赖 `pysqlite3-binary`（Chroma 要求 sqlite3 ≥ 3.35，而 EL8 自带版本过低），
   缺失时 `main.py` 会**静默吞掉 `ImportError`**，导致排错困难 —— 已在 `requirements.txt` 显式声明

---

## ⚠️ 升级 / 部署注意事项

如果你在部署这套版本，以下几点**必须手工处理**（自动化流程覆盖不到）：

1. **改过 `package.json` / `package-lock.json`** ⇒ 服务器必须重跑 `npm ci`
   （本次引入了 KaTeX，跳过这步会导致前端构建产物缺失依赖）
2. **同步 `rag_system/backend/requirements.txt`** 并重装依赖：
   ```bash
   scp rag_system/backend/requirements.txt ecs-user@47.116.138.65:/opt/blog/rag/backend/
   cd /opt/blog/rag/backend && .venv/bin/pip install -r requirements.txt && sudo systemctl restart rag
   ```
3. **数据库增量**只跑 `blog-server/sql/perf-index.sql`（幂等，`Duplicate key name` 属正常）
4. **敏感配置全部在服务器侧文件**，不进版本库：
   - `/opt/blog/blog.env` — `JWT_SECRET`、`RAG_API_TOKEN`
   - `/opt/blog/application-prod.yml` — DB / Redis 密码、OAuth Secret、OSS AK-SK
   - `/opt/blog/rag/backend/.env` — DeepSeek / Embedding Key、`RAG_API_TOKEN`（**必须与 `blog.env` 一致**）
   - `/opt/blog/backup.env` — `DB_PASSWORD`

---

## 🔒 安全提醒

- **迁移包中的明文密钥已清理**，`.env` 权限已收紧为 `600`
- 所有凭据均支持**不改代码、不重新构建**即可轮换（走环境变量注入）
- 若曾通过命令行传过密码，建议清理 shell 历史：`history -c && history -w`

---

## 📚 完整变更记录

| 类别 | 提交 |
|---|---|
| 安全 | `5ff6479` 线上事故级问题修复 · `a3c9c64` 服务器加固与备份 |
| 性能 | `0a4ac87` 索引/聚合查询 · `5432d44` SEO 与冗余清理 |
| 功能（编辑器） | `5e41a0e` 版本历史/回收站/素材库/定时发布 |
| 功能（阅读） | `f044b6b` 详情页统一/灯箱/代码块/落地页 · `ae2c54b` 分类级联 · `aef3441` KaTeX |
| 修复 | `46b07d2` AI 流式 · `3955654` UI 第一批 · `0c96942` 高亮主题与错误栈 |
| 运维 | `7ee76cd` 换机收尾工具与文档 · `80454a3` 依赖锁定 |
| CI | `ab5a51b` Actions 升级 + typecheck + 依赖审计 + RAG 语法检查 |

**配套文档**：[`docs/README.md`](README.md) 项目文档索引 · [`deploy/换机收尾操作手册.md`](../deploy/换机收尾操作手册.md) 换机收尾手册

---

> 本项目为个人技术博客（笔记以计算机网络、操作系统、Docker 实验为主），持续迭代中。

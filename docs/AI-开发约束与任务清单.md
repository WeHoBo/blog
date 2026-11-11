# AI 开发约束与任务清单（配合《HarmonyOS-App-开发文档》使用）

> 用法：把本文件与《HarmonyOS-App-开发文档.md》一起上传给 DevEco 的 Vibe Coding / Agent，
> 然后使用第 5 节的「第一条指令」模板。
> 本文件是**强制约束**，与开发文档冲突时以本文件为准。

---

## 1. 角色与目标

你要开发一个 HarmonyOS NEXT 原生 App（ArkTS + ArkUI），是一个个人博客的**阅读客户端**。
后端 API 已全部就绪，**不允许修改后端**，App 只消费现有接口。

**一期只做 9 项功能**：文章列表、文章详情、分类/标签/归档、上下篇/相关推荐、评论读取、
评论发布、个人资料、图片查看、OAuth 登录。

**明确不做**：注册、发文/编辑、图片上传、后台管理、点赞、收藏、AI 助手、推送。

---

## 2. 技术硬约束

| 项 | 约束 |
|---|---|
| 语言 | **ArkTS**（不是 TypeScript）。**禁止 `any` / `unknown` 作为业务类型** |
| 框架 | ArkUI 声明式；路由用 **`Navigation` + `NavPathStack`**，不要用旧的 `@ohos.router` |
| SDK | HarmonyOS NEXT，API 12+ |
| 禁止 | 引入任何 npm / 三方 JS 包；禁止使用 Android / Java 代码或思路 |
| 禁止 | 运行时给对象增删属性、`Object.assign` 改结构、`JSON.parse` 后直接当有类型对象用 |
| 权限 | 只申请 `ohos.permission.INTERNET`，不要加任何其他权限 |
| 注释 | 中文注释，解释「为什么」而不是「是什么」 |

> 若不确定某个 HarmonyOS API 的签名，**先说明"需要确认 API"并给出替代方案**，
> 不要凭空编造 API 名称或参数。

---

## 3. 后端事实（不可假设，必须照此实现）

- BaseURL：`https://codeup.asia/api`
- 所有响应统一信封：`{ "code": 200, "message": "success", "data": ... }`
- 鉴权：`Authorization: Bearer <token>`；JWT **24 小时过期，无 refresh token**
- ⚠️ **未登录 / token 过期返回 HTTP `403`，响应体为空**（不是 401）
- ⚠️ **业务失败返回 HTTP `200` + `body.code != 200`**，必须判 `code`
- ⚠️ **`/api/` 下路径拼错也返回 403**（安全层先于路由匹配）⇒ 403 先查 URL 拼写
- 时间格式：`yyyy-MM-ddTHH:mm:ss`（东八区，无时区后缀）；**归档接口是 `yyyy-MM-dd`**
- 分页：请求 `pageNum`（从 1 开始）/ `pageSize`；响应 `{ records, total, current, pages, size }`
- 图片：OSS 绝对 URL，需做「相对路径 → 绝对路径」归一化兜底

### 允许调用的接口白名单（**只准用这些，禁止编造**）

| 用途 | 方法与路径 | 鉴权 |
|---|---|---|
| 文章列表 | `GET /article/list`（参数 `pageNum` `pageSize` `categoryId` `tagId` `keyword` `sort`） | 否 |
| 文章详情 | `GET /article/{id}` | 否 |
| 文章详情（slug） | `GET /article/post/{slug}`（**必须 encodeURIComponent**） | 否 |
| 上下篇 | `GET /article/{id}/neighbors` | 否 |
| 相关推荐 | `GET /article/{id}/related` | 否 |
| 分类列表 | `GET /category/list`（含 `articleCount`） | 否 |
| 标签列表 | `GET /article/tags`（**注意不是 `/tag/list`，那个没计数**） | 否 |
| 归档 | `GET /article/archive`（`data.archives[]`） | 否 |
| 评论列表 | `GET /comment/list?articleId={id}&pageNum=&pageSize=` | 否 |
| 发表评论 | `POST /comment`（body `{articleId, content}`，≤500 字，10 秒限流） | **是** |
| 当前用户 | `GET /auth/me` | **是** |
| 改资料 | `PUT /auth/profile`（body `{nickname, avatar}`，服务端无长度校验） | **是** |
| 站点信息 | `GET /site-config/public` | 否 |
| 发起登录 | `GET /auth/oauth/{github\|gitee\|huawei}`（**必须在 Web 组件里打开**，不能用 http 请求） | 否 |

**如需白名单之外的接口，必须先停下来询问，不得自行发明。**

---

## 4. 关键实现要求

1. **网络层必须统一封装**：处理 403、`body.code`、超时（10s）、JSON 解析失败兜底。
2. **正文渲染**：`contentMd` 经 Web 组件 + 本地 markdown-it/highlight.js/KaTeX 渲染。
   `contentHtml` 恒为 `null`，**禁止使用**。
3. **登录**：Web 组件加载 `/auth/oauth/{provider}`，拦截 `https://codeup.asia/oauth/callback`
   URL，解析 `token` 与 `error`，成功后关闭 Web 页。
4. **图片 URL**：统一走 `normalizeUrl()` 归一化。
5. **列表分页**：切换筛选条件必须重置页码并清空旧数据；用请求序号丢弃过期响应。

---

## 5. 工作方式（**必须遵守**）

- **一次只做一个任务**。不要一次生成多个页面。
- 每次开始前，先用 5 行以内说明：要改哪些文件、每个文件做什么。
- 代码必须能直接编译；不确定的 API 要标注 `// TODO: 确认 API 签名`，不要硬编。
- 每完成一个任务，列出「验收自测步骤」，我再决定是否进入下一个。

### 第一条指令（可直接粘贴）

> 请先读附件《HarmonyOS-App-开发文档.md》，但**只读第 4 章（全局约定）和第 5 章（接口详解）**。
> 然后按《AI-开发约束与任务清单.md》执行**任务 1**：搭建工程骨架 + 网络层 + 文章列表页。
> 要求：先列出要创建的文件清单和每个文件的职责，我确认后再写代码。
> 禁止编造接口，只能使用约束文件中的白名单。

---

## 6. 任务序列（严格按顺序，一次一个）

### 任务 1 · 工程骨架 + 网络层 + 列表页
- 产出：`model/types.ets`、`api/HttpClient.ets`、`api/article.ets`、`pages/Index.ets`
- 验收：列表能显示 10 条文章（标题/封面/分类/时间）；下拉刷新、触底加载可用；无网络时有错误提示

### 任务 2 · Markdown 渲染 PoC（**最高风险，先单独验证**）
- 产出：`resources/rawfile/markdown/*`、`components/MarkdownView.ets`
- 验收：一篇含**代码块 + 行内公式 + 块级公式 + 表格**的文章渲染正确；字号与行距可读

### 任务 3 · 文章详情页
- 产出：`pages/ArticleDetail.ets`、`components/ArticleCard.ets` 复用
- 验收：标题/元信息/正文/标签/上下篇/相关推荐齐全；上一篇下一篇能跳转；首末篇按钮禁用

### 任务 4 · 分类 / 标签 / 归档
- 产出：三个页面 + `api/taxonomy.ets`
- 验收：点分类能筛出对应文章；标签显示文章数；归档按年月分组、可跳转

### 任务 5 · OAuth 登录
- 产出：`pages/Login.ets`、`store/AuthStore.ets`
- 验收：能拿到 token 并持久化；重启 App 后 `/auth/me` 返回 200；token 过期时自动引导重登

### 任务 6 · 评论
- 产出：`components/CommentItem.ets`、评论列表与输入区（并入详情页）
- 验收：列表正确显示昵称头像；未登录点评论跳登录；发布成功率 100%；超 500 字与 10 秒限流提示正确

### 任务 7 · 个人资料 + 图片查看
- 产出：`pages/Profile.ets`、`components/ImageViewer.ets`
- 验收：昵称可改并持久化；头像 URL 可改；正文图片点击放大、双指缩放、可关闭

### 任务 8 · 打磨与验收
- 按开发文档第 10 章清单逐条自测，修复发现的问题

---

## 7. 绝对禁止的行为

- 修改 `codeup.asia` 后端的任何代码或配置
- 自行发明接口路径、参数名、字段名
- 使用 `any` 绕过类型检查
- 一次性生成全部页面
- 引入未经验证的第三方库
- 用 `http` 模块请求 OAuth 授权地址
- 在列表页请求文章正文（`contentMd`）

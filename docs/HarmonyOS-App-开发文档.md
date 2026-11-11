# 好啵博客 · 鸿蒙原生 App 开发文档

| 项 | 内容 |
|---|---|
| 文档版本 | v1.0 |
| 编写日期 | 2026-10-28 |
| 目标平台 | HarmonyOS NEXT（纯 ArkTS / ArkUI，不兼容 Android 工程） |
| 后端 | `https://codeup.asia`（Spring Boot 3.2.7，纯 REST + JWT） |
| 文档依据 | 直接读取项目源码 + 线上接口实测（2026-10-28） |
| 适用范围 | 测试阶段个人使用；未来可能以个人主体上架 |

> **本文档中的所有响应示例均来自线上真实响应**，非推测。
> 标注「⚠️」的条目是实测踩到的坑，实现时必须处理。

---

## 1. 范围定义

### 1.1 本期交付范围（9 项）

| # | 功能 | 优先级 |
|---|---|---|
| 1 | 文章列表（分页 / 按分类 / 按标签 / 按标题搜索） | P0 |
| 2 | 文章详情（Markdown 渲染） | P0 |
| 3 | 分类 / 标签 / 归档 | P0 |
| 4 | 上下篇 / 相关推荐 | P1 |
| 5 | 评论读取 | P1 |
| 6 | 评论发布 | P1 |
| 7 | 个人资料（查看 / 改昵称头像） | P1 |
| 8 | 图片查看（缩放） | P1 |
| 9 | **OAuth 登录**（评论与资料的前置依赖） | P0 |

### 1.2 明确不做

| 不做 | 原因 |
|---|---|
| 注册 | 项目本无注册：`register.vue` 是跳转 stub，**首次 OAuth 登录即自动建号** |
| 发文 / 编辑 | `ArticleEditor.vue` 717 行 + CodeMirror 6，ArkTS 无对应实现 |
| 图片上传 | 仅 `hasRole('admin')`，multipart，移动端低频 |
| 后台管理（站点设置 / 用户 / 日志 / 回收站 / 版本历史） | 桌面场景，误操作代价高 |
| AI 助手 | 需登录 + 流式，列为二期 |
| 点赞 / 收藏 | 后端无接口（`like_count` 字段存在但无接口），收藏只能本地存储 |
| SSR / SEO / sitemap | 原生 App 无爬虫，全部不成立 |

### 1.3 设计原则

1. **后端零改动** —— 本期所有接口均已就绪，不改后端一行代码。
2. **不碰管理员接口** —— 不引入 admin 角色，安全面最小化。
3. **阅读优先** —— 首屏速度、正文渲染质量、滚动流畅度是核心体验。
4. **失败可解释** —— 任何错误都要给出可读原因，不允许白屏。

---

## 2. 架构

### 2.1 现状（后端能力）

```
鸿蒙 App
   │  HTTPS  JSON
   ▼
https://codeup.asia/api/*  ──►  Spring Boot（JWT 无状态鉴权）
   │
   ├─► MySQL 8（文章 / 评论 / 分类 / 标签 / 用户）
   ├─► Redis（JWT state、浏览量计数、聚合缓存、评论限流）
   └─► 阿里云 OSS（图片，返回绝对 URL）
```

关键事实：

- 后端**纯 REST**，无 session、无 cookie 依赖。前端网页用 cookie 存 token，只是前端自己的选择，App 用本地存储即可。
- **正文只有 `contentMd`**，服务端不渲染 Markdown；`contentHtml` 字段存在但网页渲染时未使用（实测多为 `null`）。
- 图片直链为 OSS 绝对地址，App 可直接加载。

### 2.2 客户端分层建议

```
entry/src/main/ets/
├── pages/                 # 页面（ArkUI）
│   ├── Index.ets          # 首页：文章列表
│   ├── ArticleDetail.ets  # 文章详情
│   ├── Taxonomy.ets       # 分类 / 标签 / 归档（可拆 3 页）
│   ├── Profile.ets        # 个人资料
│   └── Login.ets          # 登录（Web 组件）
├── components/            # 复用组件
│   ├── ArticleCard.ets
│   ├── MarkdownView.ets   # Web 组件 + 本地渲染器
│   ├── ImageViewer.ets
│   └── CommentItem.ets
├── api/                   # 接口层（按域拆文件）
│   ├── HttpClient.ets     # 统一请求 / 错误 / 鉴权
│   ├── article.ets
│   ├── taxonomy.ets
│   ├── comment.ets
│   └── auth.ets
├── model/                 # 数据结构（interface）
│   └── types.ets
├── store/                 # 全局状态：登录态、主题
│   └── AppStore.ets
└── utils/                 # 日期、URL、错误文案
```

---

## 3. 环境准备

### 3.1 工具

| 项 | 要求 |
|---|---|
| DevEco Studio | 已安装（你已完成） |
| SDK | HarmonyOS NEXT API 12 及以上（建议用当前 DevEco 默认最新稳定版） |
| 语言 | ArkTS（TypeScript 子集，**禁用 `any`、禁止运行时增删对象属性**） |
| 调试 | 真机调试最可靠；模拟器对 Web 组件与网络行为可能与真机有差异 |

### 3.2 module.json5 权限

```json5
"requestPermissions": [
  { "name": "ohos.permission.INTERNET" }
]
```

> 本期只需网络权限。不要申请存储、位置、通讯录等无关权限 —— 会直接影响上架审核与隐私合规检测。

### 3.3 网络配置

- BaseURL 常量：`https://codeup.asia/api`
- 全部走 HTTPS，无需配置明文流量白名单。
- 证书为 Let's Encrypt，系统信任链正常。

---

## 4. 全局约定（**实现前必读**）

### 4.1 统一响应信封

所有接口返回同一结构：

```json
{ "code": 200, "message": "success", "data": { } }
```

| 字段 | 说明 |
|---|---|
| `code` | **200 表示业务成功**；其他值为失败 |
| `message` | 失败原因（可直接展示给用户，但建议做一次映射，见 9.1） |
| `data` | 业务数据，可能为 `null` |

### 4.2 ⚠️ HTTP 状态码与业务码是两套体系

这是本项目最容易踩的坑，必须理解清楚：

| 场景 | HTTP 状态 | body.code | body.data |
|---|---|---|---|
| 正常 | `200` | `200` | 数据 |
| 业务失败（如文章不存在、评论太频繁） | **`200`** | `400` / `404` / `500` | `null` |
| 未登录 / token 过期 | **`403`** | 无（**响应体为空**） | — |
| 参数校验失败（`@Valid` / BindException） | `400` | `400` | `null` |
| 无权限（角色不足） | `403` | `403` | `null` |
| 服务器内部错误 | `500` | `500` | `null` |

**实测证据**：

```bash
$ curl -s -o /dev/null -w '%{http_code}' https://codeup.asia/api/auth/me
403
```

**实现要求**：

```typescript
// 正确：先看 HTTP，再看业务码
if (status === 403) { /* 视为未登录，清理 token 并引导重新登录 */ }
else if (status >= 200 && status < 300) {
  const body = JSON.parse(text);
  if (body.code !== 200) { throw new ApiError(body.code, body.message); }
  return body.data;
}
```

> ⚠️ 只判 HTTP 状态码会把「文章不存在」当成成功，拿到 `null` 后崩溃。
> ⚠️ 只判 `body.code` 又处理不了 403（此时没有 body）。
> **两者都要判。**

#### ⚠️⚠️ 特别陷阱：`/api/**` 下拼错路径 = 403，而不是 404

因为安全规则是 `.requestMatchers("/api/**").authenticated()`，**任何 `/api/` 开头的请求都会先过安全层**。
路径写错（接口不存在）时，请求在到达 404 处理之前就被拦下，返回 **403**。

实测对照：

| 请求 | 实际返回 |
|---|---|
| `GET /api/app/posts`（不存在的接口） | **403** |
| `GET /api/nonexistent-abc`（随便一个不存在的） | **403** |
| `GET /api/article/list`（正确） | 200 |
| `GET /xxx-not-exist`（非 `/api` 前缀） | 404 |

⇒ **看到 403 先别急着怀疑登录态，先确认 URL 拼写正确。**
正确路径是 `/api/article/list`，参数是 `pageNum` / `pageSize`，不是 `/api/app/posts?page=&size=`。

> 补充：在浏览器里直接访问时，因为 `Accept: text/html`，Spring 会返回 **Whitelabel Error Page**（`This application has no explicit mapping for /error`），
> 而 curl / App 拿到的是 JSON。同一状态码两种表现形式，不是两个问题。

### 4.3 鉴权

```
Authorization: Bearer <token>
```

- token 由 OAuth 回调下发（见 5.1.2），为 JWT。
- **有效期 24 小时**（`jwt.expiration: 86400000`），**没有 refresh token**。
- 过期后所有需鉴权接口返回 **HTTP 403 且无响应体** ⇒ App 必须做「静默重登」：
  捕获 403 → 清 token → 进入登录页（或弹一次登录引导）。
- 哪类接口需要 token：

| 类型 | 需要登录 |
|---|---|
| `GET /api/article/**`、`/api/category/**`、`/api/tag/**`、`GET /api/comment/**`、`/api/friend-link/**`、`/api/site-config/public` | ❌ 不需要 |
| `POST /api/comment` | ✅ 需要 |
| `GET /api/auth/me`、`PUT /api/auth/profile` | ✅ 需要 |

### 4.4 时间格式

实测：

```json
"createTime": "2026-09-22T16:55:54"
```

- ISO-8601 **本地时间**，无时区后缀，无毫秒。
- 后端服务器时区为 `Asia/Shanghai`，**按东八区直接解析**，不要当 UTC。

### 4.5 图片 URL

实测为 OSS 绝对地址：

```json
"cover": "https://wehobo.oss-cn-wulanchabu.aliyuncs.com/images/b0130586935043e68ceb160fa7d136bf.png"
```

⚠️ 但生产配置 `file.upload.base-url` 与开发默认值不同，**历史数据可能存在相对路径** `/uploads/xxx.png`。App 侧统一做一次归一化：

```typescript
function normalizeUrl(u?: string): string {
  if (!u) return '';
  if (u.startsWith('http')) return u;
  if (u.startsWith('/')) return `https://codeup.asia${u}`;
  return `https://codeup.asia/${u}`;
}
```

### 4.6 ⚠️ slug 可能含中文与空格

实测数据：

```json
"slug": "六、-linux 实操配套理论-1784389821507"
"slug": "高数错题集合"
```

⇒ 拼接 `/post/{slug}` 时**必须 encodeURIComponent**，否则请求直接失败。

```typescript
const url = `${BASE}/article/post/${encodeURIComponent(slug)}`;
```

### 4.7 分页

后端返回的分页结构（MyBatis-Plus）：

```json
{ "records": [], "total": 35, "size": 20, "current": 1, "pages": 2 }
```

- 列表接口入参为 `pageNum`（从 **1** 开始）、`pageSize`。
- **服务端不限制 pageSize 上限** ⇒ 客户端自行约束（建议 10~20），避免一次拉全量。
- 列表接口已排除 `contentMd` / `contentHtml`，单条记录很小，可放心分页。

### 4.8 空值

部分接口返回的实体包含大量 `null` 字段（尤其是 `neighbors`、详情里的 `publishAt`、`series` 等）。**所有可选字段必须按可空处理**，禁止直接取属性做运算。

---

## 5. 接口详解

> 统一前缀：`https://codeup.asia/api`
> 下文所有「响应」示例只保留 `data` 部分，外层信封省略。

### 5.1 认证

#### 5.1.1 发起 OAuth 授权

```
GET /auth/oauth/{provider}
provider ∈ github | gitee | huawei
```

- **无参数**，需要登录态吗：不需要
- 响应：`302` 跳转到第三方授权页（不能当作 JSON 接口调用）
- 实测：

| provider | HTTP | 跳转目标 |
|---|---|---|
| github | 302 | `github.com/login/oauth/authorize` |
| gitee | 302 | `gitee.com/oauth/authorize` |
| huawei | 302 | `oauth-login.cloud.huawei.com/oauth2/v3/authorize` |

**App 用法**：在 **Web 组件**里直接打开该 URL，不要用 http 模块请求它。

#### 5.1.2 授权回调（App 拦截点）⭐

用户在第三方页面授权后，服务端会 302 到：

```
https://codeup.asia/oauth/callback?token=<JWT>&userId=<id>&username=<...>&nickname=<...>&avatar=<...>&role=<user|admin>
```

- `username` / `nickname` / `avatar` 已做 URL 编码（可能含中文）。
- 失败时改为：

```
https://codeup.asia/oauth/callback?error=<URL编码的中文原因>
```

**App 实现方式**：

1. 用 Web 组件加载 `https://codeup.asia/api/auth/oauth/github`
2. 在 Web 组件的 URL 拦截回调里判断前缀 `https://codeup.asia/oauth/callback`
3. 命中后解析 query：
   - 有 `token` → 存 token + 用户信息 → 关闭 Web 页 → 继续原操作
   - 有 `error` → 显示 `decodeURIComponent(error)` → 停留或返回
4. **后端无需任何改动**

⚠️ 注意：`/oauth/callback` 是**前端页面路由**，Web 组件会真正去加载它。建议在 URL 拦截阶段就拦下并 `return true` 阻止加载，避免出现空白页闪烁。

#### 5.1.3 获取当前用户

```
GET /auth/me
```

- 需要登录：**是**（`Authorization: Bearer <token>`）
- 响应：

```json
{
  "token": null,
  "userId": 3,
  "username": "gitee_Herbert-Code1",
  "nickname": "我也不知道取什么名字",
  "avatar": "https://wehobo.oss-cn-wulanchabu.aliyuncs.com/images/d3c910ad118c414281ddcbd0745e36bf.jpg",
  "role": "user"
}
```

- 失败：HTTP `403`（无体）→ 视为未登录。

#### 5.1.4 更新个人资料

```
PUT /auth/profile
Content-Type: application/json
```

请求体（只需传要改的字段，`null` 字段会被忽略）：

```json
{ "nickname": "新昵称", "avatar": "https://.../a.jpg" }
```

- 需要登录：**是**
- 响应：`data` 为 `null`，`code=200` 即成功
- ⚠️ **服务端无长度校验**：`nickname` 传 10 万字也会入库。客户端必须自行限制（建议昵称 ≤ 20 字）。
- ⚠️ 只能改昵称与头像，**不能改用户名、不能改密码**（密码登录已下线）。
- ⚠️ `avatar` 是 URL 字符串，**没有上传能力**。UI 上只能提供「填外链」或「选预设头像」。

---

### 5.2 文章

#### 5.2.1 文章列表

```
GET /article/list
```

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `pageNum` | int | 否 | 1 | 页码，从 1 开始 |
| `pageSize` | int | 否 | 10 | 每页条数，服务端**无上限** |
| `categoryId` | long | 否 | — | 分类 ID，**后端自动包含其所有子分类** |
| `tagId` | long | 否 | — | 标签 ID |
| `keyword` | string | 否 | — | ⚠️ **只匹配标题**（`LIKE title`），不是全文检索 |
| `sort` | string | 否 | 默认 | 传 `title` = 按标题升序；其他值 = 按时间倒序 |

- 需要登录：不需要
- 排序规则固定为 **置顶优先**（`isTop DESC`），再按时间倒序或标题升序。

响应（实测）：

```json
{
  "total": 35,
  "current": 1,
  "pages": 35,
  "records": [
    {
      "id": 108,
      "title": "高数错题集合",
      "slug": "高数错题集合",
      "summary": "整理自2026年9月22号的典型错题",
      "cover": "https://wehobo.oss-cn-wulanchabu.aliyuncs.com/images/b0130586935043e68ceb160fa7d136bf.png",
      "viewCount": 86,
      "commentCount": 0,
      "isTop": 1,
      "categoryId": 69,
      "categoryName": "数学",
      "createTime": "2026-09-22T16:55:54",
      "wordCount": 10,
      "author": {
        "nickname": "我也不知道取什么名字",
        "avatar": "https://wehobo.oss-cn-wulanchabu.aliyuncs.com/images/d3c910ad118c414281ddcbd0745e36bf.jpg"
      }
    }
  ]
}
```

要点：

- 列表**不含正文**，字段很轻，可放心翻页。
- `author` 可能不存在（作者被删时），按可空处理。
- `categoryName` 服务端已兜底，不会为空（无分类时返回「未建档文章」）。
- `isTop`：`1` = 置顶。

#### 5.2.2 文章详情（按 ID）

```
GET /article/{id}
```

- 需要登录：不需要
- 响应 `data` 含三个键：`article` / `tags` / `author`

```json
{
  "article": {
    "id": 108,
    "createTime": "2026-09-22T16:55:54",
    "updateTime": "2026-09-22T16:55:54",
    "title": "高数错题集合",
    "slug": "高数错题集合",
    "contentMd": "# 标题\n\n正文 Markdown ...",
    "contentHtml": null,
    "summary": "整理自2026年9月22号的典型错题",
    "cover": "https://...",
    "status": 1,
    "publishAt": null,
    "userId": 3,
    "categoryId": 69,
    "series": null,
    "isTop": 1,
    "viewCount": 86,
    "commentCount": 0,
    "likeCount": 0,
    "wordCount": 10,
    "isDeleted": 0
  },
  "tags": [
    { "id": 4, "name": "考研", "slug": "考研", "articleCount": null }
  ],
  "author": {
    "id": 3,
    "username": "gitee_Herbert-Code1",
    "nickname": "我也不知道取什么名字",
    "avatar": "https://..."
  }
}
```

要点：

- ⚠️ **正文请使用 `contentMd`**。`contentHtml` 实测为 `null`，网页端也不使用它。
- ⚠️ **此接口每次调用都会让 `viewCount` +1**（Redis 计数 + 数据库原子自增）。
  同一用户重复进入会重复计数，这是现有行为，不是 bug。
  ⇒ 客户端不要为了「刷新」而重复请求详情；返回上一页再进入属于正常阅读行为。
- `tags[].articleCount` 恒为 `null`（详情接口不统计），需要计数请用 `/article/tags`。
- 文章不存在时：HTTP `200` + `body.code = 404`，`message = "文章不存在"`。

#### 5.2.3 文章详情（按 slug）

```
GET /article/post/{slug}
```

- 与按 ID 完全等价，返回结构一致。
- ⚠️ **slug 含中文与空格，必须 `encodeURIComponent`**。
- 建议：列表接口给的是 `slug`，用 slug 更利于分享；但 slug 可能被后台修改，**本地缓存请以 `id` 为主键**。

#### 5.2.4 上一篇 / 下一篇

```
GET /article/{id}/neighbors
```

- 需要登录：不需要
- 响应：

```json
{
  "prev": { "id": 104, "title": "...", "slug": "..." },
  "next": null
}
```

⚠️ 实测返回的对象里**只有 `id`/`title`/`slug` 有值，其余字段全部为 `null`**（SQL 只 select 了三列）。这是正常现象，不要当成数据异常。

- 首篇的 `prev`、末篇的 `next` 为 `null` 或整个键缺失。
- 排序依据是「置顶 → 创建时间 → id」。

#### 5.2.5 相关推荐

```
GET /article/{id}/related
```

- 需要登录：不需要
- 响应：文章数组（最多 4 条）

```json
[
  {
    "id": 88,
    "title": "考研数学-高等数学知识点详细总结",
    "slug": "考研数学-高等数学知识点详细总结-1786797763771",
    "cover": "https://...",
    "summary": "...",
    "viewCount": 210,
    "createTime": "2026-08-15T10:00:00"
  }
]
```

- 规则：同分类优先取 4 条，不足则用最新文章补齐。
- 字段只含上表 6 项，**没有 `categoryName` / `author`**，卡片需简化展示。

---

### 5.3 分类 / 标签 / 归档

#### 5.3.1 分类列表（扁平）

```
GET /category/list
```

- 需要登录：不需要
- 响应（实测）：

```json
[
  {
    "id": 43,
    "createTime": "2026-08-10T00:29:27",
    "updateTime": "2026-09-20T14:34:23",
    "name": "计算机网络",
    "slug": "计算机网络",
    "parentId": 0,
    "sort": 0,
    "articleCount": 13
  }
]
```

- `articleCount` 为**含子孙分类的累计数**，可直接展示。
- `parentId = 0` 表示顶级分类。
- ⚠️ 另有一个 `GET /article/categories`，返回结构相同但**计数不可信**：父级恒为 0，叶子也大面积返回 0。
  **展示计数一律用本接口**（`/category/list`）。

#### 5.3.2 分类树

```
GET /category/tree
```

- 返回嵌套结构，适合分组展示：

```json
[
  {
    "id": 43, "name": "计算机网络", "slug": "...", "parentId": 0,
    "articleCount": 13,
    "children": [ { "id": 44, "name": "操作系统", "children": [] } ]
  }
]
```

> 建议：分类页用 `/category/list`（扁平，按 `parentId` 分组即可），层级不深时更简单。

#### 5.3.3 标签列表（**带文章数，推荐**）

```
GET /article/tags
```

- 需要登录：不需要
- 响应（实测）：

```json
[
  { "id": 1, "name": "AI", "slug": "AI", "articleCount": 0 },
  { "id": 2, "name": "408", "slug": "408", "articleCount": 20 }
]
```

⚠️ 存在另一个接口 `GET /tag/list`，但**它返回的 `articleCount` 恒为 `null`**（实现直接 `selectList` 未统计）。
⇒ **标签页请用 `/article/tags`**，不要用 `/tag/list`。

#### 5.3.4 归档

```
GET /article/archive
```

- 需要登录：不需要
- 响应（实测）：

```json
{
  "archives": [
    {
      "year": 2026,
      "months": [
        {
          "month": 9,
          "count": 15,
          "articles": [
            { "id": 108, "title": "高数错题集合", "slug": "高数错题集合", "createTime": "2026-09-22" }
          ]
        }
      ]
    }
  ]
}
```

- 结构为 `archives[] → year → months[] → articles[]`。
- `createTime` 这里是**纯日期字符串** `yyyy-MM-dd`，与详情接口的 `T` 格式不同，解析时要分别处理。
- 月份按倒序排列，年份按倒序排列，可直接渲染。

---

### 5.4 评论

#### 5.4.1 评论列表

```
GET /comment/list?articleId={id}&pageNum=1&pageSize=20
```

| 参数 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `articleId` | **是** | — | 文章 ID |
| `pageNum` | 否 | 1 | 页码 |
| `pageSize` | 否 | 20 | 每页条数 |

- 需要登录：不需要
- 响应：

```json
{
  "records": [
    {
      "id": 12,
      "articleId": 108,
      "content": "写得很好",
      "createTime": "2026-09-25T10:30:00",
      "userId": 3,
      "username": "gitee_Herbert-Code1",
      "nickname": "我也不知道取什么名字",
      "avatar": "https://..."
    }
  ],
  "total": 1,
  "size": 20,
  "current": 1,
  "pages": 1
}
```

- 只返回 `status = 1`（正常）的评论，按时间倒序。
- 无评论时 `records: []`、`total: 0`。

#### 5.4.2 发表评论

```
POST /comment
Content-Type: application/json
Authorization: Bearer <token>
```

请求体：

```json
{ "articleId": 108, "content": "写得很好" }
```

- 需要登录：**是**
- 成功响应：`data` 为新建的评论实体（`id`、`articleId`、`content`、`userId`、`status`、`createTime`）
- 服务端规则：

| 规则 | 违反时的提示 |
|---|---|
| `articleId` 与 `content` 必填、`content` 去空格后非空 | `参数不完整` |
| `content` ≤ **500 字** | `评论内容不能超过 500 字` |
| 同一用户对同一文章 **10 秒内只能发 1 条**（Redis 原子限流） | `评论太频繁了，请稍后再试` |
| 文章必须存在且 `status = 1` | `文章不存在或不可评论` |
| 只允许写 `articleId` / `content`，其余字段由服务端填充 | — |

- ⚠️ 以上业务失败均返回 **HTTP 200 + `body.code = 400`**，必须按 `code` 判错。
- 成功后建议本地把新评论插入列表头部并 `commentCount + 1`（或重新拉第一页）。

---

### 5.5 站点信息（可选，成本极低）

```
GET /site-config/public
```

- 需要登录：不需要
- 响应：KV 映射（实测键名如下）

```json
{
  "siteName": "we博客",
  "siteDesc": "一个记录学习笔记的小站~",
  "bloggerName": "weihaobo",
  "bloggerBio": "My name is weihaobo，welcome to everyone~\n",
  "bloggerAvatar": "https://...",
  "bgImage": "https://...",
  "aboutContent": "we博客是 weihaobo（好啵）的个人记录学习的小站..."
}
```

用途：App 启动时拉一次，用于「关于」页与页头品牌信息。`aboutContent` 是 Markdown，可用同一套渲染器展示。

---

## 6. 数据模型（ArkTS）

```typescript
// 统一信封
export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

export interface PageData<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export interface Author {
  id?: number;
  username?: string;
  nickname?: string;
  avatar?: string;
}

export interface ArticleListItem {
  id: number;
  title: string;
  slug: string;
  summary?: string;
  cover?: string;
  viewCount?: number;
  commentCount?: number;
  isTop?: number;
  categoryId?: number;
  categoryName?: string;
  createTime: string;       // yyyy-MM-ddTHH:mm:ss
  wordCount?: number;
  author?: Author;
}

export interface Article {
  id: number;
  title: string;
  slug: string;
  contentMd: string;        // 正文，唯一可信来源
  contentHtml?: string | null;
  summary?: string;
  cover?: string;
  status?: number;
  publishAt?: string | null;
  userId?: number;
  categoryId?: number;
  series?: string | null;
  isTop?: number;
  viewCount?: number;
  commentCount?: number;
  likeCount?: number;
  wordCount?: number;
  createTime: string;
  updateTime?: string;
}

export interface Tag {
  id: number;
  name: string;
  slug: string;
  articleCount?: number | null;
}

export interface Category {
  id: number;
  name: string;
  slug: string;
  parentId: number;
  sort?: number;
  articleCount?: number;
  children?: Category[];
}

export interface CommentItem {
  id: number;
  articleId: number;
  content: string;
  createTime: string;
  userId?: number;
  username?: string;
  nickname?: string;
  avatar?: string;
}

export interface LoginUser {
  token: string | null;
  userId: number;
  username: string;
  nickname?: string;
  avatar?: string;
  role: string;
}

export interface ArticleDetail {
  article: Article;
  tags: Tag[];
  author?: Author;
}

export interface Neighbors {
  prev?: { id: number; title: string; slug: string } | null;
  next?: { id: number; title: string; slug: string } | null;
}

export interface ArchiveMonth {
  month: number;
  count: number;
  articles: { id: number; title: string; slug: string; createTime: string }[];
}

export interface ArchiveYear {
  year: number;
  months: ArchiveMonth[];
}
```

---

## 7. 页面设计

| 页面 | 依赖接口 | 关键状态 | 边界情况 |
|---|---|---|---|
| **首页 / 列表** | `/article/list`<br>`/article/categories`（可并入筛选） | 页码、分类筛选、关键词、加载中、到底 | 空列表、翻页失败、快速滑动重复请求 |
| **详情** | `/article/{id}` 或 `/post/{slug}`<br>`/comment/list`<br>`/{id}/neighbors`<br>`/{id}/related` | 正文、评论列表、评论输入、登录态 | 文章 404（code=404）、正文渲染失败、无评论、未登录点评论 |
| **分类** | `/category/list` 或 `/tree` | 分组、选中项 | `articleCount = 0` 的分类是否隐藏 |
| **标签** | `/article/tags` | 标签云/列表 | 计数为 0 的标签 |
| **归档** | `/article/archive` | 年份折叠、月份分组 | 月份为空、跨年 |
| **个人资料** | `/auth/me`<br>`PUT /auth/profile` | 昵称草稿、保存中 | 未登录跳登录、昵称超长、保存失败 |
| **登录** | Web 组件加载 `/auth/oauth/{p}` | 加载中、授权中、失败原因 | 用户取消、回调缺 token、网络中断 |

**导航建议**：底部三个 Tab —— 文章 / 分类（含标签、归档入口）/ 我的。

---

## 8. 关键实现方案

### 8.1 网络层封装（必须做的一层）

```typescript
export class ApiError extends Error {
  code: number;
  constructor(code: number, message: string) { super(message); this.code = code; }
}

export async function request<T>(
  path: string,
  method: 'GET' | 'POST' | 'PUT' = 'GET',
  body?: object
): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = await TokenStore.get();
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const resp = await httpRequest(BASE_URL + path, method, headers, body);
  // 1) 未登录 / token 过期：HTTP 403，无响应体
  if (resp.status === 403) {
    await TokenStore.clear();
    throw new ApiError(403, '登录已过期，请重新登录');
  }
  // 2) HTTP 层错误
  if (resp.status < 200 || resp.status >= 300) {
    throw new ApiError(resp.status, `请求失败(${resp.status})`);
  }
  // 3) 业务层：HTTP 200 也可能是失败
  const envelope = JSON.parse(resp.body) as ApiResult<T>;
  if (envelope.code !== 200) {
    throw new ApiError(envelope.code, envelope.message);
  }
  return envelope.data;
}
```

统一封装要覆盖：超时（建议 10s）、重试（GET 可重试 1 次，POST 不重试）、JSON 解析异常兜底。

### 8.2 Markdown 渲染（本项目最大技术风险）

**为什么不能直接用 `contentHtml`**：该字段实测为 `null`，服务端不渲染 Markdown。

**方案：Web 组件 + 打包本地渲染器**（已在模拟器跑通，见 8.2.1 的三个坑）

1. 用 esbuild 把 `markdown-it` + `highlight.js` + `katex` + `markdown-it-footnote` + `markdown-it-task-lists`
   打成**单个 IIFE**，落到 `resources/rawfile/markdown/`：
   ```
   rawfile/markdown/index.html     页面骨架 + 全部样式
   rawfile/markdown/renderer.js    渲染器（约 574KB）
   rawfile/markdown/katex.min.css
   rawfile/markdown/hljs.css
   rawfile/markdown/fonts/         KaTeX 字体（20 个 woff2）
   ```
2. ArkTS 侧读出这四个文件，**内联成一个自包含 HTML**，用 `controller.loadData(html, 'text/html', 'UTF-8', baseUrl)` 加载；
3. `onPageEnd` 后 `runJavaScript('window.renderArticle(payload)')`，payload 是
   `{ title, author, avatar, date, views, cover, markdown }`（日期/阅读数在 ArkTS 侧格式化好）；
4. 渲染器内部：`markdown-it` 解析 + `highlight.js` 高亮 + `katex` 处理 `$...$` / `$$...$$`，`html:false` 防 XSS；
   mermaid 体积约 3MB，**一期不做**。

**必须处理的四件事**：

| 问题 | 处理 |
|---|---|
| 长文高度 | **不要让 Web 高度等于内容高度**：实测 2 万字 + 600 公式的正文高 **52308vp**，ArkWeb 只画得出顶部一小块（其余白屏）。做法是 Web 固定为可视高度、**自己滚动**；因此文章头部（标题/作者/封面）也必须放进网页才能跟着滚走 |
| 滚到文末 | Web `nestedScroll({ scrollForward: SELF_FIRST, scrollBackward: SELF_FIRST })`，网页到底后手势交给外层 Scroll，露出标签/推荐/评论 |
| 图片点击放大 | JS 监听 `img` 点击 → `javaScriptProxy` 回传 URL → 原生全屏预览 |
| 代码块横向滚动 | CSS `pre { overflow-x: auto }`；`pre` 不要设 `white-space: pre-wrap` |

#### 8.2.1 三个必踩的坑（都已验证）

1. **`Web({ src: $rawfile('markdown/index.html') })` 会黑屏**：ArkWeb 对 `resource://rawfile` 主文档的 MIME
   推断失败，页面根本不加载（表现为一块纯黑，高度永远停在兜底值）。改为读文件 + `loadData` 强制 `text/html`。
2. **内联 JS 必须转义 `<!--` 与 `<script`**：highlight.js 的 HTML 语法正则里含这两个序列；HTML 解析器在
   `<script>` 内遇到 `<!--` 会进入「转义状态」，后续 `</script>` 不再闭合 → 整段脚本被当文本、
   `window.renderArticle` 恒为 `undefined`。用 `\x3C` 等价替换（字符串与正则里含义相同）。
3. **不要在 `onControllerAttached` 里 `loadData`**：初始 `about:blank` 的导航还没结束，它完成后会覆盖刚加载的文档
   （`document.scripts.length === 0`）。应等 `onPageEnd` 里判断 `url` 不是 `data:` 时再加载。

> 调试手段：`hdc shell hilog -x | grep A00001`（应用日志域），或用 `runJavaScript` 探针打印
> `document.scripts.length` / `typeof window.renderArticle` / `#content` 子节点数，比看截图猜快得多。
> 模拟器可直接 `hdc install -r entry-default-unsigned.hap` 安装未签名包，配合
> `uitest uiInput click/swipe` + `snapshot_display` 做无人工回归。

**备选方案**（若 Web 方案不可接受）：纯 ArkTS 解析 + `StyledString` 渲染。工作量约 3~5 倍，且公式与代码高亮需自研，**不建议一期采用**。

### 8.3 登录实现（已落地）

**结论：一期采用「App 内 Web 授权」（跳网页），不接华为账号原生一键登录。** 理由见 8.3.3。

#### 8.3.1 实际流程

```
点击登录 → 原生选择平台（GitHub / Gitee / 华为网页）
  → Web 组件 loadUrl("https://codeup.asia/api/auth/oauth/{provider}")   ← 注意没有 /authorize
  → 后端生成 state（存 Redis）并 302 到平台授权页
  → 用户在该页面登录并授权
  → 平台回跳 /api/auth/oauth/{provider}/callback?code=...
  → 后端换 token、建号/查号、签 JWT
  → 后端 302 到 https://codeup.asia/oauth/callback?token=...&userId=...&nickname=...
  → App 在 onLoadIntercept 命中，return true 阻止加载
  → 解析 query（decodeURIComponent）→ 写 preferences + AppStorage
  → pop 回首页，顶栏立即变成头像 + 昵称
```

实现文件：`api/auth.ets`（URL/`fetchMe`）、`utils/authStore.ets`（持久化）、`pages/Login.ets`（Web 拦截）、
`entryability/EntryAbility.ets`（启动恢复）、`pages/Index.ets`（顶栏入口 + 退出菜单）。

要点：

- **不要用 http 模块请求授权地址**（只会拿到 302，不会渲染页面）。
- 拦截时 `return true`；额外用 `onPageBegin` 做双保险（个别版本 302 回跳不走 `onLoadIntercept`）。
- 失败分支读 `error` 参数并 `decodeURIComponent` 展示。
- 授权页需要 Cookie/localStorage：`domStorageAccess(true)` + `databaseAccess(true)`；
  用 `setCustomUserAgent()` 设移动端 Chrome UA（在 `onControllerAttached` 里、包 try/catch）。
- token 存 `preferences`（私有目录），昵称/头像写 `AppStorage`，UI 用 `@StorageLink` 订阅。
- 启动时用 `GET /auth/me` 校验一次：**403 就清本地登录态**（后端失效 token 返回 403 空体）。
- GitHub 在国内网络经常加载不出来（实测模拟器上空白页），**建议主推 Gitee / 华为**。

#### 8.3.2 两种「华为账号登录」不是一回事

| | 网页 OAuth（本次采用） | 华为账号原生（Account Kit） |
|---|---|---|
| 交互 | Web 页面登录，需输华为账号密码（或已登录状态） | 系统半模态授权卡片，免浏览器、通常一键 |
| 触发条件 | 点击后打开 `api/auth/oauth/huawei` | 设备已登录华为账号 + AGC 已开通服务 + 签名指纹已注册 + **真机** |
| 能选哪些平台 | GitHub / Gitee / 华为 | 仅华为账号 |
| 后端要求 | 已实现，零改动 | 需新增「移动应用 authorizationCode 换 token」接口（移动端 client_id/secret 与网页端不同） |
| 开发期可自测 | 任何设备（含模拟器） | 模拟器**不支持**，必须有正式签名 + 真机 |
| 上架要求 | 无 | 需在 AGC 配置应用信息，随应用上架审核 |

#### 8.3.3 为什么一期不接 Account Kit

1. 当前工程是**未签名调试包**，没有 AGC 应用与签名指纹，Account Kit 直接不可用；
2. 模拟器不支持，开发期无法回归；
3. 后端 `HuaweiOAuthProvider` 是**网页 OAuth**（client_id 为网页应用），Account Kit 的 code 需要移动应用凭据换取，
   属于后端改造，与「一期后端零改动」冲突。

后续要接的话：AGC 开通华为账号 → 配包名与签名 SHA-256 → `module.json5` 加 `client_id` metadata →
`authentication.HuaweiIDProvider().createAuthorizationWithHuaweiIDRequest()` 拿 code → 后端新增换 token 接口。


#### 8.3.4 黑屏兜底（实测踩过）

现象：登录页（Gitee / GitHub / 华为）与文章正文**同时**变成整块黑色，返回、重进、切后台都无效。

排查过程（结论：模拟器图形层故障，不是业务代码）：

1. 用 `uitest dumpLayout` 导出组件树，**黑屏时 Web 内部的标题、图片、按钮全在无障碍树里**（`heading`、`image`、
   `staticText` 都可读），说明页面已加载、布局正常，**只是没有合成出画面**。
2. 同一时刻外网页面（Gitee）也黑 ⇒ 不是 `loadData` 的内联 HTML、也不是 600KB 体积的问题。
3. **同一个安装包**，12:02 全黑，12:06~12:12 连续打开 5 次（3 篇文章 + 2 次登录）全部正常 ⇒ 非确定性，
   代码 bug 不会这样。
4. 崩溃目录有 `sysfreeze-render_service-*.log`（渲染服务冻结）；**重启整个模拟器后不再复现**。
5. 已排除：反复截屏（连拍 6 张正常）、反复创建/销毁登录 Web（4 轮正常）、连续打开 4 篇长文（正常）、
   `RenderMode.SYNC_RENDER`（改同步渲染后仍然黑，已回退）。

结论与处置：

- 属于**模拟器 ArkWeb/图形合成层的偶发故障**，真机未复现。遇到时**重启模拟器**（只杀 App 有时有效，有时无效）。
- 注意：黑屏时 `onFirstContentfulPaint` **仍会触发**，所以「首帧看门狗」检测不到这种合成层黑屏，
  只能作为一个通用的加载失败兜底。
- 已保留的兜底：
  1. Web 显式 `.backgroundColor('#FFFFFF')`（黑块 → 白底）；
  2. 登录页 12s 未画出首帧 → **原生**卡片「页面加载不出来？[重新加载] [返回]」
     （重新加载 = `authUrl='' → setTimeout 60ms → 重设`，重建 Web 组件）；
  3. `onRenderExited` → 登录页退回平台选择并提示；文章页显示「正文渲染失败，请点击重试」。
     ⚠️ 渲染进程死后 `runJavaScript` 回调不会再来，超时校验等不到，必须监听这个事件。
- ⚠️ 排查时 `hilog` 缓冲很短（约 6000 行，几分钟就滚掉），要抓现场必须立刻抓。
- ⚠️ **二次确认（2026-10-29）**：除了随机偶发，**给 Web 改尺寸**也会稳定触发 —— 键盘弹出把窗口压小、
  收起后再恢复，正文就整块黑了（要重启模拟器才恢复）。所以文章页现在尽量不让 Web resize：
  键盘避让改 `NONE` + 自己加 padding（见 8.7.2），Web 先量高度再创建（见 8.7.3）。

### 8.4 图片

- 列表封面：统一按 `objectFit: Cover` + 固定宽高比（16:9 或 4:3），避免布局跳动。
- 详情内图片：宽度 100%，高度自适应；点击进入原生大图查看（支持双指缩放、下滑关闭）。
- 图片 URL 一律先过 `normalizeUrl()`（见 4.5）。
- 建议加一层内存缓存 + 磁盘缓存，避免重复下载。

### 8.5 列表分页

- `pageNum` 从 1 开始，首次加载 `pageSize = 10`。
- 触底加载下一页；`current >= pages` 时停止。
- 切换分类/标签/关键词时必须**重置页码并清空旧数据**。
- 快速切换筛选条件时，用请求序号丢弃过期响应（防止旧结果覆盖新结果）。

### 8.6 分类 / 标签 / 归档页（已落地）

一个页面 + `Tabs` 三个 tab，共用 `pages/Taxonomy.ets`；筛选结果复用 `pages/ArticleList.ets`，
卡片复用 `components/ArticleCard.ets`（首页与筛选列表同一份，避免版式跑偏）。

实现要点：

1. **分类必须用 `GET /category/list`**（见 5.3.1）。`GET /article/categories` 的计数不可信：
   父级恒为 0，**叶子也大面积返回 0**（实测「网络协议」9 篇返回 0、「操作系统核心原理」4 篇返回 0）。
2. 分类接口是**扁平数组**，自己按 `parentId` 组树；`sort` 升序。父节点默认展开，
   子节点列表最前面插一行「全部」（`categoryId` 传父 id）——`/article/list` 传父分类会自动含整棵子树。
   实测「操作系统」= 学校课程 6 + 操作系统基础 1 + 核心原理 4 + Linux 1 = 12，与「全部」行一致。
3. 系统兜底分类 `slug = "uncategorized"`（未建档文章）在文章数为 0 时不展示，避免前台出现空类别。
4. 标签计数用 `/article/tags`，按 `articleCount` 降序（0 篇排最后）。
5. 归档 `data` 是 `{ archives: [...] }`；层级为「年 → 月 → 文章」。
   行内日期**只显示 `MM-DD`**：年份在分组标题、月份在分组行，完整年月既冗余又会把窄列挤成两行（已踩）。

#### 8.6.1 关键词搜索（已落地）

入口在首页顶栏下方的搜索框（`Index.SearchBar()`），不是单独的搜索页：

- 输入后点「搜索」或键盘回车 → `pushPathByName('ArticleList', { title: '搜索：xxx', categoryId: 0, tagId: 0, keyword: xxx })`；
- 空关键词只 toast 提示、不跳转（避免进到一个必然空结果的页面）；
- `ArticleList` 把 `keyword` 透传给 `GET /article/list?keyword=`，空列表文案区分
  「没有找到包含「xxx」的文章」和「这里还没有文章」；
- ⚠️ 后端 `keyword` **只对 `title` 做 LIKE**，不搜正文/标签/分类。
  搜索框占位文案因此写「搜索文章标题」，不要写成「搜索文章、标签、分类」（会误导用户）。

### 8.7 评论发布（已落地）

接口只有「读」和「写」两个（后端没有回复 / 点赞 / 审核），所以前端也只做这两件事：

| 动作 | 接口 | 说明 |
| --- | --- | --- |
| 读 | `GET /comment/list?articleId&pageNum&pageSize` | 按 `createTime` **倒序**，未登录可读 |
| 写 | `POST /comment` `{articleId, content}` | **必须登录**（Bearer），发布即通过 |

实现位置：`pages/ArticleDetail.ets` 的 `CommentComposer` / `CommentBar` / `submitComment()`，
接口封装在 `api/comment.ets`。

#### 8.7.1 交互设计

1. 评论区入口共三种状态（由 `@StorageLink('auth_is_login')` 驱动，登录页返回后自动切换）：
   - 未登录 → 「登录后参与评论 / 去登录」→ 打开 `Login` 页（不直接弹网页授权，避免打断阅读）；
   - 已登录 → 一行「写评论…」；
   - 点击后 → **底部弹出编辑栏**（TextArea + 字数统计 + 取消 / 发布）。
2. 前端先挡一次空内容 / 超长（500 字），与后端 `MAX_CONTENT_LENGTH` 保持一致；
   `TextArea.maxLength(500)` 同时限制输入，字数到顶变红。
3. 发布成功：清空、收起、`loadComments(1)` 重新拉第一页。
   **不要本地 `total + 1`**：列表本来就按时间倒序，重拉后新评论就在最上面，总数也以服务端为准。
4. 失败统一 `toast(toUserMessage(e))`：
   - 10 秒限流 → 后端 message「评论太频繁了，请稍后再试」（HTTP 200 + code 400）；
   - 403（token 过期）→ 额外 `logoutAuth()`，评论区自动切回「登录后参与评论」，
     否则用户会对着一个永远发不出去的输入框反复点。
5. `submitting` 状态防连点（后端限流只是兜底，不是交互手段）。

#### 8.7.2 键盘避让：三种模式只有一种能用（实测）

编辑栏必须在键盘上方，否则「发布」按钮点不到。三种做法都试过：

| 模式 | 结果 |
| --- | --- |
| `OFFSET`（默认） | 整页上移，但只保证**被聚焦的输入框**可见，下面的按钮仍被键盘盖住 |
| `RESIZE` | 布局正确，但窗口压缩会让 **ArkWeb 跟着改尺寸**，键盘收起后正文整块变黑、不再重绘 |
| `NONE` + 自己加底部 padding | ✅ 采用。窗口尺寸不变，Web 不 resize，编辑栏按键盘高度整体抬高 |

实现：

```ts
// aboutToAppear
this.getUIContext().setKeyboardAvoidMode(KeyboardAvoidMode.NONE);   // 需从 '@kit.ArkUI' 导入
const win = await window.getLastWindow(ctx);
win.on('keyboardHeightChange', (px: number) => { this.keyboardH = px2vp(px); });
```

```ts
Column() { TopBar(); ArticleBody() }
  .height('100%')
  .padding({ bottom: this.keyboardH })

// 编辑栏单独做浮层（Stack 底部对齐），靠 margin 抬到键盘上沿
if (composing && isLogin) {
  this.CommentBar()   // 根节点：.margin({ bottom: this.keyboardH })
}
```

注意：

- `KeyboardAvoidMode` 有**两个同名枚举**：`component/common.d.ts` 里只有 `DEFAULT/NONE`（全局），
  `@ohos.arkui.UIContext` 才有 `RESIZE/OFFSET/NONE`。**必须显式 `import { KeyboardAvoidMode } from '@kit.ArkUI'`**，
  否则报 `Property 'RESIZE' does not exist`。
- 离开本页要 `setKeyboardAvoidMode(KeyboardAvoidMode.OFFSET)` 复原，避免影响登录页的 Web。
- ⚠️ **编辑栏不要塞回正文 `Column` 里**（最初就是这么写的，后来发现会掉按钮）：
  正文 `Scroll` 是 `layoutWeight(1)`，实测它先把可用高度吃掉，编辑栏只分到 125vp（自然高度 164vp），
  里面的「取消 / 发布」整行被压成 0，`dumpLayout` 里只剩一个 `TextArea` —— 用户根本点不到发布。
  放在 `Stack` 里做浮层 + `.margin({ bottom: keyboardH })` 才是稳的（Stack 是窗口尺寸，所以要自己减键盘高度）。
  浮层会盖住正文末尾，正文底部留白按 `composing ? 200 : 28` 动态加高即可。

#### 8.7.3 顺带修掉的两个老问题

1. **正文高度不能在创建后再改**。原先是 `MarkdownView({ pageHeight: viewportH || 600 })`，
   会先按 600 建 Web、再 resize 到 699 —— 这个 resize 正是模拟器黑屏的触发点之一（见 8.3.4）。
   现在改成 `if (viewportH > 0) { MarkdownView(...) } else { 占位块 }`，**一次就以最终高度创建**。
2. **`viewportH` 只取第一次有效值**（`viewportH === 0 && h > 300`）。键盘 / 窗口变化引起的
   `onAreaChange` 一律忽略，避免 Web 反复 resize；阈值 300vp 用来过滤键盘态（竖屏正常约 700vp）。

#### 8.7.4 自测记录（模拟器，2026-10-29）

- 打开编辑栏 → 键盘弹出 → 编辑栏整体在键盘上方，计数器 `0/500`，空内容时「发布」为禁用态；
- 输入「手动避让方案验证」→ 发布 → 评论总数 2 → 3，新评论出现在列表最上面（时间倒序）；
- 点「取消」→ 编辑栏收起、键盘收回，正文区**未黑屏、未塌陷**；
- 验收阶段又测了一轮（见 8.9）：未登录分支「登录后参与评论 / 去登录」实测正常，
  从评论入口进登录页 → Gitee 授权 → 自动回到原文并恢复登录态；
- 10 秒限流：后端 `SETNX`（`comment:rate:{userId}:{articleId}`，TTL 10s）已核实；
  客户端提示走的是与 403 / 超长同一个 `toUserMessage(e)` 分支，但**没能构造出必然命中 10 秒窗口的自动化用例**
  （一条 `uitest uiInput` 指令就要 1 秒以上，两次发布之间稳定超过 10 秒）；
- token 过期（403 → `logoutAuth()`）仍为代码走查，未实测。

> ⚠️ 测试在文章「高数错题集合」(id=108) 留下了多条测试评论（见 8.9.4 的清理 SQL），需要时删除。

### 8.8 个人资料与图片查看（已落地）

#### 8.8.1 个人资料页

入口：首页顶栏头像 → `pages/Profile.ets`（原来是弹菜单，只有「退出登录」，容易误触）。

能改的只有两个字段，因为后端 `PUT /auth/profile` 就只认这两个：

| 字段 | 限制 | 说明 |
| --- | --- | --- |
| `nickname` | 非空、≤20 字 | 显示在顶栏、文章作者、评论 |
| `avatar` | `http(s)://` 或 `/` 开头 | 只存 URL，不做上传 |

**头像为什么不能从相册选**：`POST /api/upload` 带 `@PreAuthorize("hasRole('admin')")`，
且一期明确不做上传能力。要做的话得先改后端权限（把上传开放给登录用户），属于另一件事。

保存链路：`saveProfile()` → 成功后 `authStore.updateProfile()` 写 AppStorage →
首页顶栏（`@StorageLink('auth_nickname')`）、评论区头像自动刷新，**页面之间不需要传值**。
进入页面时会先 `GET /auth/me` 拉一次服务端值，覆盖本地缓存（用户可能刚在网页端改过）。

**键盘避让**：与 8.7.2 同一套方案（`NONE` + 底部 padding）。另外「保存」做成**固定底栏**，
不能放在滚动表单里 —— 昵称输入时键盘会挡住下半屏，按钮就点不到了（实测踩过）。

**退出登录**：移到资料页底部，`showAlertDialog` 二次确认（`secondaryButton.fontColor` 用红色）。

#### 8.8.2 图片查看

详情页的大图预览（`ArticleDetail.ImageViewer`）支持：

| 手势 | 行为 |
| --- | --- |
| 双指捏合 | 1x ~ 4x 缩放 |
| 放大后拖动 | 平移查看细节 |
| 双击 | 1x ⇄ 2.5x |
| 1x 时下滑 > 100vp | 关闭 |
| 右上角 ✕ | 任何时候关闭 |

实现要点：

1. 手势是**增量式**的：`pinchBase` / `panBase` 记录手势开始时的值，过程中用 `base + 增量` 计算，
   松手后再把 base 更新成当前值；否则会出现「越缩越快 / 拖一下就跳走」。
2. **刻意不做「点击任意处关闭」**：双击放大的第一下会先触发单击，两者叠加会「一放大就退出」。
3. 封面图要单独绑点击 —— 渲染器的 `postProcess()` 只处理正文容器 `#content`，
   `<img class="a-cover">` 在头部 `#head` 里，不补这段的话点封面没反应（实测）。
   作者头像不绑：装饰性小图标点开放大会很怪。

#### 8.8.3 自测记录（模拟器，2026-10-29）

- 头像 → 资料页：头像/昵称/`@username` 正确显示，头像地址计数 `89/300` 完整可见；
- 昵称末尾加「测」→ 保存 → 顶栏立刻变成「…测」；**退出重进资料页仍是「…测」**
  （说明真的写进了服务端，不是只改了本地）；
- 再删掉「测」保存，恢复原名；
- 编辑时「保存」按钮稳定停在键盘上方（固定底栏 + padding）；
- 退出登录弹二次确认，点「取消」留在页面；
- 正文内图片、封面图都能点开大图；双击放大到 2.5x 正常；双击还原后下滑关闭正常；✕ 关闭正常。
- 未覆盖：双指捏合（`uitest uiInput` 不支持多指）、放大后的拖动（只做了代码走查）。


### 8.9 验收记录（模拟器实测，2026-10-29）

对照第 10 节清单逐条走了一遍。状态含义：**✅ 实测**（模拟器上点出来）、
**🟡 走查**（只读代码/后端实现，未在设备上构造出场景）、**⬜ 未做**。

#### 8.9.1 功能项（第 10.1 节）

| 清单项 | 状态 | 证据 / 说明 |
|---|---|---|
| 首页列表加载 / 翻页 / 触底 | ✅ | 首页 35 篇正常；进「网络协议」分类 9 篇，滚到底显示「共 9 篇，已经到底了」，与分类页计数一致 |
| 分类 / 标签 / 关键词筛选 | ✅ | 分类「网络协议」列表 9 篇全属该类；标签「#docker」7 篇全是 Docker 实验；关键词「曲率」命中 1 篇、「408」命中 1 篇；无结果显示「没有找到包含「xxx」的文章」；切换时标题与数据同步变化，无串数据 |
| 首页关键词搜索入口 | ✅ | 本轮新增：顶栏下方搜索框（占位「搜索文章标题」）+ 「搜索」按钮，`doSearch()` trim 后跳 `ArticleList`；空关键词只 toast 不跳转；输入后出现 ✕ 清除，点击后输入框清空、宽度复原 |
| 详情页渲染 | ✅ | 「高数错题集合」标题/作者/时间/阅读数正常，Web 内 `heading`/`code`/`cell`/`mathML*` 节点齐全 —— 代码块、表格、KaTeX 公式都在（`contentHtml` 为空是设计如此，客户端现渲染） |
| 上下篇跳转 | ✅ | 点「下一篇」→ 新文章标题与正文整体切换，无残留旧内容 |
| 相关推荐最多 4 条 | ✅ | 「高数错题集合」底部 4 条（3 条同分类 + 1 条补齐），点击可进 |
| 归档按年/月分组 | ✅ | 2026 → 9 月 15 篇 → 09-22 / 09-21 逐日分组，条目可点 |
| 评论列表昵称/头像 | ✅ | 倒序展示，昵称与头像正常 |
| 未登录引导 → 登录 → 发布 | ✅ | 退出后在详情页看到「登录后参与评论 / 去登录」；点「去登录」进登录页；Gitee 授权后自动回原文、输入栏恢复「写评论…」 |
| 评论发布 | ✅ | 输入「浮层编辑栏修复验证」→ 发布 → 总数 +1、新评论在列表最上、输入栏收起 |
| 10 秒限流提示 | 🟡 | 后端 `CommentController` 用 Redis `SETNX` 限流（key `comment:rate:{userId}:{articleId}`，TTL 10s，错误文案「评论太频繁了，请稍后再试」）；客户端走统一 `toUserMessage(e)` toast。**未能构造自动化用例**：两次发布之间隔了 >10s |
| 超 500 字 | 🟡 | 前端 `TextArea.maxLength(500)` 直接拦住输入，后端也有 `MAX_CONTENT_LENGTH` 兜底；正常打字走不到报错分支 |
| 个人资料读写 | ✅ | 昵称/`@username`/头像地址回填正确，保存后顶栏即时变化且重进页面仍在（8.8.3）已完整测过 |
| 图片放大 / 关闭 | ✅ | 正文图片点击 → 全屏 `Image` + 右上 ✕；点 ✕ 关闭正常；下滑 >100vp 关闭正常。捏合缩放 / 放大后拖动无法用 `uitest` 模拟（不支持多指），仍为 ⬜ |

#### 8.9.2 稳定性 / 接口项（第 10.2、10.3 节）

| 清单项 | 状态 | 说明 |
|---|---|---|
| 连续翻页 20 页 | ⬜ | 线上文章总量才 35 篇，构造不出 20 页 |
| 弱网不白屏 | ⬜ | 需要代理或限速工具配合，本轮未做 |
| 切后台回前台 | ⬜ | 未做 |
| token 过期引导 | 🟡 | `ApiError.code === 403 → logoutAuth()` 逻辑在评论、资料页均有；未真的把 token 改坏去验证 |
| 所有响应检查 `body.code` | 🟡 | `HttpClient` 统一判 `code !== 200` 抛 `ApiError`；但列表类接口若后端返回 200+code≠200 的**空数据**，页面只会显示空态 |
| 403 识别为未登录 | 🟡 | 见上，走查 |
| slug `encodeURIComponent` | 🟡 | `utils/articlePath.ts` 统一拼接，走查 |
| 图片 URL 归一化 | ✅ | 相对路径图片能正常加载并点开大图（`normalizeUrl` 实测生效） |
| 归档日期分别解析 | ✅ | 归档页 `yyyy-MM-dd` 与详情 `yyyy-MM-ddTHH:mm:ss` 均正常显示 |

#### 8.9.3 本轮发现并修掉的问题

**评论编辑栏的「取消 / 发布」按钮消失（严重，等于发不了评论）**

- 现象：点「写评论…」后键盘弹出，只看到输入框，按钮那一行完全不见；`dumpLayout` 里编辑栏
  只剩一个 `TextArea`，父 `Column` 高度 214px（自然高度应为 280px）。
- 原因：编辑栏原先作为正文 `Column` 的最后一个子节点；正文 `Scroll` 是 `layoutWeight(1)`，
  实测它先占满可用高度，编辑栏只分到 125vp，里面 32vp 的按钮行被压成 0，直接不渲染。
- 修法：编辑栏移到外层 `Stack`（`alignContent: Bottom`）做浮层，根节点
  `.margin({ bottom: this.keyboardH })` 抬到键盘上沿；正文底部留白改为 `composing ? 200 : 28`。
- 复测：`TextArea [67,1086][1190,1439]`、`取消 [763,1466]`、`发布 [993,1466]` 三个节点都在，
  空内容时「发布」为 disabled；输入后发布成功、总数 +1。

#### 8.9.4 测试数据清理

文章「高数错题集合」(`id=108`) 上堆积了本轮与上一轮的测试评论。后端 `CommentController`
**没有删除接口**，需要直连数据库清理（`comment_count` 也要一起回填）：

```sql
-- 先看清楚再删
SELECT id, content, create_time FROM comment
 WHERE article_id = 108
   AND content IN ('来自鸿蒙App的评论测试','手动避让方案验证','hhhhh',
                   '浮层编辑栏修复验证','限流第二条','限流A','限流B1','限流B2')
 ORDER BY create_time DESC;

DELETE FROM comment
 WHERE article_id = 108
   AND content IN ('来自鸿蒙App的评论测试','手动避让方案验证','hhhhh',
                   '浮层编辑栏修复验证','限流第二条','限流A','限流B1','限流B2');

UPDATE article
   SET comment_count = (SELECT COUNT(*) FROM comment WHERE article_id = 108 AND status = 1)
 WHERE id = 108;
```

---

## 9. 错误处理

### 9.1 错误文案映射

后端 `message` 已是中文，但部分偏技术化，建议做一层映射：

| 原始 message | 建议展示 |
|---|---|
| `文章不存在` | 这篇文章可能已被删除 |
| `评论太频繁了，请稍后再试` | 评论太快啦，10 秒后再试 |
| `评论内容不能超过 500 字` | 评论最多 500 字 |
| `参数不完整` | 内容不能为空 |
| `登录已过期`（客户端自造） | 登录已过期，请重新登录 |

未知错误统一兜底为「网络开小差了，请稍后重试」，**不要直接暴露堆栈或英文**。

### 9.2 必须覆盖的场景

| 场景 | 期望行为 |
|---|---|
| 无网络 | 显示离线提示 + 重试按钮，不白屏 |
| 请求超时 | 同上，且不重复提交 |
| token 过期（403） | 清 token → 引导登录 → 登录后**回到原页面并恢复原操作** |
| 文章 404（code=404） | 显示「文章已删除」+ 返回按钮 |
| 评论发送失败 | 保留输入内容，允许重试 |
| 正文渲染异常 | 降级显示纯文本（`contentMd` 原文），不能整页空白 |

---

## 10. 验收清单

### 10.1 功能

- [ ] 首页列表可加载、可翻页、触底加载正常
- [ ] 按分类 / 标签 / 关键词筛选结果正确，切换时数据不串
- [ ] 详情页正文正确渲染：标题、段落、列表、表格、**代码块高亮**、**行内与块级公式**
- [ ] 上下篇可跳转，首末篇按钮正确禁用
- [ ] 相关推荐最多 4 条，点击可进入
- [ ] 归档按年/月分组，条目可跳转
- [ ] 评论列表正确显示昵称与头像
- [ ] 未登录点评论 → 引导登录；登录后可发布
- [ ] 评论超过 500 字 / 10 秒内重复发布 → 提示正确且不发请求或正确报错
- [ ] 个人资料可读取、可修改昵称与头像 URL
- [ ] 正文图片点击可放大、可缩放、可关闭

### 10.2 稳定性

- [ ] 连续翻页 20 页无崩溃、无内存持续增长
- [ ] 弱网（模拟 2G）下不白屏，有加载态与失败态
- [ ] 切后台再回前台，页面状态不丢失
- [ ] token 过期后首次操作能正确引导重登

### 10.3 接口正确性（重点核对）

- [ ] 所有响应都检查了 `body.code`，而不只是 HTTP 状态
- [ ] 403 被识别为「未登录」而不是「无权限」
- [ ] slug 拼接做了 `encodeURIComponent`
- [ ] 图片 URL 做了相对/绝对归一化
- [ ] 归档的 `yyyy-MM-dd` 与详情的 `yyyy-MM-ddTHH:mm:ss` 分别解析

---

## 11. 发布合规（个人主体）

| 事项 | 说明 | 建议时间点 |
|---|---|---|
| **App 备案** | 国内分发的 App 须完成工信部备案，个人主体可办，需域名/服务器信息（`codeup.asia` 可用） | 开发中期就启动，有等待周期 |
| 隐私政策 | 必须声明收集哪些信息、用途、存储方式；本 App 仅需网络权限，相对简单 | 提审前 |
| 权限最小化 | 只申请 `ohos.permission.INTERNET` | 全程 |
| 内容合规 | 含评论（UGC），审核可能要求举报入口与内容管理机制 | 提审前评估 |
| 签名与 Profile | DevEco 自动签名仅用于调试；发布需 AGC 创建应用并生成发布证书 | 提审前 |

> 注意：HarmonyOS NEXT **不兼容 Android**，不能用 Android 工程或 APK 改包，必须纯 ArkTS 重写。

---

## 12. 附录：接口自测命令

在服务器或本地终端直接验证（`--resolve` 用于绕开本机 DNS 缓存，按需删掉）：

```bash
H='--noproxy * --max-time 20 --resolve codeup.asia:443:47.116.138.65'

# 文章列表
curl -s $H 'https://codeup.asia/api/article/list?pageNum=1&pageSize=2'

# 文章详情（会 +1 浏览量）
curl -s $H 'https://codeup.asia/api/article/108'

# 按 slug（中文需编码）
curl -s $H "https://codeup.asia/api/article/post/$(python -c "import urllib.parse;print(urllib.parse.quote('高数错题集合'))")"

# 上下篇
curl -s $H 'https://codeup.asia/api/article/108/neighbors'

# 相关推荐
curl -s $H 'https://codeup.asia/api/article/108/related'

# 分类 / 标签 / 归档
curl -s $H 'https://codeup.asia/api/category/list'
curl -s $H 'https://codeup.asia/api/article/tags'
curl -s $H 'https://codeup.asia/api/article/archive'

# 评论列表
curl -s $H 'https://codeup.asia/api/comment/list?articleId=108'

# 未登录访问受保护接口（应为 403）
curl -s -o /dev/null -w '%{http_code}\n' $H 'https://codeup.asia/api/auth/me'

# OAuth 跳转（应为 302）
curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' $H 'https://codeup.asia/api/auth/oauth/gitee'
```

---

## 13. 实施顺序建议

| 步骤 | 内容 | 产出 |
|---|---|---|
| 1 | 网络层 + 列表页 | 能拉通 `/article/list` 并展示卡片 |
| 2 | Markdown 渲染 PoC | 一篇含代码块 + 公式的文章正确显示 |
| 3 | 详情页 | 正文 + 标签 + 上下篇 |
| 4 | 分类 / 标签 / 归档 | 三个入口页 |
| 5 | OAuth 登录 | 能拿到 token 并调通 `/auth/me` |
| 6 | 评论 | 列表 + 发布 |
| 7 | 个人资料 + 图片查看 | 补齐体验 |
| 8 | 打磨与验收 | 按第 10 节清单逐条过 |

> **第 2 步是唯一的「未知项」**，其余都是常规列表/表单/详情。建议先花 2~3 天只做第 2 步，跑通再往下做。

---

*本文档基于 2026-10-28 的代码与线上接口实测编写。若后端接口有变更，请以实际响应为准。*

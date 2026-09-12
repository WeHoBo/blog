# UI 优化 · 第一批改动报告

> 本次按《UI界面优化建议清单》的**第 1 批（最快见效）**执行：5 项 P0 + 2 项顺带修复。
> 全部只改**本地代码**，未动服务器；服务器操作步骤见文末。
> 所有改动均已通过本机编译/实测验证，验证证据见第 3 节。

---

## 1. 改动总览

| # | 问题 | 涉及文件 | 状态 |
|---|---|---|---|
| 1 | 复制代码丢失所有换行（P0） | `MarkdownRenderer.vue`、`main.css` | ✅ 已修 + 实测 5/5 |
| 2 | 锚点跳转被固定顶栏遮挡（P0） | `main.css` | ✅ 已修 |
| 3 | 删除提示文案与真实行为不符（P0） | `admin/articles/index.vue`、`useFeedback.ts`、`AppConfirm.vue` | ✅ 已修 |
| 4 | 个人中心「新密码」是死功能（P0） | `profile.vue`、`AuthController.java` | ✅ 前端 + 后端一起下线 |
| 5 | 关于页 `html:true` 重开 XSS 口子（P0） | `about.vue` | ✅ 已修 |
| ★ | 手机上没有复制按钮（P1，顺带） | `main.css` | ✅ 已修 |
| ★ | 确认框没有危险态（P1，顺带） | `useFeedback.ts`、`AppConfirm.vue` | ✅ 已修 |

共改动 **8 个文件**（前端 7 + 后端 1）。无新增配置项、**无数据库变更**。

**本次批次的 8 个文件（精确清单）**

```
blog-web/components/MarkdownRenderer.vue
blog-web/assets/css/main.css
blog-web/components/AppConfirm.vue
blog-web/composables/useFeedback.ts
blog-web/pages/admin/articles/index.vue
blog-web/pages/profile.vue
blog-web/pages/about.vue
blog-server/blog-front-api/src/main/java/com/blog/front/controller/AuthController.java
```

> ⚠️ 工作区里还有一批**更早的「服务器加固与备份」改动尚未提交**
> （`.gitignore`、`JwtUtils.java`、`application.yml`、新增 `logback-spring.xml`、`deploy/`、`rag_system/backend/.env.example`），
> 那批属于上一个任务，和本次 UI 改动**不是同一批**。提交时建议分开，便于出问题时定位。

---

## 2. 逐项改动明细

### 2.1 复制代码丢换行（P0，收益最高）

**根因**：正文用 `v-html` 渲染，每个代码行是独立的 block 级 `<span class="line">`，**行与行之间没有任何换行符**；复制按钮却直接读 `code.textContent` → 整段代码被挤成一行。

改动文件：`blog-web/components/MarkdownRenderer.vue`

1. **复制改为按行重建**（`enhance()` 里的按钮点击处理）：

```js
// 旧
const code = pre.querySelector('code')?.textContent || ''

// 新
const codeEl = pre.querySelector('code')
const lineEls = codeEl ? Array.from(codeEl.querySelectorAll<HTMLElement>('.line')) : []
const code = lineEls.length
  ? lineEls.map((l) => l.textContent || '').join('\n')
  : (codeEl?.textContent || '')
```

2. **渲染处剥离多余尾换行**：`markdown-it` 传入的代码内容末尾自带一个 `\n`，`highlight.js` 的输出末尾又会补一个 `\n`。两者叠加会在代码块**底部多渲染 2 个空行**（复制时也会多出换行）：

```js
const content = str.replace(/\n$/, '')                       // 剥离 markdown-it 的
const result = hljs.highlight(content, {...}).value.replace(/\n$/, '')  // 剥离 hljs 的
```

3. **空行不再注入 `&nbsp;` 占位**：`.line` 的 `min-height: 1.4em` 已能撑出行高。原先的占位符会让**复制的空行里混入不换行空格（U+00A0）**。

改动文件：`blog-web/assets/css/main.css`

4. **触屏设备常显复制按钮**（`★` 顺带）：原先按钮依靠 `pre:hover` 显示，手机没有 hover，永远点不到。

```css
@media (hover: none) {
  .code-copy-btn { opacity: 1; }
}
```

---

### 2.2 锚点跳转被固定顶栏遮挡（P0）

顶栏是 64px 的固定条，全站没有任何 `scroll-margin-top`，因此点目录条目、点首页「开始阅读」（`#articles`）后，目标标题会藏在顶栏下面。

改动文件：`blog-web/assets/css/main.css`

```css
/* 锚点跳转避让固定顶部导航（Header 高约 64px） */
[id] {
  scroll-margin-top: 5rem;
}
```

对所有带 `id` 的元素生效——这是「固定头部 + 锚点」的标准做法。

---

### 2.3 删除提示文案与实际行为不符（P0）

后台文章列表的删除是**软删除（进回收站）**，但确认框写的是「删除后无法恢复」，与回收站页的「彻底删除后无法恢复」自相矛盾，会吓到用户。

改动文件：`blog-web/pages/admin/articles/index.vue`（2 处）

| | 旧文案 | 新文案 |
|---|---|---|
| 单篇 | 确定删除这篇文章？删除后无法恢复。 | 确定删除这篇文章？**将移入回收站，可在回收站恢复。** |
| 批量 | 确定删除选中的 N 篇文章？删除后无法恢复。 | 确定删除选中的 N 篇文章？**将移入回收站，可在回收站恢复。** |

**顺带实现「危险态」**（`★`，`useFeedback.ts` + `AppConfirm.vue`）：删除类确认框的「确定」按钮改为**红色**，并给出更贴切的标题（"删除文章" / "批量删除"）。
> 为什么必须一起做：`confirmDialog` 原本只接受 2 个参数，若只传第 3 个参数会造成 TypeScript 类型错误。另外删除确认框本就该在视觉上「有分量」。
> 兼容性：其余 6 处 `confirmDialog(...)` 调用仍是 2 个参数，`danger` 默认 `false` → 仍是蓝色按钮，行为完全不变。

---

### 2.4 个人中心「新密码」死功能（P0）

账号唯一登录方式是 OAuth（GitHub / Gitee / 华为），密码登录已下线；但个人中心仍有「新密码」输入框，后端 `/auth/profile` 也仍会把密码加密写库 → 用户设了一个**永远用不上的密码**。

改动文件：`blog-web/pages/profile.vue`
- 删除「新密码（留空不修改）」整个输入块
- 从 `form` 中移除 `password` 字段

改动文件：`blog-server/blog-front-api/.../AuthController.java`
- 删除密码写入分支：
```java
if (profile.getPassword() != null && !profile.getPassword().isBlank()) {
    user.setPassword(passwordEncoder.encode(profile.getPassword()));
}
```
- 一并移除随之不再使用的 `PasswordEncoder` 注入字段与 import（`AdminUserController` / `OAuthController` 仍在正常使用该 Bean，未受影响）

---

### 2.5 关于页 XSS 加固（P0）

`about.vue` 用 `new MarkdownIt({ html: true })` + `v-html`，而正文渲染器 `MarkdownRenderer.vue` 是**刻意关掉** `html` 的（并注释说明「避免配合 v-html 造成存储型 XSS」）→ 关于页把这条防线撤掉了，安全策略自相矛盾。

改动文件：`blog-web/pages/about.vue`

```js
// html: false —— 与 MarkdownRenderer 保持一致，禁用裸 HTML
return new MarkdownIt({ html: false, linkify: true }).render(aboutContent.value)
```

**动手前已核实线上真实内容**：`GET https://codeup.asia/api/site-config/public` 返回 `"aboutContent":"222222"` —— 是纯文本、不含任何裸 HTML。因此本次改动**零视觉影响**，只是把安全策略补齐。

---

## 3. 验证过程与证据

| 验证项 | 手段 | 结果 |
|---|---|---|
| 前端语法 | `@vue/compiler-sfc` 全量编译 `blog-web/**/*.vue` | **39 个文件，0 个问题** |
| 复制代码正确性 | 用真实 `markdown-it` + `highlight.js` 渲染，再模拟浏览器 `querySelectorAll('.line').map(textContent).join('\n')` | **5/5 用例与源码逐字一致** |
| 后端编译与测试 | `mvn -pl blog-front-api -am test` | **Tests run: 39, Failures: 0, Errors: 0 — BUILD SUCCESS** |
| 全局一致性 | grep | `html: true` → **0 处**；`profile.vue` 中 `password` → **0 处** |

**复制代码实测结果**（修复前 / 修复后）：

| 用例 | 修复前 | 修复后 |
|---|---|---|
| Java（含内部空行，5 行） | 0 换行 | **4 换行，与源码一致** |
| JS（4 行） | 0 换行 | **3 换行，与源码一致** |
| 纯文本无语言（2 行） | 0 换行 | **1 换行，与源码一致** |
| 含 HTML 特殊字符（3 行） | 0 换行 | **2 换行，与源码一致** |
| Python 缩进敏感（4 行） | 0 换行 | **3 换行，与源码一致** |

> 修复前的 5 个用例**全部丢失换行**——这条对技术博客的核心操作影响确实很大。

---

## 4. 本次**未**做的部分（及原因）

| 项 | 优先级 | 为什么这次不做 |
|---|---|---|
| **文章详情页合并**（`/post/{slug}` 缺封面/目录/标签/作者/上下篇/评论/相关推荐） | P0 | 审计文档自己标注「**风险最高、需单独排期 + 人工回归**」。它要改动两条 canonical 路由共用的渲染层，属于结构性重构，不适合和其它小改动混在一批 —— 出问题时无法定位是哪一批引起的。建议单独一轮处理。 |
| **站点设置 4 项生效**（`siteDesc`/`bloggerName`/`bloggerBio`/`bloggerAvatar` 前台未使用） | P0 | 卡在一个**需要你拍板的语义问题**：首页侧栏现在展示的是「**登录用户本人**」的头像昵称，而 `bloggerAvatar` 是「**博主本人**」的。两者不是同一个东西，不能简单替换。需要先确定侧栏到底要展示谁。 |

> 其余 P1/P2 项（首页 URL 同步、归档页、AI 输入法回车、后台导航选中态、空状态等）保持原状，未改动。

---

## 5. 部署步骤（本地改 → 手动部署）

> 本次**无新增配置项、无数据库变更**，因此不需要动 `application-prod.yml`，也不需要跑 SQL。

### 5.1 前端（改了 `.vue` / `.css`，必须重新构建）

```bash
# 本地
cd blog-web
npm run build

# 把整个 .output 目录传到服务器（不要只传 .output/public！）
# 落地位置：/opt/blog/web/.output
# 然后重启前端服务：
systemctl restart blog-web
```

### 5.2 后端（只改了 AuthController）

```bash
# 本地
cd blog-server
mvn -pl blog-front-api -am package -DskipTests

# 上传 target/blog-front-api-1.0.0.jar 到服务器 /opt/blog/
systemctl restart blog-api
```

> 提醒：服务器上跑的是 `blog-front-api-1.0.0.jar`，**落地文件名要和 service 里写的一致**，否则跑的还是旧 jar。

---

## 6. 上线后自测清单

- [ ] **代码块复制**：桌面 hover 代码块 → 出现「复制」→ 点一下 → 粘贴到编辑器/终端 → 换行正确、无多余空行、无 `&nbsp;`
- [ ] **手机复制**：窄屏/手机打开文章，代码块右上角复制按钮**直接可见**并可点击
- [ ] **代码块底部**：不再多出空白行
- [ ] **锚点避让**：点文章目录条目 / 首页「开始阅读」→ 目标标题**不被顶栏遮住**
- [ ] **删除文案**：后台文章管理 → 删除 → 弹窗写「将移入回收站，可在回收站恢复」，且「确定」按钮是**红色**；删除后文章确实出现在**回收站**页
- [ ] **个人中心**：`/profile` 不再有「新密码」字段；改昵称/头像能正常保存
- [ ] **关于页**：`/about` 正常显示，无 HTML 源码泄漏
- [ ] **回归**：随便打开几篇文章，正文/代码块/图片显示正常

---

## 7. 顺带发现（本次**未改**，仅记录）

- **代码块是「嵌套 `<pre>`」结构**：`markdown-it` 会把 `highlight()` 的返回内容再包一层 `<pre><code class="language-x">`，于是站内每个代码块实际是「外层无样式 `<pre>` + 内层 `<pre class="hljs">`」。属既有结构，改动会影响布局，未在本次触碰。若后续要优化代码块间距/滚动条，可从这里入手。

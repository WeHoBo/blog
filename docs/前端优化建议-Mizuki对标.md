# 前端优化建议（对标 Mizuki）

> 评审对象：`D:\cxdownload\Mizuki-master`（https://mizuki.mysqil.com/）vs 本站 `blog-web`
> 日期：2026-11-06　状态：**仅建议，未改任何代码**

---

## 一、结论先行

三句话：

1. **Mizuki 值得抄的是「正文渲染的细节功底」和「设置的颗粒度」，不是它的技术栈。**
   它是纯静态 Astro 站点（无数据库、无接口），我们是 Nuxt SSR + Spring Boot + MySQL，两边的数据流根本不同，
   照搬框架层会把自己坑死。
2. **真正该立刻做的只有 4 件事**，全部在我们现有代码里，加起来不超过半天，但收益（流量、可读性、正确性）最大：
   图片按需处理、宽元素破栏、表格滚动、目录与正文共用一套标题规则。
3. **Mizuki 里有一半特性我们不该要**（Live2D 看板娘、BGM 播放器、樱花特效、追番数据）。
   这些是「个人展示型站点」的装饰，我们是技术博客，而且上一轮刚把高饱和装饰清理干净，加回来是倒退。

---

## 二、Mizuki 的架构拆解

### 2.1 技术栈

| 维度 | Mizuki | 我们 |
|---|---|---|
| 框架 | Astro 7（`output: "static"`） | Nuxt 3.21（`ssr: true`） |
| 交互层 | Svelte 5 群岛（53 个 `.svelte`） | Vue 3 单页应用 |
| 样式 | Tailwind 4（`@theme`）+ Stylus 变量 | Tailwind 3 + `tailwind.config.ts` |
| 内容源 | 本地 Markdown 文件 | MySQL（后台编辑器写入） |
| 内容渲染 | 构建期 remark/rehype 插件链 → 静态 HTML | 运行时 `markdown-it` + `hljs`，SSR 出 HTML |
| 路由过渡 | swup（拦截 `<a>`，替换 `main`） | Vue Router（本来就无刷新） |
| 搜索 | Pagefind（构建期生成索引） | 后端 `LIKE title` |
| 代码高亮 | expressive-code（构建期着色） | highlight.js（运行时着色） |
| 图片 | Astro `<Picture>`：AVIF/WebP + 多尺寸 srcset | 原图 `<img>`，无 srcset |
| 规模 | 120 `.astro` + 53 `.svelte` + 215 `.ts` | 16 组件 + 24 页面 |

### 2.2 三条核心设计（这才是值得学的部分）

**(1) 内容增强全部放在编译期。**

Mizuki 的 `astro.config.mjs` 挂了一整条插件链：`remarkMath`、`remarkDirective`、`remarkSectionize`、
`rehypeSlug`、`rehypeWrapTable`、`rehypeCodeGroup`、`rehypeComponents`（admonition / github 卡片 / 图片网格）、
`rehypeAutolinkHeadings`……

效果是：**作者写 Markdown，读者看到的是富文本**，而浏览器拿到的只是 HTML，没有一行 JS 参与排版。
它的 `package.json` 里 30+ 个依赖，一半是给这条链服务的。

我们的对应位置是 `components/MarkdownRenderer.vue` 里的 `new MarkdownIt({...})` 实例。
差的不在「有没有渲染器」，而在**规则数量**。

**(2) 设置项做成「配置 + 可切换」两层。**

`src/config/*.ts` 有 20 个配置文件，每个功能都有 `enable` 与 `switchable` 两个开关：
`enable` 决定站点有没有这个功能，`switchable` 决定读者能不能自己改。
`backgroundWallpaper.ts` 就是典型：

```ts
fullscreenWallpaperConfig = {
  enable: true,
  src: { desktop: [...], mobile: [...] },   // 桌面/手机两套图
  carousel: { enable: true, interval: 5 },   // 轮播
  opacity: 0.8, blur: 1,                     // 图片本身的不透明度/模糊
  overlay: { switchable: { opacity, blur, cardOpacity } },  // 读者可调
}
```

对比我们刚做的「主图背景」：只有 4 个模式，没有桌面/手机分图、没有轮播、没有透明度/模糊、没有卡片透明度。
**这不是必须补的**，但说明我们做的是最小版，后续要扩有明确的参照物。

**(3) 首屏状态用内联脚本抢在绘制前落地。**

`Layout.astro` 的 `<head>` 里有一段 `is:inline` 脚本，在 CSS 生效前就把 `dark` 类、
`data-waves-enabled`、`data-banner-title-enabled` 从 `localStorage` 写进 `<html>`。
这就是它切换夜间模式、开关壁纸时**不闪**的原因。

我们的现状：壁纸模式和夜间模式都在 `onMounted` 之后才读 `localStorage`，
所以「隐藏壁纸」的用户每次刷新会先看到壁纸再消失。这是我自己上一轮埋的坑，见 P0-4。

---

## 三、架构对照：为什么不能照搬

| 能力 | Mizuki 怎么做 | 我们能不能照搬 | 原因 |
|---|---|---|---|
| 页面过渡 | swup 拦链接换 `main` | ❌ 不要 | Vue Router 已经是无刷新，再套 swup 会双重接管路由 |
| 搜索 | Pagefind 构建期索引 | ❌ 不能 | 内容在 MySQL，构建时拿不到全量正文 |
| 代码高亮 | 构建期着色 | ⚠️ 部分 | 我们是运行时渲染，换 Shiki 会增加首屏体积 |
| 图片优化 | Astro `<Picture>` 出 AVIF | ✅ 换个实现 | 我们没构建期图片管线，但 OSS 自带处理参数 |
| 内容增强 | remark/rehype 插件 | ✅ 可以 | `markdown-it` 一样能挂规则，只是 API 不同 |
| 配置体系 | 20 个 TS 配置文件 | ⚠️ 不要 | 我们的配置在 `site_config` 表里，双份配置必然打架 |
| 主题切换动画 | View Transitions API | ✅ 可以 | 纯浏览器能力，框架无关 |

---

## 四、P0：半天内做完，收益最大

### P0-1　图片走 OSS 处理参数（实测省 96% 流量）

**问题**：所有图片都是原图直出。`ArticleCard.vue`、`ArticleDetail.vue`、`WallpaperLayer.vue` 里的
`<img :src="article.cover">` 和 `background-image: url(...)` 拿到的都是上传时的原始文件。

**实测**（刚刚用线上真实图片跑的）：

| 文件 | 原图 | `?x-oss-process=image/resize,w_640/format,webp/quality,q_80` |
|---|---|---|
| `42c0f8c1….jpeg` | **341 KB** | **8 KB**（−97.6%） |
| `d8e80191….png` | **243 KB** | **8 KB**（−96.7%） |
| `b0130586….png` | 28 KB | 3 KB |

首页一屏 10 张卡片，如果按 300 KB 算就是 3 MB；改成 8 KB 后不到 100 KB。
**这是整份建议里唯一能直接量化到「手机流量」的一项**，且不需要装任何依赖——阿里云 OSS 自带。

**改法**（约 30 行，新增 `utils/image.ts`）：

```ts
const OSS_HOST = 'wehobo.oss-cn-wulanchabu.aliyuncs.com'

/** 给 OSS 图片加处理参数；非 OSS 域名（外链图）原样返回 */
export function img(url: string, opts: { w?: number; q?: number; format?: 'webp' | 'avif' } = {}) {
  if (!url || !url.includes(OSS_HOST)) return url
  if (url.includes('x-oss-process')) return url          // 已处理过，别叠加
  const { w = 1280, q = 80, format = 'webp' } = opts
  const p = [`resize,w_${w}`, `format,${format}`, `quality,q_${q}`].join('/')
  return `${url}${url.includes('?') ? '&' : '?'}x-oss-process=image/${p}`
}
```

用法分三处，宽度按实际显示尺寸给：

| 位置 | 显示尺寸 | 参数 |
|---|---|---|
| 列表卡片封面 | ~250px | `w_640` |
| 详情页封面缩略图 | ~128px | `w_320` |
| 正文内图片 | ~800px | `w_1280` |
| 主图背景（横幅） | 满宽 | `w_1920` |

进阶：卡片加 `srcset`，让 DPR=2 的屏幕拿 2 倍图——

```html
<img :src="img(cover, { w: 640 })"
     :srcset="`${img(cover,{w:640})} 1x, ${img(cover,{w:1280})} 2x`"
     loading="lazy" decoding="async" />
```

⚠️ 注意：`format,webp` 对已经带透明通道的 PNG 是安全的，但如果原图是 GIF 动图会被转成静态首帧，
   上传口要限制动图（现有上传接口是否允许 GIF，需要确认）。

---

### P0-2　宽元素破栏 + 表格滚动容器

**问题一**：`.article-content` 没有正文宽度约束，正文一直铺到卡片边缘。
1440px 屏上，正文行宽约 860px，中文一行 45–50 字，**超出舒适阅读区间（35–40 字）**，
长文读起来眼睛累。而正文一窄，宽表格和行间公式就更难受。

**问题二**：`main.css:116` 是 `.article-content table { @apply w-full }`，
**没有滚动容器**。一张 6 列的表格在手机上会被压成豆腐块，或者撑破卡片。

**Mizuki 的做法**（`post-content-rails.css`，48 行）：正文用 `--post-reading-width` 限制宽度，
同时把表格、代码块、行间公式、Mermaid、图片网格这些「宽元素」**单独放行到卡片全宽**：

```css
.markdown-content > * { max-width: var(--post-reading-width); }         /* 正文窄 */
.markdown-content > :is(.table-wrapper, .expressive-code,
                        .katex-display, .mermaid, .image-grid) {
  max-width: none;                                                       /* 宽元素全宽 */
}
```

**落到我们的代码**（两处，约 25 行）：

1. `assets/css/main.css` 增加：
```css
.article-content { --reading-width: 44rem; }
.article-content > * { max-width: var(--reading-width); }
.article-content > :is(table, pre, .katex-display, .mermaid-wrapper, .code-wrapper) {
  max-width: 100%;            /* 宽元素回到卡片全宽 */
}
```

2. `MarkdownRenderer.vue` 里给表格套一层滚动容器（markdown-it 规则覆盖）：
```js
md.renderer.rules.table_open  = () => '<div class="table-wrap"><table>'
md.renderer.rules.table_close = () => '</table></div>'
```
```css
.table-wrap { overflow-x: auto; -webkit-overflow-scrolling: touch; margin: 1.5rem 0; }
.table-wrap table { margin: 0; min-width: 100%; }
```

> 为什么值得做：这是**唯一一处同时改善「长文可读性」和「移动端不破版」的改动**，
> 而且完全不影响现有数据结构。

---

### P0-3　目录与正文共用一套标题规则（现在有 3 个 bug）

现状：`MarkdownRenderer.vue:136` 用 `heading_open` 生成 id，`TableOfContents.vue:31` 又用正则
从**原始 Markdown 文本**里重新扫一遍标题。两份实现，规则各写各的，后果有三个（已核实）：

| # | Bug | 表现 | 根因 |
|---|---|---|---|
| 1 | 代码块里的 `# 注释` 被当成标题 | 目录多出「安装依赖」这类条目，点了跳不动 | TOC 用 `line.match(/^#{1,4}\s+/)` 逐行扫，不识别 ``` 围栏 |
| 2 | 重复标题撞 id | 两篇都有「总结」小节 → `id="总结"` 重复，目录两条都跳到第一个 | `heading_open` 只做 `toLowerCase().replace(...)`，**没有去重计数** |
| 3 | 起始层级错位 | 文章从 `##` 开始写，目录整体多缩进一级 | `INDENT_CLASS` 按绝对 `#` 数量映射，没做「最小层级归一化」 |

**Mizuki 的做法**：`toc-utils.ts` 的 `extractHeadings()` —— **不解析原文，直接从渲染后的 DOM 里读**
`h1[id]…h6[id]`，再用 `getMinLevel()` 把层级归一化。这样标题 id 只有「渲染器」一个来源，天然不会分叉。

**我们的改法**（推荐程度：DOM 提取 > 共享函数）：

- 方案 A（推荐，改动最小）：`TableOfContents` 改成在 `onMounted` / `content` 变化后
  `document.querySelector('.article-content')` 里读 `h1..h6`，复用 MarkdownRenderer 已生成的 id。
  代价：TOC 从「SSR 就有」变成「客户端才有」，首屏目录会晚一帧——可以接受，或者保留正则作为 SSR 兜底。
- 方案 B：抽出 `utils/slug.ts` 的 `createSlugger()`（内部带 `Map<string, number>` 去重），
  MarkdownRenderer 和 TOC 都调它。注意 TOC 仍需跳过代码围栏。

另外顺手补两点：
- `slugify` 对中文标题会保留中文字符，生成 `#高数错题集合` 这种 id，**URL 里会出现中文**。
  现代浏览器没问题，但分享出去被转义后很难看。建议中文标题统一走 `h-1`、`h-2` 递增 id（Mizuki 用 `rehype-slug` + `github-slugger` 也是保留原文，但它全站英文）。
- 标题右侧加一个 hover 出现的 `#` 锚点（Mizuki 的 `rehypeAutolinkHeadings`），
  方便复制「指向某一节」的链接。10 行 CSS + 一段 `heading_open` 拼接。

---

### P0-4　首屏状态前置，消除壁纸/主题闪烁

**问题**：`useWallpaper.ts` 的 `hydrate()` 挂在 `onMounted`，`@nuxtjs/color-mode` 的深色类也是客户端加。
结果是刷新时：先按默认「横幅」画一帧 → 读到 `localStorage` 是「隐藏」→ 壁纸消失。
深色模式同理会白闪一下（color-mode 自带内联脚本，实际影响较小，但壁纸这块是我上轮引入的）。

**Mizuki 的做法**：`<head>` 里一段同步脚本，在首次绘制前把状态写进 `<html>`：

```html
<script is:inline>
  (function(){
    try {
      var m = localStorage.getItem('wallpaperMode') || 'banner';
      document.documentElement.setAttribute('data-wallpaper', m);
    } catch(e){}
  })();
</script>
```

**我们的改法**：`nuxt.config.ts` 的 `app.head.script` 加一段 `innerHTML` 内联脚本，
写 `documentElement.dataset.wallpaper`；`layouts/default.vue` 的 `mainPad` 和 `WallpaperLayer` 的
`wrapperClass` 优先读这个 attribute（首屏），`hydrate()` 之后交给响应式状态接管。
CSS 侧也可以用 `html[data-wallpaper="none"] #wallpaper{display:none}` 兜底，做到零闪烁。

---

## 五、P1：1–2 天，明显提升「内容表现力」

### P1-5　Admonition 提示块

Mizuki 支持 20+ 别名（`note/tip/warning/caution/important/…`），实际渲染成 5 种样式。
我们正文里目前只能用 `>` 引用块表达「注意」，视觉上和普通引用没区别。

**成本**：markdown-it 一个 block 规则（40 行），语法建议直接用 GFM 写法，编辑器里零学习成本：

```md
> [!NOTE]
> 这里的 `--ph` 是色相变量，改它等于换主题色。
```

**收益**：教程类文章（你现在写的考研数学、408 笔记）特别吃这个——「易错点」「考点提示」有统一的视觉承载。

### P1-6　连续图片自动成组

Mizuki 的 `remarkAutoImageGrid`：连续 2–4 张图自动并排成网格，点击进灯箱。
我们的文章里有大量题目截图，现在是一张一行、占满宽度，长文被拉得很长。

**成本**：`MarkdownRenderer` 里在 `enhance()` 阶段做 DOM 合并（同段落内连续 `<img>` 包进 `.img-grid`），
或写一个 markdown-it 渲染后处理。CSS 用 `grid-template-columns: repeat(auto-fit, minmax(240px,1fr))`。
可以和现有 `bindImageLightbox` 直接复用。

### P1-7　代码块补齐三件小事

我们已经有：语言标签、行号、`{3-5}` 行高亮、折叠、复制、换行切换——**这块完成度已经接近 Mizuki 了**。
缺的是：

1. **代码组 Tab**（Mizuki `rehype-code-group`）：同一份逻辑的 Java / Vue 两版并排切换。对多端项目文章有用。
2. **语言徽标**：现在只有文字语言名，可以加个 `Java` 的小色块（注意：别用高饱和，用暖灰底 + 主题色描边）。
3. **行号开关**：行号现在一直显示，短代码块里有点碍眼，可以做成默认关闭、hover 出现。

### P1-8　搜索：从「只搜标题」到「搜得到内容」

现状：后端 `WHERE title LIKE %kw%`。搜「红黑树」如果标题没这几个字就搜不到，这是最影响体验的一处短板。

三个选项，按成本排：

| 方案 | 成本 | 效果 | 备注 |
|---|---|---|---|
| A. 后端扩展到 `title + summary + tag` | 半天（SQL 改一行） | 命中率提升有限 | 最快，但搜不到正文 |
| B. MySQL `FULLTEXT` 索引（ngram 分词） | 1 天 | 能搜正文，支持相关度排序 | 需要建表变更 + 中文分词配置 |
| C. 前端构建期索引（Pagefind 思路） | 2 天 | 体验最好（离线、瞬时） | 与我们「内容在库」冲突，需要构建时拉全量导出 |

**建议先做 A**，把「搜不到」变成「搜得到大部分」；如果以后文章过百篇再上 B。
另外 UI 侧可以抄 Mizuki 三点：搜索历史（localStorage 存 5 条）、`↑↓` 键盘选择、结果里高亮命中词。

---

## 六、P2：需要权衡，不急

### P2-9　给公开页面加缓存（`routeRules`）

Mizuki 全部是静态文件，TTFB 接近 0。我们每篇文章要跑 5 个接口
（文章+标签+作者、评论、上下篇、相关推荐、站点配置），SSR 串起来才吐 HTML。

```ts
routeRules: {
  '/api/article/**': { swr: 60 },      // 60 秒内复用，容忍 1 分钟延迟
  '/api/category/**': { swr: 300 },
  '/post/**': { swr: 60 },
}
```

⚠️ **两个必须注意的副作用**：
1. 详情接口每访问一次 `viewCount + 1`，缓存会让你丢计数（这是你现在的功能，不能悄悄弄丢）。
2. 「发布后立刻可见」会变成「最多 1 分钟后可见」，后台发布完要提示用户。

**结论**：35 篇文章的体量，不做也完全撑得住。**等流量真上来再做**，或者只对分类/标签这类「不计数」的接口做。

### P2-10　主题切换用 View Transitions

Mizuki 切深浅色时用 `document.startViewTransition()` 做圆形扩散，观感很好。
我们目前是整页 `.dark` 类一换，全站重绘，长文页会有明显的「卡一下」。

```ts
if (document.startViewTransition && !matchMedia('(prefers-reduced-motion: reduce)').matches) {
  document.startViewTransition(() => { /* 切类 */ })
}
```
配套 CSS `::view-transition-old/new(root)` 加 0.3s 淡入。**成本约 20 行，收益是"高级感"**，
但要确认和你现在的 `pageTransition`（fadeInUp）不冲突。

### P2-11　渲染器插件化

现在 `MarkdownRenderer.vue` 279 行，把 markdown-it 配置、代码块规则、mermaid、灯箱、代码工具条全塞在一个文件里。
每加一个增强（admonition、图片网格、锚点），这个文件都会更长。

建议拆成 `utils/markdown/` 目录：`index.ts`（导出单例 md）、`plugins/heading.ts`、`plugins/table.ts`、
`plugins/admonition.ts`，`enhance/` 放所有「渲染后 DOM 增强」。
**这不是为了好看，是因为 P0-3 要求「TOC 和正文共用 slug」——不拆开，共享逻辑没地方放。**

---

## 七、明确不建议照搬

| 特性 | 为什么不要 |
|---|---|
| Live2D 看板娘（`l2d-widget`，Pio 组件） | 装饰性重资产，移动端耗电、遮挡内容；和"生活杂志"风格也不搭 |
| BGM 播放器（`MusicPlayer.svelte`） | 自动播放被浏览器拦截，用户还得手动关；正经技术博客没人开 |
| 樱花/落雪特效（`sakura-effect.ts`） | 上一轮刚因为「高饱和装饰」清理过一次，尤其桌面端。**我们自研的同款 `FestivalEffect.vue`（按月份飘 ❄️/🌸/🍂）已于 2026-11-07 一并删除** |
| 追番/追剧数据（`anime`/`bangumi`/`bilibili` 页面 + 定时脚本） | 需要第三方 API + 定时任务 + 数据落盘，维护成本远大于展示价值 |
| swup 页面过渡 | 和 Vue Router 职责重叠，会抢路由 |
| Pagefind 搜索 | 内容在数据库，构建期拿不到 |
| 20 个 TS 配置文件体系 | 我们的配置已经在 `site_config` 表，两套配置必然不同步 |
| Astro `<Picture>` / Font API | 框架绑定，我们没这套构建管线（用 OSS 参数替代，效果接近） |
| 动态 OG 图（`src/pages/og/`，takumi） | 收益小（阅读量主要来自站内和搜索），成本不低 |
| 宽屏版式 `ultrawidePostLayout` | 我们正文本来就窄，破栏做完（P0-2）再看是否需要 |

---

## 八、建议的落地顺序

**第一批（半天，做完就能感知）**
1. P0-1 图片 OSS 参数 —— 首页/详情页改 3 个组件 + 1 个工具函数
2. P0-2 宽元素破栏 + 表格滚动 —— 2 处 CSS + 2 行渲染规则
3. P0-3 目录 bug（先修 3 个 bug，再统一 slug）
4. P0-4 首屏状态前置

**第二批（1–2 天，内容表现力）**
5. P1-5 Admonition 提示块
6. P1-6 图片网格
7. P1-8 搜索扩到 summary/标签（先做最省事的 A 方案）

**第三批（有空再说）**
8. P1-7 代码组 / 语言徽标
9. P2-10 View Transitions
10. P2-11 渲染器拆分（做 P0-3 时顺手做）

---

## 九、验收方式

每项改动都可以落到「能在本地/线上验证的具体现象」上，避免"改完感觉好了"：

| 项 | 怎么验证 |
|---|---|
| P0-1 | DevTools Network 看首屏图片总字节数（改前 vs 改后），`content-type` 应变成 `image/webp` |
| P0-2 | 造一篇带 6 列表格 + 长公式的文章，375px 宽度下不出现横向溢出（`document.documentElement.scrollWidth === 375`） |
| P0-3 | 造一篇含 ```java 代码块里 `# 注释`、两个同名「总结」小节的草稿，检查目录条目数与跳转 |
| P0-4 | 设置「隐藏壁纸」后硬刷新（Ctrl+Shift+R），截图首帧，不应出现壁纸 |
| P1-5 | 编辑器里输入 `> [!NOTE]`，实时预览即应渲染成提示块 |

---

## 十、一句话总结

Mizuki 的强项是**编译期把内容做到极致的静态站点**：图片、代码、公式、表格全部在构建时就处理好，
浏览器只负责排版。我们是**运行时渲染 + 后端驱动**，拿不到这份红利，
但可以把它那套「正文体验」的做法**平移到 markdown-it 上**（P0-2、P0-3、P1-5、P1-6 都是这个思路），
再把图片交给 OSS 处理（P0-1）——这五项做完，阅读体验的差距基本就抹平了，剩下的都是风格选择。

---

## 十一、落地记录（2026-11-06）

四个 P0 已全部实施，未动后端。

| 项 | 落地方式 | 改动文件 |
|---|---|---|
| P0-1 图片 | 新增 `ossImage()`，按显示尺寸拼 `x-oss-process`；正文图在渲染阶段改写 URL（SSR 即输出处理后的地址，不产生二次下载） | `utils/image.ts`（新增）、`ArticleCard`、`ArticleDetail`、`Header`、`index`、`about`、`profile`、`MediaLibraryDialog`、`ArticleEditor`、`WallpaperLayer` |
| P0-2 破栏 | `--reading-width: 44rem` 约束正文，表格/代码/公式/mermaid 破栏；表格包 `.table-wrap` 并给 `min-width: 560px` | `assets/css/main.css`、`utils/markdownCore.ts` |
| P0-3 目录 | markdown-it 实例整体抽到 `utils/markdownCore.ts`；标题 id 由去重 slugger 生成；目录改走 token 提取并按最小级别归一化 | `utils/markdownCore.ts`（新增）、`components/MarkdownRenderer.vue`、`components/TableOfContents.vue` |
| P0-4 首屏 | `app.head` 内联脚本在首次绘制前写 `html[data-wallpaper]`；壁纸几何/蒙层/正文上边距全部改为 CSS 由该属性驱动 | `nuxt.config.ts`、`layouts/default.vue`、`components/WallpaperLayer.vue`、`composables/useWallpaper.ts`、`assets/css/main.css` |

**实测**（Chrome headless 注入读数）：

- 排版：1440 宽正文 704px、表格 1065px（破栏生效）；390 宽表格 560px 溢出进滚动容器、页面无横向滚动
- 图片：首页 15 张封面全部走 `w_640` 的 WebP，原图直出 0 张；单张实测 341KB → 8KB
- 目录：`总结` 与 `总结-1` 各自独立；代码块内以 `#` 开头的注释未进入目录；从 `##` 写起的文章缩进正确

**顺手清掉的旧主题残留**：行内代码从 rose 玫红改为暖纸底 + 陶土字；文章标题的蓝色 `.neon-glow` 移除。

**未做 / 待办**：P1-7（代码组 Tab / 语言徽标 / 行号开关）与 P2 全部保持原样。
P1-5、P1-6、P1-8 的落地见下一节。

---

## 十二、落地记录（第二批：P1-5 / P1-6 / P1-8）

### P1-5 提示块（Admonition）

在 `markdownCore.ts` 里加了一个 markdown-it core 规则，把 `> [!NOTE]` 这类引用改写成 `<div class="admonition">`：

- 五种类型 `NOTE / TIP / IMPORTANT / WARNING / CAUTION`，同时认中文别名（注意/说明、提示/技巧、重要、警告、小心/危险）
- 标题由 CSS `::before` 从 `data-title` 取，不用额外节点；没有正文内容时不会留下空 `<p>`
- **普通引用块完全不受影响**：只有 `>` 后紧跟 `[!TYPE]` 才改写

### P1-6 连续图片自动成组

同样是 core 规则：连续 2 段「只含图片」的段落合并成 `.img-grid`，`auto-fit minmax(200px, 1fr)` + 固定 4:3 裁切。
单张图片不合并；网格在阅读栏内正常排布，不破栏。

### P1-8 搜索

分两半，**后端一半需要重新部署 jar**：

| 位置 | 改动 |
|---|---|
| `ArticleService.page()` | 关键词从「只 LIKE 标题」扩到 **标题 + 摘要 + 标签名 + 分类名**；标签/分类先查 id 再以 `OR ... IN (...)` 并入，限长 500；整组用 `and(...)` 包起来（否则 OR 会短路掉 `status=1`/`is_deleted=0`，草稿会漏到前台） |
| `Header.vue` | 搜索历史下拉（localStorage，最多 8 条，可单条删除/清空）；↑↓ 选择、Enter 搜索、Esc 关闭、点空白收起；输入法 `isComposing` 保护（用 Enter 确认候选词不会误触发搜索）；搜索词与地址栏双向同步 |
| `ArticleCard.vue` | 新增 `keyword` 属性，标题/摘要命中处加 `<mark class="search-hit">`；走 v-html 前**无条件转义** |
| `index.vue` | 搜索结果标题旁显示命中总数；空结果提示里说明搜索范围（正文不参与匹配） |

**为什么高亮要单独写 `utils/highlight.ts`**：先把整段转义再正则替换，含 `&` 的关键词会变成 `&amp;` 而永远匹配不上；
先替换再转义又会把 `<mark>` 一起转掉。正确顺序是「按命中位置切片 → 逐片转义 → 命中片单独包 mark」。

**实测**：

- 后端：49 个测试全绿（新增 2 个断言 SQL 结构：`summary`/`category_id`/`OR id IN` 都在，且关键词分组之前已有 `status`、`is_deleted`）
- 前端：`check-search.mjs` 46 项全绿；无头浏览器实测 —— 列表卡与网格卡的标题/摘要高亮正确且保留原文大小写，
  标题里的 `<img src=x onerror=alert(1)>` 被转义成纯文本（DOM 中真实 `<img>` 标签数 = 0），
  历史面板渲染出 3 条并可用 ↓ 选中第一条

**已知边界**：正文（`content_md` / `content_html`）刻意不进 LIKE —— 那是 LONGTEXT，`%kw%` 全表扫成本太高，
真要全文检索得上 MySQL 全文索引或独立的检索服务（等同引入新组件，不在本次范围）。

---

## 十三、落地记录（第三批：P1-7 代码块三件小事）

### 先说一个顺手挖出来的旧 bug（与 P0-2 有关）

markdown-it 的默认 fence 规则会检查 `highlight()` 的返回值**是不是以 `<pre` 开头**，不是就自动再包一层
`<pre><code class="language-x">`。我们的 `highlight()` 返回的是 `<div class="code-wrapper">`，
于是每个代码块外面都多套了一个 `<pre>`。后果有两个：

1. 多出 `<pre>` 的默认外边距，代码块上下间距一直是设计值的两倍；
2. **P0-2 里「代码破栏」实际上是失效的** —— `.article-content > .code-wrapper` 永远匹配不上，
   因为 `.code-wrapper` 变成了 `<pre>` 的孙子，代码块一直被压在 44rem 的阅读栏里。

修法：自己写 `md.renderer.rules.fence`，直接返回 `highlight()` 的结果，不让框架补壳。
修完代码块宽度从 704px 变成 1392px（与卡片同宽），和表格、公式一致。

> 这条也解释了「为什么当时只验证了表格没验证代码」—— 表格是 `renderer.rules.table_open` 直接输出的 div，
> 本来就是 `.article-content` 的直接子元素，所以破栏生效。教训：验证破栏要逐个元素量宽度，不能只看一个代表。

### 三件事

| 项 | 语法 | 实现 |
|---|---|---|
| 代码组 Tab | 连续两个以上 ```java tab="后端 Java" | `core.ruler` 把连续的、都带 `tab=` 的 fence 标记成一组，`fence` 规则输出 `.code-group` + tab 按钮；切换在 `MarkdownRenderer` 的 enhance 里（切 `is-active`，显隐交给 CSS） |
| 语言徽标 | 自动 | 左上角从「一整块文字」改成 `[JAVA] 文件名` 两段：语言走 uppercase 小色块（暖灰底 + 主题色描边），文件名走等宽字体并允许省略 |
| 行号开关 | 工具条「行号」按钮 | **默认关闭**：原来 `padding-left: 3.5rem` 常年给行号留槽，短代码块左边空一条。现在默认 1.25rem，打开后加 `.show-line-no` 同时扩左边距，数字才压不到代码上 |

**和原建议的一处偏差**：建议里写的是「默认关闭、hover 出现」，我改成了**显式开关**。
hover 显示行号要么常年占着左边距（等于没关），要么一 hover 就横向抖动；显式开关两个问题都没有，
而且「按行讨论」本来就是偶发需求，不值得为它牺牲常态排版。

### 顺手清掉的颜色残留

- 行高亮 `{3-5}` 的蓝色 `#3b82f6` → 主题色（左色条 `var(--primary)` + 主题色淡底）
- 代码块工具条的激活态 `#2563eb`（蓝）→ `var(--primary)`；底色从冷灰 `#374151` 换成暖墨
- `.gradient-text`（后台「文章管理」标题）的蓝→紫→粉渐变 → 主题色深→浅渐变

### 实测

| 项 | 结果 |
|---|---|
| 代码块宽度 | 1440 宽：正文 704px、代码块 1392px（与卡片同宽，破栏生效），页面无横向溢出（docW = winW = 1424） |
| 代码组 | 两个 `tab=` 代码块 → 2 个 tab；点第二个 tab 后 `tab2Active=true`、`panel2Visible=true`、`panel1Visible=false` |
| 单块 `tab=` | 不成组，按普通代码块渲染 |
| 语言徽标 | ```` ```js title="server.js" ```` → `[js] server.js`；无 title 时只有 `[js]`；无语言时不出徽标 |
| 行号 | 默认 `lineNoDisplay=none`；点「行号」后 `lineNoDisplay=inline` 且块上有 `show-line-no` |
| 转义 | `tab="<img src=x onerror=alert(1)>"` 在按钮里是转义文本，DOM 中真实 `<img>` 数 = 0 |
| 自检 | `check-code-block.mjs` 重写后 59 项全绿；其余 9 个 check-* 全绿 |

**顺带修的工具债**：`check-code-block.mjs` 原来把 `highlight()` 的逻辑**复制了一份**再断言
（等于只证明"抄得对"，真实实现改了它照样全绿）。现在改成用 esbuild 编 `utils/markdownCore.ts` 后直接调
`renderMarkdown`，测的是真实实现。

**边界**：鸿蒙 App 用的是它自己那份精简渲染器（`.renderer-entry.js` → `rawfile/markdown/renderer.js`，
只有 pre/code，没有 code-wrapper），所以这些新特性不会自动出现在 App 里，也不会因为这次改动而坏掉。

---

## 十四、落地记录（第四批：P2-9 公开页缓存）

### 为什么值得做

打开一篇文章，SSR 要串 **5 个接口**（详情、上下篇、相关推荐、分类列表、站点配置）。这些内容对同一个
URL 来说是**完全一样**的，但每个访客都要把 5 次 HTTP + 一次 markdown 渲染重跑一遍。
给公开页加一个**很短的**缓存窗口，就能把「N 次渲染」压成「每个窗口 1 次」。

### 用的是 Nitro 内建缓存，不是 CDN

```ts
'/post/**': { cache: { maxAge: 10, swr: true, varies: ['cookie'] } }
```

- `cache` → Nitro 自己维护一个内存 LRU，命中时**直接返回存好的整页响应**，连 SSR 都不启动；
- `swr: true` → 过期后**先返回旧页**（`stale-while-revalidate`），后台再渲染新页。
  所以访客的等待时间永远是「读内存」，不会出现「刚好过期那一刻有人等 800ms」；
- `varies: ['cookie']` → 把 Cookie 头哈希进缓存 key。**这条不能省**，见下一节。

窗口值按「这个页面多久不变值得在意」来定，**没有一处超过 5 分钟**：
详情 10s / 首页 20s / 标签·分类·归档 30s / 标签墙·分类墙 60s / 关于 120s / 隐私政策 300s。
后台、个人中心、登录、OAuth、AI 一律**不写规则**（没有规则 = 不缓存）。

### ⭐ 坑一：不按 cookie 分桶会把登录态发给所有人

顶部导航是按登录态渲染的（读 token cookie）。如果所有访客共用一份 HTML，
**匿名访客会看到某个人「已登录 + 他的头像」**，反过来的话登录用户会看不到自己的入口。

`varies: ['cookie']` 让 Nitro 按 Cookie 头的 hash 分桶：没有 token cookie 的匿名访客天然归到同一桶
（这是收益来源），登录用户按各自的 token 各占一桶（不共享，但也只影响他自己）。
实测：4 次请求（匿名 ×2、登录 ×2）→ 后端只被调 2 次。

### ⭐ 坑二：缓存之后阅读数不再自动增长

原来 `viewCount + 1` 是**在服务端的 GET 详情里做的**。缓存生效后，窗口内的访问根本不进服务端，
阅读数就停了 —— 一个热门文章在 10s 窗口里被打开 100 次，只 +1。

改法是把「计数」和「渲染」拆开：

| 谁请求 | 行为 |
|---|---|
| Nuxt SSR 去取详情 | 带 `X-Blog-Client: ssr` 头，后端**只读不计数** |
| 浏览器打开页面后 | `POST /api/article/{id}/view` 打一次点，返回最新数字并覆盖显示 |

后端对应改动：

- `ArticleService.getPublicById(id, boolean countView)` —— 拆出 `loadPublicArticle` / `applyViewCount` /
  `currentViewCount` / `incrementViewCount`，读路径与写路径彻底分离；
- `ArticleController` 两个详情接口收 `X-Blog-Client`，等于 `ssr` 就不计数；
- 新增 `POST /api/article/{id}/view`（匿名可调，返回 `Result<Integer>` 最新总数）；
- `SecurityConfig` 里这条规则必须排在 `/api/**` `authenticated()` **之前**，否则匿名访客 403。

前端 `composables/useApi.ts` 在 server 端统一给请求加 `X-Blog-Client: ssr`，
`ArticleDetail.vue` 用 `watch(articleId, …, { immediate: true })` 在客户端打点，
`viewCountLive` 初始值取 `props.article.viewCount`（SSR 已经给了正确的基数），
拿到响应后直接覆盖 —— 不需要本地 +1 再抖一下。

> 为什么不用「前端也 +1」：那样会和服务端算出来的值打架。让服务端返回**权威数字**，前端只负责显示。

### 实测（mock 后端 + 无头 Chrome）

| 项 | 结果 |
|---|---|
| 缓存命中 | 连续请求，后端调用计数不涨；响应头 `cache-control: s-maxage=N, stale-while-revalidate` |
| SSR 不计数 | 后端收到的详情请求带 `x-blog-client: ssr` |
| 打点生效 | 页面加载后发出 `POST /api/article/1/view`，页面显示 43（后端基数 42） |
| 登录态分桶 | 匿名 ×2 + 登录 ×2 → 后端只被调 2 次（匿名共用、登录各自） |
| 自检 | `check-cache.mjs` 36 项全绿；后端 53 个测试全绿（新增 4 个） |

### 边界与代价（要如实告诉用户的）

- **窗口内后台发文不会立刻可见**：最多等一个窗口（详情页 10s）。这是「延迟越低越好」的代价上限。
- 缓存是**单进程内存**的。以后 `blog-web` 若上 PM2 cluster / 多实例，各进程各存一份，
  命中率按实例数摊薄（不会出错，只是收益变小）。真要跨实例，得换 Redis driver 或上 CDN。
- 重启 `blog-web` 缓存清空，冷启动后第一波请求照旧回源。
- **部署必须两半一起上**：前端重新 `build`（`routeRules` 是构建期产物）+ 后端换 jar
  （新增 `/view` 接口）。只上前端的话打点会 404，阅读数在缓存窗口内完全不动。

---

## 十五、落地记录（第五批：P2-10 主题切换圆形扩散）

### 原来卡在哪

改主题 = 给 `<html>` 换一个 `.dark` 类。这一下会让浏览器**整页重新计算样式并重绘**：
长文页（几百个代码高亮 span、表格、图片）能明显看到一帧「跳」过去，观感是「闪一下」。

### View Transitions 干的事

```
点开关 → 浏览器给当前界面拍一张快照 → 执行我们的回调（换类）→ 再拍一张 → 在两张之间做动画
```

**关键点：动画跑在快照上，不是跑在真实 DOM 上。** 所以就算换类的重绘很慢，用户看到的也是
两张图片之间的平滑动画，而不是中间那一帧的抖动。

### 实现

`blog-web/utils/viewTransition.ts`（103 行，含注释；真正的逻辑 30 行左右）

| 函数 | 作用 |
|---|---|
| `supportsViewTransition()` | 判断浏览器有没有 `startViewTransition` |
| `prefersReducedMotion()` | 读 `(prefers-reduced-motion: reduce)` |
| `originFromElement(el)` | **从开关元素的矩形**算圆心（不是鼠标坐标） |
| `revealRadius(origin, w, h)` | 圆心到「最远那个视口角」的距离，保证圆能盖满 |
| `withViewTransition(origin, update)` | 写 `--vt-*` 变量 → 启动过渡 → 等回调 resolve → 返回 `finished` |

**为什么圆心取元素而不是鼠标**：键盘（Tab + Enter/空格）和读屏触发时没有可信的鼠标坐标；
`click` 事件的 `currentTarget` 两种情况都拿得到，元素矩形也总有。

**半径为什么不用 150vmax**：那样也能盖满，但圆从按钮长出来会在前半段「慢得看不出在扩散」。
按到最远角的距离算，视觉速度线性得多。

### ⭐ 踩到的坑：等不起这一拍，动画就等于没放

`@nuxtjs/color-mode` 用的是 Vue 的 `watch`（**pre-flush**），改 `colorMode.preference` 之后
class 要到**下一次 tick** 才真正落到 `<html>` 上。而 `startViewTransition` 的回调一 resolve 就立刻拍「新快照」。

所以写成：

```ts
withViewTransition(originFromElement(el), async () => {
  colorMode.preference = next
  await nextTick()   // 等 class 落到 <html>
  await nextTick()   // 再等一拍，稳妥
})
```

少这两个 `await`，拍到的「新快照」还是旧主题 —— 新旧两帧一模一样，动画 420ms 里什么都不会动，
表现是「点了开关，页面愣了一下才变」。**这个 bug 在静态检查里完全看不出来**，只能在浏览器里
读「快照时刻的 `<html>` class」才能抓到（见下面的验证方式）。

### CSS

```css
::view-transition-old(root), ::view-transition-new(root) { animation: none; mix-blend-mode: normal; }
::view-transition-old(root) { z-index: 1; }
::view-transition-new(root) { z-index: 2; animation: theme-reveal .42s cubic-bezier(.4,0,.2,1); }
@keyframes theme-reveal {
  from { clip-path: circle(0 at var(--vt-cx, 50%) var(--vt-cy, 50%)); }
  to   { clip-path: circle(var(--vt-r, 150vmax) at var(--vt-cx, 50%) var(--vt-cy, 50%)); }
}
```

- 两张快照都关掉默认的交叉淡入（`animation: none`），只让新帧用自己的动画，否则会「边淡入边扩散」，糊；
- `mix-blend-mode: normal` 是必须的 —— 默认会给新帧 `plus-lighter`，深色叠浅色会发灰；
- 变量有 `50% / 150vmax` 兜底，万一 JS 没写上变量，圆也会从正中正常播完。

### 与 `pageTransition` 会不会打架

不会。`pageTransition`（`.page-enter` → `fadeInUp`）只在**路由切换**时由 Vue 加类，
主题切换不触发路由，两者时间上不重叠。实测点开关前后页面没有额外的位移或淡入。

### 实测

| 层 | 结果 |
|---|---|
| 静态自检 `check-view-transition.mjs` | **38 项全绿**：圆心/半径数学、四类降级（不支持 / reduced-motion / 无 window / 启动抛错）、变量写入时机、Header 接线、CSS 规则 |
| 真实浏览器（CDP + headless Chrome 156） | **20 项全绿**：点一次只启动一次过渡、**快照时刻 `<html>` 已是 `dark`**、圆心与开关中心偏差 ≤1.5px、半径 ≥ 最远角距离、新帧动画名 = `theme-reveal`、结束后不回滚、`aria-checked` 同步、localStorage 落盘、无横向溢出 |
| 减少动态效果 | 模拟 `prefers-reduced-motion: reduce` → **不启动过渡**，但主题照常切换 |
| 键盘/程序触发 | `dispatchEvent(click)` 也能启动，圆心仍在开关上 |
| 中间帧截图 | 120ms 处截图：圆内已深色、圆外仍浅色，圆心正落在开关上，扩散方向正确 |

### 边界

- Safari 18 以下、部分 Firefox 版本没有这个 API → 静默退回「直接换类」，用户只是看不到动画，不会坏；
- 过渡进行中（420ms）浏览器会暂时吞掉输入，这是 API 行为，不是我们的 bug；连点两下也只会有一层过渡
  （`startViewTransition` 会抛错，我们 `catch` 后直接切，且 `done` 标志保证主题不会被切两次）；
- 只用了 `root` 一个 transition name，没有做「开关滑块也跟着动」那种细粒度动画 —— 那需要给元素
  `view-transition-name`，收益远小于维护成本。

---

## 十六、P2-11 渲染器插件化：只做了有用的那一半

原建议是拆成 `utils/markdown/` 目录（`index.ts` + `plugins/*.ts` + `enhance/`），理由写得很明确：

> **这不是为了好看，是因为 P0-3 要求「TOC 和正文共用 slug」——不拆开，共享逻辑没地方放。**

这个**目的**已经在 P0-3 顺带完成了，而且比原方案更直接：

| 原计划 | 实际做法 | 效果 |
|---|---|---|
| `utils/markdown/index.ts` 导出单例 md | `utils/markdownCore.ts` 导出 `renderMarkdown()` / `extractHeadings()` / `createSlugger()` / `plainHeadingText()` | 服务端和客户端共用**同一个模块、同一套规则**，不会再出现两套 slug |
| `plugins/admonition.ts`、`plugins/table.ts` …… | `admonitionPlugin` / `imageGridPlugin` / `codeGroupPlugin` 三个真插件（`md.core.ruler.push`）已经各自独立成函数 | 语法扩展彼此解耦，加新语法只加一个函数 + 一行 `.use()` |
| `enhance/` 放 DOM 增强 | 留在 `MarkdownRenderer.vue`（灯箱、代码工具条、tab 切换） | 这些增强**依赖组件生命周期与客户端事件**，放 `utils/` 反而要额外传 DOM 根节点，不如留在组件里 |

**没有继续拆目录是刻意的决定**，理由有三条：

1. 剩下的「未拆」部分全是 `md.renderer.rules.xxx = …` 这种单行规则覆盖（blockquote、table、image、
   paragraph），把它们搬到 `plugins/` 只是换个文件位置，**行为零变化、测试覆盖也不变**；
2. 这个文件现在 521 行、被 59 项自检覆盖、且**鸿蒙 App 有一份等价副本**
   （`renderer.js`）。再动结构就得同步改两份，收益为负；
3. 真正的痛点（TOC 和正文 slug 不一致、代码块注释被当标题、同名小节跳错位置）已经全部修完，
   现在**没有「共享逻辑没地方放」的压力**。

**结论**：P2-11 按「目的达成」关闭，不为了目录好看去动一个已稳定的模块。

---

## 十七、体验微调（2026-11-07：删季节特效 + 顶栏图标统一）

### 1. 删掉季节飘落特效

`FestivalEffect.vue` 会按月份在**全站**飘 emoji：12–2 月 ❄️、3–5 月 🌸、9–11 月 🍂（6–8 月自动关闭）。
它对阅读没有任何帮助，还会挡视线、在长文页持续触发合成层重绘。

处理：`layouts/default.vue` 和 `layouts/admin.vue` 两处引用都去掉，组件文件删除。
**注意是全站移除，不只首页** —— 因为特效挂在 layout 上，后台也在飘。

> 这条其实早就在本文档「明确不建议照搬」清单里（樱花/落雪特效），只是那说的是 Mizuki 的，
> 我们自己另写了一个同款的没意识到。现在两边都干净了。

### 2. 顶栏图标：字符/手写 path → 统一线条图标

| 位置 | 改前 | 改后 |
|---|---|---|
| 明暗切换 | **没有**（埋在设置面板里） | 太阳/月亮图标，**点一下直接切**（也走 P2-10 的圆形扩散，圆心取该按钮） |
| 外观设置 | 手写 path 画的调色板，形状歪（截图里像个不规则圆圈带两个点） | 标准 Lucide `palette`：1 条轮廓 + 3 个实心色点 |
| 移动端菜单 | `☰` / `✕` 字符 | Lucide `menu` / `x`，与左侧图标同粗细 |

统一参数：`stroke-width="1.6"`、`stroke="currentColor"`，所以深浅色自动跟随，不需要两套颜色。
图标语义按「点了会变成什么」：浅色时显示月亮、深色时显示太阳，`aria-label` 同步跟着变。

**顺带解决了「未登录访客不知道去哪切主题」**：原来要猜那个齿轮/调色板是设置，现在顶栏直接有明暗按钮，
设置面板里的开关保留（两个入口都能用）。

### 验证

`dbg-shot.mjs`（CDP + headless Chrome）**15 项全绿**：页面无季节 emoji、无飘落覆盖层、
顶栏三个按钮都是 SVG、调色板是 4 个图形元素、点明暗按钮启动过渡且圆心在顶栏（`--vt-cy < 64`）、
切换后图标与 `aria-label` 同步、再点回浅色、设置面板仍能打开且壁纸 4 模式都在。
11 个 check-* 全绿（`.vue` 文件数 47 → 46，因为删了特效组件）。

---

## 十八、外观自定义（2026-11-07 第二批：色相滑块 + 波浪动画 + 图标统一）

用户对着 Mizuki 的设置面板截图问「这些为什么没做」，并明确要求补三样：**主题色相滑块**、
**波浪动画开关**、**图标全换线条风**。前两样直接照 Mizuki 的实现抄。

### 1. 主题色相滑块（Theme Color）

**这是把上一版的结论推翻了。** 之前设置面板里原本有色相滑块，后来被移除（代码注释写着
「主图背景：壁纸显示方式（替代原来的主题色滑块）」），理由是「暖纸+墨色+单一陶土色」的规范。
这次用户明确要，就加回来 —— 但**只放开色相**：

| 变量 | 默认 | 用户能否改 | 为什么 |
|---|---|---|---|
| `--ph` 色相 | 22（陶土） | ✅ 0–360，step 5 | 这是「换一种强调色」的诉求，本身不破坏层次 |
| `--ps` 饱和 | 44% | ❌ 锁死 | 放开就能调出高饱和亮色，全站立刻变味 |
| `--pl` 亮度 | 46% | ❌ 锁死 | 同上；而且暗色模式下亮度要单独适配，不是一个滑块能解决的 |

实现照抄 Mizuki 的思路（`localStorage` 存 `hue` + 写 `--hue` 变量），我们这边是 `--ph`：

- `composables/useThemeColor.ts`：`parseHue`（非法值一律 null）/ `clampHue` / `setHue` / `resetHue` / `hydrate`
- 滑块：`input[type=range]` min 0 max 360 step 5，彩虹渐变轨道 + 白色小竖条（thumb 宽 0.45rem、高 1.05rem）
- 数值徽章（`22` / `200`）+ **重置按钮只在非默认色时出现**（默认色时隐藏，和 Mizuki 一致）
- 面板里加了一行说明：「只改变强调色（按钮、链接、标签）。纸面底色与正文墨色不动。」——
  避免用户拖完发现背景没变而以为坏了

### ⭐ 首屏防跳色：必须走内联脚本

色相存在 `localStorage`，服务端读不到。不处理的话流程是：SSR 按 22 画一帧 → hydrate 读到 200 → 跳一下。

沿用壁纸那套已有做法，在 `nuxt.config.ts` 的 `app.head.script` 里**再加一段**（不是改原来那段，
避免动到已经在工作的代码）：

```js
var h=parseInt(localStorage.getItem('blog-hue')||'',10);
if(h>=0&&h<=360){document.documentElement.style.setProperty('--ph',String(h))}
```

**只认 0–360 的整数**：脏数据（NaN、`12px`、-5、400）一律不写，让 CSS 里的默认 22 兜底。
实测刷新后首屏 `--ph` 直接就是 280，没有中间态。

### 2. 波浪动画（Banner Options → Waves Animation）

照抄 Mizuki 的 `gentle-wave`：一条 path 用四个 `<use>` 叠起来，`opacity` 0.25 → 1、
时长 7s → 20s 递减且都带负延迟，慢的那几层被快的那几层拖着走，就有「水深浅不同」的视差。

```html
<svg viewBox="0 20 150 32" preserveAspectRatio="none">
  <use href="#blog-gentle-wave" y="0" class="wave-1"/>  <!-- 0.25 / 7s -->
  ... y=3 / y=5 / y=7（0.5/10s、0.75/13s、1/20s）
</svg>
```

三个关键点：

1. **`fill` 必须是页面底色**。为了实现这个，把纸色抽成 `--page-bg`（浅色 `hsl(38 30% 94%)`、
   深色 `hsl(28 10% 8%)`），`body`、横幅底部的渐变蒙层、波浪三处**共用同一个变量** ——
   否则波浪会像贴了一条灰带子，而不是「图片被纸面吃掉」。
2. **只在「横幅 + 有图」时挂载**（`v-if="wave && mode === 'banner'"`）。full 是固定满屏、cover 是整页重复，
   波浪会飘在正文中间，很怪。设置面板里对其他模式会显示「（仅横幅模式可见）」。
3. **默认开**（Mizuki 也是默认开）。存 `'1'/'0'`，只有明确存了 `'0'` 才算关 ——
   这样老用户没存过值时不会突然多出一条波浪以外的意外行为。

### 3. 图标全部改成线条风

一次清掉所有 emoji / 几何字符图标，统一参数 `stroke-width 1.6`（个别 1.8）+ `stroke="currentColor"`：

| 位置 | 改前 | 改后 |
|---|---|---|
| 搜索框 | 已有 SVG，但描边 2 | 统一 1.6 |
| 写文章 | `✏️` | 铅笔 |
| 个人主页 / 管理后台 / 退出 | `👤` `📋` `🚪` | user / grid / log-out |
| 设置面板标题 | `⚙️ 设置` | 调色板 + 「外观设置」 |
| 面板：主题色 / 主图 / 布局 / 排序 | `🎨`(无) `🖼️` `📄` `🔀` | droplet / image / layout / arrow-up-down |
| 壁纸四模式 | 几何字符 `▭ ▣ ▤ ▢` | 四个 path 图形 |
| 移动端汉堡 | `☰` / `✕` 字符 | menu / x SVG（字形在不同系统差异大） |
| 搜索历史删除 | `✕` | 小 x SVG |
| 返回顶部 | `↑` | arrow-up SVG |

顺带修掉一处**不一致**：搜索图标、历史时钟、下拉箭头原本是 `stroke-width="2"`，和新图标并排明显更粗，
一并改成 1.6。

### 4. 去重：一个功能只留一个入口

用户的判断是对的：这几处完全重复。

- ❌ 头像下拉里的「设置」→ 删掉，**只留顶栏调色板按钮**（未登录时也能用）
- ❌ 设置面板里的「夜间模式」→ 删掉，**只留顶栏太阳/月亮**（上一次刚加的，还带扩散动画）
- ✅ 现在：明暗 = 顶栏按钮；其它外观项 = 调色板面板。两个入口，零重叠。

### 验证

| 层 | 结果 |
|---|---|
| `check-customize.mjs`（新增，静态） | **51 项全绿**：色相边界（0/360/脏数据/越界）、饱和亮度未被带动、内联脚本只认 0–360、滑块范围与 step、波浪四层参数、`fill` 用 `--page-bg`、只在横幅挂载、入口不重复、无 emoji 残留、描边统一 |
| `dbg-customize.mjs`（新增，CDP 真实浏览器） | **33 项全绿**：波浪贴壁纸底边 ±2px、关开关后消失且落盘 `'0'`、切全屏自动不显示、拖到 200 后 `--ph`/强调色/localStorage 同步、重置按钮出现与隐藏、**刷新后首屏就是 280（不跳色）**、面板里只剩一个 `role=switch` |
| 其余 11 个 check-* | 全绿（`.vue` 47 个） |

**验证方法上的一个坑**：检查「波浪是否存在」必须有壁纸图（`bgImage`），本地后端返回空 `{}` 时根本不会渲染。
做法是临时放一张 `public/__test-bg.svg` + 把 `setImage(... || '')` 改成指向它，验完**立刻还原并用 grep 复核**
（`grep setImage layouts/default.vue` 必须回到 `|| ''`）。这是唯一能真实验证壁纸相关特性的办法。

### 生产构建复核（2026-11-07）

dev 包和生产包是两套打包结果（压缩、tree-shake、CSS 抽包都不同），所以改完必须**在生产产物上再验一次**。
这次的做法：

1. 停 dev → 删 `.output`/`.nuxt` → `CI=1 NUXT_TELEMETRY_DISABLED=1 nuxt build`（55s，4.65MB / gzip 1.16MB）
2. **产物级 grep**：`blog-gentle-wave`、`wave-drift`、`hue-slider`、`blog-hue` 均能在
   `server/chunks/build/*` 与 `public/_nuxt/*.css|*.js` 里找到；`FestivalEffect` **零命中**
3. 起 `node .output/server/index.mjs`（PORT=3101）→ 首页 200，`cache-control: s-maxage=20` 说明 routeRules 仍生效，
   `/_nuxt/@vite/client` 返回 404 确认是生产而非 dev
4. 新脚本 `dbg-prod-customize.mjs`（18 项）：首屏 `--ph=22`、无壁纸不渲染波浪（符合 `v-if`）、
   滑块 0–360 与 `localStorage` 同步、关波浪落 `'0'`、**刷新后仍 180（内联脚本先行）**、`#__nuxt` hydration 正常

> 一个坑：`grep -c` 统计这种上 GB 单行 HTML 会因 NUL 字节被判成 binary，返回 0 或失真；
> 判断「字符串在不在」用 `node -e "s.indexOf(k)"` 更靠谱 —— 本次就是先被误判成「内联脚本没进去」。

---

## 十九、Banner Title + 低饱和主题色（2026-11-07 第三批）

### 1. Banner Title：把写死的横幅文案接回「站点设置」

和 Mizuki 面板逐项对齐后，只剩 **Banner Title** 与 **Wide Screen** 没做（其余 4 项已落地）。
其中 Banner Title 不只是「能不能改一行字」——首页那行是**硬编码在 `pages/index.vue`** 里的，
改字要改代码重新构建；而后台「站点设置」四个博客字段（`bloggerName` / `bloggerBio` /
`bloggerAvatar` / `siteDesc`）早就可编辑，却**一个都没被前台使用**（待办第 3 条）。

**做法**

- 新增 `composables/useSiteConfig.ts`：`useAsyncData(SITE_CONFIG_KEY, …)`。
  原来那个同名 composable 是 `useState` + 手动 `fetch()`，而且**没有任何调用方**（死代码）；
  它的问题是一处调用一次就会发一次请求。改成 `useAsyncData` 后按 key 去重，
  且状态**按请求隔离** —— 用模块级 Promise 做去重会在 SSR 并发时把 A 的配置返回给 B。
- `layouts/default.vue`（壁纸）与 `pages/index.vue`（横幅）共用这一次请求；
- 首页读 `bloggerName`（标题）、`bloggerBio` **第一行**（副标题，多行会把卡片撑高）、
  `bloggerAvatar`（头像，走 `ossImage` 压图）；SEO 的 title/description 读 `siteName`/`siteDesc`；
- 全部带兜底：取不到就回落到原来的「你好，我是好啵 / 记录我的计算机学习与技术实践」，
  后台漏配不会出现空白横幅。

**验证**：本地 `site-config/public` 返回 `{}`，直接跑看不到效果。为了不往源码塞测试文案
（塞了容易忘还原），临时起一个「只覆盖 `site-config`、其余转发真后端 8080」的代理放在 8081，
把 `nuxt.config` 的 proxy 指过去，SSR 断言：博主名 / 简介第一行 / 头像 / SEO 标题描述全部命中、
**简介第二行确实没进横幅**；验完立刻把 proxy 改回 8080 并 grep 复核。
另配 `check-banner.mjs`（21 项静态护栏），防止以后有人把文案又写死回去。

### 2. 主题色改成「低饱和 + 低明度」

用户反馈：不要高饱和高明度（很鲜艳），要柔和、带灰调/粉彩调。原来 `--ps: 44% / --pl: 46%`，
在绿、青几个色相上会直接变成荧光色。

- `--ps: 26%`、`--pl: 40%`、`--primary-dark: 33%`；
- **暗色单独覆盖** `--primary: hsl(var(--ph) var(--ps) 52%)` —— 40% 的陶土压在近黑卡片上
  对比只有 ~2.7:1，数字和链接会「发闷」，提到 52% 后约 4.2:1，饱和度仍是 26%，不会变回荧光色；
- 色相滑块的渐变条**必须换成同一套饱和/明度**（原来 56–72% 的荧光彩虹）：预览色和落地色不一致，
  用户会以为没生效；
- 因为 Tailwind 的 `primary-50…900` 全从 `--ps` 派生（见 `tailwind.config.ts`），
  改这两个变量就整站生效（链接、按钮、标签、目录高亮、Admonition 左边框），不用逐组件改。

**验证**：`dbg-theme-muted.mjs`（10 项）把色相拖到 22/90/150/210/270/330，逐个量浏览器算出来的
`--primary` / `--primary-dark`，确认饱和度都在 20–30%、明度在 34–46%；渐变条九个色标最高 26%。
亮/暗各截图确认观感。`check-customize.mjs` 同步把「44%/46%」的旧断言改成新基线（并新增
「暗色提一档」「渐变条全低饱和」两条），51 → 56 项。

### 3. Wide Screen 仍然不做

正文 `--reading-width: 44rem` 是刻意的（舒适行宽），宽表格/代码/公式才破栏；
再叠一个宽屏开关会和现有破栏规则打架，收益也低。留在「明确不建议照搬」清单里。

## 二十、导入 PDF（2026-11-07 第四批）

需求原文是「添加一个直接导入 PDF 文件的功能」。确认了两个点：**产出是文章正文（不是附件库）**、
**文字和图片都要**。最终做成后台编辑器工具栏的一个入口：`📄 导入 PDF` → 选文件 → 插到光标处。

### 1. 为什么放前端解析

后端解析要动 Java、重打 jar、再走一轮部署，风险翻倍；而 PDF 常带版权/隐私内容，不上传原文更稳妥
（只有**提取出来的图片**会走原有上传链进 OSS）。解析失败用户立刻看到原因，也不用去翻服务器日志。

### 2. 文字重建：一行对一行，只做三件确定的事

PDF 里没有「段落」，文字是按排版指令存的，同一个段落往往是一堆独立文本项，只有 `hasEOL` 能判断换行。
所以**不猜段落**，只做三件风险可控的事：

1. **字号明显大于正文的行 → `##` 小标题**。阈值取中位数 ×1.2（不是 1.35 —— 技术文档里 h2 常常
   14px 对 18px，只有 1.28 倍，1.35 会全部漏掉）；再配合「长度 ≤ 40 字」「不以。！？；，结尾」两条兜底，
   避免把正文里的大号数字误判成标题。
2. **行尾连字符断词拼回**：`informa-` + `tion` → `information`。
3. **清理 PDF 抽字带来的多余空格**：`第  一  章` → `第一章`，中文之间的空格直接去掉，英文之间保留一个。

### 3. 图片：要插回它原来的位置，不能堆到页尾

第一版把每页的图统一追加在页尾，结果「图在第二段后面」变成「图在全文最后」，导入后还得手动拖。
要拿到图片在版面上的位置，只能在遍历 `getOperatorList()` 时**维护 CTM 栈**：
`save`/`restore` 压栈出栈、`transform` 乘矩阵、进 `paintFormXObjectBegin` 时乘对象自己的矩阵
（Chrome 导出的 PDF 常把图画在 Form XObject 里，不乘这一下拿到的 y 接近 0，图就被判成「在所有文字下方」）。

⚠️ **矩阵乘法必须用 pdf.js 的约定**（`Util.transform(m1, m2)`，行向量）：
```
e' = m1[0]*m2[4] + m1[2]*m2[5] + m1[4]
```
写成常见的列向量约定，第一步（单位矩阵）看不出问题，但页面级翻转矩阵 `1 0 0 -1 0 792`
和图片自己的 `400 0 0 260 x y` 复合时，平移量会被乘成 **-641672** 这种量级 —— 表现就是
「文字全对、图片永远在页尾」，极难从现象反推。

跳过规则：边长 < 96px（项目符号/图标）、同页重复 objId（水印/页眉 logo）、`paintImageMaskXObject`
蒙版（导出来是黑块）、超过 30 张（防止扫描件把 OSS 和正文撑爆）。

### 4. ⚠️ 上传接口会「HTTP 200 + code≠200」，必须校验返回地址

联调时发现 `/api/upload` 在异常情况下会回 `{"code":200,"data":[]}`。
`uploadWithProgress` 判 `code === 200` 就 resolve，于是 `[]` 被拼成 `![]()` —— 正文里留下一张
没有地址的破图，作者完全看不出是哪一步坏的。所以 `importPdf` 的上传回调里加了一道校验：
**返回的必须是 `http(s)://` 开头的字符串，否则抛错**；`pdfToMarkdown` 把失败张数汇总成一条警告
（「N 张图片没能上传，已从正文跳过…」），而不是每张刷一条 toast。

### 5. 体积：pdfjs 不能进首屏包

`await import('~/utils/pdfImport')`（动态），`pdfjs-dist` 在里面再动态 import；
worker 用 `pdfjs-dist/build/pdf.worker.min.mjs?url` 交给打包器，不硬编码路径。
构建结果：编辑器主 chunk 668 kB、**pdf 单独一个 334 kB 的 chunk**，不点「导入 PDF」不会下载。
⚠️ 动态 import 的说明符必须是 `~/utils/pdfImport`：写裸路径 `/utils/pdfImport.ts` 在 dev 下 404
（Nuxt 只把它挂在 `/_nuxt/` 下），生产包里更没有这个文件。

### 6. 验证

- `dbg-pdf-import.mjs`（23 → 25 项）：用 Chrome `Page.printToPDF` 现场生成一份带中文/连字符/位图的真 PDF
  （⚠️ 只能用**位图**，SVG 会被 Chrome 烘成矢量路径，PDF 里根本没有图像 XObject），
  再分两层验：
  - **解析层**：`/_nuxt/utils/pdfImport.ts` 直接调用，上传函数换成假的，断言文字/标题/断词/图片/顺序/进度；
  - **集成层**：真开后台编辑器（`#__nuxt.__vue_app__` 里把 pinia 的 auth store 改成管理员态，
    再 `router.push` 客户端进页面 —— 整页刷新会被 `plugins/auth-init.ts` 拿假 token 调 `/auth/me`
    失败后 `logout()` 踢出去），用 `DOM.setFileInputFiles` 塞 PDF 进 file input，
    断言正文里出现 PDF 内容、且**上传失败时不会留下 `![]()`**。
- `check-pdf.mjs`（30 项静态护栏）：依赖与体积策略、行/字号成对收集、矩阵约定（连「别写成列向量」
  都单独一条）、CTM 栈、Form XObject、上传校验、交互细节、构建脚本。

### 7. 已知边界

- 扫描件没有文字层 → 仍然按图片导入，并提示「需要可编辑文字得先做 OCR」；
- 不识别表格结构（表格会被拆成一行一行的文字），复杂表格建议截图；
- 不还原加粗/斜体/链接；标题只在「字号明显更大」时识别，纯靠缩进/编号区分层级的文档会平铺；
- 单文件上限 40MB（前端拦），图片上限 30 张。

## 二十一、目录可收起 + PDF 导入卡死修复（2026-11-07 第五批）

### 1. PDF 导入卡在「提取图片」

线上反馈：导入某个 PDF 时停在「提取图片」不动。

**根因**：`page.objs.get(objId, cb)` 是「数据到了才回调」——图片字节由 worker 在需要时补发，
而软蒙版、被裁掉的图、损坏的资源可能**永远不会送过来**。没有超时，`await` 就永远挂着。
（本地测试用的 Chrome `printToPDF` 产物每张图都能正常回调，所以一直没暴露。）

**修法：所有 await 都加上限**，宁可少一张图，也不能让整篇导入死住：

| 环节 | 上限 | 超时后 |
|---|---|---|
| `getDocument` 打开 | 60s | 抛错「PDF 打开超时，文件可能已损坏或受保护」 |
| `getTextContent` 单页文字 | 20s | 跳过该页 + 警告，继续下一页 |
| `getOperatorList` 绘制指令 | 30s | 跳过该页图片 + 警告 |
| `objs.get` 单张图数据 | 5s | 跳过这张图（计入「数据没能读出来」） |
| 图片上传（XHR） | 60s | 计入「没能上传」，不拼空地址 |

再加两层止损：**同一页连着 3 张取不到就放弃该页剩余图片**（否则几十张 ×5s 就是几分钟）；
**整篇累计 5 张失败就停止后面所有页的图片提取**（只导文字）。

顺带把「扫描版 PDF 图片多」的进度做细：进度文案会显示「读取第 x/y 张图」，
不再是干站着的一行「提取图片」。

**验证**：`dbg-pdf-import.mjs` 新增「多页多图（防卡死）」——3 页 3 图，断言 90s 内结束、
三页三图全部导出（实测 146ms）；`check-pdf.mjs` 从 30 项加到 38 项，
把「每个 await 都有超时」「同页止损」「整篇止损」逐条锁住。

### 2. 左侧目录可收起（对标 DeepSeek 的「收起边栏」）

DeepSeek 顶栏那个「收起边栏」按钮的体验是：**想专注阅读时把侧栏收掉，需要时再点开**。
本站文章页左侧目录同理 —— 长文读者常把它当噪音，但不该逼他们去调浏览器缩放。

- 收起按钮放在目录标题「📑 目录」**同一行右侧**（不额外占一行高度），图标是内联线条 SVG；
- 收起后左侧留一条 **36×36 的窄轨**（`sticky top-20`），点一下展开 —— 比在顶栏另加一个按钮
  更贴近阅读动线，也不会和「外观设置」抢位置；
- 状态存 `localStorage['toc_collapsed']`，**默认展开**（老读者升级后不该突然找不到目录）；
  刷新后由 `onMounted` 读回来（SSR 只能渲染默认展开，所以会有一瞬间展开→收起，属预期）；
- 只在 `lg` 以上出现：移动端本来就没有左侧目录列，不给它加多余按钮；
- 顺手修了一个既有小问题：**没有小标题的文章不再渲染空的 56px 目录列**（改用
  `extractHeadings(...).length > 0` 判断，和目录组件同一套规则）。

**验证**：`dbg-toc-collapse.mjs`（14 项）——本地库是空的，所以临时起
`mock-article.mjs`（8081，只覆盖 `/api/article/1`，其余转发 8080）并临时把 `nuxt.config` 的 proxy 指过去，
验完立刻改回 8080 并用 grep 复核（这一步每次都不能省）。
断言覆盖：目录存在/列出小标题/按钮存在 → 点收起 → 列消失+窄条出现+localStorage=1 →
刷新后仍收起 → 点窄条展开 → 窄屏（420px）两者都不显示。

⚠️ 测试踩坑：**别在「文字出现」后就点按钮**。SSR HTML 里按钮就在，但点击监听要等水合完成才挂上，
点早了完全没反应（表现和「功能没写」一模一样）。脚本里改成「等 `#__nuxt.__vue_app__` + 点击重试」。

另配 `check-toc-collapse.mjs`（20 项静态护栏）。

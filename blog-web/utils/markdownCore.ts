import MarkdownIt from 'markdown-it'
// 仅引入常用语言子集（~300KB），体积远小于完整包；文章页必需，SSR 同样可用
import hljs from 'highlight.js/lib/common'
import markdownItFootnote from 'markdown-it-footnote'
import markdownItTaskLists from 'markdown-it-task-lists'
// LaTeX 数学公式（$...$ 行内 / $$...$$ 行间）。markdown-it 核心不认数学语法，
// 没有这个插件时 `$|f(x)|\le M$` 只会被当成普通文本原样输出。
import * as markdownItKatexModule from '@vscode/markdown-it-katex'
import { ossImage, IMG_W } from './image'

/**
 * 正文渲染的唯一实现（文章页、编辑器预览、目录共用）。
 *
 * 为什么要抽出来：此前「正文标题 id」由本文件的 heading_open 生成，而目录组件
 * 又用正则从原始 Markdown 里重新扫一遍标题 —— 两份实现必然分叉，实际已经出了三个 bug：
 *   1. 代码块里的 `# 注释` 被目录当成标题；
 *   2. 两处同名小节生成同一个 id，目录两条都跳到第一个；
 *   3. 文章从 `##` 写起时目录整体多缩进一级。
 * 现在 id 只由这里生成，目录改为复用同一套规则从 token 里取标题（见 extractHeadings）。
 */

export interface HeadingItem {
  id: string
  text: string
  /** 原始级别 1–6 */
  level: number
  /** 归一化后的层级：最小标题级别为 0（目录缩进用这个） */
  depth: number
}

/**
 * `@vscode/markdown-it-katex` 是**纯 CJS** 包（只有 main，没有 exports/module 字段），
 * 而模块内部又写了 `exports.default` + `__esModule`，于是「默认导出」到底是函数还是
 * 一层 `{ default: fn }` 包装，取决于由谁来加载：
 *   - Vite 打包（客户端）：识别 __esModule，直接给到函数；
 *   - Nitro SSR 把 node_modules 依赖外置、交给 Node 原生 ESM 加载：default 会变成包装对象。
 * 两种形态都剥一层，避免服务端 `md.use()` 抛 "plugin.apply is not a function" 直接 500。
 */
const markdownItKatex: any = ((): any => {
  const mod: any = markdownItKatexModule
  if (typeof mod === 'function') return mod
  if (typeof mod?.default === 'function') return mod.default
  return mod?.default?.default
})()

/** 超过这个行数的代码块默认折叠，右上角给「展开」按钮 */
export const CODE_FOLD_THRESHOLD = 30

/**
 * 标题 slug。保持与历史版本一致（小写、空白转连字符、只留中英文数字），
 * 这样已经有外链的 `#安装说明` 不会失效。
 */
function baseSlug(text: string): string {
  return text
    .toLowerCase()
    .trim()
    .replace(/\s+/g, '-')
    .replace(/[^\w\u4e00-\u9fff\-]/g, '')
    .replace(/-{2,}/g, '-')
}

/**
 * 带去重的 slug 生成器。
 * markdown-it 的 render 是同步的，每次渲染新建一个实例即可；
 * 用闭包而不是模块级变量，避免 SSR 并发渲染两篇文章时互相污染计数。
 */
export function createSlugger(): (text: string) => string {
  const used = new Map<string, number>()
  let fallback = 0
  return (text: string) => {
    let slug = baseSlug(text)
    if (!slug) {
      fallback += 1
      slug = `section-${fallback}`
    }
    const seen = used.get(slug) || 0
    used.set(slug, seen + 1)
    return seen === 0 ? slug : `${slug}-${seen}`
  }
}

/**
 * 把标题里的行内 Markdown 标记去掉，得到纯文本。
 * `## 用 **Redis** 做缓存` → `用 Redis 做缓存`
 */
export function plainHeadingText(raw: string): string {
  return (raw || '')
    .replace(/!?\[([^\]]*)\]\([^)]*\)/g, '$1') // 链接/图片取文字
    .replace(/`{1,3}([^`]*)`{1,3}/g, '$1')
    .replace(/[*_~]{1,3}/g, '')
    .replace(/<[^>]+>/g, '')
    .trim()
}

/**
 * 解析围栏代码块「语言名」之外的元信息。
 *
 * markdown-it 的 highlight(str, langName, langAttrs) 第 3 个参数就是 info 串里
 * 除语言名以外的剩余部分，常见写法：
 *   ```js title="server.js" {3-5,8}
 * 这里把它拆成「文件名」与「要高亮的行号集合」。
 */
function parseFenceMeta(attrs?: string) {
  const raw = attrs || ''
  const titleMatch = raw.match(/title\s*=\s*(?:"([^"]*)"|'([^']*)'|(\S+))/)
  const title = titleMatch ? (titleMatch[1] ?? titleMatch[2] ?? titleMatch[3] ?? '') : ''

  const highlightLines = new Set<number>()
  const rangeMatch = raw.match(/\{([^}]*)\}/)
  if (rangeMatch) {
    for (const part of rangeMatch[1].split(',')) {
      const p = part.trim()
      if (!p) continue
      const range = p.match(/^(\d+)\s*-\s*(\d+)$/)
      if (range) {
        const a = Number(range[1])
        const b = Number(range[2])
        const from = Math.min(a, b)
        const to = Math.max(a, b)
        // 上限保护：写成 {1-999999} 也不至于把行号集合撑爆
        for (let i = from; i <= to && i - from < 5000; i++) highlightLines.add(i)
      } else if (/^\d+$/.test(p)) {
        highlightLines.add(Number(p))
      }
    }
  }
  return { title, highlightLines }
}

/**
 * 围栏信息串里的 `tab="标签"`：用于把连续的多个代码块拼成可切换的代码组。
 *   ```java tab="后端 Java"
 * 引号可省；标签里允许有空格（后端 Java），所以按引号优先取值。
 */
function fenceTabLabel(info?: string): string {
  const m = (info || '').match(/(?:^|\s)tab\s*=\s*(?:"([^"]*)"|'([^']*)'|(\S+))/)
  return m ? (m[1] ?? m[2] ?? m[3] ?? '') : ''
}


const md = new MarkdownIt({
  // html: false —— 禁用正文中的裸 HTML，避免配合 v-html 造成存储型 XSS。
  // 代码块高亮、mermaid 等由下方 renderer 规则生成 HTML，不受此开关影响。
  html: false, linkify: true, typographer: true, breaks: true,
  highlight(str: string, lang: string, attrs?: string) {
    if (lang === 'mermaid') {
      return '<div class="mermaid-wrapper"><div class="mermaid">' + md.utils.escapeHtml(str) + '</div></div>'
    }
    const { title, highlightLines } = parseFenceMeta(attrs)
    // markdown-it 传入的代码块内容末尾自带一个 \n；highlight.js 的结果末尾也会补一个 \n。
    // 不剥离的话，代码块底部会多渲染一个空行，复制出来的代码末尾也会多出换行。
    const content = str.replace(/\n$/, '')
    const rawLines = content.split('\n')

    let highlighted: string | null = null
    if (lang && hljs.getLanguage(lang)) {
      try {
        const value = hljs.highlight(content, { language: lang, ignoreIllegals: true }).value.replace(/\n$/, '')
        // 行高亮要按行包 <span>，若着色结果行数与原文不一致（理论上不该发生），
        // 宁可退回纯文本，也不要错位标错行。
        if (value.split('\n').length === rawLines.length) highlighted = value
      } catch {}
    }

    // 空行不再塞 `&nbsp;` 占位：`.line` 的 min-height 已能撑出行高，
    // 保留占位符会让复制出来的空行里混入不换行空格（U+00A0）。
    const sourceLines = highlighted ? highlighted.split('\n') : rawLines.map((l) => md.utils.escapeHtml(l))
    const lines = sourceLines
      .map((l, i) => {
        const marked = highlightLines.has(i + 1) ? ' line-highlight' : ''
        return `<span class="line${marked}"><span class="line-no"></span>${l}</span>`
      })
      .join('')

    // 左上角徽标：语言名做成小色块（暖灰底 + 主题色描边），有 title 时后面跟文件名。
    // 语言名不转大写就显示 `Java`，但徽标统一 uppercase 更整齐（css 负责）。
    const langBadge = lang
      ? `<span class="code-lang-badge">${md.utils.escapeHtml(lang)}</span>`
      : ''
    const nameText = title || ''
    const label = (langBadge || nameText)
      ? `<span class="code-lang-label${title ? ' is-file' : ''}">`
        + langBadge
        + (nameText ? `<span class="code-lang-name">${md.utils.escapeHtml(nameText)}</span>` : '')
        + '</span>'
      : ''
    const collapsed = rawLines.length > CODE_FOLD_THRESHOLD ? ' code-collapsed' : ''
    return `<div class="code-wrapper${collapsed}" data-lines="${rawLines.length}">`
      + label + '<pre class="hljs"><code>' + lines + '</code></pre></div>'
  }
})
  .use(markdownItFootnote)
  .use(markdownItTaskLists)
  // throwOnError:false —— 公式写错时只在原位渲染一段红色错误提示（KaTeX 自带），
  // 绝不能让它抛异常：md.render() 一抛，整篇正文的 HTML 就全没了。
  .use(markdownItKatex, { throwOnError: false, errorColor: '#cc0000' })
  .use(admonitionPlugin)
  .use(imageGridPlugin)
  .use(codeGroupPlugin)

/* ---------------------------------------------------------------
   提示块（Admonition）
   --------------------------------------------------------------- */

/** 英文写法与中文别名都收，编辑器里写哪个都行 */
const ADMONITION_ALIAS: Record<string, string> = {
  NOTE: 'note', 说明: 'note', 备注: 'note',
  TIP: 'tip', 提示: 'tip', 技巧: 'tip',
  IMPORTANT: 'important', 重要: 'important',
  WARNING: 'warning', 警告: 'warning', 注意: 'warning',
  CAUTION: 'caution', 小心: 'caution', 危险: 'caution'
}

/** 显示在块首的标签文字。不做五色区分 —— 全站只有暖纸 + 陶土一个强调色 */
const ADMONITION_TITLE: Record<string, string> = {
  note: '说明',
  tip: '提示',
  important: '重要',
  warning: '警告',
  caution: '小心'
}

const ADMONITION_MARK = /^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION|说明|备注|提示|技巧|重要|警告|注意|小心|危险)\]\s*/i

/**
 * 把 `> [!NOTE]` 开头的引用块改写成提示块。
 *
 * 为什么用 GFM 写法而不是自己发明 `:::tip`：
 * GitHub、Obsidian、Typora 都认这一套，作者在别的编辑器里写好的草稿可以直接粘过来，
 * 编辑器里也不需要额外的语法高亮支持。
 */
function admonitionPlugin(mdInstance: any) {
  mdInstance.core.ruler.push('admonition', (state: any) => {
    const tokens = state.tokens
    for (let i = 0; i < tokens.length; i++) {
      if (tokens[i].type !== 'blockquote_open') continue
      const pOpen = tokens[i + 1]
      const inline = tokens[i + 2]
      const pClose = tokens[i + 3]
      if (pOpen?.type !== 'paragraph_open' || inline?.type !== 'inline' || pClose?.type !== 'paragraph_close') continue

      const m = (inline.content || '').match(ADMONITION_MARK)
      if (!m) continue
      const type = ADMONITION_ALIAS[m[1].toUpperCase()] || ADMONITION_ALIAS[m[1]] || 'note'

      // 找配对的 blockquote_close（引用块可以嵌套，按深度配对）
      let depth = 0
      let closeIdx = -1
      for (let j = i; j < tokens.length; j++) {
        if (tokens[j].type === 'blockquote_open') depth++
        else if (tokens[j].type === 'blockquote_close' && --depth === 0) { closeIdx = j; break }
      }
      if (closeIdx < 0) continue

      tokens[i].meta = { ...(tokens[i].meta || {}), admonition: type }
      tokens[closeIdx].meta = { ...(tokens[closeIdx].meta || {}), admonitionEnd: true }

      // 剥掉 `[!NOTE]` 标记：content 与 children 是两份，都要改
      inline.content = inline.content.replace(ADMONITION_MARK, '')
      const kids = inline.children || []
      if (kids.length && kids[0].type === 'text') {
        kids[0].content = kids[0].content.replace(ADMONITION_MARK, '')
        if (!kids[0].content) {
          kids.shift()
          // 标记后面紧跟的换行也一起去掉，否则块首会多一个空行
          if (kids.length && kids[0].type === 'softbreak') kids.shift()
        }
      }
      // 只有 `> [!NOTE]` 没有任何内容：整段隐藏，避免留下空段落
      if (!kids.length) {
        tokens[i + 1].meta = { ...(tokens[i + 1].meta || {}), hidden: true }
        tokens[i + 3].meta = { ...(tokens[i + 3].meta || {}), hidden: true }
      }
    }
  })
}

md.renderer.rules.blockquote_open = function (tokens: any, idx: number) {
  const type = tokens[idx].meta?.admonition
  if (!type) return '<blockquote>\n'
  return `<div class="admonition admonition-${type}" data-title="${ADMONITION_TITLE[type] || '说明'}">\n`
}
md.renderer.rules.blockquote_close = function (tokens: any, idx: number) {
  return tokens[idx].meta?.admonitionEnd ? '</div>\n' : '</blockquote>\n'
}

/* ---------------------------------------------------------------
   连续图片自动成组
   --------------------------------------------------------------- */

const defaultParagraphOpen = md.renderer.rules.paragraph_open
  || ((tokens: any, idx: number, options: any, _env: any, self: any) => self.renderToken(tokens, idx, options))
const defaultParagraphClose = md.renderer.rules.paragraph_close
  || ((tokens: any, idx: number, options: any, _env: any, self: any) => self.renderToken(tokens, idx, options))

/** 这一段是否「只包含一张图片」（允许图片前后有空白） */
function isImageOnlyParagraph(tokens: any[], i: number): boolean {
  const open = tokens[i]
  const inline = tokens[i + 1]
  const close = tokens[i + 2]
  if (open?.type !== 'paragraph_open' || inline?.type !== 'inline' || close?.type !== 'paragraph_close') return false
  const meaningful = (inline.children || []).filter(
    (c: any) => c.type === 'image' || (c.type === 'text' && c.content.trim() !== '')
  )
  return meaningful.length === 1 && meaningful[0].type === 'image'
}

/**
 * 连续 2 段以上的「单图段落」合并成图片网格。
 *
 * 文章里贴题目截图时，一张一行会把正文拉得很长；两张以上并排后
 * 一眼能扫完，也不用来回滚动。合并发生在 token 层，SSR 输出的 HTML 就已经是网格，
 * 不存在「先单列渲染、再由客户端重排」的跳动。
 */
function imageGridPlugin(mdInstance: any) {
  mdInstance.core.ruler.push('image-grid', (state: any) => {
    const tokens = state.tokens
    let i = 0
    while (i < tokens.length) {
      if (!isImageOnlyParagraph(tokens, i)) { i += 1; continue }
      let last = i
      let count = 1
      while (isImageOnlyParagraph(tokens, last + 3)) { last += 3; count++ }
      if (count >= 2) {
        for (let k = i; k <= last; k += 3) {
          tokens[k].meta = { ...(tokens[k].meta || {}), inGrid: true, gridOpen: k === i }
          tokens[k + 2].meta = { ...(tokens[k + 2].meta || {}), inGrid: true, gridClose: k === last }
        }
      }
      i = last + 3
    }
  })
}

md.renderer.rules.paragraph_open = function (tokens: any, idx: number, options: any, env: any, self: any) {
  const meta = tokens[idx].meta
  if (meta?.hidden) return ''
  if (meta?.gridOpen) return '<div class="img-grid">'
  if (meta?.inGrid) return ''
  return defaultParagraphOpen(tokens, idx, options, env, self)
}
md.renderer.rules.paragraph_close = function (tokens: any, idx: number, options: any, env: any, self: any) {
  const meta = tokens[idx].meta
  if (meta?.hidden) return ''
  if (meta?.gridClose) return '</div>\n'
  if (meta?.inGrid) return ''
  return defaultParagraphClose(tokens, idx, options, env, self)
}

/* ---------------------------------------------------------------
   代码组（同一段逻辑的多语言版本，Tab 切换）
   --------------------------------------------------------------- */

/**
 * 连续出现、且都带 `tab="…"` 的代码块合并成一组：
 *
 *   ```java tab="后端 Java"
 *   ```vue  tab="前端 Vue"
 *
 * 少于 2 段时不合并（写错一个有 tab 不会把另一块拖下水）；
 * 中间的空白行不产生 token，所以「空行分隔」不影响相邻判断。
 */
function codeGroupPlugin(mdInstance: any) {
  mdInstance.core.ruler.push('code-group', (state: any) => {
    const tokens = state.tokens
    let i = 0
    while (i < tokens.length) {
      if (tokens[i].type !== 'fence' || !fenceTabLabel(tokens[i].info)) { i += 1; continue }
      const run: number[] = []
      let j = i
      while (j < tokens.length && tokens[j].type === 'fence' && fenceTabLabel(tokens[j].info)) {
        run.push(j)
        j += 1
      }
      if (run.length >= 2) {
        const labels = run.map((k) => fenceTabLabel(tokens[k].info))
        run.forEach((k, n) => {
          tokens[k].meta = {
            ...(tokens[k].meta || {}),
            codeGroup: true,
            groupFirst: n === 0,
            groupLast: n === run.length - 1,
            groupLabels: labels
          }
        })
      }
      i = j
    }
  })
}

/**
 * 围栏代码块渲染。
 *
 * 为什么要自己写 fence 规则：markdown-it 默认会看 highlight() 的返回值是不是以 `<pre` 开头，
 * 不是就自动再包一层 `<pre><code class="language-x">`。我们的 highlight 返回的是
 * `<div class="code-wrapper">`，于是每个代码块外面都多套了一个 `<pre>`，后果有两个：
 *   1. 多出 `<pre>` 的默认 margin，代码块上下间距比设计值大；
 *   2. `.article-content > .code-wrapper` 的破栏规则永远匹配不上（它变成 pre 的孙子），
 *      代码块一直没能比正文更宽 —— P0-2 里「代码破栏」实际上是失效的。
 * 这里直接返回 highlight 的结果，不让框架补壳。
 */
md.renderer.rules.fence = function (tokens: any, idx: number, options: any) {
  const token = tokens[idx]
  const info = token.info ? md.utils.unescapeAll(token.info).trim() : ''
  // 与 markdown-it 内部一致的拆分：第一个词是语言名，其余原样（含空格）传给 highlight
  const parts = info ? info.split(/(\s+)/g) : ['']
  const langName = parts[0] || ''
  const langAttrs = parts.slice(2).join('')
  const body = options.highlight
    ? (options.highlight(token.content, langName, langAttrs) || md.utils.escapeHtml(token.content))
    : md.utils.escapeHtml(token.content)

  const meta = token.meta || {}
  if (!meta.codeGroup) return body + '\n'

  let html = ''
  if (meta.groupFirst) {
    const labels: string[] = meta.groupLabels || []
    const tabs = labels
      .map((label, i) => `<button type="button" class="code-group-tab${i === 0 ? ' is-active' : ''}"`
        + ` role="tab" aria-selected="${i === 0}" data-tab-index="${i}">${md.utils.escapeHtml(label)}</button>`)
      .join('')
    html += `<div class="code-group"><div class="code-group-tabs" role="tablist">${tabs}</div>`
  }
  html += `<div class="code-group-panel${meta.groupFirst ? ' is-active' : ''}" role="tabpanel">${body}</div>`
  if (meta.groupLast) html += '</div>'
  return html + '\n'
}

/**
 * 标题：生成 id 并由 slugger 去重（修复「同名小节撞 id」）。
 * env.slugger 由 renderMarkdown / extractHeadings 注入，避免模块级状态在 SSR 并发时串味。
 */md.renderer.rules.heading_open = function (tokens: any, idx: number, _options: any, env: any) {
  const token = tokens[idx]
  const next = tokens[idx + 1]
  const slugger = env?.slugger || createSlugger()
  const text = next && next.type === 'inline' ? plainHeadingText(next.content) : ''
  const id = slugger(text || 'section')
  token.attrSet('id', id)
  return `<${token.tag} id="${md.utils.escapeHtml(id)}">`
}

/**
 * 标题末尾的 `#` 锚点：鼠标悬停时出现，点一下地址栏就带上锚点，
 * 方便把「指向某一节」的链接发出去。aria-hidden，不进入朗读顺序。
 */
md.renderer.rules.heading_close = function (tokens: any, idx: number) {
  // 顺序是 heading_open / inline / heading_close，所以要退两格才能拿到开了 id 的那个 token
  const open = tokens[idx - 2]
  const id = open?.attrGet?.('id') || ''
  const anchor = id
    ? `<a class="heading-anchor" href="#${md.utils.escapeHtml(id)}" aria-hidden="true" tabindex="-1">#</a>`
    : ''
  return anchor + `</${tokens[idx].tag}>\n`
}

/**
 * 表格：包一层横向滚动容器（修复「窄屏宽表撑破卡片」）。
 * markdown-it 默认输出 `<table>`，这里改成 `<div class="table-wrap"><table>`。
 */
md.renderer.rules.table_open = () => '<div class="table-wrap"><table>'
md.renderer.rules.table_close = () => '</table></div>'

/**
 * 正文图片：按正文列宽取图（修复「直接引用上传原图」）。
 * 在渲染阶段就改好 URL，SSR 输出的 HTML 里已经是处理过的地址，
 * 不会出现「先下原图、再由客户端换成小图」的双重下载。
 * 非 OSS 外链由 ossImage 内部原样返回，不受影响。
 */
const defaultImageRule = md.renderer.rules.image
  || ((tokens: any, idx: number, options: any, _env: any, self: any) => self.renderToken(tokens, idx, options))

md.renderer.rules.image = function (tokens: any, idx: number, options: any, env: any, self: any) {
  const token = tokens[idx]
  const srcIdx = token.attrIndex('src')
  if (srcIdx >= 0) {
    const raw = token.attrs![srcIdx][1]
    token.attrs![srcIdx][1] = ossImage(raw, { w: IMG_W.content })
  }
  // 图片延迟加载 + 异步解码：正文图通常在首屏之外，别和首屏抢带宽
  token.attrSet('loading', 'lazy')
  token.attrSet('decoding', 'async')
  return defaultImageRule(tokens, idx, options, env, self)
}

/** 渲染正文。每次调用新建 slugger，保证同一篇文章内标题 id 稳定且唯一 */
export function renderMarkdown(content: string): string {
  return md.render(content || '', { slugger: createSlugger() })
}

/**
 * 从 Markdown 里提取标题，供目录使用。
 *
 * 走 token 而不是正则：代码块里以 `#` 开头的注释不会被误判成标题，
 * 且与 renderMarkdown 共用同一套 slug 规则，id 天然一致。
 */
export function extractHeadings(content: string): HeadingItem[] {
  if (!content) return []
  const slugger = createSlugger()
  const tokens = md.parse(content, {})
  const items: HeadingItem[] = []

  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i]
    if (token.type !== 'heading_open') continue
    const inline = tokens[i + 1]
    const level = Number(token.tag.slice(1)) || 2
    const text = inline && inline.type === 'inline' ? plainHeadingText(inline.content) : ''
    items.push({ id: slugger(text || 'section'), text: text || '（无标题）', level, depth: 0 })
  }

  // 层级归一化：以最小标题级别为 0，避免「从 ## 写起」时整份目录缩进错位
  if (items.length) {
    const minLevel = Math.min(...items.map((h) => h.level))
    for (const h of items) h.depth = Math.min(h.level - minLevel, 3)
  }
  return items
}

export { md }

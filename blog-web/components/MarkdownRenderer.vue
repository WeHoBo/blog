<template>
  <div class="article-content" v-html="renderedHtml" ref="contentRef"></div>
</template>

<script setup lang="ts">
import MarkdownIt from 'markdown-it'
// 仅引入常用语言子集（~300KB），体积远小于完整包；文章页必需，SSR 同样可用
import hljs from 'highlight.js/lib/common'
import markdownItFootnote from 'markdown-it-footnote'
import markdownItTaskLists from 'markdown-it-task-lists'
// 正文图片点击放大。手写实现放独立模块，避免这里继续膨胀（见 utils/imageLightbox.ts 顶部注释）
import { bindImageLightbox } from '~/utils/imageLightbox'

const props = defineProps<{ content: string }>()

// mermaid 体积大（含上百个子模块，~3MB），放在 public/vendor 运行时按需加载，
// 不参与打包 —— 避免其所有子模块被 Nuxt prefetch 到每个页面
let mermaid: any = null
const needMermaid = (s: string) => /```mermaid\b/.test(s || '')

async function ensureMermaid() {
  if (!mermaid) {
    mermaid = (await import(/* @vite-ignore */ '/vendor/mermaid/mermaid.esm.min.mjs')).default
    mermaid.initialize({ startOnLoad: false, theme: 'default' })
  }
}

/** 超过这个行数的代码块默认折叠，右上角给「展开」按钮 */
const CODE_FOLD_THRESHOLD = 30

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

    // 有 title 时展示文件名（读者更关心"这是哪个文件"），否则退回语言名
    const labelText = title || lang || ''
    const label = labelText
      ? `<span class="code-lang-label${title ? ' is-file' : ''}">${md.utils.escapeHtml(labelText)}</span>`
      : ''
    const collapsed = rawLines.length > CODE_FOLD_THRESHOLD ? ' code-collapsed' : ''
    return `<div class="code-wrapper${collapsed}" data-lines="${rawLines.length}">`
      + label + '<pre class="hljs"><code>' + lines + '</code></pre></div>'
  }
})
.use(markdownItFootnote)
.use(markdownItTaskLists)

md.renderer.rules.heading_open = function (tokens: any, idx: number) {
  const token = tokens[idx]
  const next = tokens[idx + 1]
  if (next && next.type === 'inline') {
    const text = next.content
    const id = text.toLowerCase().replace(/\s+/g, '-').replace(/[^\w\u4e00-\u9fff\-]/g, '')
    return `<${token.tag} id="${id}">`
  }
  return `<${token.tag}>`
}

const renderedHtml = computed(() => md.render(props.content || ''))
const contentRef = ref<HTMLElement | null>(null)

/**
 * 所有「依赖渲染后 DOM」的增强统一在这里做：
 *   - mermaid 渲染
 *   - 代码块工具条（复制 / 换行 / 展开折叠）
 *   - mermaid 图点击放大
 *   - 正文图片点击放大（灯箱）
 * <p>
 * 以前这些逻辑写在 onMounted 里、只在组件挂载时跑一次；而正文可能：
 *   - 在编辑器里实时变化（v-html 被反复重写）
 *   - 在文章页做客户端路由跳转（组件不重新挂载，只换 props）
 * 两种情况都会让这些增强在旧的 DOM 上失效。改成 watch + nextTick 之后，
 * 每次内容真正变化都会重新挂一遍增强，实时预览里的 mermaid 和复制按钮才能正常工作。
 */
async function enhance(el: HTMLElement) {
  // Mermaid rendering（按需加载）
  const mermaidEls = el.querySelectorAll('.mermaid')
  if (mermaidEls.length > 0 || needMermaid(props.content)) {
    await ensureMermaid()
    const nodes = el.querySelectorAll('.mermaid')
    if (nodes.length > 0) {
      try {
        await mermaid.run({ nodes: Array.from(nodes) })
      } catch {}
    }
  }

  // 代码块工具条：复制 / 换行开关 / 展开折叠
  // 先清旧的再挂，避免重复绑定（内容重渲染会整段替换 DOM，但编辑器实时预览可能连续触发 enhance）
  el.querySelectorAll('.code-actions').forEach((b) => b.remove())
  el.querySelectorAll('.code-wrapper').forEach((wrapper) => {
    const node = wrapper as HTMLElement
    const pre = node.querySelector('pre.hljs')
    if (!pre) return
    node.style.position = 'relative'

    const actions = document.createElement('div')
    actions.className = 'code-actions'

    // 复制：按「行」重建原文并补回 \n。
    // v-html 渲染出的 <code> 里每行是独立的 block 级 <span class="line">，
    // 行与行之间没有任何换行符，直接取 textContent 会把整段代码挤成一行。
    const copyBtn = document.createElement('button')
    copyBtn.type = 'button'
    copyBtn.className = 'code-action-btn'
    copyBtn.textContent = '复制'
    copyBtn.setAttribute('aria-label', '复制代码')
    copyBtn.onclick = () => {
      const codeEl = pre.querySelector('code')
      const lineEls = codeEl ? Array.from(codeEl.querySelectorAll<HTMLElement>('.line')) : []
      const code = lineEls.length
        ? lineEls.map((l) => l.textContent || '').join('\n')
        : (codeEl?.textContent || '')
      const done = () => { copyBtn.textContent = '✓'; setTimeout(() => copyBtn.textContent = '复制', 2000) }
      if (navigator.clipboard) navigator.clipboard.writeText(code).then(done)
      else { const t = document.createElement('textarea'); t.value = code; document.body.appendChild(t); t.select(); document.execCommand('copy'); document.body.removeChild(t); done() }
    }

    // 换行开关：长行代码默认横向滚动，窄屏读者可切到自动换行
    const wrapBtn = document.createElement('button')
    wrapBtn.type = 'button'
    wrapBtn.className = 'code-action-btn'
    wrapBtn.textContent = '换行'
    wrapBtn.setAttribute('aria-label', '切换自动换行')
    wrapBtn.setAttribute('aria-pressed', 'false')
    wrapBtn.onclick = () => {
      const on = node.classList.toggle('code-wrap')
      wrapBtn.classList.toggle('is-active', on)
      wrapBtn.setAttribute('aria-pressed', String(on))
    }

    actions.appendChild(copyBtn)
    actions.appendChild(wrapBtn)

    // 只在超长代码块上出现（短块没必要多一个按钮）
    if (Number(node.dataset.lines || 0) > CODE_FOLD_THRESHOLD) {
      const foldBtn = document.createElement('button')
      foldBtn.type = 'button'
      foldBtn.className = 'code-action-btn'
      foldBtn.textContent = node.classList.contains('code-collapsed') ? '展开' : '收起'
      foldBtn.setAttribute('aria-label', '展开或收起代码块')
      foldBtn.onclick = () => {
        const collapsed = node.classList.toggle('code-collapsed')
        foldBtn.textContent = collapsed ? '展开' : '收起'
      }
      actions.appendChild(foldBtn)
    }

    node.appendChild(actions)
  })

  // Mermaid click to zoom
  el.querySelectorAll('.mermaid-wrapper').forEach(wrapper => {
    if ((wrapper as HTMLElement).dataset.zoomBound === '1') return
    ;(wrapper as HTMLElement).dataset.zoomBound = '1'
    wrapper.addEventListener('click', async () => {
      if (!mermaid) {
        try { await ensureMermaid() } catch { return }
      }
      const div = document.createElement('div')
      div.className = 'fixed inset-0 bg-black/70 z-50 flex items-center justify-center p-8'
      div.innerHTML = '<div class="bg-white dark:bg-gray-900 rounded-xl p-6 max-w-4xl max-h-full overflow-auto"><button aria-label="关闭" class="absolute top-4 right-4 text-2xl text-gray-500 hover:text-gray-800 dark:hover:text-white">&times;</button></div>'
      const clone = (wrapper.querySelector('.mermaid') as HTMLElement).cloneNode(true) as HTMLElement
      clone.style.transform = 'scale(1.2)'
      const box = div.querySelector('div')!
      box.appendChild(clone)
      box.querySelector('button')!.onclick = () => div.remove()
      div.onclick = (e) => { if (e.target === div) div.remove() }
      document.body.appendChild(div)
      try { mermaid.run({ nodes: [clone.querySelector('svg')].filter(Boolean) }) } catch {}
    })
    ;(wrapper as HTMLElement).style.cursor = 'pointer'
    wrapper.setAttribute('title', '点击放大')
  })

  // 正文图片点击放大（灯箱）：与上面的 mermaid 放大对称，图片终于也能点开了
  bindImageLightbox(el)
}

onMounted(async () => {
  const el = contentRef.value
  if (el) await enhance(el)
})

// 内容变化时重新挂增强：flush:'post' 保证此刻 v-html 已经更新到 DOM
watch(() => props.content, async () => {
  await nextTick()
  const el = contentRef.value
  if (el) await enhance(el)
})
</script>

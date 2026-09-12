<template>
  <div class="article-content" v-html="renderedHtml" ref="contentRef"></div>
</template>

<script setup lang="ts">
import MarkdownIt from 'markdown-it'
// 仅引入常用语言子集（~300KB），体积远小于完整包；文章页必需，SSR 同样可用
import hljs from 'highlight.js/lib/common'
import markdownItFootnote from 'markdown-it-footnote'
import markdownItTaskLists from 'markdown-it-task-lists'

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

const md = new MarkdownIt({
  // html: false —— 禁用正文中的裸 HTML，避免配合 v-html 造成存储型 XSS。
  // 代码块高亮、mermaid 等由下方 renderer 规则生成 HTML，不受此开关影响。
  html: false, linkify: true, typographer: true, breaks: true,
  highlight(str: string, lang: string) {
    if (lang === 'mermaid') {
      return '<div class="mermaid-wrapper"><div class="mermaid">' + md.utils.escapeHtml(str) + '</div></div>'
    }
    const label = lang ? `<span class="code-lang-label">${lang}</span>` : ''
    // markdown-it 传入的代码块内容末尾自带一个 \n；highlight.js 的结果末尾也会补一个 \n。
    // 不剥离的话，代码块底部会多渲染一个空行，复制出来的代码末尾也会多出换行。
    const content = str.replace(/\n$/, '')
    if (lang && hljs.getLanguage(lang)) {
      try {
        const result = hljs.highlight(content, { language: lang, ignoreIllegals: true }).value.replace(/\n$/, '')
        // 空行不再塞 `&nbsp;` 占位：`.line` 的 min-height 已能撑出行高，
        // 保留占位符会让复制出来的空行里混入不换行空格（U+00A0）。
        const lines = result.split('\n').map((l) => `<span class="line"><span class="line-no"></span>${l}</span>`).join('')
        return '<div class="code-wrapper">' + label + '<pre class="hljs"><code>' + lines + '</code></pre></div>'
      } catch {}
    }
    const lines = content.split('\n').map((l) => `<span class="line"><span class="line-no"></span>${md.utils.escapeHtml(l)}</span>`).join('')
    return '<div class="code-wrapper">' + label + '<pre class="hljs"><code>' + lines + '</code></pre></div>'
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
 * 所有「依赖渲染后 DOM」的增强（mermaid 渲染、代码块复制按钮、点击放大）统一在这里做。
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

  // Copy buttons（先清旧的再挂，避免重复绑定）
  el.querySelectorAll('pre.hljs .code-copy-btn').forEach(b => b.remove())
  el.querySelectorAll('pre.hljs').forEach((pre) => {
    const codeBlock = pre.closest('.code-wrapper')
    const container = codeBlock || pre
    ;(container as HTMLElement).style.position = 'relative'

    const btn = document.createElement('button')
    btn.className = 'code-copy-btn'; btn.textContent = '复制'
    btn.setAttribute('aria-label', '复制代码')
    btn.onclick = () => {
      // 复制时按「行」重建原文并补回 \n：
      // v-html 渲染出的 <code> 里每行是独立的 block 级 <span class="line">，
      // 行与行之间没有任何换行符，直接取 textContent 会把整段代码挤成一行。
      const codeEl = pre.querySelector('code')
      const lineEls = codeEl ? Array.from(codeEl.querySelectorAll<HTMLElement>('.line')) : []
      const code = lineEls.length
        ? lineEls.map((l) => l.textContent || '').join('\n')
        : (codeEl?.textContent || '')
      const copy = () => { btn.textContent = '✓'; setTimeout(() => btn.textContent = '复制', 2000) }
      if (navigator.clipboard) navigator.clipboard.writeText(code).then(copy)
      else { const t = document.createElement('textarea'); t.value = code; document.body.appendChild(t); t.select(); document.execCommand('copy'); document.body.removeChild(t); copy() }
    }
    container.appendChild(btn)
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

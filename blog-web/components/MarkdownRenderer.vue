<template>
  <div class="article-content" v-html="renderedHtml" ref="contentRef"></div>
</template>

<script setup lang="ts">
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import markdownItFootnote from 'markdown-it-footnote'
import markdownItTaskLists from 'markdown-it-task-lists'
import mermaid from 'mermaid'

const props = defineProps<{ content: string }>()

mermaid.initialize({ startOnLoad: false, theme: 'default' })

const md = new MarkdownIt({
  html: true, linkify: true, typographer: true, breaks: true,
  highlight(str: string, lang: string) {
    if (lang === 'mermaid') {
      return '<div class="mermaid-wrapper"><div class="mermaid">' + md.utils.escapeHtml(str) + '</div></div>'
    }
    const label = lang ? `<span class="code-lang-label">${lang}</span>` : ''
    if (lang && hljs.getLanguage(lang)) {
      try {
        const result = hljs.highlight(str, { language: lang, ignoreIllegals: true }).value
        const lines = result.split('\n').map((l, i) => `<span class="line"><span class="line-no"></span>${l || '&nbsp;'}</span>`).join('')
        return '<div class="code-wrapper">' + label + '<pre class="hljs"><code>' + lines + '</code></pre></div>'
      } catch {}
    }
    const lines = str.split('\n').map((l, i) => `<span class="line"><span class="line-no"></span>${md.utils.escapeHtml(l) || '&nbsp;'}</span>`).join('')
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

onMounted(async () => {
  const el = contentRef.value
  if (!el) return

  // Mermaid rendering
  const mermaidEls = el.querySelectorAll('.mermaid')
  if (mermaidEls.length > 0) {
    await mermaid.run({ nodes: Array.from(mermaidEls) })
  }

  // Copy buttons + Mermaid fullscreen
  el.querySelectorAll('pre.hljs').forEach((pre, i) => {
    const codeBlock = pre.closest('.code-wrapper')
    const container = codeBlock || pre
    ;(container as HTMLElement).style.position = 'relative'

    const btn = document.createElement('button')
    btn.className = 'code-copy-btn'; btn.textContent = '复制'
    btn.onclick = () => {
      const code = pre.querySelector('code')?.textContent || ''
      const copy = () => { btn.textContent = '✓'; setTimeout(() => btn.textContent = '复制', 2000) }
      if (navigator.clipboard) navigator.clipboard.writeText(code).then(copy)
      else { const t = document.createElement('textarea'); t.value = code; document.body.appendChild(t); t.select(); document.execCommand('copy'); document.body.removeChild(t); copy() }
    }
    container.appendChild(btn)
  })

  // Mermaid click to zoom
  el.querySelectorAll('.mermaid-wrapper').forEach(wrapper => {
    wrapper.addEventListener('click', () => {
      const div = document.createElement('div')
      div.className = 'fixed inset-0 bg-black/70 z-50 flex items-center justify-center p-8'
      div.innerHTML = '<div class="bg-white dark:bg-gray-900 rounded-xl p-6 max-w-4xl max-h-full overflow-auto"><button class="absolute top-4 right-4 text-2xl text-gray-500 hover:text-gray-800 dark:hover:text-white">&times;</button></div>'
      const clone = (wrapper.querySelector('.mermaid') as HTMLElement).cloneNode(true) as HTMLElement
      clone.style.transform = 'scale(1.2)'
      const box = div.querySelector('div')!
      box.appendChild(clone)
      box.querySelector('button')!.onclick = () => div.remove()
      div.onclick = (e) => { if (e.target === div) div.remove() }
      document.body.appendChild(div)
      mermaid.run({ nodes: [clone.querySelector('svg')].filter(Boolean) })
    })
    ;(wrapper as HTMLElement).style.cursor = 'pointer'
    wrapper.setAttribute('title', '点击放大')
  })
})
</script>
